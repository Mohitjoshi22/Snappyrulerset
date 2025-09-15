// ExportScreen.kt - Fixed to capture full canvas
package com.example.snappyrulerset

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    shapes: List<DrawnShape>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveFormat by remember { mutableStateOf(ExportFormat.PNG) }

    // Generate preview bitmap
    LaunchedEffect(shapes) {
        scope.launch {
            previewBitmap = generateDrawingBitmap(shapes, density)
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    "Export Drawing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back to Drawing"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        // Preview Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            } else {
                previewBitmap?.let { bitmap ->
                    PreviewCard(
                        bitmap = bitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: run {
                    EmptyPreview()
                }
            }
        }

        // Action Buttons
        ActionButtonsRow(
            onSave = {
                if (previewBitmap != null) {
                    showSaveDialog = true
                }
            },
            onShare = {
                previewBitmap?.let { bitmap ->
                    scope.launch {
                        shareDrawing(context, bitmap)
                    }
                }
            },
            onCancel = onNavigateBack,
            isEnabled = !isLoading && previewBitmap != null,
            modifier = Modifier.padding(16.dp)
        )
    }

    // Save Format Dialog
    if (showSaveDialog) {
        SaveFormatDialog(
            currentFormat = saveFormat,
            onFormatSelected = { format ->
                saveFormat = format
                previewBitmap?.let { bitmap ->
                    scope.launch {
                        saveDrawing(context, bitmap, format)
                    }
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
private fun PreviewCard(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Drawing Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun EmptyPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No drawing to preview",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Save Button
        Button(
            onClick = onSave,
            enabled = isEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save")
        }

        // Share Button
        Button(
            onClick = onShare,
            enabled = isEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share")
        }

        // Cancel Button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SaveFormatDialog(
    currentFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Export Format") },
        text = {
            Column {
                ExportFormat.values().forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentFormat == format,
                            onClick = { onFormatSelected(format) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = format.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = format.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Export Format Enum
enum class ExportFormat(
    val displayName: String,
    val description: String,
    val extension: String,
    val mimeType: String,
    val quality: Int = 100
) {
    PNG("PNG", "High quality, lossless", "png", "image/png"),
    JPEG_HIGH("JPEG (High)", "Good quality, smaller size", "jpg", "image/jpeg", 90),
    JPEG_MEDIUM("JPEG (Medium)", "Medium quality, small size", "jpg", "image/jpeg", 70)
}

// File Operations - FIXED VERSION
private suspend fun generateDrawingBitmap(
    shapes: List<DrawnShape>,
    density: androidx.compose.ui.unit.Density
): Bitmap? = withContext(Dispatchers.Default) {
    if (shapes.isEmpty()) return@withContext null

    // Calculate the bounding box of all shapes
    val bounds = calculateDrawingBounds(shapes)

    // Add padding around the drawing
    val padding = with(density) { 50.dp.toPx() }
    val adjustedBounds = RectF(
        bounds.left - padding,
        bounds.top - padding,
        bounds.right + padding,
        bounds.bottom + padding
    )

    // Ensure minimum size and reasonable maximum
    val minSize = with(density) { 400.dp.toPx() }
    val maxSize = with(density) { 2000.dp.toPx() }

    val width = (adjustedBounds.width()).coerceIn(minSize, maxSize).toInt()
    val height = (adjustedBounds.height()).coerceIn(minSize, maxSize).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Fill background with white
    canvas.drawColor(Color.White.toArgb())

    // Draw grid background
    drawGrid(canvas, width.toFloat(), height.toFloat(), density)

    val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = with(density) { 3.dp.toPx() }
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Translate canvas to center the drawing
    val offsetX = -adjustedBounds.left
    val offsetY = -adjustedBounds.top

    shapes.forEach { shape ->
        paint.color = getToolColor(shape.tool).toArgb()

        // Convert Compose Path to Android Path with proper offset
        val androidPath = convertToAndroidPath(shape, offsetX, offsetY)
        canvas.drawPath(androidPath, paint)
    }

    bitmap
}

// Calculate bounding box of all drawn shapes
private fun calculateDrawingBounds(shapes: List<DrawnShape>): RectF {
    if (shapes.isEmpty()) {
        return RectF(0f, 0f, 400f, 400f) // Default size if no shapes
    }

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    shapes.forEach { shape ->
        shape.points.forEach { point ->
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
    }

    return RectF(minX, minY, maxX, maxY)
}

// Draw grid background on bitmap canvas
private fun drawGrid(
    canvas: Canvas,
    width: Float,
    height: Float,
    density: androidx.compose.ui.unit.Density
) {
    val gridSpacing = with(density) { 20.dp.toPx() }
    val gridPaint = Paint().apply {
        color = Color.Gray.copy(alpha = 0.2f).toArgb()
        strokeWidth = with(density) { 0.5.dp.toPx() }
        isAntiAlias = true
    }

    // Draw vertical lines
    var x = 0f
    while (x <= width) {
        canvas.drawLine(x, 0f, x, height, gridPaint)
        x += gridSpacing
    }

    // Draw horizontal lines
    var y = 0f
    while (y <= height) {
        canvas.drawLine(0f, y, width, y, gridPaint)
        y += gridSpacing
    }
}

private fun convertToAndroidPath(
    shape: DrawnShape,
    offsetX: Float,
    offsetY: Float
): Path {
    val path = Path()
    val points = shape.points

    if (points.isNotEmpty()) {
        val firstPoint = points.first()
        path.moveTo(firstPoint.x + offsetX, firstPoint.y + offsetY)

        points.drop(1).forEach { point ->
            path.lineTo(point.x + offsetX, point.y + offsetY)
        }
    }

    return path
}

private suspend fun saveDrawing(
    context: Context,
    bitmap: Bitmap,
    format: ExportFormat
) = withContext(Dispatchers.IO) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "drawing_$timestamp.${format.extension}"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, filename)

        FileOutputStream(file).use { out ->
            val compressFormat = when (format) {
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(compressFormat, format.quality, out)
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Drawing saved to Downloads/$filename", Toast.LENGTH_LONG).show()
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private suspend fun shareDrawing(
    context: Context,
    bitmap: Bitmap
) = withContext(Dispatchers.IO) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "drawing_$timestamp.png"

        val cacheDir = File(context.cacheDir, "shared_drawings")
        cacheDir.mkdirs()
        val file = File(cacheDir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Check out my technical drawing!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, "Share Drawing"))
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun getToolColor(tool: DrawingTool): androidx.compose.ui.graphics.Color = when (tool) {
    DrawingTool.FREEHAND -> Color.Black
    DrawingTool.RULER -> Color.Blue
    DrawingTool.SETSQUARE -> Color.Green
    DrawingTool.PROTRACTOR -> Color.Red
    DrawingTool.COMPASS -> Color.Magenta
}