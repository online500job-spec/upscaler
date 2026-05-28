package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UpscaleDao {
    @Query("SELECT * FROM upscale_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<UpscaleItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: UpscaleItem)

    @Delete
    suspend fun deleteItem(item: UpscaleItem)

    @Query("DELETE FROM upscale_history")
    suspend fun clearAllHistory()
}
