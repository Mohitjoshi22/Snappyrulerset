// DrawingScreen.kt - With two-finger zoom functionality

package com.example.snappyrulerset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.*

// Canvas state for zoom and pan
data class CanvasState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DrawingScreen(
    viewModel: DrawingViewModel = viewModel(),
    onNavigateToExport: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    // Canvas transformation state
    var canvasState by remember { mutableStateOf(CanvasState()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Main Canvas with zoom and pan
        ZoomableDrawingCanvas(
            shapes = viewModel.shapes,
            currentShape = viewModel.currentShape,
            currentTool = viewModel.currentTool,
            canvasState = canvasState,
            onCanvasStateChange = { canvasState = it },
            onStartDrawing = { offset ->
                // Transform screen coordinates to canvas coordinates
                val canvasOffset = screenToCanvas(offset, canvasState)
                viewModel.startDrawing(canvasOffset)
            },
            onContinueDrawing = { offset ->
                val canvasOffset = screenToCanvas(offset, canvasState)
                viewModel.continueDrawing(canvasOffset)
            },
            onEndDrawing = viewModel::endDrawing,
            modifier = Modifier.fillMaxSize()
        )

        // HUD Overlay
        if (viewModel.hudInfo.isVisible) {
            HudOverlay(
                hudInfo = viewModel.hudInfo,
                canvasState = canvasState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        // Zoom controls
        ZoomControls(
            canvasState = canvasState,
            onZoomIn = {
                canvasState = canvasState.copy(
                    scale = (canvasState.scale * 1.2f).coerceIn(0.1f, 5f)
                )
            },
            onZoomOut = {
                canvasState = canvasState.copy(
                    scale = (canvasState.scale / 1.2f).coerceIn(0.1f, 5f)
                )
            },
            onResetZoom = {
                canvasState = CanvasState()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Tool overlays with transformation
        when (viewModel.currentTool) {
            DrawingTool.RULER -> RulerOverlay(canvasState)
            DrawingTool.SETSQUARE -> SetSquareOverlay(canvasState)
            DrawingTool.PROTRACTOR -> ProtractorOverlay(canvasState)
            else -> {}
        }

        // Bottom Toolbar
        BottomToolBar(
            currentTool = viewModel.currentTool,
            onToolSelected = { viewModel.currentTool = it },
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onExport = onNavigateToExport,
            onSettings = onNavigateToSettings,
            onClear = viewModel::clearCanvas,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}
@Composable
fun ZoomableDrawingCanvas(
    shapes: List<DrawnShape>,
    currentShape: DrawnShape?,
    currentTool: DrawingTool,
    canvasState: CanvasState,
    onCanvasStateChange: (CanvasState) -> Unit,
    onStartDrawing: (Offset) -> Unit,
    onContinueDrawing: (Offset) -> Unit,
    onEndDrawing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDrawing by remember { mutableStateOf(false) }
    var initialDistance by remember { mutableStateOf(0f) }
    var initialScale by remember { mutableStateOf(1f) }
    var initialOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(currentTool) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        when (event.changes.size) {
                            1 -> {
                                // Single finger - handle drawing
                                val change = event.changes.first()

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        if (!isDrawing) {
                                            isDrawing = true
                                            onStartDrawing(change.position)
                                        }
                                    }
                                    PointerEventType.Move -> {
                                        if (isDrawing && change.pressed) {
                                            onContinueDrawing(change.position)
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        if (isDrawing) {
                                            isDrawing = false
                                            onEndDrawing()
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // Two fingers - handle zoom and pan
                                if (isDrawing) {
                                    // Stop drawing if it was in progress
                                    isDrawing = false
                                    onEndDrawing()
                                }

                                val change1 = event.changes[0]
                                val change2 = event.changes[1]

                                val currentDistance = (change1.position - change2.position).getDistance()
                                val currentCenter = (change1.position + change2.position) / 2f

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        initialDistance = currentDistance
                                        initialScale = canvasState.scale
                                        initialOffset = Offset(canvasState.offsetX, canvasState.offsetY)
                                    }

                                    PointerEventType.Move -> {
                                        if (initialDistance > 0) {
                                            val zoomFactor = currentDistance / initialDistance
                                            val newScale = (initialScale * zoomFactor).coerceIn(0.1f, 5f)

                                            // Calculate pan based on the center point movement
                                            val panChange = event.calculatePan()

                                            onCanvasStateChange(
                                                canvasState.copy(
                                                    scale = newScale,
                                                    offsetX = canvasState.offsetX + panChange.x,
                                                    offsetY = canvasState.offsetY + panChange.y
                                                )
                                            )
                                        }
                                    }
                                }

                                // Consume the events to prevent other gesture detection
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        // Apply transformations (pan + zoom)
        withTransform({
            translate(canvasState.offsetX, canvasState.offsetY)
            scale(canvasState.scale, canvasState.scale)
        }) {
            // Grid background
            drawGrid()

            // Draw completed shapes
            shapes.forEach { shape ->
                drawPath(
                    path = shape.path,
                    color = Color.Black,
                    style = Stroke(width = (3.dp.toPx() / canvasState.scale))
                )
            }

            // Current shape
            currentShape?.let { shape ->
                drawPath(
                    path = shape.path,
                    color = getToolColor(currentTool),
                    style = Stroke(
                        width = (3.dp.toPx() / canvasState.scale),
                        pathEffect = if (currentTool != DrawingTool.FREEHAND) {
                            PathEffect.dashPathEffect(floatArrayOf(10f / canvasState.scale, 5f / canvasState.scale))
                        } else null
                    )
                )
            }
        }
    }
}
@Composable
fun ZoomControls(
    canvasState: CanvasState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${(canvasState.scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onResetZoom,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusWeak,
                        contentDescription = "Reset Zoom",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HudOverlay(
    hudInfo: HudInfo,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Length: ${(hudInfo.length * canvasState.scale).toInt()}px",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Angle: ${hudInfo.angle.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Zoom: ${(canvasState.scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BottomToolBar(
    currentTool: DrawingTool,
    onToolSelected: (DrawingTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // First Row: Drawing Tools
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    icon = Icons.Filled.Edit,
                    isSelected = currentTool == DrawingTool.FREEHAND,
                    onClick = { onToolSelected(DrawingTool.FREEHAND) },
                    contentDescription = "Freehand"
                )

                ToolButton(
                    icon = Icons.Default.Remove,
                    isSelected = currentTool == DrawingTool.RULER,
                    onClick = { onToolSelected(DrawingTool.RULER) },
                    contentDescription = "Ruler"
                )

                ToolButton(
                    icon = Icons.Default.Stop,
                    isSelected = currentTool == DrawingTool.SETSQUARE,
                    onClick = { onToolSelected(DrawingTool.SETSQUARE) },
                    contentDescription = "Set Square"
                )

                ToolButton(
                    icon = Icons.Default.MoreHoriz,
                    isSelected = currentTool == DrawingTool.PROTRACTOR,
                    onClick = { onToolSelected(DrawingTool.PROTRACTOR) },
                    contentDescription = "Protractor"
                )

                ToolButton(
                    icon = Icons.Default.RadioButtonUnchecked,
                    isSelected = currentTool == DrawingTool.COMPASS,
                    onClick = { onToolSelected(DrawingTool.COMPASS) },
                    contentDescription = "Compass"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second Row: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = Icons.Default.Undo,
                    onClick = onUndo,
                    contentDescription = "Undo"
                )

                ActionButton(
                    icon = Icons.Default.Redo,
                    onClick = onRedo,
                    contentDescription = "Redo"
                )

                ActionButton(
                    icon = Icons.Default.Delete,
                    onClick = onClear,
                    contentDescription = "Clear"
                )

                ActionButton(
                    icon = Icons.Default.Share,
                    onClick = onExport,
                    contentDescription = "Export",
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                ActionButton(
                    icon = Icons.Default.Settings,
                    onClick = onSettings,
                    contentDescription = "Settings",
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else Color.Transparent
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    backgroundColor: Color = Color.Transparent
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Tool Overlays with transformation support
@Composable
fun RulerOverlay(canvasState: CanvasState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(canvasState.offsetX, canvasState.offsetY)
            scale(canvasState.scale, canvasState.scale)
        }) {
            // Draw ruler marks
            val spacing = 50f
            for (i in 0 until (size.width / spacing).toInt()) {
                val x = i * spacing
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = (1.dp.toPx() / canvasState.scale)
                )
            }
            for (i in 0 until (size.height / spacing).toInt()) {
                val y = i * spacing
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = (1.dp.toPx() / canvasState.scale)
                )
            }
        }
    }
}

@Composable
fun SetSquareOverlay(canvasState: CanvasState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(canvasState.offsetX, canvasState.offsetY)
            scale(canvasState.scale, canvasState.scale)
        }) {
            drawLine(
                color = Color.Blue.copy(alpha = 0.3f),
                start = Offset(size.width * 0.2f, size.height * 0.8f),
                end = Offset(size.width * 0.8f, size.height * 0.8f),
                strokeWidth = (2.dp.toPx() / canvasState.scale)
            )
            drawLine(
                color = Color.Blue.copy(alpha = 0.3f),
                start = Offset(size.width * 0.2f, size.height * 0.8f),
                end = Offset(size.width * 0.2f, size.height * 0.2f),
                strokeWidth = (2.dp.toPx() / canvasState.scale)
            )
        }
    }
}

@Composable
fun ProtractorOverlay(canvasState: CanvasState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(canvasState.offsetX, canvasState.offsetY)
            scale(canvasState.scale, canvasState.scale)
        }) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = minOf(size.width, size.height) * 0.3f

            for (angle in 0..359 step 15) {
                val radians = angle * PI / 180
                val end = Offset(
                    center.x + radius * cos(radians).toFloat(),
                    center.y + radius * sin(radians).toFloat()
                )
                drawLine(
                    color = Color.Red.copy(alpha = 0.3f),
                    start = center,
                    end = end,
                    strokeWidth = (1.dp.toPx() / canvasState.scale)
                )
            }

            drawCircle(
                color = Color.Red.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = (2.dp.toPx() / canvasState.scale))
            )
        }
    }
}

// Helper functions
private fun getToolColor(tool: DrawingTool): Color = when (tool) {
    DrawingTool.FREEHAND -> Color.Black
    DrawingTool.RULER -> Color.Blue
    DrawingTool.SETSQUARE -> Color.Green
    DrawingTool.PROTRACTOR -> Color.Red
    DrawingTool.COMPASS -> Color.Magenta
}

private fun screenToCanvas(screenOffset: Offset, canvasState: CanvasState): Offset {
    return Offset(
        (screenOffset.x - canvasState.offsetX) / canvasState.scale,
        (screenOffset.y - canvasState.offsetY) / canvasState.scale
    )
}

private fun DrawScope.drawGrid() {
    val gridSpacing = 20.dp.toPx()
    val gridColor = Color.Gray.copy(alpha = 0.2f)

    // Draw vertical lines
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 0.5.dp.toPx()
        )
        x += gridSpacing
    }

    // Draw horizontal lines
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.5.dp.toPx()
        )
        y += gridSpacing
    }
}

private fun drawRulerGuides(drawScope: DrawScope) {
    with(drawScope) {
        // Draw measurement marks
        val spacing = 20.dp.toPx()
        for (i in 0 until (size.width / spacing).toInt()) {
            val x = i * spacing
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, 10.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

private fun drawProtractorGuides(drawScope: DrawScope) {
    with(drawScope) {
        val center = Offset(size.width / 2, size.height / 4)
        val radius = size.width * 0.3f

        // Draw angle guides
        for (angle in 0..180 step 10) {
            val radians = angle * PI / 180
            val end = Offset(
                center.x + radius * cos(radians).toFloat(),
                center.y + radius * sin(radians).toFloat()
            )
            drawLine(
                color = Color.Red.copy(alpha = 0.3f),
                start = center,
                end = end,
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}