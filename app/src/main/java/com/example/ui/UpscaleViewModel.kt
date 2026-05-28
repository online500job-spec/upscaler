package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.UpscaleDatabase
import com.example.data.UpscaleItem
import com.example.data.UpscaleRepository
import com.example.engine.GeminiService
import com.example.engine.UpscaleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat

data class InputImage(
    val uri: Uri,
    val bitmap: Bitmap,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val width: Int,
    val height: Int
)

data class ProcessedResult(
    val originalImage: InputImage,
    val upscaledBitmap: Bitmap,
    val upscaledPath: String,
    val upscaledWidth: Int,
    val upscaledHeight: Int,
    val upscaledSizeBytes: Long,
    val upscaledSizeFormatted: String,
    val ratio: Int,
    val mode: String,
    val durationMs: Long
)

class UpscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "UpscaleViewModel"
    private val database = UpscaleDatabase.getDatabase(application)
    private val repository = UpscaleRepository(database.upscaleDao())

    // UI Configuration States
    private val _selectedImages = MutableStateFlow<List<InputImage>>(emptyList())
    val selectedImages: StateFlow<List<InputImage>> = _selectedImages.asStateFlow()

    private val _upscaledResults = MutableStateFlow<List<ProcessedResult>>(emptyList())
    val upscaledResults: StateFlow<List<ProcessedResult>> = _upscaledResults.asStateFlow()

    private val _activeResultIndex = MutableStateFlow(0)
    val activeResultIndex: StateFlow<Int> = _activeResultIndex.asStateFlow()

    // Engine options
    private val _upscaleRatio = MutableStateFlow(2)
    val upscaleRatio: StateFlow<Int> = _upscaleRatio.asStateFlow()

    private val _upscaleMode = MutableStateFlow("High Quality") // "Standard", "High Quality", "Ultra Detail"
    val upscaleMode: StateFlow<String> = _upscaleMode.asStateFlow()

    private val _enableDenoise = MutableStateFlow(true)
    val enableDenoise: StateFlow<Boolean> = _enableDenoise.asStateFlow()

    private val _enableSharpen = MutableStateFlow(true)
    val enableSharpen: StateFlow<Boolean> = _enableSharpen.asStateFlow()

    private val _enableFaceEnhancement = MutableStateFlow(true)
    val enableFaceEnhancement: StateFlow<Boolean> = _enableFaceEnhancement.asStateFlow()

    // Processing status states
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress.asStateFlow()

    private val _currentProcessingIndex = MutableStateFlow(0)
    val currentProcessingIndex: StateFlow<Int> = _currentProcessingIndex.asStateFlow()

    // Gemini states
    private val _geminiAnalyticsText = MutableStateFlow<String?>(null)
    val geminiAnalyticsText: StateFlow<String?> = _geminiAnalyticsText.asStateFlow()

    private val _isGeminiAnalyzing = MutableStateFlow(false)
    val isGeminiAnalyzing: StateFlow<Boolean> = _isGeminiAnalyzing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Room History logs collected safely as reactive StateFlow
    val historyLog: StateFlow<List<UpscaleItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setUpscaleRatio(ratio: Int) {
        _upscaleRatio.value = ratio
    }

    fun setUpscaleMode(mode: String) {
        _upscaleMode.value = mode
    }

    fun setDenoise(enabled: Boolean) {
        _enableDenoise.value = enabled
    }

    fun setSharpen(enabled: Boolean) {
        _enableSharpen.value = enabled
    }

    fun setFaceEnhancement(enabled: Boolean) {
        _enableFaceEnhancement.value = enabled
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        viewModelScope.launch {
            val loadedList = mutableListOf<InputImage>()
            val context = getApplication<Application>().applicationContext
            
            // Limit selection to 6, maintaining strict multi-upload system limits
            val finalUris = uris.take(6)
            
            for (uri in finalUris) {
                try {
                    val inputImage = withContext(Dispatchers.IO) {
                        // Resolve structural details
                        val contentResolver = context.contentResolver
                        var name = "image_${System.currentTimeMillis()}.png"
                        var sizeBytes = 0L
                        
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIdx != -1) name = cursor.getString(nameIdx)
                                if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                            }
                        }

                        // Load Bitmap safely
                        var inputStream: InputStream? = contentResolver.openInputStream(uri)
                        
                        // Measure bounds first to avoid severe OOM on large captures
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream?.close()

                        var width = options.outWidth
                        var height = options.outHeight

                        // Target dimension limit for local prototyping environment to prevent Canvas crashes: 1024 maxWidth
                        var sampleSize = 1
                        while (width / sampleSize > 1024 || height / sampleSize > 1024) {
                            sampleSize *= 2
                        }

                        inputStream = contentResolver.openInputStream(uri)
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                        inputStream?.close()

                        if (bitmap == null) {
                            throw Exception("Failed to decode visual data stream")
                        }

                        val finalW = bitmap.width
                        val finalH = bitmap.height
                        
                        // If file size is not populated from resolver, query length of opened file
                        if (sizeBytes == 0L) {
                            val pfd = contentResolver.openFileDescriptor(uri, "r")
                            sizeBytes = pfd?.statSize ?: 0L
                            pfd?.close()
                        }

                        InputImage(
                            uri = uri,
                            bitmap = bitmap,
                            name = name,
                            sizeBytes = sizeBytes,
                            sizeFormatted = formatFileSize(sizeBytes),
                            width = finalW,
                            height = finalH
                        )
                    }
                    loadedList.add(inputImage)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading content index: ${e.localizedMessage}")
                    _statusMessage.value = "Failed to load image metadata structural features."
                }
            }
            
            _selectedImages.value = (_selectedImages.value + loadedList).take(6)
            _geminiAnalyticsText.value = null // Reset analyses
        }
    }

    /**
     * Remove an image before upscaling.
     */
    fun removeSelectedImage(index: Int) {
        val current = _selectedImages.value.toMutableList()
        if (index in current.indices) {
            val removed = current.removeAt(index)
            _selectedImages.value = current
            removed.bitmap.recycle()
        }
    }

    /**
     * Reorder images in picker (Drag sorting).
     */
    fun moveSelectedImage(fromIndex: Int, toIndex: Int) {
        val current = _selectedImages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _selectedImages.value = current
        }
    }

    fun clearAllSelected() {
        val oldImages = _selectedImages.value
        val oldResults = _upscaledResults.value
        _selectedImages.value = emptyList()
        _upscaledResults.value = emptyList()
        _geminiAnalyticsText.value = null
        oldImages.forEach { it.bitmap.recycle() }
        oldResults.forEach { it.upscaledBitmap.recycle() }
    }

    /**
     * Runs the Queue Processing model for the multi-upscaler system.
     * Upscales all selected images in sequence, updates progress bars dynamically, and logs outputs to Room DB database.
     */
    fun startUpscaleQueue() {
        if (_isProcessing.value) {
            _statusMessage.value = "Processing is already in progress, please wait."
            return
        }
        val pending = _selectedImages.value
        if (pending.isEmpty()) {
            _statusMessage.value = "Select or upload images first."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            val oldResults = _upscaledResults.value
            _upscaledResults.value = emptyList()
            oldResults.forEach { it.upscaledBitmap.recycle() }
            _activeResultIndex.value = 0
            
            val ratio = _upscaleRatio.value
            val mode = _upscaleMode.value
            val denoise = _enableDenoise.value
            val sharpen = _enableSharpen.value
            val faceRestore = _enableFaceEnhancement.value
            
            val tempResultsList = mutableListOf<ProcessedResult>()
            val context = getApplication<Application>().applicationContext

            for (i in pending.indices) {
                _currentProcessingIndex.value = i
                _processingProgress.value = 0
                val originalInput = pending[i]
                
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Call the Kotlin scaling, denoising, sharpening, face restoring engine
                    val upscaledBitmap = UpscaleEngine.upscaleImage(
                        context = context,
                        source = originalInput.bitmap,
                        ratio = ratio,
                        mode = mode,
                        enableDenoise = denoise,
                        enableSharpen = sharpen,
                        enableFaceEnhancer = faceRestore,
                        onProgress = { progress ->
                            _processingProgress.value = progress
                        }
                    )

                    val durationMs = System.currentTimeMillis() - startTime
                    
                    // Write high resolution output to secure private storage
                    val savedPath = withContext(Dispatchers.IO) {
                        UpscaleEngine.saveBitmapToStorage(
                            context = context,
                            bitmap = upscaledBitmap,
                            name = originalInput.name
                        )
                    }

                    val savedFile = File(savedPath)
                    val outSizeBytes = savedFile.length()

                    val result = ProcessedResult(
                        originalImage = originalInput,
                        upscaledBitmap = upscaledBitmap,
                        upscaledPath = savedPath,
                        upscaledWidth = upscaledBitmap.width,
                        upscaledHeight = upscaledBitmap.height,
                        upscaledSizeBytes = outSizeBytes,
                        upscaledSizeFormatted = formatFileSize(outSizeBytes),
                        ratio = ratio,
                        mode = mode,
                        durationMs = durationMs
                    )
                    tempResultsList.add(result)
                    
                    // Store to Room Database history
                    withContext(Dispatchers.IO) {
                        repository.insert(
                            UpscaleItem(
                                originalName = originalInput.name,
                                originalPath = originalInput.uri.toString(),
                                upscaledPath = savedPath,
                                originalWidth = originalInput.width,
                                originalHeight = originalInput.height,
                                upscaledWidth = upscaledBitmap.width,
                                upscaledHeight = upscaledBitmap.height,
                                originalSize = originalInput.sizeBytes,
                                upscaledSize = outSizeBytes,
                                ratio = ratio,
                                mode = mode,
                                durationMs = durationMs
                            )
                        )
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error upscaling input index $i: ${e.localizedMessage}")
                    _statusMessage.value = "Failed upscaling '${originalInput.name}' profile."
                }
            }

            _upscaledResults.value = tempResultsList
            _activeResultIndex.value = 0
            _isProcessing.value = false
            _processingProgress.value = 100
            
            if (tempResultsList.isNotEmpty()) {
                _statusMessage.value = "Batch processing completed successfully!"
            }
        }
    }

    /**
     * Set which processed index to view on details container
     */
    fun selectActiveResult(index: Int) {
        _activeResultIndex.value = index
    }

    /**
     * Call Gemini API model image analyzer.
     */
    fun runGeminiAnalysisForActive() {
        val list = _selectedImages.value
        if (list.isEmpty()) return
        
        val activeInput = list.getOrNull(_activeResultIndex.value) ?: list.first()

        viewModelScope.launch {
            _isGeminiAnalyzing.value = true
            _geminiAnalyticsText.value = "Scanning visual elements & calculating structural details..."
            
            val result = GeminiService.analyzeImageUpscale(activeInput.bitmap)
            result.onSuccess { analysis ->
                _geminiAnalyticsText.value = analysis
            }.onFailure { exception ->
                _geminiAnalyticsText.value = null
                _isGeminiAnalyzing.value = false
                _statusMessage.value = exception.localizedMessage ?: "Gemini Analysis failed"
            }
            _isGeminiAnalyzing.value = false
        }
    }

    /**
     * Packages current processed batch list into a ZIP file in external storage / app caches,
     * so that the user can download or share all processed items at once.
     */
    fun packageAllAsZip(outputFile: File): Boolean {
        val results = _upscaledResults.value
        if (results.isEmpty()) return false
        val files = results.map { File(it.upscaledPath) }
        return UpscaleEngine.createZipFromFiles(files, outputFile)
    }

    fun deleteHistoryLog(item: UpscaleItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(item)
            // Delete actual upscaled file if exists
            val file = File(item.upscaledPath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun clearEntireHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            // Clear files inside internal folder
            val dir = File(getApplication<Application>().filesDir, "upscaled_images")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    override fun onCleared() {
        super.onCleared()
        val oldImages = _selectedImages.value
        val oldResults = _upscaledResults.value
        _selectedImages.value = emptyList()
        _upscaledResults.value = emptyList()
        oldImages.forEach {
            try {
                it.bitmap.recycle()
            } catch (ignored: Exception) {}
        }
        oldResults.forEach {
            try {
                it.upscaledBitmap.recycle()
            } catch (ignored: Exception) {}
        }
    }
}
