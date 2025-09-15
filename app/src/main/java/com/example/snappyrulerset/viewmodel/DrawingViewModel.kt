import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.*

enum class DrawingTool {
    FREEHAND, RULER, SETSQUARE, PROTRACTOR, COMPASS
}

data class DrawnShape(
    val path: Path = Path(),
    val points: List<Offset> = emptyList(),
    val tool: DrawingTool = DrawingTool.FREEHAND,
    val isComplete: Boolean = false
)

data class HudInfo(
    val length: Float = 0f,
    val angle: Float = 0f,
    val isVisible: Boolean = false
)

class DrawingViewModel : ViewModel() {
    var currentTool by mutableStateOf(DrawingTool.FREEHAND)
    var shapes by mutableStateOf(listOf<DrawnShape>())
    var currentShape by mutableStateOf<DrawnShape?>(null)
    var hudInfo by mutableStateOf(HudInfo())

    private val undoStack = mutableListOf<List<DrawnShape>>()
    private val redoStack = mutableListOf<List<DrawnShape>>()

    fun startDrawing(point: Offset) {
        saveStateForUndo()
        currentShape = DrawnShape(
            points = listOf(point),
            tool = currentTool
        ).apply {
            path.moveTo(point.x, point.y)
        }
        updateHUD()
    }

    fun continueDrawing(point: Offset) {
        currentShape?.let { shape ->
            val newPoints = shape.points + point
            val constrainedPoint = when (currentTool) {
                DrawingTool.RULER -> constrainToLine(shape.points.first(), point)
                DrawingTool.SETSQUARE -> constrainToRightAngle(shape.points, point)
                DrawingTool.PROTRACTOR -> constrainToAngle(shape.points, point)
                else -> point
            }

            currentShape = shape.copy(
                points = shape.points + constrainedPoint
            ).apply {
                path.reset()
                if (points.isNotEmpty()) {
                    path.moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { p ->
                        path.lineTo(p.x, p.y)
                    }
                }
            }
            updateHUD()
        }
    }

    fun endDrawing() {
        currentShape?.let { shape ->
            shapes = shapes + shape.copy(isComplete = true)
            currentShape = null
            hudInfo = hudInfo.copy(isVisible = false)
        }
    }

    private fun constrainToLine(start: Offset, current: Offset): Offset {
        // Snap to horizontal, vertical, or 45-degree angles
        val dx = current.x - start.x
        val dy = current.y - start.y
        val angle = atan2(dy, dx) * 180 / PI

        return when {
            abs(angle) < 15 || abs(angle) > 165 -> Offset(current.x, start.y) // Horizontal
            abs(angle - 90) < 15 || abs(angle + 90) < 15 -> Offset(start.x, current.y) // Vertical
            abs(angle - 45) < 15 -> { // 45 degrees
                val distance = min(abs(dx), abs(dy))
                Offset(start.x + distance * dx.sign, start.y + distance * dy.sign)
            }
            abs(angle + 45) < 15 || abs(angle - 135) < 15 -> { // -45 degrees
                val distance = min(abs(dx), abs(dy))
                Offset(start.x + distance * dx.sign, start.y - distance * dx.sign)
            }
            else -> current
        }
    }

    private fun constrainToRightAngle(points: List<Offset>, current: Offset): Offset {
        if (points.size < 2) return current
        // Create right angles based on previous segments
        val prev = points[points.size - 2]
        val dx = current.x - prev.x
        val dy = current.y - prev.y
        return if (abs(dx) > abs(dy)) Offset(current.x, prev.y) else Offset(prev.x, current.y)
    }

    private fun constrainToAngle(points: List<Offset>, current: Offset): Offset {
        if (points.isEmpty()) return current
        // Snap to common angles (0, 30, 45, 60, 90, 120, 135, 150, 180 degrees)
        val start = points.first()
        val dx = current.x - start.x
        val dy = current.y - start.y
        val distance = sqrt(dx * dx + dy * dy)
        val angle = atan2(dy, dx) * 180 / PI

        val snapAngles = listOf(0, 30, 45, 60, 90, 120, 135, 150, 180, -30, -45, -60, -90, -120, -135, -150)
        val snappedAngle = snapAngles.minByOrNull { abs(it - angle) }?.toDouble() ?: angle
        val radians = snappedAngle * PI / 180

        return Offset(
            start.x + (distance * cos(radians)).toFloat(),
            start.y + (distance * sin(radians)).toFloat()
        )
    }

    private fun updateHUD() {
        currentShape?.let { shape ->
            if (shape.points.size >= 2) {
                val start = shape.points.first()
                val end = shape.points.last()
                val dx = end.x - start.x
                val dy = end.y - start.y
                val length = sqrt(dx * dx + dy * dy)
                val angle = atan2(dy, dx) * 180 / PI

                hudInfo = HudInfo(
                    length = length,
                    angle = angle.toFloat(),
                    isVisible = true
                )
            }
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(shapes)
            shapes = undoStack.removeAt(undoStack.size - 1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(shapes)
            shapes = redoStack.removeAt(redoStack.size - 1)
        }
    }

    private fun saveStateForUndo() {
        undoStack.add(shapes)
        redoStack.clear()
        // Limit undo stack size
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun clearCanvas() {
        saveStateForUndo()
        shapes = emptyList()
        currentShape = null
    }
}
