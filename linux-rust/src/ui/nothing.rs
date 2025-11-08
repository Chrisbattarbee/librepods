use std::collections::HashMap;
use std::sync::Arc;
use iced::{Background, Border, Length, Theme};
use iced::widget::{container, text, column, row, Space};
use crate::bluetooth::att::ATTManager;
use crate::devices::enums::{DeviceData, DeviceInformation, NothingState};
use crate::ui::window::Message;

pub fn nothing_view<'a>(
    mac: &str,
    devices_list: &HashMap<String, DeviceData>,
    state: &NothingState,
    att_manager: Arc<ATTManager>
) -> iced::widget::Container<'a, Message> {
    let mut information_col = iced::widget::column![];
    let mac = mac.to_string();
    if let Some(device) = devices_list.get(mac.as_str()) {
        if let Some(DeviceInformation::Nothing(ref nothing_info)) = device.information {
            information_col = information_col
                .push(text("Device Information").size(18).style(
                    |theme: &Theme| {
                        let mut style = text::Style::default();
                        style.color = Some(theme.palette().primary);
                        style
                    }
                ))
                .push(Space::with_height(iced::Length::from(10)))
                .push(
                    iced::widget::row![
                        text("Serial Number").size(16).style(
                            |theme: &Theme| {
                                let mut style = text::Style::default();
                                style.color = Some(theme.palette().text);
                                style
                            }
                        ),
                        Space::with_width(Length::Fill),
                        text(nothing_info.serial_number.clone()).size(16)
                    ]
                )
                .push(
                    iced::widget::row![
                        text("Firmware Version").size(16).style(
                            |theme: &Theme| {
                                let mut style = text::Style::default();
                                style.color = Some(theme.palette().text);
                                style
                            }
                        ),
                        Space::with_width(Length::Fill),
                        text(nothing_info.firmware_version.clone()).size(16)
                    ]
                );
        }
    }
    container(
        column![
            row![
                text("Noise Control Mode").size(18),
                Space::with_width(Length::Fill),
            //     combobox here
            ],
            container(information_col)
                .style(
                    |theme: &Theme| {
                        let mut style = container::Style::default();
                        style.background = Some(Background::Color(theme.palette().primary.scale_alpha(0.1)));
                        let mut border = Border::default();
                        border.color = theme.palette().text;
                        style.border = border.rounded(20);
                        style
                    }
                )
                .padding(20)
        ]
    )
        .padding(20)
        .center_x(Length::Fill)
        .height(Length::Fill)
}


// if let Err(e) = manager.write(
//         ATTHandles::NothingEverything,
//         &[
//             0x55,
//             0x60, 0x01,
//             0x0F, 0xF0,
//             0x03, 0x00,
//             0x00, 0x01, // the 0x00 is an incremental counter, but it works without it
//             mode.to_byte(), 0x00,
//             0x00, 0x00 // these both bytes were something random, 0 works too
//         ]
//     ).await {
//         log::error!("Failed to set noise cancellation mode for device {}: {}", mac, e);
//     }