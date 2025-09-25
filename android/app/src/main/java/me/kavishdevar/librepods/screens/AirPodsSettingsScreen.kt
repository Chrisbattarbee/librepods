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

package me.kavishdevar.librepods.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.composables.AudioSettings
import me.kavishdevar.librepods.composables.BatteryView
import me.kavishdevar.librepods.composables.CallControlSettings
import me.kavishdevar.librepods.composables.ConnectionSettings
import me.kavishdevar.librepods.composables.MicrophoneSettings
import me.kavishdevar.librepods.composables.NameField
import me.kavishdevar.librepods.composables.NavigationButton
import me.kavishdevar.librepods.composables.NoiseControlSettings
import me.kavishdevar.librepods.composables.PressAndHoldSettings
import me.kavishdevar.librepods.composables.StyledButton
import me.kavishdevar.librepods.composables.StyledIconButton
import me.kavishdevar.librepods.composables.StyledScaffold
import me.kavishdevar.librepods.composables.StyledToggle
import me.kavishdevar.librepods.constants.AirPodsNotifications
import me.kavishdevar.librepods.services.AirPodsService
import me.kavishdevar.librepods.ui.theme.LibrePodsTheme
import me.kavishdevar.librepods.utils.AACPManager
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
@Composable
fun AirPodsSettingsScreen(dev: BluetoothDevice?, service: AirPodsService,
                          navController: NavController, isConnected: Boolean, isRemotelyConnected: Boolean) {
    var isLocallyConnected by remember { mutableStateOf(isConnected) }
    var isRemotelyConnected by remember { mutableStateOf(isRemotelyConnected) }
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)
    val bleOnlyMode = sharedPreferences.getBoolean("ble_only_mode", false)
    var device by remember { mutableStateOf(dev) }
    var deviceName by remember {
        mutableStateOf(
            TextFieldValue(
                sharedPreferences.getString("name", device?.name ?: "AirPods Pro").toString()
            )
        )
    }

    LaunchedEffect(service) {
        isLocallyConnected = service.isConnectedLocally
    }

    val nameChangeListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "name") {
                deviceName = TextFieldValue(sharedPreferences.getString("name", "AirPods Pro").toString())
            }
        }
    }

    DisposableEffect(Unit) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(nameChangeListener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(nameChangeListener)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun handleRemoteConnection(connected: Boolean) {
        isRemotelyConnected = connected
    }

    val context = LocalContext.current

    val connectionReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "me.kavishdevar.librepods.AIRPODS_CONNECTED_REMOTELY" -> {
                        coroutineScope.launch {
                            handleRemoteConnection(true)
                        }
                    }
                    "me.kavishdevar.librepods.AIRPODS_DISCONNECTED_REMOTELY" -> {
                        coroutineScope.launch {
                            handleRemoteConnection(false)
                        }
                    }
                    AirPodsNotifications.AIRPODS_CONNECTED -> {
                        coroutineScope.launch {
                            isLocallyConnected = true
                        }
                    }
                    AirPodsNotifications.AIRPODS_DISCONNECTED -> {
                        coroutineScope.launch {
                            isLocallyConnected = false
                        }
                    }
                    AirPodsNotifications.DISCONNECT_RECEIVERS -> {
                        try {
                            context?.unregisterReceiver(this)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction("me.kavishdevar.librepods.AIRPODS_CONNECTED_REMOTELY")
            addAction("me.kavishdevar.librepods.AIRPODS_DISCONNECTED_REMOTELY")
            addAction(AirPodsNotifications.AIRPODS_CONNECTED)
            addAction(AirPodsNotifications.AIRPODS_DISCONNECTED)
            addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(connectionReceiver, filter, RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(connectionReceiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(connectionReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val darkMode = isSystemInDarkTheme()
    StyledScaffold(
        title = deviceName.text,
        actionButtons = listOf {
            StyledIconButton(
                onClick = { navController.navigate("app_settings") },
                icon = "􀍟",
                darkMode = darkMode
            )
        },
        snackbarHostState = snackbarHostState
    ) { spacerHeight, hazeState ->
        if (isLocallyConnected || isRemotelyConnected) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(spacerHeight))
                LaunchedEffect(service) {
                    service.let {
                        it.sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                            putParcelableArrayListExtra("data", ArrayList(it.getBattery()))
                        })
                        it.sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                            putExtra("data", it.getANC())
                        })
                    }
                }

                BatteryView(service = service)
                Spacer(modifier = Modifier.height(32.dp))

                // Show BLE-only mode indicator
                if (bleOnlyMode) {
                    Text(
                        text = "BLE-only mode - advanced features disabled",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = 0.6f),
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        ),
                        modifier = Modifier.padding(8.dp, bottom = 16.dp)
                    )
                }

                // Only show name field when not in BLE-only mode
                if (!bleOnlyMode) {
                    NameField(
                        name = stringResource(R.string.name),
                        value = deviceName.text,
                        navController = navController
                    )
                }

                if (!bleOnlyMode) {
                    Spacer(modifier = Modifier.height(32.dp))
                    NavigationButton(to = "hearing_aid", stringResource(R.string.hearing_aid), navController)

                    Spacer(modifier = Modifier.height(16.dp))
                    NoiseControlSettings(service = service)

                    Spacer(modifier = Modifier.height(16.dp))
                    CallControlSettings(hazeState = hazeState)

                    // camera control goes here, airpods side is done, i just need to figure out how to listen to app open/close events

                    Spacer(modifier = Modifier.height(16.dp))
                    PressAndHoldSettings(navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))
                    AudioSettings(navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))
                    ConnectionSettings()

                    Spacer(modifier = Modifier.height(16.dp))
                    MicrophoneSettings(hazeState)

                    Spacer(modifier = Modifier.height(16.dp))
                    StyledToggle(
                        label = stringResource(R.string.sleep_detection),
                        controlCommandIdentifier = AACPManager.Companion.ControlCommandIdentifiers.SLEEP_DETECTION_CONFIG
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationButton(to = "head_tracking", stringResource(R.string.head_gestures), navController)

                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationButton(to = "accessibility", "Accessibility", navController = navController)

                    Spacer(modifier = Modifier.height(16.dp))
                    StyledToggle(
                        label = stringResource(R.string.off_listening_mode).uppercase(),
                        controlCommandIdentifier = AACPManager.Companion.ControlCommandIdentifiers.ALLOW_OFF_OPTION,
                        description = stringResource(R.string.off_listening_mode_description)
                    )

                    // an about card- everything but the version number is unknown - will add later if i find out

                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationButton("debug", "Debug", navController)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
        else {
            val backdrop = rememberLayerBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = rememberLayerBackdrop(),
                        exportedBackdrop = backdrop,
                        shape = { RoundedCornerShape(0.dp) },
                        highlight = {
                            Highlight.AmbientDefault.copy(alpha = 0f)
                        }
                    )
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.airpods_not_connected),
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.airpods_not_connected_description),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(32.dp))
                StyledButton(
                    onClick = { navController.navigate("troubleshooting") },
                    backdrop = backdrop
                ) {
                    Text(
                        text = "Troubleshoot Connection",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AirPodsSettingsScreenPreview() {
    Column (
        modifier = Modifier.height(2000.dp)
    ) {
        LibrePodsTheme (
            darkTheme = true
        ) {
            AirPodsSettingsScreen(dev = null, service = AirPodsService(), navController = rememberNavController(), isConnected = true, isRemotelyConnected = false)
        }
    }
}
