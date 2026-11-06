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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.DailyProjection
import com.example.ui.ScenarioFilter
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ThresholdDanger
import com.example.ui.theme.WorstCaseAmber
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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

    val monthTicks = remember(dailyPoints) {
        val ticks = mutableListOf<MonthTick>()
        var lastYearMonth: YearMonth? = null
        val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.US)

        for (i in dailyPoints.indices) {
            val date = dailyPoints[i].date
            val ym = YearMonth.from(date)
            if (ym != lastYearMonth) {
                ticks.add(MonthTick(index = i, label = date.format(formatter)))
                lastYearMonth = ym
            }
        }
        ticks
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val leftMarginDp = 52.dp
    val rightMarginDp = 12.dp
    val topMarginDp = 14.dp
    val bottomMarginDp = 28.dp

    val leftMarginPx = with(density) { leftMarginDp.toPx() }
    val rightMarginPx = with(density) { rightMarginDp.toPx() }
    val topMarginPx = with(density) { topMarginDp.toPx() }
    val bottomMarginPx = with(density) { bottomMarginDp.toPx() }

    // Calculate dynamic Y-axis bounds (min and max balances)
    val allValues = mutableListOf<Double>()
    allValues.add(minimumThreshold)
    for (p in dailyPoints) {
        if (scenarioFilter != ScenarioFilter.WORST_CASE) allValues.add(p.endingAvgBalance)
        if (scenarioFilter != ScenarioFilter.AVERAGE && p.isWorstCaseActive) allValues.add(p.endingMaxBalance)
    }

    val rawMinY = allValues.minOrNull() ?: 0.0
    val rawMaxY = allValues.maxOrNull() ?: 5000.0
    val rawRange = max(100.0, rawMaxY - rawMinY)

    val yStep = calculateNiceStep(rawRange)
    var minY = floor(rawMinY / yStep) * yStep
    var maxY = ceil(rawMaxY / yStep) * yStep

    if (minY > 0 && minY <= yStep) {
        minY = 0.0
    }
    if (minY == maxY) {
        maxY += yStep
    }
    val yRange = maxY - minY

    val yTicks = remember(minY, maxY, yStep) {
        val ticks = mutableListOf<Double>()
        var curr = minY
        while (curr <= maxY + 0.001) {
            ticks.add(curr)
            curr += yStep
        }
        ticks
    }

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val axisBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

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
                            if (selectedPoint.isWorstCaseActive) {
                                Text(
                                    text = formatCurrency(selectedPoint.endingMaxBalance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPoint.isBelowThresholdMax) ThresholdDanger else MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = "N/A (>limit)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(top = 8.dp, bottom = 4.dp)
                .pointerInput(dailyPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            isTouching = true
                            val relX = offset.x - leftMarginPx
                            val currentPlotWidth = size.width - leftMarginPx - rightMarginPx
                            touchFraction = if (currentPlotWidth > 0) (relX / currentPlotWidth).coerceIn(0f, 1f) else 0f
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
                            val relX = offset.x - leftMarginPx
                            val currentPlotWidth = size.width - leftMarginPx - rightMarginPx
                            touchFraction = if (currentPlotWidth > 0) (relX / currentPlotWidth).coerceIn(0f, 1f) else 0f
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
                            val relX = change.position.x - leftMarginPx
                            val currentPlotWidth = size.width - leftMarginPx - rightMarginPx
                            touchFraction = if (currentPlotWidth > 0) (relX / currentPlotWidth).coerceIn(0f, 1f) else 0f
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartWidth = size.width
                val chartHeight = size.height
                val pointCount = dailyPoints.size

                val plotWidth = (chartWidth - leftMarginPx - rightMarginPx).coerceAtLeast(1f)
                val plotHeight = (chartHeight - topMarginPx - bottomMarginPx).coerceAtLeast(1f)
                val baselineY = topMarginPx + plotHeight

                fun getX(index: Int): Float {
                    return if (pointCount <= 1) leftMarginPx + plotWidth / 2f
                    else leftMarginPx + (index.toFloat() / (pointCount - 1).toFloat()) * plotWidth
                }

                fun getY(value: Double): Float {
                    val normalized = ((value - minY) / yRange).toFloat()
                    return baselineY - (normalized * plotHeight)
                }

                // Horizontal grid lines and Y-axis text labels
                for (tickVal in yTicks) {
                    val gy = getY(tickVal)
                    if (gy in (topMarginPx - 1f)..(baselineY + 1f)) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(leftMarginPx, gy),
                            end = Offset(leftMarginPx + plotWidth, gy),
                            strokeWidth = 1f
                        )

                        val labelStr = formatYAxisLabel(tickVal)
                        val measured = textMeasurer.measure(
                            text = labelStr,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = axisTextColor,
                                textAlign = TextAlign.End
                            )
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelStr,
                            topLeft = Offset(
                                x = leftMarginPx - measured.size.width - with(density) { 6.dp.toPx() },
                                y = gy - (measured.size.height / 2f)
                            ),
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = axisTextColor
                            )
                        )
                    }
                }

                // Minimum threshold line (Red dashed line) and label
                val threshY = getY(minimumThreshold)
                if (threshY in topMarginPx..baselineY) {
                    drawLine(
                        color = ThresholdDanger.copy(alpha = 0.85f),
                        start = Offset(leftMarginPx, threshY),
                        end = Offset(leftMarginPx + plotWidth, threshY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )

                    val threshLabel = formatYAxisLabel(minimumThreshold)
                    val measuredThresh = textMeasurer.measure(
                        text = threshLabel,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThresholdDanger
                        )
                    )
                    // Draw red threshold label if not directly on top of a normal tick
                    val isCloseToTick = yTicks.any { abs(getY(it) - threshY) < with(density) { 14.dp.toPx() } }
                    if (!isCloseToTick) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = threshLabel,
                            topLeft = Offset(
                                x = leftMarginPx - measuredThresh.size.width - with(density) { 6.dp.toPx() },
                                y = threshY - (measuredThresh.size.height / 2f)
                            ),
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThresholdDanger
                            )
                        )
                    }
                }

                // Zero line if in view
                if (minY < 0 && maxY > 0) {
                    val zeroY = getY(0.0)
                    if (zeroY in topMarginPx..baselineY) {
                        drawLine(
                            color = zeroLineColor,
                            start = Offset(leftMarginPx, zeroY),
                            end = Offset(leftMarginPx + plotWidth, zeroY),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // Draw Average Projection curve & gradient area
                if (scenarioFilter != ScenarioFilter.WORST_CASE && pointCount > 1) {
                    val avgPath = Path()
                    val areaPath = Path()

                    areaPath.moveTo(leftMarginPx, baselineY)
                    avgPath.moveTo(getX(0), getY(dailyPoints[0].endingAvgBalance))
                    areaPath.lineTo(getX(0), getY(dailyPoints[0].endingAvgBalance))

                    for (i in 1 until pointCount) {
                        val prevX = getX(i - 1)
                        val prevY = getY(dailyPoints[i - 1].endingAvgBalance)
                        val currX = getX(i)
                        val currY = getY(dailyPoints[i].endingAvgBalance)

                        val controlX = (prevX + currX) / 2f
                        avgPath.cubicTo(controlX, prevY, controlX, currY, currX, currY)
                        areaPath.cubicTo(controlX, prevY, controlX, currY, currX, currY)
                    }

                    areaPath.lineTo(leftMarginPx + plotWidth, baselineY)
                    areaPath.close()

                    // Gradient fill underneath average projection
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                EmeraldLight.copy(alpha = 0.28f),
                                EmeraldLight.copy(alpha = 0.02f)
                            ),
                            startY = topMarginPx,
                            endY = baselineY
                        )
                    )

                    // Stroke line
                    drawPath(
                        path = avgPath,
                        color = EmeraldPrimary,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }

                // Draw Worst-Case / Maximum Cost projection line (bounded by cutoff window)
                val activeWorstCasePoints = dailyPoints.filter { it.isWorstCaseActive }
                if (scenarioFilter != ScenarioFilter.AVERAGE && activeWorstCasePoints.size > 1) {
                    val maxPath = Path()
                    val firstActiveIndex = dailyPoints.indexOfFirst { it.isWorstCaseActive }
                    maxPath.moveTo(getX(firstActiveIndex), getY(activeWorstCasePoints[0].endingMaxBalance))

                    for (i in 1 until activeWorstCasePoints.size) {
                        val prevPoint = activeWorstCasePoints[i - 1]
                        val currPoint = activeWorstCasePoints[i]
                        val prevIndex = dailyPoints.indexOf(prevPoint)
                        val currIndex = dailyPoints.indexOf(currPoint)

                        val prevX = getX(prevIndex)
                        val prevY = getY(prevPoint.endingMaxBalance)
                        val currX = getX(currIndex)
                        val currY = getY(currPoint.endingMaxBalance)

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

                // Axis border lines (left border and bottom baseline)
                drawLine(
                    color = axisBorderColor,
                    start = Offset(leftMarginPx, topMarginPx),
                    end = Offset(leftMarginPx, baselineY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = axisBorderColor,
                    start = Offset(leftMarginPx, baselineY),
                    end = Offset(leftMarginPx + plotWidth, baselineY),
                    strokeWidth = 1.5f
                )

                // Draw monthly ticks and "MMM yy" labels along X-axis
                var lastDrawnLabelRight = -1000f
                val labelSpacingPx = with(density) { 8.dp.toPx() }
                val tickLengthPx = with(density) { 4.dp.toPx() }

                for (mTick in monthTicks) {
                    val tx = getX(mTick.index)
                    // Small vertical tick mark on bottom baseline
                    drawLine(
                        color = axisBorderColor,
                        start = Offset(tx, baselineY),
                        end = Offset(tx, baselineY + tickLengthPx),
                        strokeWidth = 1.5f
                    )

                    val measuredMonth = textMeasurer.measure(
                        text = mTick.label,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = axisTextColor
                        )
                    )
                    val labelLeft = tx - (measuredMonth.size.width / 2f)
                    // Prevent overlapping labels
                    if (labelLeft >= lastDrawnLabelRight + labelSpacingPx &&
                        labelLeft >= leftMarginPx - 10f &&
                        labelLeft + measuredMonth.size.width <= chartWidth) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = mTick.label,
                            topLeft = Offset(
                                x = labelLeft,
                                y = baselineY + tickLengthPx + with(density) { 2.dp.toPx() }
                            ),
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = axisTextColor
                            )
                        )
                        lastDrawnLabelRight = labelLeft + measuredMonth.size.width
                    }
                }

                // If user is scrubbing/touching, draw scrubber vertical line & node indicators
                if (isTouching && selectedIndex in dailyPoints.indices) {
                    val touchX = getX(selectedIndex)
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(touchX, topMarginPx),
                        end = Offset(touchX, baselineY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Point node for Average
                    if (scenarioFilter != ScenarioFilter.WORST_CASE) {
                        val avgY = getY(selectedPoint.endingAvgBalance)
                        drawCircle(color = Color.White, radius = 6f, center = Offset(touchX, avgY))
                        drawCircle(color = EmeraldPrimary, radius = 4f, center = Offset(touchX, avgY))
                    }

                    // Point node for Worst-Case (only if active)
                    if (scenarioFilter != ScenarioFilter.AVERAGE && selectedPoint.isWorstCaseActive) {
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
                    val activeDays = dailyPoints.count { it.isWorstCaseActive }
                    val worstLabel = if (activeDays < dailyPoints.size) "Worst-Case (${activeDays}d)" else "Worst-Case Flow"
                    LegendItem(color = WorstCaseAmber, label = worstLabel, isDashed = (scenarioFilter == ScenarioFilter.BOTH))
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

private data class MonthTick(
    val index: Int,
    val label: String
)

fun calculateNiceStep(range: Double): Double {
    if (range <= 0.0) return 500.0
    val rawStep = range / 5.0

    val niceSteps = doubleArrayOf(
        250.0, 500.0, 1000.0, 2000.0, 2500.0, 5000.0,
        10000.0, 20000.0, 25000.0, 50000.0, 100000.0
    )

    for (step in niceSteps) {
        if (rawStep <= step) return step
    }

    var step = 100000.0
    while (rawStep > step) {
        step *= 2.0
    }
    return step
}

fun formatYAxisLabel(amount: Double): String {
    val absAmount = abs(amount)
    val prefix = if (amount < 0) "-$" else "$"
    return when {
        absAmount >= 1_000_000 -> {
            val m = absAmount / 1_000_000.0
            if (m % 1.0 == 0.0) "${prefix}%.0fM".format(Locale.US, m)
            else "${prefix}%.1fM".format(Locale.US, m)
        }
        absAmount >= 10_000 -> {
            val k = absAmount / 1_000.0
            if (k % 1.0 == 0.0) "${prefix}%.0fk".format(Locale.US, k)
            else "${prefix}%.1fk".format(Locale.US, k)
        }
        else -> {
            "${prefix}%,.0f".format(Locale.US, absAmount)
        }
    }
}

