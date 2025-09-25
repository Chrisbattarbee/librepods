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

package me.kavishdevar.librepods.composables

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.refraction
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.utils.inspectDragGestures
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun ConfirmationDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    message: String,
    confirmText: String = "Ok",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = { showDialog.value = false },
    backdrop: Backdrop,
) {
    AnimatedVisibility(
        visible = showDialog.value,
        enter = fadeIn() + scaleIn(initialScale = 1.25f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        val animationScope = rememberCoroutineScope()
        val progressAnimation = remember { Animatable(0f) }
        var pressStartPosition by remember { mutableStateOf(Offset.Zero) }
        val offsetAnimation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        val interactiveHighlightShader = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RuntimeShader(
                    """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 offset;

half4 main(float2 coord) {
    float2 center = offset;
    float dist = distance(coord, center);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
                )
            } else {
                null
            }
        }

        val isLightTheme = !isSystemInDarkTheme()
        val contentColor = if (isLightTheme) Color.Black else Color.White
        val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
        val containerColor = if (isLightTheme) Color(0xFFFFFFFF).copy(0.6f) else Color(0xFF101010).copy(0.6f)

        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss, indication = null, interactionSource = remember { MutableInteractionSource() } )
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clickable(onClick = {}, indication = null, interactionSource = remember { MutableInteractionSource() } )
                    .drawBackdrop(
                        backdrop,
                        { RoundedCornerShape(48f.dp) },
                        highlight = { Highlight { HighlightStyle.Solid } },
                        onDrawSurface = { drawRect(containerColor) },
                        effects = {
                            colorControls(
                                brightness = if (isLightTheme) 0.4f else 0.2f,
                                saturation = 1.5f
                            )
                            blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                            refraction(24f.dp.toPx(), 48f.dp.toPx(), true)
                        },
                        layer = {
                            val width = size.width
                            val height = size.height

                            val progress = progressAnimation.value
                            val maxScale = 0f
                            val scale = lerp(1f, 1f + maxScale, progress)

                            val maxOffset = size.minDimension
                            val initialDerivative = 0.05f
                            val offset = offsetAnimation.value
                            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                            val maxDragScale = 0.1f
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX =
                                scale +
                                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                        (width / height).fastCoerceAtMost(1f)
                            scaleY =
                                scale +
                                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                        (height / width).fastCoerceAtMost(1f)
                        },
                        onDrawFront = {
                            val progress = progressAnimation.value.fastCoerceIn(0f, 1f)
                            if (progress > 0f) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && interactiveHighlightShader != null) {
                                    drawRect(
                                        Color.White.copy(0.05f * progress),
                                        blendMode = BlendMode.Plus
                                    )
                                    interactiveHighlightShader.apply {
                                        val offset = pressStartPosition + offsetAnimation.value
                                        setFloatUniform("size", size.width, size.height)
                                        setColorUniform("color", Color.White.copy(0.075f * progress).toArgb())
                                        setFloatUniform("radius", size.maxDimension / 2)
                                        setFloatUniform(
                                            "offset",
                                            offset.x.fastCoerceIn(0f, size.width),
                                            offset.y.fastCoerceIn(0f, size.height)
                                        )
                                    }
                                    drawRect(
                                        ShaderBrush(interactiveHighlightShader),
                                        blendMode = BlendMode.Plus
                                    )
                                } else {
                                    drawRect(
                                        Color.White.copy(0.125f * progress),
                                        blendMode = BlendMode.Plus
                                    )
                                }
                            }
                        },
                        contentEffects = {
                            refraction(8f.dp.toPx(), 24f.dp.toPx(), false)
                        }
                    )
                    .fillMaxWidth(0.75f)
                    .requiredWidthIn(min = 200.dp, max = 360.dp)
                    .pointerInput(animationScope) {
                        val progressAnimationSpec = spring(0.5f, 300f, 0.001f)
                        val offsetAnimationSpec = spring(1f, 300f, Offset.VisibilityThreshold)
                        val onDragStop: () -> Unit = {
                            animationScope.launch {
                                launch { progressAnimation.animateTo(0f, progressAnimationSpec) }
                                launch { offsetAnimation.animateTo(Offset.Zero, offsetAnimationSpec) }
                            }
                        }
                        inspectDragGestures(
                            onDragStart = { down ->
                                pressStartPosition = down.position
                                animationScope.launch {
                                    launch { progressAnimation.animateTo(1f, progressAnimationSpec) }
                                    launch { offsetAnimation.snapTo(Offset.Zero) }
                                }
                            },
                            onDragEnd = { onDragStop() },
                            onDragCancel = onDragStop
                        ) { _, dragAmount ->
                            animationScope.launch {
                                offsetAnimation.snapTo(offsetAnimation.value + dragAmount)
                            }
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        message,
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = contentColor.copy(alpha = 0.8f),
                            fontFamily = FontFamily(Font(R.font.sf_pro))
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp, bottom = 24.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Box(
                        //     Modifier
                        //         .clip(RoundedCornerShape(50.dp))
                        //         .background(containerColor.copy(0.2f))
                        //         .clickable(onClick = onDismiss)
                        //         .height(48.dp)
                        //         .weight(1f)
                        //         .padding(horizontal = 16.dp),
                        //     contentAlignment = Alignment.Center
                        // ) {
                        StyledButton(
                            onClick = onDismiss,
                            backdrop = backdrop,
                            surfaceColor = if (isLightTheme) Color(0xFFAAAAAA).copy(0.8f) else Color(0xFF202020).copy(0.8f),
                            modifier = Modifier.weight(1f),
                            isInteractive = false
                        ) {
                            Text(
                                dismissText,
                                style = TextStyle(contentColor, 16.sp)
                            )
                        }
                        // Box(
                        //     Modifier
                        //         .clip(RoundedCornerShape(50.dp))
                        //         .background(accentColor)
                        //         .clickable(onClick = onConfirm)
                        //         .height(48.dp)
                        //         .weight(1f)
                        //         .padding(horizontal = 16.dp),
                        //     contentAlignment = Alignment.Center
                        // ) {
                        StyledButton(
                            onClick = onConfirm,
                            backdrop = backdrop,
                            surfaceColor = accentColor,
                            modifier = Modifier.weight(1f),
                            isInteractive = false
                        ) {
                            Text(
                                confirmText,
                                style = TextStyle(Color.White, 16.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}
