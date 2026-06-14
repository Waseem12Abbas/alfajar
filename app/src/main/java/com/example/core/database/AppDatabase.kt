package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.models.User
import com.example.models.VehiclePass

@Database(entities = [User::class, VehiclePass::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun vehiclePassDao(): VehiclePassDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to vehicle_passes table
                db.execSQL("ALTER TABLE vehicle_passes ADD COLUMN last_modified TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vehicle_passes ADD COLUMN device_id TEXT NOT NULL DEFAULT ''")
                // Backfill existing records with current timestamp to enable sync
                db.execSQL("UPDATE vehicle_passes SET last_modified = strftime('%Y-%m-%dT%H:%M:%SZ', 'now') WHERE last_modified = ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pakpass_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
