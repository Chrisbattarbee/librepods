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

package me.kavishdevar.librepods.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.composables.StyledIconButton
import me.kavishdevar.librepods.composables.StyledScaffold
import me.kavishdevar.librepods.composables.StyledSwitch
import me.kavishdevar.librepods.utils.AACPManager
import me.kavishdevar.librepods.utils.RadareOffsetFinder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class, ExperimentalEncodingApi::class)
@Composable
fun AppSettingsScreen(navController: NavController) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showIrkDialog by remember { mutableStateOf(false) }
    var showEncKeyDialog by remember { mutableStateOf(false) }
    var irkValue by remember { mutableStateOf("") }
    var encKeyValue by remember { mutableStateOf("") }
    var irkError by remember { mutableStateOf<String?>(null) }
    var encKeyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val savedIrk = sharedPreferences.getString(AACPManager.Companion.ProximityKeyType.IRK.name, null)
        val savedEncKey = sharedPreferences.getString(AACPManager.Companion.ProximityKeyType.ENC_KEY.name, null)

        if (savedIrk != null) {
            try {
                val decoded = Base64.decode(savedIrk)
                irkValue = decoded.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                irkValue = ""
                e.printStackTrace()
            }
        }

        if (savedEncKey != null) {
            try {
                val decoded = Base64.decode(savedEncKey)
                encKeyValue = decoded.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                encKeyValue = ""
                e.printStackTrace()
            }
        }
    }

    var showPhoneBatteryInWidget by remember {
        mutableStateOf(sharedPreferences.getBoolean("show_phone_battery_in_widget", true))
    }
    var conversationalAwarenessPauseMusicEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("conversational_awareness_pause_music", false))
    }
    var relativeConversationalAwarenessVolumeEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("relative_conversational_awareness_volume", true))
    }
    var openDialogForControlling by remember {
        mutableStateOf(sharedPreferences.getString("qs_click_behavior", "dialog") == "dialog")
    }
    var disconnectWhenNotWearing by remember {
        mutableStateOf(sharedPreferences.getBoolean("disconnect_when_not_wearing", false))
    }

    var takeoverWhenDisconnected by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_disconnected", true))
    }
    var takeoverWhenIdle by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_idle", true))
    }
    var takeoverWhenMusic by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_music", false))
    }
    var takeoverWhenCall by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_call", true))
    }

    var takeoverWhenRingingCall by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_ringing_call", true))
    }
    var takeoverWhenMediaStart by remember {
        mutableStateOf(sharedPreferences.getBoolean("takeover_when_media_start", true))
    }

    var useAlternateHeadTrackingPackets by remember {
        mutableStateOf(sharedPreferences.getBoolean("use_alternate_head_tracking_packets", false))
    }

    var bleOnlyMode by remember {
        mutableStateOf(sharedPreferences.getBoolean("ble_only_mode", false))
    }

    // Ensure the default value is properly set if not exists
    LaunchedEffect(Unit) {
        if (!sharedPreferences.contains("ble_only_mode")) {
            sharedPreferences.edit { putBoolean("ble_only_mode", false) }
        }
    }

    fun validateHexInput(input: String): Boolean {
        val hexPattern = Regex("^[0-9a-fA-F]{32}$")
        return hexPattern.matches(input)
    }

    var isProcessingSdp by remember { mutableStateOf(false) }
    var actAsAppleDevice by remember { mutableStateOf(false) }

    BackHandler(enabled = isProcessingSdp) {}

    StyledScaffold(
        title = stringResource(R.string.app_settings),
        navigationButton = {
            StyledIconButton(
                onClick = { navController.popBackStack() },
                icon = "􀯶",
                darkMode = isDarkTheme
            )
        }
    ) { spacerHeight, hazeState ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .hazeSource(state = hazeState)
        ) {
            Spacer(modifier = Modifier.height(spacerHeight))

            val isDarkTheme = isSystemInDarkTheme()
            val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            val textColor = if (isDarkTheme) Color.White else Color.Black

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.widget).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 8.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showPhoneBatteryInWidget = !showPhoneBatteryInWidget
                            sharedPreferences.edit { putBoolean("show_phone_battery_in_widget", showPhoneBatteryInWidget)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.show_phone_battery_in_widget),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.show_phone_battery_in_widget_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = showPhoneBatteryInWidget,
                        onCheckedChange = {
                            showPhoneBatteryInWidget = it
                            sharedPreferences.edit { putBoolean("show_phone_battery_in_widget", it)}
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.connection_mode).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            bleOnlyMode = !bleOnlyMode
                            sharedPreferences.edit { putBoolean("ble_only_mode", bleOnlyMode)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ble_only_mode),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Text(
                            text = stringResource(R.string.ble_only_mode_description),
                            fontSize = 13.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = bleOnlyMode,
                        onCheckedChange = {
                            bleOnlyMode = it
                            sharedPreferences.edit { putBoolean("ble_only_mode", it)}
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.conversational_awareness).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val sliderValue = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(sliderValue) {
                    if (sharedPreferences.contains("conversational_awareness_volume")) {
                        sliderValue.floatValue = sharedPreferences.getInt("conversational_awareness_volume", 43).toFloat()
                    }
                }

                fun updateConversationalAwarenessPauseMusic(enabled: Boolean) {
                    conversationalAwarenessPauseMusicEnabled = enabled
                    sharedPreferences.edit { putBoolean("conversational_awareness_pause_music", enabled)}
                }

                fun updateRelativeConversationalAwarenessVolume(enabled: Boolean) {
                    relativeConversationalAwarenessVolumeEnabled = enabled
                    sharedPreferences.edit { putBoolean("relative_conversational_awareness_volume", enabled)}
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateConversationalAwarenessPauseMusic(!conversationalAwarenessPauseMusicEnabled)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.conversational_awareness_pause_music),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.conversational_awareness_pause_music_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = conversationalAwarenessPauseMusicEnabled,
                        onCheckedChange = {
                            updateConversationalAwarenessPauseMusic(it)
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateRelativeConversationalAwarenessVolume(!relativeConversationalAwarenessVolumeEnabled)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.relative_conversational_awareness_volume),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.relative_conversational_awareness_volume_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = relativeConversationalAwarenessVolumeEnabled,
                        onCheckedChange = {
                            updateRelativeConversationalAwarenessVolume(it)
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.conversational_awareness_volume),
                    fontSize = 16.sp,
                    color = textColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFFD9D9D9)
                val activeTrackColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5)
                val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)

                Slider(
                    value = sliderValue.floatValue,
                    onValueChange = {
                        sliderValue.floatValue = it
                        sharedPreferences.edit { putInt("conversational_awareness_volume", it.toInt())}
                    },
                    valueRange = 10f..85f,
                    onValueChangeFinished = {
                        sliderValue.floatValue = sliderValue.floatValue.roundToInt().toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(vertical = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = activeTrackColor,
                        inactiveTrackColor = trackColor,
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(4.dp, CircleShape)
                                .background(thumbColor, CircleShape)
                        )
                    },
                    track = {
                        Box (
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            contentAlignment = Alignment.CenterStart
                        )
                        {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(trackColor, RoundedCornerShape(4.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(((sliderValue.floatValue - 10) * 100) /7500)
                                    .height(4.dp)
                                    .background(if (conversationalAwarenessPauseMusicEnabled) trackColor else activeTrackColor, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10%",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = textColor.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = "85%",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = textColor.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.quick_settings_tile).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                fun updateQsClickBehavior(enabled: Boolean) {
                    openDialogForControlling = enabled
                    sharedPreferences.edit { putString("qs_click_behavior", if (enabled) "dialog" else "cycle")}
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateQsClickBehavior(!openDialogForControlling)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.open_dialog_for_controlling),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.open_dialog_for_controlling_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = openDialogForControlling,
                        onCheckedChange = {
                            updateQsClickBehavior(it)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.ear_detection).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                fun updateDisconnectWhenNotWearing(enabled: Boolean) {
                    disconnectWhenNotWearing = enabled
                    sharedPreferences.edit { putBoolean("disconnect_when_not_wearing", enabled)}
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateDisconnectWhenNotWearing(!disconnectWhenNotWearing)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.disconnect_when_not_wearing),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.disconnect_when_not_wearing_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = disconnectWhenNotWearing,
                        onCheckedChange = {
                            updateDisconnectWhenNotWearing(it)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.takeover_header).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.takeover_airpods_state),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenDisconnected = !takeoverWhenDisconnected
                            sharedPreferences.edit { putBoolean("takeover_when_disconnected", takeoverWhenDisconnected)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_disconnected),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_disconnected_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenDisconnected,
                        onCheckedChange = {
                            takeoverWhenDisconnected = it
                            sharedPreferences.edit { putBoolean("takeover_when_disconnected", it)}
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenIdle = !takeoverWhenIdle
                            sharedPreferences.edit { putBoolean("takeover_when_idle", takeoverWhenIdle)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_idle),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_idle_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenIdle,
                        onCheckedChange = {
                            takeoverWhenIdle = it
                            sharedPreferences.edit { putBoolean("takeover_when_idle", it)}
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenMusic = !takeoverWhenMusic
                            sharedPreferences.edit { putBoolean("takeover_when_music", takeoverWhenMusic)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_music),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_music_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenMusic,
                        onCheckedChange = {
                            takeoverWhenMusic = it
                            sharedPreferences.edit { putBoolean("takeover_when_music", it)}
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenCall = !takeoverWhenCall
                            sharedPreferences.edit { putBoolean("takeover_when_call", takeoverWhenCall)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_call),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_call_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenCall,
                        onCheckedChange = {
                            takeoverWhenCall = it
                            sharedPreferences.edit { putBoolean("takeover_when_call", it)}
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.takeover_phone_state),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenRingingCall = !takeoverWhenRingingCall
                            sharedPreferences.edit { putBoolean("takeover_when_ringing_call", takeoverWhenRingingCall)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_ringing_call),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_ringing_call_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenRingingCall,
                        onCheckedChange = {
                            takeoverWhenRingingCall = it
                            sharedPreferences.edit { putBoolean("takeover_when_ringing_call", it)}
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            takeoverWhenMediaStart = !takeoverWhenMediaStart
                            sharedPreferences.edit { putBoolean("takeover_when_media_start", takeoverWhenMediaStart)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.takeover_media_start),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.takeover_media_start_desc),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = takeoverWhenMediaStart,
                        onCheckedChange = {
                            takeoverWhenMediaStart = it
                            sharedPreferences.edit { putBoolean("takeover_when_media_start", it)}
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.advanced_options).uppercase(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro))
                ),
                modifier = Modifier.padding(8.dp, bottom = 2.dp, top = 24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showIrkDialog = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.set_identity_resolving_key),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.set_identity_resolving_key_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showEncKeyDialog = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.set_encryption_key),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.set_encryption_key_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            useAlternateHeadTrackingPackets = !useAlternateHeadTrackingPackets
                            sharedPreferences.edit {
                                putBoolean(
                                    "use_alternate_head_tracking_packets",
                                    useAlternateHeadTrackingPackets)}
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.use_alternate_head_tracking_packets),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.use_alternate_head_tracking_packets_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = useAlternateHeadTrackingPackets,
                        onCheckedChange = {
                            useAlternateHeadTrackingPackets = it
                            sharedPreferences.edit { putBoolean("use_alternate_head_tracking_packets", it)}
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("troubleshooting")
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.troubleshooting),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.troubleshooting_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    actAsAppleDevice = RadareOffsetFinder.isSdpOffsetAvailable()
                }
                val restartBluetoothText = stringResource(R.string.found_offset_restart_bluetooth)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isProcessingSdp,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!isProcessingSdp) {
                                val newValue = !actAsAppleDevice
                                actAsAppleDevice = newValue
                                isProcessingSdp = true
                                coroutineScope.launch {
                                    if (newValue) {
                                        val radareOffsetFinder = RadareOffsetFinder(context)
                                        val success = radareOffsetFinder.findSdpOffset()
                                        if (success) {
                                            Toast.makeText(context, restartBluetoothText, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        RadareOffsetFinder.clearSdpOffset()
                                    }
                                    isProcessingSdp = false
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.act_as_an_apple_device),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.act_as_an_apple_device_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                        if (actAsAppleDevice) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.act_as_an_apple_device_warning),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                    StyledSwitch(
                        checked = actAsAppleDevice,
                        onCheckedChange = {
                            if (!isProcessingSdp) {
                                actAsAppleDevice = it
                                isProcessingSdp = true
                                coroutineScope.launch {
                                    if (it) {
                                        val radareOffsetFinder = RadareOffsetFinder(context)
                                        val success = radareOffsetFinder.findSdpOffset()
                                        if (success) {
                                            Toast.makeText(context, restartBluetoothText, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        RadareOffsetFinder.clearSdpOffset()
                                    }
                                    isProcessingSdp = false
                                }
                            }
                        },
                        enabled = !isProcessingSdp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reset_hook_offset),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = {
                        Text(
                            "Reset Hook Offset",
                            fontFamily = FontFamily(Font(R.font.sf_pro)),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    text = {
                        Text(
                            stringResource(R.string.reset_hook_offset_description),
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        )
                    },
                    confirmButton = {
                        val successText = stringResource(R.string.hook_offset_reset_success)
                        val failureText = stringResource(R.string.hook_offset_reset_failure)
                        TextButton(
                            onClick = {
                                if (RadareOffsetFinder.clearHookOffsets()) {
                                    Toast.makeText(
                                        context,
                                        successText,
                                        Toast.LENGTH_LONG
                                    ).show()

                                    navController.navigate("onboarding") {
                                        popUpTo("settings") { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        failureText,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showResetDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                stringResource(R.string.reset),
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(
                                "Cancel",
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }

            if (showIrkDialog) {
                AlertDialog(
                    onDismissRequest = { showIrkDialog = false },
                    title = {
                        Text(
                            stringResource(R.string.set_identity_resolving_key),
                            fontFamily = FontFamily(Font(R.font.sf_pro)),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.enter_irk_hex),
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = irkValue,
                                onValueChange = {
                                    irkValue = it.lowercase().filter { char -> char.isDigit() || char in 'a'..'f' }
                                    irkError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = irkError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.None
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5),
                                    unfocusedBorderColor = if (isDarkTheme) Color.Gray else Color.LightGray
                                ),
                                supportingText = {
                                    if (irkError != null) {
                                        Text(stringResource(R.string.must_be_32_hex_chars), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                label = { Text(stringResource(R.string.irk_hex_value)) }
                            )
                        }
                    },
                    confirmButton = {
                        val successText = stringResource(R.string.irk_set_success)
                        val errorText = stringResource(R.string.error_converting_hex)
                        TextButton(
                            onClick = {
                                if (!validateHexInput(irkValue)) {
                                    irkError = "Must be exactly 32 hex characters"
                                    return@TextButton
                                }

                                try {
                                    val hexBytes = ByteArray(16)
                                    for (i in 0 until 16) {
                                        val hexByte = irkValue.substring(i * 2, i * 2 + 2)
                                        hexBytes[i] = hexByte.toInt(16).toByte()
                                    }

                                    val base64Value = Base64.encode(hexBytes)
                                    sharedPreferences.edit { putString(AACPManager.Companion.ProximityKeyType.IRK.name, base64Value)}

                                    Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                                    showIrkDialog = false
                                } catch (e: Exception) {
                                    irkError = errorText + " " + (e.message ?: "Unknown error")
                                }
                            }
                        ) {
                            Text(
                                "Save",
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showIrkDialog = false }
                        ) {
                            Text(
                                "Cancel",
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }

            if (showEncKeyDialog) {
                AlertDialog(
                    onDismissRequest = { showEncKeyDialog = false },
                    title = {
                        Text(
                            stringResource(R.string.set_encryption_key),
                            fontFamily = FontFamily(Font(R.font.sf_pro)),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.enter_enc_key_hex),
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = encKeyValue,
                                onValueChange = {
                                    encKeyValue = it.lowercase().filter { char -> char.isDigit() || char in 'a'..'f' }
                                    encKeyError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = encKeyError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    capitalization = KeyboardCapitalization.None
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5),
                                    unfocusedBorderColor = if (isDarkTheme) Color.Gray else Color.LightGray
                                ),
                                supportingText = {
                                    if (encKeyError != null) {
                                        Text(stringResource(R.string.must_be_32_hex_chars), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                label = { Text(stringResource(R.string.enc_key_hex_value)) }
                            )
                        }
                    },
                    confirmButton = {
                        val successText = stringResource(R.string.encryption_key_set_success)
                        val errorText = stringResource(R.string.error_converting_hex)
                        TextButton(
                            onClick = {
                                if (!validateHexInput(encKeyValue)) {
                                    encKeyError = "Must be exactly 32 hex characters"
                                    return@TextButton
                                }

                                try {
                                    val hexBytes = ByteArray(16)
                                    for (i in 0 until 16) {
                                        val hexByte = encKeyValue.substring(i * 2, i * 2 + 2)
                                        hexBytes[i] = hexByte.toInt(16).toByte()
                                    }

                                    val base64Value = Base64.encode(hexBytes)
                                    sharedPreferences.edit { putString(AACPManager.Companion.ProximityKeyType.ENC_KEY.name, base64Value)}

                                    Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                                    showEncKeyDialog = false
                                } catch (e: Exception) {
                                    encKeyError = errorText + " " + (e.message ?: "Unknown error")
                                }
                            }
                        ) {
                            Text(
                                "Save",
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEncKeyDialog = false }
                        ) {
                            Text(
                                "Cancel",
                                fontFamily = FontFamily(Font(R.font.sf_pro)),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                )
            }
        }
    }
}
