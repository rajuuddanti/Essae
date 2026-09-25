package com.mahamart.essae.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PluDao {
    @Query("SELECT * FROM plu ORDER BY number") fun observeAll(): Flow<List<Plu>>
    @Query("SELECT COUNT(*) FROM plu") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<Plu>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: Plu)
    @Delete suspend fun delete(item: Plu)
    @Query("DELETE FROM plu") suspend fun deleteAll()
}
