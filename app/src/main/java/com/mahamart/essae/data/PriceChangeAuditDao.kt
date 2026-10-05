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

    @Query("SELECT EXISTS(SELECT 1 FROM price_change_audit WHERE adminUpdateId = :updateId AND pluNo = :pluNo)")
    suspend fun hasAdminPushAudit(updateId: String, pluNo: Int): Boolean

    @Query("SELECT * FROM price_change_audit ORDER BY changedAt DESC")
    fun observeAll(): Flow<List<PriceChangeAudit>>

    @Query("SELECT * FROM price_change_audit WHERE cloudSynced = 0 ORDER BY changedAt ASC")
    suspend fun getUnsynced(): List<PriceChangeAudit>

    @Query("""
        SELECT a.pluNo
        FROM price_change_audit a
        WHERE a.status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM price_change_audit newer
              WHERE newer.pluNo = a.pluNo
                AND (
                    newer.changedAt > a.changedAt
                    OR (
                        newer.changedAt = a.changedAt
                        AND newer.id > a.id
                    )
                )
          )
    """)
    suspend fun getPendingPriceChangePluNumbers(): List<Int>

    @Query("""
        SELECT a.pluNo
        FROM price_change_audit a
        WHERE a.status = 'PENDING'
          AND NOT EXISTS (
              SELECT 1
              FROM price_change_audit newer
              WHERE newer.pluNo = a.pluNo
                AND (
                    newer.changedAt > a.changedAt
                    OR (
                        newer.changedAt = a.changedAt
                        AND newer.id > a.id
                    )
                )
          )
    """)
    fun observePendingPriceChangePluNumbers(): Flow<List<Int>>

    @Query("""
        SELECT a.* FROM price_change_audit a
        JOIN plu p ON p.number = a.pluNo
        WHERE a.status = 'PENDING'
          AND a.source = 'ADMIN_PUSH'
          AND ABS(a.newPrice - p.unitPrice) > 0.0001
          AND NOT EXISTS (
              SELECT 1
              FROM price_change_audit newer
              WHERE newer.pluNo = a.pluNo
                AND newer.status = 'PENDING'
                AND newer.source = 'ADMIN_PUSH'
                AND (
                    newer.changedAt > a.changedAt
                    OR (
                        newer.changedAt = a.changedAt
                        AND newer.id > a.id
                    )
                )
          )
        ORDER BY a.changedAt ASC, a.id ASC
    """)
    suspend fun getPendingAdminPushChanges(): List<PriceChangeAudit>

    @Query("UPDATE price_change_audit SET status = 'UPLOADED', uploadedAt = :uploadedAt WHERE status = 'PENDING'")
    suspend fun markAllPendingUploaded(uploadedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM price_change_audit WHERE status = 'PENDING' AND pluNo IN (:pluNumbers)")
    suspend fun deletePendingForPluNumbers(pluNumbers: List<Int>)

    @Query("UPDATE price_change_audit SET cloudSynced = 1 WHERE id IN (:ids)")
    suspend fun markCloudSynced(ids: List<Long>)

    @Query("UPDATE price_change_audit SET status = 'UPLOADED', uploadedAt = :uploadedAt WHERE pluNo = :pluNo AND status = 'PENDING' AND newPrice = :newPrice")
    suspend fun markUploaded(pluNo: Int, newPrice: Double, uploadedAt: Long = System.currentTimeMillis())
}
