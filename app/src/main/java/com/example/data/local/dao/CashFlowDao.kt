package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowDao {

    // --- Cash Flow Items ---
    @Query("SELECT * FROM cash_flow_items ORDER BY startDateEpochDay ASC, id ASC")
    fun getAllItems(): Flow<List<CashFlowItemEntity>>

    @Query("SELECT * FROM cash_flow_items WHERE isActive = 1 ORDER BY startDateEpochDay ASC")
    fun getActiveItems(): Flow<List<CashFlowItemEntity>>

    @Query("SELECT * FROM cash_flow_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): CashFlowItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CashFlowItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CashFlowItemEntity>)

    @Update
    suspend fun updateItem(item: CashFlowItemEntity)

    @Delete
    suspend fun deleteItem(item: CashFlowItemEntity)

    @Query("DELETE FROM cash_flow_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    // --- Actual Adjustments ---
    @Query("SELECT * FROM actual_adjustments ORDER BY occurrenceDateEpochDay ASC")
    fun getAllActuals(): Flow<List<ActualAdjustmentEntity>>

    @Query("SELECT * FROM actual_adjustments WHERE periodicItemId = :itemId")
    fun getActualsForItem(itemId: Long): Flow<List<ActualAdjustmentEntity>>

    @Query(
        "SELECT * FROM actual_adjustments WHERE periodicItemId = :itemId AND occurrenceDateEpochDay = :epochDay LIMIT 1"
    )
    suspend fun getActualForOccurrence(itemId: Long, epochDay: Long): ActualAdjustmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateActual(actual: ActualAdjustmentEntity): Long

    @Delete
    suspend fun deleteActual(actual: ActualAdjustmentEntity)

    @Query(
        "DELETE FROM actual_adjustments WHERE periodicItemId = :itemId AND occurrenceDateEpochDay = :epochDay"
    )
    suspend fun deleteActualForOccurrence(itemId: Long, epochDay: Long)

    @Query("DELETE FROM actual_adjustments WHERE periodicItemId = :itemId")
    suspend fun deleteActualsForItem(itemId: Long)

    // --- User Settings ---
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettingsDirect(): UserSettingsEntity?

    @Query("DELETE FROM cash_flow_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM actual_adjustments")
    suspend fun clearAllActuals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettingsEntity)
}
