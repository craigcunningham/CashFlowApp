package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.DailyProjection
import com.example.ui.ScenarioFilter
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ThresholdDanger
import com.example.ui.theme.WorstCaseAmber
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun CashFlowChart(
    dailyPoints: List<DailyProjection>,
    minimumThreshold: Double,
    scenarioFilter: ScenarioFilter,
    modifier: Modifier = Modifier
) {
    if (dailyPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No projection data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var touchFraction by remember { mutableFloatStateOf(-1f) }
    var isTouching by remember { mutableStateOf(false) }

    // Selected point index based on touch fraction
    val selectedIndex = if (isTouching && touchFraction in 0f..1f) {
        val idx = (touchFraction * (dailyPoints.size - 1)).toInt().coerceIn(0, dailyPoints.size - 1)
        idx
    } else {
        dailyPoints.size - 1 // Default to latest
    }
    val selectedPoint = dailyPoints[selectedIndex]

    // Calculate Y-axis bounds (min and max balances)
    val allValues = mutableListOf<Double>()
    allValues.add(minimumThreshold)
    for (p in dailyPoints) {
        if (scenarioFilter != ScenarioFilter.WORST_CASE) allValues.add(p.endingAvgBalance)
        if (scenarioFilter != ScenarioFilter.AVERAGE) allValues.add(p.endingMaxBalance)
    }

    val rawMinY = allValues.minOrNull() ?: 0.0
    val rawMaxY = allValues.maxOrNull() ?: 5000.0
    val paddingY = max(500.0, (rawMaxY - rawMinY) * 0.12)
    val minY = min(0.0, rawMinY - paddingY) // include 0 baseline if negative or close
    val maxY = rawMaxY + paddingY
    val yRange = if (maxY - minY == 0.0) 1.0 else (maxY - minY)

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cash_flow_chart_container")
    ) {
        // Scrubber / Inspector Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = surfaceColor.copy(alpha = 0.7f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedPoint.date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedPoint.events.isNotEmpty()) {
                        Text(
                            text = "${selectedPoint.events.size} transaction(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "No transactions scheduled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (scenarioFilter != ScenarioFilter.WORST_CASE) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Avg Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = formatCurrency(selectedPoint.endingAvgBalance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPoint.isBelowThresholdAvg) ThresholdDanger else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (scenarioFilter != ScenarioFilter.AVERAGE) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Worst-Case",
                                style = MaterialTheme.typography.labelSmall,
                                color = WorstCaseAmber
                            )
                            Text(
                                text = formatCurrency(selectedPoint.endingMaxBalance),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPoint.isBelowThresholdMax) ThresholdDanger else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(top = 8.dp, bottom = 4.dp)
                .pointerInput(dailyPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            isTouching = true
                            touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            tryAwaitRelease()
                            isTouching = false
                            touchFraction = -1f
                        }
                    )
                }
                .pointerInput(dailyPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isTouching = true
                            touchFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isTouching = false
                            touchFraction = -1f
                        },
                        onDragCancel = {
                            isTouching = false
                            touchFraction = -1f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartWidth = size.width
                val chartHeight = size.height
                val pointCount = dailyPoints.size

                fun getX(index: Int): Float {
                    return if (pointCount <= 1) chartWidth / 2f
                    else (index.toFloat() / (pointCount - 1).toFloat()) * chartWidth
                }

                fun getY(value: Double): Float {
                    val normalized = ((value - minY) / yRange).toFloat()
                    return chartHeight - (normalized * chartHeight)
                }

                // Horizontal grid lines: Min, Zero (if visible), Threshold, Max
                val gridValues = listOfNotNull(
                    minY,
                    if (minY < 0 && maxY > 0) 0.0 else null,
                    minimumThreshold,
                    maxY
                )
                for (gVal in gridValues) {
                    val gy = getY(gVal)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, gy),
                        end = Offset(chartWidth, gy),
                        strokeWidth = 1f
                    )
                }

                // Minimum threshold line (Red dashed line)
                val threshY = getY(minimumThreshold)
                drawLine(
                    color = ThresholdDanger.copy(alpha = 0.85f),
                    start = Offset(0f, threshY),
                    end = Offset(chartWidth, threshY),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // Zero line if in view
                if (minY < 0 && maxY > 0) {
                    val zeroY = getY(0.0)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(0f, zeroY),
                        end = Offset(chartWidth, zeroY),
                        strokeWidth = 1.5f
                    )
                }

                // Draw Average Projection curve & gradient area
                if (scenarioFilter != ScenarioFilter.WORST_CASE && pointCount > 1) {
                    val avgPath = Path()
                    val areaPath = Path()

                    areaPath.moveTo(0f, chartHeight)
                    avgPath.moveTo(getX(0), getY(dailyPoints[0].endingAvgBalance))
                    areaPath.lineTo(getX(0), getY(dailyPoints[0].endingAvgBalance))

                    for (i in 1 until pointCount) {
                        val prevX = getX(i - 1)
                        val prevY = getY(dailyPoints[i - 1].endingAvgBalance)
                        val currX = getX(i)
                        val currY = getY(dailyPoints[i].endingAvgBalance)

                        // Smooth Bézier curve between points
                        val controlX = (prevX + currX) / 2f
                        avgPath.cubicTo(controlX, prevY, controlX, currY, currX, currY)
                        areaPath.cubicTo(controlX, prevY, controlX, currY, currX, currY)
                    }

                    areaPath.lineTo(chartWidth, chartHeight)
                    areaPath.close()

                    // Gradient fill underneath average projection
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EmeraldLight.copy(alpha = 0.28f),
                                EmeraldLight.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    // Stroke line
                    drawPath(
                        path = avgPath,
                        color = EmeraldPrimary,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }

                // Draw Worst-Case / Maximum Cost projection line
                if (scenarioFilter != ScenarioFilter.AVERAGE && pointCount > 1) {
                    val maxPath = Path()
                    maxPath.moveTo(getX(0), getY(dailyPoints[0].endingMaxBalance))

                    for (i in 1 until pointCount) {
                        val prevX = getX(i - 1)
                        val prevY = getY(dailyPoints[i - 1].endingMaxBalance)
                        val currX = getX(i)
                        val currY = getY(dailyPoints[i].endingMaxBalance)

                        val controlX = (prevX + currX) / 2f
                        maxPath.cubicTo(controlX, prevY, controlX, currY, currX, currY)
                    }

                    drawPath(
                        path = maxPath,
                        color = WorstCaseAmber,
                        style = Stroke(
                            width = 2.8f,
                            cap = StrokeCap.Round,
                            pathEffect = if (scenarioFilter == ScenarioFilter.BOTH) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null
                        )
                    )
                }

                // If user is scrubbing/touching, draw scrubber vertical line & node indicators
                if (isTouching && selectedIndex in dailyPoints.indices) {
                    val touchX = getX(selectedIndex)
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(touchX, 0f),
                        end = Offset(touchX, chartHeight),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Point node for Average
                    if (scenarioFilter != ScenarioFilter.WORST_CASE) {
                        val avgY = getY(selectedPoint.endingAvgBalance)
                        drawCircle(color = Color.White, radius = 6f, center = Offset(touchX, avgY))
                        drawCircle(color = EmeraldPrimary, radius = 4f, center = Offset(touchX, avgY))
                    }

                    // Point node for Worst-Case
                    if (scenarioFilter != ScenarioFilter.AVERAGE) {
                        val maxYPoint = getY(selectedPoint.endingMaxBalance)
                        drawCircle(color = Color.White, radius = 6f, center = Offset(touchX, maxYPoint))
                        drawCircle(color = WorstCaseAmber, radius = 4f, center = Offset(touchX, maxYPoint))
                    }
                }
            }
        }

        // Legend row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (scenarioFilter != ScenarioFilter.WORST_CASE) {
                    LegendItem(color = EmeraldPrimary, label = "Average Flow")
                }
                if (scenarioFilter != ScenarioFilter.AVERAGE) {
                    LegendItem(color = WorstCaseAmber, label = "Worst-Case Flow", isDashed = (scenarioFilter == ScenarioFilter.BOTH))
                }
                LegendItem(color = ThresholdDanger, label = "Safety (${formatCurrency(minimumThreshold)})", isDashed = true)
            }

            Text(
                text = "Hold/drag to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isDashed: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isDashed) {
            Canvas(modifier = Modifier.size(width = 12.dp, height = 4.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, 2f),
                    end = Offset(size.width, 2f),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 4.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

fun formatCurrency(amount: Double): String {
    return String.format(Locale.US, "$%,.0f", amount)
}
