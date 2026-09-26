package com.mahamart.essae.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceChangeAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PriceChangeAudit): Long

    @Query("SELECT * FROM price_change_audit ORDER BY changedAt DESC")
    fun observeAll(): Flow<List<PriceChangeAudit>>

    @Query("SELECT * FROM price_change_audit WHERE cloudSynced = 0 ORDER BY changedAt ASC")
    suspend fun getUnsynced(): List<PriceChangeAudit>

    @Query("SELECT pluNo FROM price_change_audit WHERE status = 'PENDING'")
    suspend fun getPendingPriceChangePluNumbers(): List<Int>

    @Query("UPDATE price_change_audit SET status = 'UPLOADED', uploadedAt = :uploadedAt WHERE status = 'PENDING'")
    suspend fun markAllPendingUploaded(uploadedAt: Long = System.currentTimeMillis())

    @Query("UPDATE price_change_audit SET cloudSynced = 1 WHERE id IN (:ids)")
    suspend fun markCloudSynced(ids: List<Long>)

    @Query("UPDATE price_change_audit SET status = 'UPLOADED', uploadedAt = :uploadedAt WHERE pluNo = :pluNo AND status = 'PENDING' AND newPrice = :newPrice")
    suspend fun markUploaded(pluNo: Int, newPrice: Double, uploadedAt: Long = System.currentTimeMillis())
}
