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

import android.content.Context.MODE_PRIVATE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.utils.AACPManager
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun EarDetectionSwitch() {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)
    val service = ServiceManager.getService()!!

    val sharedPreferenceKey = "automatic_ear_detection"

    val earDetectionEnabledValue = service.aacpManager.controlCommandStatusList.find {
        it.identifier == AACPManager.Companion.ControlCommandIdentifiers.EAR_DETECTION_CONFIG
    }?.value?.takeIf { it.isNotEmpty() }?.get(0)

    var earDetectionEnabled by remember {
        mutableStateOf(
            if (earDetectionEnabledValue != null) {
                earDetectionEnabledValue == 1.toByte()
            } else {
                sharedPreferences.getBoolean(sharedPreferenceKey, false)
            }
        )
    }

    fun updateEarDetection(enabled: Boolean) {
        earDetectionEnabled = enabled
        service.aacpManager.sendControlCommand(
            AACPManager.Companion.ControlCommandIdentifiers.EAR_DETECTION_CONFIG.value,
            enabled
        )
        service.setEarDetection(enabled)

        sharedPreferences.edit()
            .putBoolean(sharedPreferenceKey, enabled)
            .apply()
    }

    val earDetectionListener = object: AACPManager.ControlCommandListener {
        override fun onControlCommandReceived(controlCommand: AACPManager.ControlCommand) {
            if (controlCommand.identifier == AACPManager.Companion.ControlCommandIdentifiers.EAR_DETECTION_CONFIG.value) {
                val newValue = controlCommand.value.takeIf { it.isNotEmpty() }?.get(0)
                val enabled = newValue == 1.toByte()
                earDetectionEnabled = enabled

                sharedPreferences.edit()
                    .putBoolean(sharedPreferenceKey, enabled)
                    .apply()
            }
        }
    }

    LaunchedEffect(Unit) {
        service.aacpManager.registerControlCommandListener(
            AACPManager.Companion.ControlCommandIdentifiers.EAR_DETECTION_CONFIG,
            earDetectionListener
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            service.aacpManager.unregisterControlCommandListener(
                AACPManager.Companion.ControlCommandIdentifiers.EAR_DETECTION_CONFIG,
                earDetectionListener
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
                updateEarDetection(!earDetectionEnabled)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.ear_detection),
                fontSize = 16.sp,
                color = textColor
            )
        }
        StyledSwitch(
            checked = earDetectionEnabled,
            onCheckedChange = {
                updateEarDetection(it)
            }
        )
    }
}

@Preview
@Composable
fun EarDetectionSwitchPreview() {
    EarDetectionSwitch()
}
