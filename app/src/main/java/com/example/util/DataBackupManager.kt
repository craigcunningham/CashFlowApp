package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BackupSnapshot(
    val version: Int,
    val exportedAtEpochMillis: Long,
    val settings: UserSettingsEntity,
    val items: List<CashFlowItemEntity>,
    val actuals: List<ActualAdjustmentEntity>
)

object DataBackupManager {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun createBackupJson(
        settings: UserSettingsEntity,
        items: List<CashFlowItemEntity>,
        actuals: List<ActualAdjustmentEntity>
    ): String {
        val root = JSONObject()
        root.put("format", "CashFlow_Backup")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Settings JSON
        val settingsObj = JSONObject().apply {
            put("startingCashAmount", settings.startingCashAmount)
            put("startingDateEpochDay", settings.startingDateEpochDay)
            put("minimumThresholdAmount", settings.minimumThresholdAmount)
            put("currencySymbol", settings.currencySymbol)
            put("notifyBelowThreshold", settings.notifyBelowThreshold)
            put("projectionHorizonMonths", settings.projectionHorizonMonths)
            put("themeMode", settings.themeMode)
        }
        root.put("settings", settingsObj)

        // Items JSON array
        val itemsArray = JSONArray()
        for (item in items) {
            val itemObj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("isPeriodic", item.isPeriodic)
                put("period", item.period)
                put("averageAmount", item.averageAmount)
                put("maximumAmount", item.maximumAmount)
                put("startDateEpochDay", item.startDateEpochDay)
                if (item.endDateEpochDay != null) {
                    put("endDateEpochDay", item.endDateEpochDay)
                }
                put("category", item.category)
                put("isActive", item.isActive)
                put("notes", item.notes)
            }
            itemsArray.put(itemObj)
        }
        root.put("items", itemsArray)

        // Actual adjustments array
        val actualsArray = JSONArray()
        for (actual in actuals) {
            val actualObj = JSONObject().apply {
                put("id", actual.id)
                put("periodicItemId", actual.periodicItemId)
                put("occurrenceDateEpochDay", actual.occurrenceDateEpochDay)
                put("actualAmount", actual.actualAmount)
                put("notes", actual.notes)
                put("recordedAtEpochMillis", actual.recordedAtEpochMillis)
            }
            actualsArray.put(actualObj)
        }
        root.put("actuals", actualsArray)

        return root.toString(2)
    }

    fun exportBackupFile(
        context: Context,
        settings: UserSettingsEntity,
        items: List<CashFlowItemEntity>,
        actuals: List<ActualAdjustmentEntity>
    ): File {
        val fileName = "CashFlow_Backup_${LocalDate.now().format(DATE_FORMATTER)}.json"
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(createBackupJson(settings, items, actuals))
        }
        return file
    }

    fun shareBackupFile(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save or Share CashFlow Backup"))
    }

    fun parseBackupJson(jsonString: String): BackupSnapshot {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        val settings = UserSettingsEntity(
            id = 1,
            startingCashAmount = settingsObj.optDouble("startingCashAmount", 5000.0),
            startingDateEpochDay = settingsObj.optLong("startingDateEpochDay", LocalDate.now().toEpochDay()),
            minimumThresholdAmount = settingsObj.optDouble("minimumThresholdAmount", 1000.0),
            currencySymbol = settingsObj.optString("currencySymbol", "$"),
            notifyBelowThreshold = settingsObj.optBoolean("notifyBelowThreshold", true),
            projectionHorizonMonths = settingsObj.optInt("projectionHorizonMonths", 6),
            themeMode = settingsObj.optString("themeMode", "SYSTEM")
        )

        val items = mutableListOf<CashFlowItemEntity>()
        val itemsArray = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            items.add(
                CashFlowItemEntity(
                    id = obj.optLong("id", 0L),
                    title = obj.getString("title"),
                    type = obj.getString("type"),
                    isPeriodic = obj.optBoolean("isPeriodic", true),
                    period = obj.optString("period", "MONTHLY"),
                    averageAmount = obj.getDouble("averageAmount"),
                    maximumAmount = obj.getDouble("maximumAmount"),
                    startDateEpochDay = obj.getLong("startDateEpochDay"),
                    endDateEpochDay = if (obj.has("endDateEpochDay")) obj.getLong("endDateEpochDay") else null,
                    category = obj.optString("category", "Other"),
                    isActive = obj.optBoolean("isActive", true),
                    notes = obj.optString("notes", "")
                )
            )
        }

        val actuals = mutableListOf<ActualAdjustmentEntity>()
        val actualsArray = root.optJSONArray("actuals") ?: JSONArray()
        for (i in 0 until actualsArray.length()) {
            val obj = actualsArray.getJSONObject(i)
            actuals.add(
                ActualAdjustmentEntity(
                    id = obj.optLong("id", 0L),
                    periodicItemId = obj.getLong("periodicItemId"),
                    occurrenceDateEpochDay = obj.getLong("occurrenceDateEpochDay"),
                    actualAmount = obj.getDouble("actualAmount"),
                    notes = obj.optString("notes", ""),
                    recordedAtEpochMillis = obj.optLong("recordedAtEpochMillis", System.currentTimeMillis())
                )
            )
        }

        return BackupSnapshot(
            version = version,
            exportedAtEpochMillis = exportedAt,
            settings = settings,
            items = items,
            actuals = actuals
        )
    }

    fun readTextFromUri(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open file input stream")
        return inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }
}
