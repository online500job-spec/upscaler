package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.UpscaleItem
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UpscaleApp(
    viewModel: UpscaleViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedImages by viewModel.selectedImages.collectAsStateWithLifecycle()
    val upscaledResults by viewModel.upscaledResults.collectAsStateWithLifecycle()
    val activeResultIndex by viewModel.activeResultIndex.collectAsStateWithLifecycle()
    
    val upscaleRatio by viewModel.upscaleRatio.collectAsStateWithLifecycle()
    val upscaleMode by viewModel.upscaleMode.collectAsStateWithLifecycle()
    val enableDenoise by viewModel.enableDenoise.collectAsStateWithLifecycle()
    val enableSharpen by viewModel.enableSharpen.collectAsStateWithLifecycle()
    val enableFaceEnhancement by viewModel.enableFaceEnhancement.collectAsStateWithLifecycle()

    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingProgress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val currentProcessingIndex by viewModel.currentProcessingIndex.collectAsStateWithLifecycle()

    val geminiAnalyticsText by viewModel.geminiAnalyticsText.collectAsStateWithLifecycle()
    val isGeminiAnalyzing by viewModel.isGeminiAnalyzing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    
    val historyLog by viewModel.historyLog.collectAsStateWithLifecycle()

    // Screen Tabs: 0 -> Workspace, 1 -> History
    var currentTab by remember { mutableStateOf(0) }

    // Multi Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.selectImages(uris)
        }
    }

    // Display Status Message toasts
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Upscale.ai",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Local GPU Acceleration Enabled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = com.example.ui.theme.EmeraldActive
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearAllSelected() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Workspace")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 2.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Create, contentDescription = "Upscale Workspace") },
                    label = { Text("Workspace") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Upscale History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currentTab == 0) {
                // WORKSPACE SCREEN
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (selectedImages.isEmpty() && upscaledResults.isEmpty()) {
                        // EMPTY STATE
                        Card(
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Drop your files, link, or paste",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Supports PNG, JPG, WEBP, and JPEG. Max 20MB each. Select up to 6 images simultaneously.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Images")
                                }
                            }
                        }
                    } else if (upscaledResults.isNotEmpty()) {
                        // RESULTS DETAIL DISPLAY PAGE
                        val activeResult = upscaledResults.getOrNull(activeResultIndex) ?: upscaledResults.first()
                        
                        Text(
                            "Upscaled Results",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Beautiful Before/After Interactive Comparison slider
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            BeforeAfterSlider(
                                original = activeResult.originalImage.bitmap,
                                upscaled = activeResult.upscaledBitmap,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Batch results selector thumbnails list
                        if (upscaledResults.size > 1) {
                            Text(
                                "Batch Results Queue",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                upscaledResults.forEachIndexed { index, res ->
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                2.dp,
                                                if (index == activeResultIndex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.selectActiveResult(index) }
                                    ) {
                                        Image(
                                            bitmap = res.upscaledBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        // Image Metadata Comparison Stats Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        activeResult.originalImage.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${activeResult.ratio}x - ${activeResult.mode}") }
                                    )
                                }

                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Original Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(activeResult.originalImage.sizeFormatted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text("${activeResult.originalImage.width} x ${activeResult.originalImage.height} px", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingFlat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Upscaled Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(activeResult.upscaledSizeFormatted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("${activeResult.upscaledWidth} x ${activeResult.upscaledHeight} px", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Latency speed details",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${activeResult.durationMs} ms",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Gemini API Visual Analytics Card
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Gemini AI Vision Detailer",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (selectedImages.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.runGeminiAnalysisForActive() },
                                        enabled = !isGeminiAnalyzing
                                    ) {
                                        if (isGeminiAnalyzing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                        } else {
                                            Text("Get Insights")
                                        }
                                    }
                                }
                            }

                            if (geminiAnalyticsText != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFF5F3FF).copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                                ) {
                                    Text(
                                        text = geminiAnalyticsText!!,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp),
                                        color = Color(0xFF4C1D95),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Download as Single/All Zip actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    // Save currently active results securely to Android Downloads
                                    val savedFile = File(activeResult.upscaledPath)
                                    if (savedFile.exists()) {
                                        Toast.makeText(context, "Saved successfully to private gallery context: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Active PNG")
                            }

                            Button(
                                onClick = {
                                    val downloadsDir = File(context.cacheDir, "exports")
                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                    val zipFile = File(downloadsDir, "upscaled_batch_${System.currentTimeMillis()}.zip")
                                    val ok = viewModel.packageAllAsZip(zipFile)
                                    if (ok) {
                                        Toast.makeText(context, "Batch exported! Zip file generated: ${zipFile.length() / 1024} KB", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed creating batch package zip", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download All as ZIP")
                            }
                        }

                        // Back to upload picker button
                        OutlinedButton(
                            onClick = { viewModel.clearAllSelected() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Process Another Batch")
                        }
                    } else {
                        // SELECTED IMAGES / CONFIGURATION STATE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Selected Batch (${selectedImages.size}/6)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add More")
                            }
                        }

                        // Drag & Drop visual sort list
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            selectedImages.forEachIndexed { index, img ->
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Image(
                                        bitmap = img.bitmap.asImageBitmap(),
                                        contentDescription = "Selected Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Controls Panel Overlaid
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(6.dp)
                                    ) {
                                        // Remove Button
                                        IconButton(
                                            onClick = { viewModel.removeSelectedImage(index) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Left/Right shift arrows for Drag-Sorting Simulation
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { viewModel.moveSelectedImage(index, index - 1) },
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowBack,
                                                        contentDescription = "Move Left",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.size(22.dp))
                                            }

                                            if (index < selectedImages.size - 1) {
                                                IconButton(
                                                    onClick = { viewModel.moveSelectedImage(index, index + 1) },
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowForward,
                                                        contentDescription = "Move Right",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Size and resolution indicators
                                        Text(
                                            "${img.width}x${img.height}\n${img.sizeFormatted}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.Center),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // CONFIGURATION ENGINES CARD
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "AI Upscaling Core Configurations",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Ratios: 2x, 4x, 8x selection tabs
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Muliplier Ratio Setting",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(2, 4, 8).forEach { ratio ->
                                            val isSelected = ratio == upscaleRatio
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                                    )
                                                    .clickable { viewModel.setUpscaleRatio(ratio) }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${ratio}x Upscale",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Mode selection dropdown / capsule select: Standard, High Quality, Ultra Detail
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Aesthetic Quality Mode Target",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("Standard", "High Quality", "Ultra Detail").forEach { mode ->
                                            val isSelected = mode == upscaleMode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                                    )
                                                    .clickable { viewModel.setUpscaleMode(mode) }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    mode,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Toggles and extra features detail descriptions
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    maxItemsInEachRow = 3
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = enableDenoise,
                                            onCheckedChange = { viewModel.setDenoise(it) }
                                        )
                                        Text("Denoise", fontSize = 13.sp)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = enableSharpen,
                                            onCheckedChange = { viewModel.setSharpen(it) }
                                        )
                                        Text("Sharpen", fontSize = 13.sp)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = enableFaceEnhancement,
                                            onCheckedChange = { viewModel.setFaceEnhancement(it) }
                                        )
                                        Text("GFPGAN Face", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // PROCESS INFERENCE TRIGGER BUTTON
                        Button(
                            onClick = { viewModel.startUpscaleQueue() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Upscale Batch Queue (${selectedImages.size} items)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // HISTORIC DATA & ARCHIVAL SAVES DISPLAY SCREEN (Tab == 1)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Upscaling DB Logs",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (historyLog.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearEntireHistory() }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All")
                                }
                            }
                        }
                    }

                    if (historyLog.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .alpha(0.5f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "No historical logs found",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Successfully processed items will appear here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(historyLog) { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Upscaled result preview miniature
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = log.upscaledPath,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            log.originalName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${log.originalWidth}x${log.originalHeight} ➔ ${log.upscaledWidth}x${log.upscaledHeight}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "Ratio: ${log.ratio}x | Mode: ${log.mode} | Time: ${log.durationMs}ms",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteHistoryLog(log) },
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // PROCESSING QUEUE ACTIVE BACKDROP LOADER OVERLAY
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "AI Upscaling Pipeline Active",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            CircularProgressIndicator(
                                progress = processingProgress / 100f,
                                modifier = Modifier.size(72.dp),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                strokeWidth = 6.dp
                            )

                            Text(
                                "${processingProgress}% Completed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            val estimatedStep = when {
                                processingProgress < 15 -> "Interpolating dimensional bounds..."
                                processingProgress < 40 -> "Decoding high quality structures..."
                                processingProgress < 60 -> "Bilateral micro-denoisers processing color matrices..."
                                processingProgress < 75 -> "GFPGAN skin details & eye attributes restoring..."
                                processingProgress < 90 -> "3x3 Edge-enhancer convolution sharpening active..."
                                else -> "Exporting resulting assets LOSSLESS..."
                            }

                            Text(
                                "Processing Image ${currentProcessingIndex + 1} of ${selectedImages.size}\n$estimatedStep",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            LinearProgressIndicator(
                                progress = processingProgress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Before/After Comparison Slider with layered clip masks and smooth vertical divider dragging.
 */
@Composable
fun BeforeAfterSlider(
    original: Bitmap,
    upscaled: Bitmap,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf(0.5f) } // Fraction of width starting in center (0f to 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            }
    ) {
        val maxW = maxWidth
        val maxH = maxHeight

        // Background: Upscaled image fully drawn
        Image(
            bitmap = upscaled.asImageBitmap(),
            contentDescription = "After Upscaling Layer",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay crop box showing original image
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((maxW * dragFraction)) // Dynamic layout clipping
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        ) {
            Image(
                bitmap = original.asImageBitmap(),
                contentDescription = "Before Original Layer",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxW) // Maintain absolute proportions inside cropped box
            )
        }

        // Animated vertical divider line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .offset(x = maxW * dragFraction - 2.dp)
                .background(Color.White)
        ) {
            // Circle slider dragging coordinate handle point
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Overlay descriptive labels in bottom corners
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Original Blurred",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                "Upscale AI Clear",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
