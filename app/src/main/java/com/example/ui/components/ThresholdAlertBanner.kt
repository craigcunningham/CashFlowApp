package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.ProjectionResult
import com.example.ui.theme.ThresholdDanger
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ThresholdAlertBanner(
    projection: ProjectionResult,
    isDismissed: Boolean,
    onDismiss: () -> Unit,
    onViewInLedger: () -> Unit,
    onAdjustThreshold: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBreached = projection.isThresholdBreachedAvg || projection.isThresholdBreachedMax

    AnimatedVisibility(
        visible = isBreached && !isDismissed,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        val breachDate = projection.thresholdBreachDateAvg ?: projection.thresholdBreachDateMax
        val breachAmt = if (projection.isThresholdBreachedAvg) projection.lowestAvgBalance else projection.lowestMaxBalance
        val dateFormatted = breachDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "an upcoming date"
        val isWorstCaseOnly = !projection.isThresholdBreachedAvg && projection.isThresholdBreachedMax

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("threshold_alert_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = ThresholdDanger.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.5.dp, ThresholdDanger.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = ThresholdDanger,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isWorstCaseOnly) "Worst-Case Cash Alert" else "Low Cash Flow Warning",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ThresholdDanger
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("dismiss_alert_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Alert",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = String.format(
                        Locale.US,
                        "Projected cash drops below your $%,.0f safety threshold on %s, reaching a low of $%,.0f (%s scenario).",
                        projection.minimumThreshold,
                        dateFormatted,
                        breachAmt,
                        if (isWorstCaseOnly) "worst-case maximum costs" else "average costs"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewInLedger,
                        modifier = Modifier.weight(1f).testTag("alert_view_ledger_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThresholdDanger
                        ),
                        border = BorderStroke(1.dp, ThresholdDanger.copy(alpha = 0.6f))
                    ) {
                        Text("Inspect Ledger", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onAdjustThreshold,
                        modifier = Modifier.weight(1f).testTag("alert_adjust_threshold_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Safety Buffer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
