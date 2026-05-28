package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upscale_history")
data class UpscaleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalName: String,
    val originalPath: String,
    val upscaledPath: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val upscaledWidth: Int,
    val upscaledHeight: Int,
    val originalSize: Long,
    val upscaledSize: Long,
    val ratio: Int,
    val mode: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
