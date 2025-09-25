 /*
 * LibrePods - AirPods liberated from Apple’s ecosystem
 *
 * Copyright (C) 2025 LibrePods contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.utils.AACPManager
import kotlin.io.encoding.ExperimentalEncodingApi

 @Composable
fun PersonalizedVolumeSwitch() {
    val service = ServiceManager.getService()!!

    val adaptiveVolumeEnabledValue = service.aacpManager.controlCommandStatusList.find {
        it.identifier == AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG
    }?.value?.takeIf { it.isNotEmpty() }?.get(0)

    var adaptiveVolumeEnabled by remember {
        mutableStateOf(
            adaptiveVolumeEnabledValue == 1.toByte()
        )
    }

    fun updatePersonalizedVolume(enabled: Boolean) {
        adaptiveVolumeEnabled = enabled
        service.aacpManager.sendControlCommand(
            AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG.value,
            enabled
        )
    }

    val adaptiveVolumeListener = object: AACPManager.ControlCommandListener {
        override fun onControlCommandReceived(controlCommand: AACPManager.ControlCommand) {
            if (controlCommand.identifier == AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG.value) {
                val newValue = controlCommand.value.takeIf { it.isNotEmpty() }?.get(0)
                adaptiveVolumeEnabled = newValue == 1.toByte()
            }
        }
    }

    LaunchedEffect(Unit) {
        service.aacpManager.registerControlCommandListener(
            AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG,
            adaptiveVolumeListener
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            service.aacpManager.unregisterControlCommandListener(
                AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG,
                adaptiveVolumeListener
            )
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    val isPressed = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                shape = RoundedCornerShape(14.dp),
                color = if (isPressed.value) Color(0xFFE0E0E0) else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed.value = true
                        tryAwaitRelease()
                        isPressed.value = false
                    }
                )
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                updatePersonalizedVolume(!adaptiveVolumeEnabled)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.personalized_volume),
                fontSize = 16.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.personalized_volume_description),
                fontSize = 12.sp,
                color = textColor.copy(0.6f),
                lineHeight = 14.sp,
            )
        }
        StyledSwitch(
            checked = adaptiveVolumeEnabled,
            onCheckedChange = {
                updatePersonalizedVolume(it)
            },
        )
    }
}

@Preview
@Composable
fun PersonalizedVolumeSwitchPreview() {
    PersonalizedVolumeSwitch()
}
