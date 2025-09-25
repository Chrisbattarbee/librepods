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

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.composables.StyledIconButton
import me.kavishdevar.librepods.composables.StyledScaffold
import me.kavishdevar.librepods.composables.StyledSlider
import me.kavishdevar.librepods.composables.StyledToggle
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.utils.AACPManager
import me.kavishdevar.librepods.utils.ATTHandles
import me.kavishdevar.librepods.utils.ATTManager
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.ExperimentalEncodingApi

private var debounceJob: Job? = null
private const val TAG = "HearingAidAdjustments"

@SuppressLint("DefaultLocale")
@ExperimentalHazeMaterialsApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalEncodingApi::class)
@Composable
fun HearingAidAdjustmentsScreen(@Suppress("unused") navController: NavController) {
    val isDarkTheme = isSystemInDarkTheme()
    val verticalScrollState = rememberScrollState()
    val hazeState = remember { HazeState() }
    val attManager = ServiceManager.getService()?.attManager ?: throw IllegalStateException("ATTManager not available")

    val aacpManager = remember { ServiceManager.getService()?.aacpManager }

    StyledScaffold(
        title = stringResource(R.string.adjustments),
        navigationButton = {
            StyledIconButton(
                onClick = { navController.popBackStack() },
                icon = "􀯶",
                darkMode = isDarkTheme
            )
        }
    ) { spacerHeight ->
        Column(
            modifier = Modifier
                .hazeSource(hazeState)
                .fillMaxSize()
                .verticalScroll(verticalScrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(spacerHeight))

            val amplificationSliderValue = remember { mutableFloatStateOf(0.5f) }
            val balanceSliderValue = remember { mutableFloatStateOf(0.5f) }
            val toneSliderValue = remember { mutableFloatStateOf(0.5f) }
            val ambientNoiseReductionSliderValue = remember { mutableFloatStateOf(0.0f) }
            val conversationBoostEnabled = remember { mutableStateOf(false) }
            val eq = remember { mutableStateOf(FloatArray(8)) }
            val ownVoiceAmplification = remember { mutableFloatStateOf(0.5f) }

            val phoneMediaEQ = remember { mutableStateOf(FloatArray(8) { 0.5f }) }
            val phoneEQEnabled = remember { mutableStateOf(false) }
            val mediaEQEnabled = remember { mutableStateOf(false) }

            val initialLoadComplete = remember { mutableStateOf(false) }

            val initialReadSucceeded = remember { mutableStateOf(false) }
            val initialReadAttempts = remember { mutableIntStateOf(0) }

            val hearingAidSettings = remember {
                mutableStateOf(
                    HearingAidSettings(
                        leftEQ = eq.value,
                        rightEQ = eq.value,
                        leftAmplification = amplificationSliderValue.floatValue + (0.5f - balanceSliderValue.floatValue) * amplificationSliderValue.floatValue * 2,
                        rightAmplification = amplificationSliderValue.floatValue + (balanceSliderValue.floatValue - 0.5f) * amplificationSliderValue.floatValue * 2,
                        leftTone = toneSliderValue.floatValue,
                        rightTone = toneSliderValue.floatValue,
                        leftConversationBoost = conversationBoostEnabled.value,
                        rightConversationBoost = conversationBoostEnabled.value,
                        leftAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                        rightAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                        netAmplification = amplificationSliderValue.floatValue,
                        balance = balanceSliderValue.floatValue,
                        ownVoiceAmplification = ownVoiceAmplification.floatValue
                    )
                )
            }

            val hearingAidEnabled = remember {
                val aidStatus = aacpManager?.controlCommandStatusList?.find { it.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID }
                val assistStatus = aacpManager?.controlCommandStatusList?.find { it.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG }
                mutableStateOf((aidStatus?.value?.getOrNull(1) == 0x01.toByte()) && (assistStatus?.value?.getOrNull(0) == 0x01.toByte()))
            }

            val hearingAidListener = remember {
                object : AACPManager.ControlCommandListener {
                    override fun onControlCommandReceived(controlCommand: AACPManager.ControlCommand) {
                        if (controlCommand.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID.value ||
                            controlCommand.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG.value) {
                            val aidStatus = aacpManager?.controlCommandStatusList?.find { it.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID }
                            val assistStatus = aacpManager?.controlCommandStatusList?.find { it.identifier == AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG }
                            hearingAidEnabled.value = (aidStatus?.value?.getOrNull(1) == 0x01.toByte()) && (assistStatus?.value?.getOrNull(0) == 0x01.toByte())
                        }
                    }
                }
            }

            val hearingAidATTListener = remember {
                object : (ByteArray) -> Unit {
                    override fun invoke(value: ByteArray) {
                        val parsed = parseHearingAidSettingsResponse(value)
                        if (parsed != null) {
                            amplificationSliderValue.floatValue = parsed.netAmplification
                            balanceSliderValue.floatValue = parsed.balance
                            toneSliderValue.floatValue = parsed.leftTone
                            ambientNoiseReductionSliderValue.floatValue = parsed.leftAmbientNoiseReduction
                            conversationBoostEnabled.value = parsed.leftConversationBoost
                            eq.value = parsed.leftEQ.copyOf()
                            ownVoiceAmplification.floatValue = parsed.ownVoiceAmplification
                            Log.d(TAG, "Updated hearing aid settings from notification")
                        } else {
                            Log.w(TAG, "Failed to parse hearing aid settings from notification")
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                aacpManager?.registerControlCommandListener(AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID, hearingAidListener)
                aacpManager?.registerControlCommandListener(AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG, hearingAidListener)
            }

            DisposableEffect(Unit) {
                onDispose {
                    aacpManager?.unregisterControlCommandListener(AACPManager.Companion.ControlCommandIdentifiers.HEARING_AID, hearingAidListener)
                    aacpManager?.unregisterControlCommandListener(AACPManager.Companion.ControlCommandIdentifiers.HEARING_ASSIST_CONFIG, hearingAidListener)
                    attManager.unregisterListener(ATTHandles.HEARING_AID, hearingAidATTListener)
                }
            }

            LaunchedEffect(amplificationSliderValue.floatValue, balanceSliderValue.floatValue, toneSliderValue.floatValue, conversationBoostEnabled.value, ambientNoiseReductionSliderValue.floatValue, ownVoiceAmplification.floatValue, initialLoadComplete.value, initialReadSucceeded.value) {
                if (!initialLoadComplete.value) {
                    Log.d(TAG, "Initial device load not complete - skipping send")
                    return@LaunchedEffect
                }

                if (!initialReadSucceeded.value) {
                    Log.d(TAG, "Initial device read not successful yet - skipping send until read succeeds")
                    return@LaunchedEffect
                }

                hearingAidSettings.value = HearingAidSettings(
                    leftEQ = eq.value,
                    rightEQ = eq.value,
                    leftAmplification = amplificationSliderValue.floatValue + if (balanceSliderValue.floatValue < 0) -balanceSliderValue.floatValue else 0f,
                    rightAmplification = amplificationSliderValue.floatValue + if (balanceSliderValue.floatValue > 0) balanceSliderValue.floatValue else 0f,
                    leftTone = toneSliderValue.floatValue,
                    rightTone = toneSliderValue.floatValue,
                    leftConversationBoost = conversationBoostEnabled.value,
                    rightConversationBoost = conversationBoostEnabled.value,
                    leftAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                    rightAmbientNoiseReduction = ambientNoiseReductionSliderValue.floatValue,
                    netAmplification = amplificationSliderValue.floatValue,
                    balance = balanceSliderValue.floatValue,
                    ownVoiceAmplification = ownVoiceAmplification.floatValue
                )
                Log.d(TAG, "Updated settings: ${hearingAidSettings.value}")
                sendHearingAidSettings(attManager, hearingAidSettings.value)
            }

            LaunchedEffect(Unit) {
                Log.d(TAG, "Connecting to ATT...")
                try {
                    attManager.enableNotifications(ATTHandles.HEARING_AID)
                    attManager.registerListener(ATTHandles.HEARING_AID, hearingAidATTListener)

                    try {
                        if (aacpManager != null) {
                            Log.d(TAG, "Found AACPManager, reading cached EQ data")
                            val aacpEQ = aacpManager.eqData
                            if (aacpEQ.isNotEmpty()) {
                                eq.value = aacpEQ.copyOf()
                                phoneMediaEQ.value = aacpEQ.copyOf()
                                phoneEQEnabled.value = aacpManager.eqOnPhone
                                mediaEQEnabled.value = aacpManager.eqOnMedia
                                Log.d(TAG, "Populated EQ from AACPManager: ${aacpEQ.toList()}")
                            } else {
                                Log.d(TAG, "AACPManager EQ data empty")
                            }
                        } else {
                            Log.d(TAG, "No AACPManager available")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading EQ from AACPManager: ${e.message}")
                    }

                    var parsedSettings: HearingAidSettings? = null
                    for (attempt in 1..3) {
                        initialReadAttempts.intValue = attempt
                        try {
                            val data = attManager.read(ATTHandles.HEARING_AID)
                            parsedSettings = parseHearingAidSettingsResponse(data = data)
                            if (parsedSettings != null) {
                                Log.d(TAG, "Parsed settings on attempt $attempt")
                                break
                            } else {
                                Log.d(TAG, "Parsing returned null on attempt $attempt")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Read attempt $attempt failed: ${e.message}")
                        }
                        delay(200)
                    }

                    if (parsedSettings != null) {
                        Log.d(TAG, "Initial hearing aid settings: $parsedSettings")
                        amplificationSliderValue.floatValue = parsedSettings.netAmplification
                        balanceSliderValue.floatValue = parsedSettings.balance
                        toneSliderValue.floatValue = parsedSettings.leftTone
                        ambientNoiseReductionSliderValue.floatValue = parsedSettings.leftAmbientNoiseReduction
                        conversationBoostEnabled.value = parsedSettings.leftConversationBoost
                        eq.value = parsedSettings.leftEQ.copyOf()
                        ownVoiceAmplification.floatValue = parsedSettings.ownVoiceAmplification
                        initialReadSucceeded.value = true
                    } else {
                        Log.d(TAG, "Failed to read/parse initial hearing aid settings after ${initialReadAttempts.intValue} attempts")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    initialLoadComplete.value = true
                }
            }

            StyledSlider(
                label = stringResource(R.string.amplification).uppercase(),
                valueRange = -1f..1f,
                mutableFloatState = amplificationSliderValue,
                onValueChange = {
                    amplificationSliderValue.floatValue = it
                },
                startIcon = "􀊥",
                endIcon = "􀊩",
                independent = true,
            )


            StyledToggle(
                label = stringResource(R.string.swipe_to_control_amplification),
                controlCommandIdentifier = AACPManager.Companion.ControlCommandIdentifiers.HPS_GAIN_SWIPE,
                description = stringResource(R.string.swipe_amplification_description)
            )

            StyledSlider(
                label = stringResource(R.string.balance).uppercase(),
                valueRange = -1f..1f,
                mutableFloatState = balanceSliderValue,
                onValueChange = {
                    balanceSliderValue.floatValue = it
                },
                snapPoints = listOf(0f),
                startLabel = stringResource(R.string.left),
                endLabel = stringResource(R.string.right),
                independent = true,
            )

            StyledSlider(
                label = stringResource(R.string.tone).uppercase(),
                valueRange = -1f..1f,
                mutableFloatState = toneSliderValue,
                onValueChange = {
                    toneSliderValue.floatValue = it
                },
                startLabel = stringResource(R.string.darker),
                endLabel = stringResource(R.string.brighter),
                independent = true,
            )

            StyledSlider(
                label = stringResource(R.string.ambient_noise_reduction).uppercase(),
                valueRange = 0f..1f,
                mutableFloatState = ambientNoiseReductionSliderValue,
                onValueChange = {
                    ambientNoiseReductionSliderValue.floatValue = it
                },
                startLabel = stringResource(R.string.less),
                endLabel = stringResource(R.string.more),
                independent = true,
            )

            AccessibilityToggle(
                text = stringResource(R.string.conversation_boost),
                mutableState = conversationBoostEnabled,
                independent = true,
                description = stringResource(R.string.conversation_boost_description)
            )
        }
    }
}

private data class HearingAidSettings(
    val leftEQ: FloatArray,
    val rightEQ: FloatArray,
    val leftAmplification: Float,
    val rightAmplification: Float,
    val leftTone: Float,
    val rightTone: Float,
    val leftConversationBoost: Boolean,
    val rightConversationBoost: Boolean,
    val leftAmbientNoiseReduction: Float,
    val rightAmbientNoiseReduction: Float,
    val netAmplification: Float,
    val balance: Float,
    val ownVoiceAmplification: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HearingAidSettings

        if (leftAmplification != other.leftAmplification) return false
        if (rightAmplification != other.rightAmplification) return false
        if (leftTone != other.leftTone) return false
        if (rightTone != other.rightTone) return false
        if (leftConversationBoost != other.leftConversationBoost) return false
        if (rightConversationBoost != other.rightConversationBoost) return false
        if (leftAmbientNoiseReduction != other.leftAmbientNoiseReduction) return false
        if (rightAmbientNoiseReduction != other.rightAmbientNoiseReduction) return false
        if (!leftEQ.contentEquals(other.leftEQ)) return false
        if (!rightEQ.contentEquals(other.rightEQ)) return false
        if (ownVoiceAmplification != other.ownVoiceAmplification) return false

        return true
    }

    override fun hashCode(): Int {
        var result = leftAmplification.hashCode()
        result = 31 * result + rightAmplification.hashCode()
        result = 31 * result + leftTone.hashCode()
        result = 31 * result + rightTone.hashCode()
        result = 31 * result + leftConversationBoost.hashCode()
        result = 31 * result + rightConversationBoost.hashCode()
        result = 31 * result + leftAmbientNoiseReduction.hashCode()
        result = 31 * result + rightAmbientNoiseReduction.hashCode()
        result = 31 * result + leftEQ.contentHashCode()
        result = 31 * result + rightEQ.contentHashCode()
        result = 31 * result + ownVoiceAmplification.hashCode()
        return result
    }
}

private fun parseHearingAidSettingsResponse(data: ByteArray): HearingAidSettings? {
    if (data.size < 104) return null
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    buffer.get() // skip 0x02
    buffer.get() // skip 0x02
    buffer.getShort() // skip 0x60 0x00

    val leftEQ = FloatArray(8)
    for (i in 0..7) {
        leftEQ[i] = buffer.float
    }
    val leftAmplification = buffer.float
    val leftTone = buffer.float
    val leftConvFloat = buffer.float
    val leftConversationBoost = leftConvFloat > 0.5f
    val leftAmbientNoiseReduction = buffer.float

    val rightEQ = FloatArray(8)
    for (i in 0..7) {
        rightEQ[i] = buffer.float
    }
    val rightAmplification = buffer.float
    val rightTone = buffer.float
    val rightConvFloat = buffer.float
    val rightConversationBoost = rightConvFloat > 0.5f
    val rightAmbientNoiseReduction = buffer.float

    val ownVoiceAmplification = buffer.float

    val avg = (leftAmplification + rightAmplification) / 2
    val amplification = avg.coerceIn(-1f, 1f)
    val diff = rightAmplification - leftAmplification
    val balance = diff.coerceIn(-1f, 1f)

    return HearingAidSettings(
        leftEQ = leftEQ,
        rightEQ = rightEQ,
        leftAmplification = leftAmplification,
        rightAmplification = rightAmplification,
        leftTone = leftTone,
        rightTone = rightTone,
        leftConversationBoost = leftConversationBoost,
        rightConversationBoost = rightConversationBoost,
        leftAmbientNoiseReduction = leftAmbientNoiseReduction,
        rightAmbientNoiseReduction = rightAmbientNoiseReduction,
        netAmplification = amplification,
        balance = balance,
        ownVoiceAmplification = ownVoiceAmplification
    )
}

private fun sendHearingAidSettings(
    attManager: ATTManager,
    hearingAidSettings: HearingAidSettings
) {
    debounceJob?.cancel()
    debounceJob = CoroutineScope(Dispatchers.IO).launch {
        delay(100)
        try {
            val currentData = attManager.read(ATTHandles.HEARING_AID)
            Log.d(TAG, "Current data before update: ${currentData.joinToString(" ") { String.format("%02X", it) }}")
            if (currentData.size < 104) {
                Log.w(TAG, "Current data size ${currentData.size} too small, cannot send settings")
                return@launch
            }
            val buffer = ByteBuffer.wrap(currentData).order(ByteOrder.LITTLE_ENDIAN)

            // for some reason
            buffer.put(2, 0x64)

            // Left ear adjustments
            buffer.putFloat(36, hearingAidSettings.leftAmplification)
            buffer.putFloat(40, hearingAidSettings.leftTone)
            buffer.putFloat(44, if (hearingAidSettings.leftConversationBoost) 1.0f else 0.0f)
            buffer.putFloat(48, hearingAidSettings.leftAmbientNoiseReduction)

            // Right ear adjustments
            buffer.putFloat(84, hearingAidSettings.rightAmplification)
            buffer.putFloat(88, hearingAidSettings.rightTone)
            buffer.putFloat(92, if (hearingAidSettings.rightConversationBoost) 1.0f else 0.0f)
            buffer.putFloat(96, hearingAidSettings.rightAmbientNoiseReduction)

            // Own voice amplification
            buffer.putFloat(100, hearingAidSettings.ownVoiceAmplification)

            Log.d(TAG, "Sending updated settings: ${currentData.joinToString(" ") { String.format("%02X", it) }}")

            attManager.write(ATTHandles.HEARING_AID, currentData)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
