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

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.refractionWithDispersion
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.R
import kotlin.math.roundToInt

@Composable
fun StyledSlider(
    label: String? = null,
    mutableFloatState: MutableFloatState,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop = rememberLayerBackdrop(),
    snapPoints: List<Float> = emptyList(),
    snapThreshold: Float = 0.05f,
    startIcon: String? = null,
    endIcon: String? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    independent: Boolean = false,
    description: String? = null
) {
    val backgroundColor = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)
    val labelTextColor = if (isLightTheme) Color.Black else Color.White

    val fraction by remember {
        derivedStateOf {
            ((mutableFloatState.floatValue - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .fastCoerceIn(0f, 1f)
        }
    }

    val animationScope = rememberCoroutineScope()
    val progressAnimationSpec = spring(0.5f, 300f, 0.001f)
    val progressAnimation = remember { Animatable(0f) }
    val innerShadowLayer =
        rememberGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }

    val sliderBackdrop = rememberLayerBackdrop()
    val trackWidthState = remember { mutableFloatStateOf(0f) }
    val trackPositionState = remember { mutableFloatStateOf(0f) }
    val startIconWidthState = remember { mutableFloatStateOf(0f) }
    val endIconWidthState = remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val content = @Composable {
        Box(
            Modifier.fillMaxWidth(if (startIcon == null && endIcon == null) 0.95f else 1f)
            ) {
            Box(
                Modifier
                    .layerBackdrop(sliderBackdrop)
                .fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (startLabel != null || endLabel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = startLabel ?: "",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = labelTextColor,
                                    fontFamily = FontFamily(Font(R.font.sf_pro))
                                )
                            )
                            Text(
                                text = endLabel ?: "",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = labelTextColor,
                                    fontFamily = FontFamily(Font(R.font.sf_pro))
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(if (startIcon == null && endIcon == null) Modifier.padding(horizontal = 12.dp) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (startIcon != null) {
                            Text(
                                text = startIcon,
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = accentColor,
                                    fontFamily = FontFamily(Font(R.font.sf_pro))
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .onGloballyPositioned {
                                        startIconWidthState.floatValue = it.size.width.toFloat()
                                    }
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .onSizeChanged { trackWidthState.floatValue = it.width.toFloat() }
                                .onGloballyPositioned {
                                    trackPositionState.floatValue =
                                        it.positionInParent().y + it.size.height / 2f
                                }
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(trackColor)
                                    .height(6f.dp)
                                    .fillMaxWidth()
                            )

                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(accentColor)
                                    .height(6f.dp)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val fraction = fraction
                                        val width =
                                            (fraction * constraints.maxWidth).fastRoundToInt()
                                        layout(width, placeable.height) {
                                            placeable.place(0, 0)
                                        }
                                    }
                            )
                        }
                        if (endIcon != null) {
                            Text(
                                text = endIcon,
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = accentColor,
                                    fontFamily = FontFamily(Font(R.font.sf_pro))
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .onGloballyPositioned {
                                        endIconWidthState.floatValue = it.size.width.toFloat()
                                    }
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .graphicsLayer {
                        val startOffset =
                            if (startIcon != null) startIconWidthState.floatValue + with(density) { 24.dp.toPx() } else with(density) { 8.dp.toPx() }
                        translationX =
                            startOffset + fraction * trackWidthState.floatValue - size.width / 2f
                        translationY =  if (startLabel != null || endLabel != null) trackPositionState.floatValue + with(density) { 22.dp.toPx() } + size.height / 2f else trackPositionState.floatValue + with(density) { 4.dp.toPx() }
                    }
                    .draggable(
                        rememberDraggableState { delta ->
                            val trackWidth = trackWidthState.floatValue
                            if (trackWidth > 0f) {
                                val targetFraction = fraction + delta / trackWidth
                                val targetValue =
                                    lerp(valueRange.start, valueRange.endInclusive, targetFraction)
                                        .fastCoerceIn(valueRange.start, valueRange.endInclusive)
                                val snappedValue = if (snapPoints.isNotEmpty()) snapIfClose(
                                    targetValue,
                                    snapPoints,
                                    snapThreshold
                                ) else targetValue
                                onValueChange(snappedValue)
                            }
                        },
                        Orientation.Horizontal,
                        startDragImmediately = true,
                        onDragStarted = {
                            animationScope.launch {
                                progressAnimation.animateTo(1f, progressAnimationSpec)
                            }
                        },
                        onDragStopped = {
                            animationScope.launch {
                                progressAnimation.animateTo(0f, progressAnimationSpec)
                                onValueChange((mutableFloatState.floatValue * 100).roundToInt() / 100f)
                            }
                        }
                    )
                    .drawBackdrop(
                        rememberCombinedBackdrop(backdrop, sliderBackdrop),
                        { RoundedCornerShape(28.dp) },
                        highlight = {
                            val progress = progressAnimation.value
                            Highlight.AmbientDefault.copy(alpha = progress)
                        },
                        shadow = {
                            Shadow(
                                radius = 4f.dp,
                                color = Color.Black.copy(0.05f)
                            )
                        },
                        layer = {
                            val progress = progressAnimation.value
                            val scale = lerp(1f, 1.5f, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = {
                            val progress = progressAnimation.value.fastCoerceIn(0f, 1f)

                            val shape = RoundedCornerShape(28.dp)
                            val outline = shape.createOutline(size, layoutDirection, this)
                            val innerShadowOffset = 4f.dp.toPx()
                            val innerShadowBlurRadius = 4f.dp.toPx()

                            innerShadowLayer.alpha = progress
                            innerShadowLayer.renderEffect =
                                BlurEffect(
                                    innerShadowBlurRadius,
                                    innerShadowBlurRadius,
                                    TileMode.Decal
                                )
                            innerShadowLayer.record {
                                drawOutline(outline, Color.Black.copy(0.2f))
                                translate(0f, innerShadowOffset) {
                                    drawOutline(
                                        outline,
                                        Color.Transparent,
                                        blendMode = BlendMode.Clear
                                    )
                                }
                            }
                            drawLayer(innerShadowLayer)

                            drawRect(Color.White.copy(1f - progress))
                        },
                        effects = {
                            refractionWithDispersion(6f.dp.toPx(), size.height / 2f)
                        }
                    )
                    .size(40f.dp, 24f.dp)
            )
        }
    }

    if (independent) {

        Column (
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = labelTextColor.copy(alpha = 0.6f),
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 0.dp)
                    .heightIn(min = 55.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            if (description != null) {
                Text(
                    text = description,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        color = (if (isSystemInDarkTheme()) Color.White else Color.Black).copy(alpha = 0.6f),
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        if (label != null) Log.w("StyledSlider", "Label is ignored when independent is false")
        if (description != null) Log.w("StyledSlider", "Description is ignored when independent is false")
        content()
    }
}

private fun snapIfClose(value: Float, points: List<Float>, threshold: Float = 0.05f): Float {
    val nearest = points.minByOrNull { kotlin.math.abs(it - value) } ?: value
    return if (kotlin.math.abs(nearest - value) <= threshold) nearest else value
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StyledSliderPreview() {
    val a = remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .background(if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFF0F0F0))
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Box (
            Modifier.align(Alignment.Center)
        )
        {
            StyledSlider(
                mutableFloatState = a,
                onValueChange = {
                    a.floatValue = it
                },
                valueRange = 0f..2f,
                independent = true,
                startLabel = "A",
                endLabel = "B"
            )
        }
    }
}
