use aes::Aes128;
use aes::cipher::generic_array::GenericArray;
use aes::cipher::{BlockEncrypt, KeyInit};
use iced::Theme;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::process::Command;

const BLUEZ_CONFIG_PATH: &str = "/etc/bluetooth/main.conf";
const APPLE_DEVICE_ID: &str = "bluetooth:004C:0000:0000";

/// Status of the BlueZ DeviceID configuration for seamless switching
#[derive(Debug, Clone, PartialEq)]
pub enum DeviceIdStatus {
    /// Correctly configured with Apple's vendor ID
    Configured,
    /// DeviceID line not present in config
    NotConfigured,
    /// Different DeviceID value is configured
    WrongValue(String),
    /// BlueZ config file not found
    FileNotFound,
    /// Error reading or parsing the config file
    ParseError(String),
}

impl std::fmt::Display for DeviceIdStatus {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            DeviceIdStatus::Configured => write!(f, "Configured"),
            DeviceIdStatus::NotConfigured => write!(f, "Not configured"),
            DeviceIdStatus::WrongValue(v) => write!(f, "Wrong value: {}", v),
            DeviceIdStatus::FileNotFound => write!(f, "Config file not found"),
            DeviceIdStatus::ParseError(e) => write!(f, "Error: {}", e),
        }
    }
}

/// Check the current DeviceID configuration in BlueZ
pub fn check_device_id_status() -> DeviceIdStatus {
    let config_path = std::path::Path::new(BLUEZ_CONFIG_PATH);

    if !config_path.exists() {
        return DeviceIdStatus::FileNotFound;
    }

    let content = match std::fs::read_to_string(config_path) {
        Ok(c) => c,
        Err(e) => return DeviceIdStatus::ParseError(e.to_string()),
    };

    // Look for DeviceID line in the config
    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("DeviceID") && trimmed.contains('=') {
            // Parse the value after '='
            if let Some(value) = trimmed.split('=').nth(1) {
                let value = value.trim();
                if value.eq_ignore_ascii_case(APPLE_DEVICE_ID) {
                    return DeviceIdStatus::Configured;
                } else {
                    return DeviceIdStatus::WrongValue(value.to_string());
                }
            }
        }
    }

    DeviceIdStatus::NotConfigured
}

/// Configure the DeviceID in BlueZ config using pkexec for privilege escalation
/// Returns Ok(()) on success, Err(message) on failure
pub fn configure_device_id() -> Result<(), String> {
    // Script to:
    // 1. Backup the config file
    // 2. Remove any existing DeviceID line
    // 3. Add DeviceID under [General] section (or create section if needed)
    let script = format!(
        r#"
        set -e
        CONFIG="{config_path}"
        BACKUP="${{CONFIG}}.bak.$(date +%Y%m%d%H%M%S)"

        # Backup existing config
        if [ -f "$CONFIG" ]; then
            cp "$CONFIG" "$BACKUP"
        fi

        # Check if file exists, create with [General] section if not
        if [ ! -f "$CONFIG" ]; then
            echo "[General]" > "$CONFIG"
            echo "DeviceID = {device_id}" >> "$CONFIG"
            exit 0
        fi

        # Remove any existing DeviceID line
        sed -i '/^[[:space:]]*DeviceID[[:space:]]*=/d' "$CONFIG"

        # Check if [General] section exists
        if grep -q '^\[General\]' "$CONFIG"; then
            # Add DeviceID after [General] line
            sed -i '/^\[General\]/a DeviceID = {device_id}' "$CONFIG"
        else
            # Add [General] section at the beginning with DeviceID
            sed -i '1i [General]\nDeviceID = {device_id}\n' "$CONFIG"
        fi
        "#,
        config_path = BLUEZ_CONFIG_PATH,
        device_id = APPLE_DEVICE_ID
    );

    let output = Command::new("pkexec")
        .args(["sh", "-c", &script])
        .output()
        .map_err(|e| format!("Failed to execute pkexec: {}", e))?;

    if output.status.success() {
        Ok(())
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr);
        if stderr.contains("dismissed") || stderr.contains("Not authorized") {
            Err("Authorization cancelled".to_string())
        } else {
            Err(format!("Configuration failed: {}", stderr))
        }
    }
}

pub fn get_devices_path() -> PathBuf {
    let data_dir = std::env::var("XDG_DATA_HOME")
        .unwrap_or_else(|_| format!("{}/.local/share", std::env::var("HOME").unwrap_or_default()));
    PathBuf::from(data_dir)
        .join("librepods")
        .join("devices.json")
}

pub fn get_preferences_path() -> PathBuf {
    let config_dir = std::env::var("XDG_CONFIG_HOME")
        .unwrap_or_else(|_| format!("{}/.local/share", std::env::var("HOME").unwrap_or_default()));
    PathBuf::from(config_dir)
        .join("librepods")
        .join("preferences.json")
}

