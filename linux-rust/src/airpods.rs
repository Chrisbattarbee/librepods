use crate::bluetooth::aacp::{AACPManager, ProximityKeyType, AACPEvent};
use crate::bluetooth::aacp::ControlCommandIdentifiers;
use crate::bluetooth::att::ATTManager;
use crate::media_controller::MediaController;
use bluer::Address;
use log::{debug, info};
use std::sync::Arc;
use ksni::Handle;
use tokio::sync::Mutex;
use tokio::time::{sleep, Duration};
use crate::ui::tray::MyTray;

pub struct AirPodsDevice {
    pub mac_address: Address,
    pub aacp_manager: AACPManager,
    pub att_manager: ATTManager,
    pub media_controller: Arc<Mutex<MediaController>>,
}

impl AirPodsDevice {
    pub async fn new(mac_address: Address, tray_handle: Handle<MyTray>) -> Self {
        info!("Creating new AirPodsDevice for {}", mac_address);
        let mut aacp_manager = AACPManager::new();
        aacp_manager.connect(mac_address).await;

        let mut att_manager = ATTManager::new();
        att_manager.connect(mac_address).await.expect("Failed to connect ATT");

        tray_handle.update(|tray: &mut MyTray| tray.connected = true).await;

        info!("Sending handshake");
        aacp_manager.send_handshake().await.expect(
            "Failed to send handshake to AirPods device",
        );

        sleep(Duration::from_millis(100)).await;

        info!("Setting feature flags");
        aacp_manager.send_set_feature_flags_packet().await.expect(
            "Failed to set feature flags",
        );

        sleep(Duration::from_millis(100)).await;

        info!("Requesting notifications");
        aacp_manager.send_notification_request().await.expect(
            "Failed to request notifications",
        );

        info!("Requesting Proximity Keys: IRK and ENC_KEY");
        aacp_manager.send_proximity_keys_request(
            vec![ProximityKeyType::Irk, ProximityKeyType::EncKey],
        ).await.expect(
            "Failed to request proximity keys",
        );
        let media_controller = Arc::new(Mutex::new(MediaController::new(mac_address.to_string())));
        let mc_clone = media_controller.clone();
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        let (command_tx, mut command_rx) = tokio::sync::mpsc::unbounded_channel();

        aacp_manager.set_event_channel(tx).await;
        tray_handle.update(|tray: &mut MyTray| tray.command_tx = Some(command_tx)).await;

        let aacp_manager_clone = aacp_manager.clone();
        tokio::spawn(async move {
            while let Some((id, value)) = command_rx.recv().await {
                if let Err(e) = aacp_manager_clone.send_control_command(id, &value).await {
                    log::error!("Failed to send control command: {}", e);
                }
            }
        });

        let (listening_mode_tx, mut listening_mode_rx) = tokio::sync::mpsc::unbounded_channel();
        aacp_manager.subscribe_to_control_command(ControlCommandIdentifiers::ListeningMode, listening_mode_tx).await;
        let tray_handle_clone = tray_handle.clone();
        tokio::spawn(async move {
            while let Some(value) = listening_mode_rx.recv().await {
                tray_handle_clone.update(|tray: &mut MyTray| {
                    tray.listening_mode = Some(value[0]);
                }).await;
            }
        });

        let (allow_off_tx, mut allow_off_rx) = tokio::sync::mpsc::unbounded_channel();
        aacp_manager.subscribe_to_control_command(ControlCommandIdentifiers::AllowOffOption, allow_off_tx).await;
        let tray_handle_clone = tray_handle.clone();
        tokio::spawn(async move {
            while let Some(value) = allow_off_rx.recv().await {
                tray_handle_clone.update(|tray: &mut MyTray| {
                    tray.allow_off_option = Some(value[0]);
                }).await;
            }
        });

        let (conversation_detect_tx, mut conversation_detect_rx) = tokio::sync::mpsc::unbounded_channel();
        aacp_manager.subscribe_to_control_command(ControlCommandIdentifiers::ConversationDetectConfig, conversation_detect_tx).await;
        let tray_handle_clone = tray_handle.clone();
        tokio::spawn(async move {
            while let Some(value) = conversation_detect_rx.recv().await {
                tray_handle_clone.update(|tray: &mut MyTray| {
                    tray.conversation_detect_enabled = Some(value[0] == 0x01);
                }).await;
            }
        });

        tokio::spawn(async move {
            while let Some(event) = rx.recv().await {
                match event {
                    AACPEvent::EarDetection(old_status, new_status) => {
                        debug!("Received EarDetection event: old_status={:?}, new_status={:?}", old_status, new_status);
                        let controller = mc_clone.lock().await;
                        debug!("Calling handle_ear_detection with old_status: {:?}, new_status: {:?}", old_status, new_status);
                        controller.handle_ear_detection(old_status, new_status).await;
                    }
                    AACPEvent::BatteryInfo(battery_info) => {
                        debug!("Received BatteryInfo event: {:?}", battery_info);
                        tray_handle.update(|tray: &mut MyTray| {
                            for b in &battery_info {
                                match b.component as u8 {
                                    0x02 => {
                                        tray.battery_r = Some(b.level);
                                        tray.battery_r_status = Some(b.status);
                                    }
                                    0x04 => {
                                        tray.battery_l = Some(b.level);
                                        tray.battery_l_status = Some(b.status);
                                    }
                                    0x08 => {
                                        tray.battery_c = Some(b.level);
                                        tray.battery_c_status = Some(b.status);
                                    }
                                    _ => {}
                                }
                            }
                        }).await;
                        debug!("Updated tray with new battery info");
                    }
                    AACPEvent::ControlCommand(status) => {
                        debug!("Received ControlCommand event: {:?}", status);
                    }
                    AACPEvent::ConversationalAwareness(status) => {
                        debug!("Received ConversationalAwareness event: {}", status);
                        let controller = mc_clone.lock().await;
                        controller.handle_conversational_awareness(status).await;
                    }
                    _ => {}
                }
            }
        });

        AirPodsDevice {
            mac_address,
            aacp_manager,
            att_manager,
            media_controller,
        }
    }
}
