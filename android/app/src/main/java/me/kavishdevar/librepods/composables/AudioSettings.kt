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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.utils.AACPManager
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun AudioSettings(navController: NavController) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = stringResource(R.string.audio).uppercase(),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {

        StyledToggle(
            label = stringResource(R.string.personalized_volume),
            description = stringResource(R.string.personalized_volume_description),
            controlCommandIdentifier = AACPManager.Companion.ControlCommandIdentifiers.ADAPTIVE_VOLUME_CONFIG,
            independent = false
        )
        HorizontalDivider(
            thickness = 1.5.dp,
            color = Color(0x40888888),
            modifier = Modifier
                .padding(start = 12.dp, end = 0.dp)
        )

        StyledToggle(
            label = stringResource(R.string.conversational_awareness),
            description = stringResource(R.string.conversational_awareness_description),
            controlCommandIdentifier = AACPManager.Companion.ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG,
            independent = false
        )
        HorizontalDivider(
            thickness = 1.5.dp,
            color = Color(0x40888888),
            modifier = Modifier
                .padding(start = 12.dp, end = 0.dp)
        )

        LoudSoundReductionSwitch()
        HorizontalDivider(
            thickness = 1.5.dp,
            color = Color(0x40888888),
            modifier = Modifier
                .padding(start = 12.dp, end = 0.dp)
        )

        NavigationButton(
            to = "adaptive_strength",
            name = stringResource(R.string.adaptive_audio),
            navController = navController,
            independent = false
        )
    }
}

@Preview
@Composable
fun AudioSettingsPreview() {
    AudioSettings(rememberNavController())
}
