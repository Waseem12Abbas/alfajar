package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.models.VehiclePass
import kotlinx.coroutines.flow.Flow

@Dao
interface VehiclePassDao {
    @Query("SELECT * FROM vehicle_passes WHERE unique_id = :uid LIMIT 1")
    suspend fun getPassByUniqueId(uid: String): VehiclePass?

    @Query("SELECT * FROM vehicle_passes ORDER BY created_at DESC")
    fun getAllPassesFlow(): Flow<List<VehiclePass>>

    @Query("SELECT * FROM vehicle_passes")
    suspend fun getAllPassesStatic(): List<VehiclePass>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPass(pass: VehiclePass): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replacePass(pass: VehiclePass): Long

    @Query("SELECT * FROM vehicle_passes WHERE last_modified > :since")
    suspend fun getModifiedSince(since: String): List<VehiclePass>

    @Query("UPDATE vehicle_passes SET is_revoked = :isRevoked WHERE id = :id")
    suspend fun setRevoked(id: Int, isRevoked: Int)

    @Query("DELETE FROM vehicle_passes WHERE id = :id")
    suspend fun deletePass(id: Int)
}
