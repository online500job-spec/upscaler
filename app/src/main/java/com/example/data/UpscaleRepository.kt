package com.example.data

import kotlinx.coroutines.flow.Flow

class UpscaleRepository(private val upscaleDao: UpscaleDao) {
    val allHistory: Flow<List<UpscaleItem>> = upscaleDao.getAllHistory()

    suspend fun insert(item: UpscaleItem) {
        upscaleDao.insertItem(item)
    }

    suspend fun delete(item: UpscaleItem) {
        upscaleDao.deleteItem(item)
    }

    suspend fun clearAll() {
        upscaleDao.clearAllHistory()
    }
}
