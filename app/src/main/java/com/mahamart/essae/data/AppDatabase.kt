package com.mahamart.essae.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Plu::class, PriceChangeAudit::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pluDao(): PluDao
    abstract fun priceChangeAuditDao(): PriceChangeAuditDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_change_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pluNo INTEGER NOT NULL,
                        pluName TEXT NOT NULL,
                        oldPrice REAL NOT NULL,
                        newPrice REAL NOT NULL,
                        source TEXT NOT NULL,
                        status TEXT NOT NULL,
                        changedAt INTEGER NOT NULL,
                        uploadedAt INTEGER,
                        deviceIp TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        scaleIp TEXT NOT NULL,
                        cloudSynced INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE price_change_audit ADD COLUMN adminUpdateId TEXT"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_price_change_audit_adminUpdateId_pluNo ON price_change_audit(adminUpdateId, pluNo)"
                )
            }
        }

        fun create(context: Context) = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "essae.db"
        )
            // MainActivity and WorkManager may open separate Room instances.
            // Broadcast invalidations so the PLU list refreshes when the worker writes.
            .enableMultiInstanceInvalidation()
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
}
