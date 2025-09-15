package com.example.snappyrulerset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snappyrulerset.ui.theme.SnappyRulerSetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnappyRulerSetTheme {
                SnappyRulerSetApp()
            }
        }
    }
}

@Composable
fun SnappyRulerSetApp() {
    val navController = rememberNavController()
    val drawingViewModel: DrawingViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "drawing",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("drawing") {
                DrawingScreen(
                    viewModel = drawingViewModel,
                    onNavigateToExport = {
                        navController.navigate("export")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("export") {
                ExportScreen(
                    shapes = drawingViewModel.shapes,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

// You'll also need these data classes and enums in separate files or in the same file:

// DrawingTool.kt
enum class DrawingTool {
    FREEHAND, RULER, SETSQUARE, PROTRACTOR, COMPASS
}

// Data classes for the drawing system
data class DrawnShape(
    val tool: DrawingTool,
    val points: List<androidx.compose.ui.geometry.Offset>,
    val path: androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path()
)

data class HudInfo(
    val isVisible: Boolean = false,
    val length: Float = 0f,
    val angle: Float = 0f
)

// DrawingViewModel.kt - Basic implementation
class DrawingViewModel : androidx.lifecycle.ViewModel() {
    var shapes by androidx.compose.runtime.mutableStateOf(listOf<DrawnShape>())
        private set

    var currentShape by androidx.compose.runtime.mutableStateOf<DrawnShape?>(null)
        private set

    var currentTool by androidx.compose.runtime.mutableStateOf(DrawingTool.FREEHAND)

    var hudInfo by androidx.compose.runtime.mutableStateOf(HudInfo())
        private set

    private val undoStack = mutableListOf<List<DrawnShape>>()
    private val redoStack = mutableListOf<List<DrawnShape>>()

    fun startDrawing(point: androidx.compose.ui.geometry.Offset) {
        // Save current state for undo
        undoStack.add(shapes.toList())
        redoStack.clear()

        val newShape = DrawnShape(
            tool = currentTool,
            points = listOf(point),
            path = androidx.compose.ui.graphics.Path().apply { moveTo(point.x, point.y) }
        )
        currentShape = newShape

        // Update HUD
        hudInfo = hudInfo.copy(isVisible = true)
    }

    fun continueDrawing(point: androidx.compose.ui.geometry.Offset) {
        currentShape?.let { shape ->
            val updatedPoints = shape.points + point
            val updatedPath = androidx.compose.ui.graphics.Path().apply {
                if (updatedPoints.isNotEmpty()) {
                    moveTo(updatedPoints.first().x, updatedPoints.first().y)
                    updatedPoints.drop(1).forEach { p ->
                        lineTo(p.x, p.y)
                    }
                }
            }

            currentShape = shape.copy(
                points = updatedPoints,
                path = updatedPath
            )

            // Update HUD with length and angle calculations
            if (updatedPoints.size >= 2) {
                val firstPoint = updatedPoints.first()
                val lastPoint = updatedPoints.last()
                val dx = lastPoint.x - firstPoint.x
                val dy = lastPoint.y - firstPoint.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()) * 180 / kotlin.math.PI

                hudInfo = hudInfo.copy(
                    length = length,
                    angle = angle.toFloat()
                )
            }
        }
    }

    fun endDrawing() {
        currentShape?.let { shape ->
            shapes = shapes + shape
            currentShape = null
            hudInfo = hudInfo.copy(isVisible = false)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(shapes.toList())
            shapes = undoStack.removeLastOrNull() ?: listOf()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(shapes.toList())
            shapes = redoStack.removeLastOrNull() ?: listOf()
        }
    }

    fun clearCanvas() {
        undoStack.add(shapes.toList())
        redoStack.clear()
        shapes = listOf()
        currentShape = null
        hudInfo = hudInfo.copy(isVisible = false)
    }
}