package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CashFlowDao
import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.CustomCategoryEntity
import com.example.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        CashFlowItemEntity::class,
        ActualAdjustmentEntity::class,
        UserSettingsEntity::class,
        CustomCategoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cashFlowDao(): CashFlowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE actual_adjustments ADD COLUMN actualDateEpochDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notifyAverageCase INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notifyWorstCase INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notificationLookaheadDays INTEGER NOT NULL DEFAULT 30")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL DEFAULT 'EXPENSE',
                        `isDefault` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_name_type` ON `custom_categories` (`name`, `type`)")

                val defaultExpenses = listOf(
                    "Housing", "Utilities", "Food", "Transportation",
                    "Health", "Insurance", "Entertainment", "Debt", "Personal", "Other"
                )
                for (cat in defaultExpenses) {
                    db.execSQL("INSERT OR IGNORE INTO `custom_categories` (`name`, `type`, `isDefault`) VALUES ('$cat', 'EXPENSE', 1)")
                }

                val defaultIncomes = listOf(
                    "Salary", "Freelance", "Investment", "Bonus", "Rental", "Other"
                )
                for (cat in defaultIncomes) {
                    db.execSQL("INSERT OR IGNORE INTO `custom_categories` (`name`, `type`, `isDefault`) VALUES ('$cat', 'INCOME', 1)")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN worstCaseProjectionDays INTEGER NOT NULL DEFAULT 60")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cash_flow_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
