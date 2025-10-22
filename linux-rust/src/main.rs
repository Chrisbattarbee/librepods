mod bluetooth;
mod airpods;
mod media_controller;
mod ui;

use std::env;
use log::{debug, info};
use dbus::blocking::Connection;
use dbus::blocking::stdintf::org_freedesktop_dbus::Properties;
use dbus::message::MatchRule;
use dbus::arg::{RefArg, Variant};
use std::collections::HashMap;
use crate::bluetooth::discovery::find_connected_airpods;
use crate::airpods::AirPodsDevice;
use bluer::Address;
use ksni::TrayMethods;
use crate::ui::tray::MyTray;

#[tokio::main]
async fn main() -> bluer::Result<()> {
    if env::var("RUST_LOG").is_err() {
        unsafe { env::set_var("RUST_LOG", "info"); }
    }

    env_logger::init();


    let tray = MyTray {
        conversation_detect_enabled: None,
        battery_l: None,
        battery_l_status: None,
        battery_r: None,
        battery_r_status: None,
        battery_c: None,
        battery_c_status: None,
        connected: false,
        listening_mode: None,
        allow_off_option: None,
        command_tx: None,
    };
    let handle = tray.spawn().await.unwrap();

    let session = bluer::Session::new().await?;
    let adapter = session.default_adapter().await?;
    adapter.set_powered(true).await?;

    info!("Listening for new connections.");

    info!("Checking for connected devices...");
    match find_connected_airpods(&adapter).await {
        Ok(device) => {
            let name = device.name().await?.unwrap_or_else(|| "Unknown".to_string());
            info!("Found connected AirPods: {}, initializing.", name);
            let _airpods_device = AirPodsDevice::new(device.address(), handle.clone()).await;
        }
        Err(_) => {
            info!("No connected AirPods found.");
        }
    }

    let conn = Connection::new_system()?;
    let rule = MatchRule::new_signal("org.freedesktop.DBus.Properties", "PropertiesChanged");
    conn.add_match(rule, move |_: (), conn, msg| {
        let Some(path) = msg.path() else { return true; };
        if !path.contains("/org/bluez/hci") || !path.contains("/dev_") {
            return true;
        }
        debug!("PropertiesChanged signal for path: {}", path);
        let Ok((iface, changed, _)) = msg.read3::<String, HashMap<String, Variant<Box<dyn RefArg>>>, Vec<String>>() else {
            return true;
        };
        if iface != "org.bluez.Device1" {
            return true;
        }
        let Some(connected_var) = changed.get("Connected") else { return true; };
        let Some(is_connected) = connected_var.0.as_ref().as_u64() else { return true; };
        if is_connected == 0 {
            return true;
        }
        let proxy = conn.with_proxy("org.bluez", path, std::time::Duration::from_millis(5000));
        let Ok(uuids) = proxy.get::<Vec<String>>("org.bluez.Device1", "UUIDs") else { return true; };
        let target_uuid = "74ec2172-0bad-4d01-8f77-997b2be0722a";
        if !uuids.iter().any(|u| u.to_lowercase() == target_uuid) {
            return true;
        }
        let name = proxy.get::<String>("org.bluez.Device1", "Name").unwrap_or_else(|_| "Unknown".to_string());
        let Ok(addr_str) = proxy.get::<String>("org.bluez.Device1", "Address") else { return true; };
        let Ok(addr) = addr_str.parse::<Address>() else { return true; };
        info!("AirPods connected: {}, initializing", name);
        let handle_clone = handle.clone();
        tokio::spawn(async move {
            let _airpods_device = AirPodsDevice::new(addr, handle_clone).await;
        });
        true
    })?;

    info!("Listening for Bluetooth connections via D-Bus...");
    loop {
        conn.process(std::time::Duration::from_millis(1000))?;
    }
}