pub fn get_app_settings_path() -> PathBuf {
    let config_dir = std::env::var("XDG_CONFIG_HOME")
        .unwrap_or_else(|_| format!("{}/.local/share", std::env::var("HOME").unwrap_or_default()));
    PathBuf::from(config_dir)
        .join("librepods")
        .join("app_settings.json")
}

fn e(key: &[u8; 16], data: &[u8; 16]) -> [u8; 16] {
    let mut swapped_key = *key;
    swapped_key.reverse();
    let mut swapped_data = *data;
    swapped_data.reverse();
    let cipher = Aes128::new(&GenericArray::from(swapped_key));
    let mut block = GenericArray::from(swapped_data);
    cipher.encrypt_block(&mut block);
    let mut result: [u8; 16] = block.into();
    result.reverse();
    result
}

pub fn ah(k: &[u8; 16], r: &[u8; 3]) -> [u8; 3] {
    let mut r_padded = [0u8; 16];
    r_padded[..3].copy_from_slice(r);
    let encrypted = e(k, &r_padded);
    let mut hash = [0u8; 3];
    hash.copy_from_slice(&encrypted[..3]);
    hash
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum MyTheme {
    Light,
    Dark,
    Dracula,
    Nord,
    SolarizedLight,
    SolarizedDark,
    GruvboxLight,
    GruvboxDark,
    CatppuccinLatte,
    CatppuccinFrappe,
    CatppuccinMacchiato,
    CatppuccinMocha,
    TokyoNight,
    TokyoNightStorm,
    TokyoNightLight,
    KanagawaWave,
    KanagawaDragon,
    KanagawaLotus,
    Moonfly,
    Nightfly,
    Oxocarbon,
    Ferra,
}

impl std::fmt::Display for MyTheme {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            Self::Light => "Light",
            Self::Dark => "Dark",
            Self::Dracula => "Dracula",
            Self::Nord => "Nord",
            Self::SolarizedLight => "Solarized Light",
            Self::SolarizedDark => "Solarized Dark",
            Self::GruvboxLight => "Gruvbox Light",
            Self::GruvboxDark => "Gruvbox Dark",
            Self::CatppuccinLatte => "Catppuccin Latte",
            Self::CatppuccinFrappe => "Catppuccin Frappé",
            Self::CatppuccinMacchiato => "Catppuccin Macchiato",
            Self::CatppuccinMocha => "Catppuccin Mocha",
            Self::TokyoNight => "Tokyo Night",
            Self::TokyoNightStorm => "Tokyo Night Storm",
            Self::TokyoNightLight => "Tokyo Night Light",
            Self::KanagawaWave => "Kanagawa Wave",
            Self::KanagawaDragon => "Kanagawa Dragon",
            Self::KanagawaLotus => "Kanagawa Lotus",
            Self::Moonfly => "Moonfly",
            Self::Nightfly => "Nightfly",
            Self::Oxocarbon => "Oxocarbon",
            Self::Ferra => "Ferra",
        })
    }
}

impl From<MyTheme> for Theme {
    fn from(my_theme: MyTheme) -> Self {
        match my_theme {
            MyTheme::Light => Theme::Light,
            MyTheme::Dark => Theme::Dark,
            MyTheme::Dracula => Theme::Dracula,
            MyTheme::Nord => Theme::Nord,
            MyTheme::SolarizedLight => Theme::SolarizedLight,
            MyTheme::SolarizedDark => Theme::SolarizedDark,
            MyTheme::GruvboxLight => Theme::GruvboxLight,
            MyTheme::GruvboxDark => Theme::GruvboxDark,
            MyTheme::CatppuccinLatte => Theme::CatppuccinLatte,
            MyTheme::CatppuccinFrappe => Theme::CatppuccinFrappe,
            MyTheme::CatppuccinMacchiato => Theme::CatppuccinMacchiato,
            MyTheme::CatppuccinMocha => Theme::CatppuccinMocha,
            MyTheme::TokyoNight => Theme::TokyoNight,
            MyTheme::TokyoNightStorm => Theme::TokyoNightStorm,
            MyTheme::TokyoNightLight => Theme::TokyoNightLight,
            MyTheme::KanagawaWave => Theme::KanagawaWave,
            MyTheme::KanagawaDragon => Theme::KanagawaDragon,
            MyTheme::KanagawaLotus => Theme::KanagawaLotus,
            MyTheme::Moonfly => Theme::Moonfly,
            MyTheme::Nightfly => Theme::Nightfly,
            MyTheme::Oxocarbon => Theme::Oxocarbon,
            MyTheme::Ferra => Theme::Ferra,
        }
    }
}
