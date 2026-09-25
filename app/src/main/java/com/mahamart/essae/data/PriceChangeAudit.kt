package com.mahamart.essae.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_change_audit")
data class PriceChangeAudit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluNo: Int,
    val pluName: String,
    val oldPrice: Double,
    val newPrice: Double,
    val source: String,
    val status: String = "PENDING",
    val changedAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null,
    val deviceIp: String = "",
    val deviceId: String = "",
    val scaleIp: String = "",
    val cloudSynced: Boolean = false
)
