package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.entity.CashFlowItemEntity
import com.example.domain.engine.CashFlowEvent
import com.example.domain.engine.ProjectionResult
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun exportScheduleToCsv(context: Context, projection: ProjectionResult): File {
        val fileName = "CashFlow_Projection_${LocalDate.now().format(DATE_FORMATTER)}.csv"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)

        FileWriter(file).use { writer ->
            // Metadata header
            writer.append("# Cash Flow Projection Report\n")
            writer.append("# Starting Date: ${projection.startDate.format(DATE_FORMATTER)}\n")
            writer.append("# Projection End Date: ${projection.endDate.format(DATE_FORMATTER)}\n")
            writer.append(String.format(Locale.US, "# Starting Balance: $%.2f\n", projection.startingBalance))
            writer.append(String.format(Locale.US, "# Safety Threshold: $%.2f\n", projection.minimumThreshold))
            writer.append(String.format(Locale.US, "# Final Projected Balance (Avg): $%.2f\n", projection.finalAvgBalance))
            writer.append(String.format(Locale.US, "# Final Projected Balance (Max/Worst-Case): $%.2f\n", projection.finalMaxBalance))
            writer.append(String.format(Locale.US, "# Lowest Projected Point (Avg): $%.2f on %s\n", projection.lowestAvgBalance, projection.lowestAvgDate?.format(DATE_FORMATTER) ?: "N/A"))
            writer.append(String.format(Locale.US, "# Lowest Projected Point (Max): $%.2f on %s\n\n", projection.lowestMaxBalance, projection.lowestMaxDate?.format(DATE_FORMATTER) ?: "N/A"))

            // Table headers
            writer.append("Date,Type,Description,Category,Period,Is Actual?,Estimated Avg Amount,Estimated Max Amount,Effective Avg Amount,Effective Max Amount,Cumulative Avg Balance,Cumulative Max Balance,Threshold Warning,Notes\n")

            for (event in projection.allEvents) {
                val isBelowThreshold = event.runningAvgBalance < projection.minimumThreshold || event.runningMaxBalance < projection.minimumThreshold
                val thresholdWarning = if (isBelowThreshold) "BELOW THRESHOLD" else "OK"

                writer.append(escapeCsv(event.date.format(DATE_FORMATTER))).append(",")
                writer.append(escapeCsv(event.type.name)).append(",")
                writer.append(escapeCsv(event.title)).append(",")
                writer.append(escapeCsv(event.category)).append(",")
                writer.append(escapeCsv(if (event.isPeriodic) event.period.label else "Ad-hoc")).append(",")
                writer.append(if (event.isActual) "YES" else "NO").append(",")
                writer.append(String.format(Locale.US, "%.2f", event.avgAmount)).append(",")
                writer.append(String.format(Locale.US, "%.2f", event.maxAmount)).append(",")
                writer.append(String.format(Locale.US, "%.2f", event.effectiveAvgAmount)).append(",")
                writer.append(String.format(Locale.US, "%.2f", event.effectiveMaxAmount)).append(",")
                writer.append(String.format(Locale.US, "%.2f", event.runningAvgBalance)).append(",")
                writer.append(String.format(Locale.US, "%.2f", event.runningMaxBalance)).append(",")
                writer.append(escapeCsv(thresholdWarning)).append(",")
                writer.append(escapeCsv(event.notes))
                writer.append("\n")
            }
        }
        return file
    }

    fun exportItemsToCsv(context: Context, items: List<CashFlowItemEntity>): File {
        val fileName = "CashFlow_Master_Items_${LocalDate.now().format(DATE_FORMATTER)}.csv"
        val file = File(context.cacheDir, fileName)

        FileWriter(file).use { writer ->
            writer.append("ID,Description,Type,Is Periodic,Period,Average Amount,Maximum Amount,Start Date,End Date,Category,Is Active,Notes\n")

            for (item in items) {
                val start = LocalDate.ofEpochDay(item.startDateEpochDay).format(DATE_FORMATTER)
                val end = item.endDateEpochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FORMATTER) } ?: ""

                writer.append(item.id.toString()).append(",")
                writer.append(escapeCsv(item.title)).append(",")
                writer.append(escapeCsv(item.type)).append(",")
                writer.append(if (item.isPeriodic) "YES" else "NO").append(",")
                writer.append(escapeCsv(item.period)).append(",")
                writer.append(String.format(Locale.US, "%.2f", item.averageAmount)).append(",")
                writer.append(String.format(Locale.US, "%.2f", item.maximumAmount)).append(",")
                writer.append(escapeCsv(start)).append(",")
                writer.append(escapeCsv(end)).append(",")
                writer.append(escapeCsv(item.category)).append(",")
                writer.append(if (item.isActive) "ACTIVE" else "INACTIVE").append(",")
                writer.append(escapeCsv(item.notes))
                writer.append("\n")
            }
        }
        return file
    }

    fun shareCsvFile(context: Context, file: File, chooserTitle: String = "Export Cash Flow CSV") {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
