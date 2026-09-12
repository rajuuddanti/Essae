package com.mahamart.essae.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plu")
data class Plu(
    @PrimaryKey val number: Int,
    val name: String,
    val code: String,
    val uom: Int, // 0 = WEIGH, 1 = PCS
    val unitPrice: Double
)
