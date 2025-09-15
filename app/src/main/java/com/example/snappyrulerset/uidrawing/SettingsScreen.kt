// SettingsScreen.kt
package com.example.snappyrulerset
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

// Settings Data Classes
data class GridSettings(
    val spacing: Float = 20f, // in dp
    val isVisible: Boolean = true
)

data class SnapSettings(
    val isEnabled: Boolean = true,
    val tolerance: Float = 15f, // snap distance in dp
    val snapToGrid: Boolean = true,
    val snapToAngles: Boolean = true
)

data class HapticsSettings(
    val isEnabled: Boolean = true,
    val intensity: Int = VibrationEffect.DEFAULT_AMPLITUDE
)

data class CalibrationSettings(
    val pixelsPerMm: Float = 3.78f, // Default value for ~160dpi screens
    val isCalibrated: Boolean = false,
    val lastCalibrationDate: Long = 0L
)

// Settings ViewModel
class SettingsViewModel : ViewModel() {
    var gridSettings by mutableStateOf(GridSettings())
    var snapSettings by mutableStateOf(SnapSettings())
    var hapticsSettings by mutableStateOf(HapticsSettings())
    var calibrationSettings by mutableStateOf(CalibrationSettings())

    fun updateGridSpacing(spacing: Float) {
        gridSettings = gridSettings.copy(spacing = spacing)
    }

    fun toggleSnapEnabled() {
        snapSettings = snapSettings.copy(isEnabled = !snapSettings.isEnabled)
    }

    fun toggleHaptics() {
        hapticsSettings = hapticsSettings.copy(isEnabled = !hapticsSettings.isEnabled)
    }

    fun updateCalibration(pixelsPerMm: Float) {
        calibrationSettings = calibrationSettings.copy(
            pixelsPerMm = pixelsPerMm,
            isCalibrated = true,
            lastCalibrationDate = System.currentTimeMillis()
        )
    }

    fun getGridSpacingInMm(): Float {
        return gridSettings.spacing / calibrationSettings.pixelsPerMm
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCalibrationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    "Settings",
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

        // Settings Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Grid Settings Section
            SettingsSection(
                title = "Grid & Canvas",
                icon = Icons.Filled.GridOn
            ) {
                GridSpacingSlider(
                    spacing = settingsViewModel.gridSettings.spacing,
                    onSpacingChange = settingsViewModel::updateGridSpacing,
                    pixelsPerMm = settingsViewModel.calibrationSettings.pixelsPerMm
                )
            }

            // Snap Settings Section
            SettingsSection(
                title = "Drawing Assistance",
                icon = Icons.Filled.TouchApp
            ) {
                SnapToggle(
                    isEnabled = settingsViewModel.snapSettings.isEnabled,
                    onToggle = settingsViewModel::toggleSnapEnabled
                )
            }

            // Haptics Section
            SettingsSection(
                title = "Feedback",
                icon = Icons.Filled.Vibration
            ) {
                HapticsToggle(
                    isEnabled = settingsViewModel.hapticsSettings.isEnabled,
                    onToggle = {
                        settingsViewModel.toggleHaptics()
                        if (settingsViewModel.hapticsSettings.isEnabled) {
                            triggerHapticFeedback(context)
                        }
                    }
                )
            }

            // Calibration Section
            SettingsSection(
                title = "Calibration",
                icon = Icons.Filled.Settings
            ) {
                CalibrationCard(
                    calibrationSettings = settingsViewModel.calibrationSettings,
                    onCalibrateClick = { showCalibrationDialog = true }
                )
            }
        }
    }

    // Calibration Dialog
    if (showCalibrationDialog) {
        CalibrationDialog(
            currentPixelsPerMm = settingsViewModel.calibrationSettings.pixelsPerMm,
            onCalibrationComplete = { pixelsPerMm ->
                settingsViewModel.updateCalibration(pixelsPerMm)
                showCalibrationDialog = false
            },
            onDismiss = { showCalibrationDialog = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Section Content
            content()
        }
    }
}

@Composable
private fun GridSpacingSlider(
    spacing: Float,
    onSpacingChange: (Float) -> Unit,
    pixelsPerMm: Float
) {
    val density = LocalDensity.current
    val spacingInMm = spacing / pixelsPerMm

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grid Spacing",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${spacingInMm.roundToInt()}mm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = spacing,
            onValueChange = onSpacingChange,
            valueRange = 10f..50f,
            steps = 7, // 5mm, 10mm, 15mm, 20mm, 25mm, 30mm, 35mm, 40mm, 45mm, 50mm
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        // Grid preview
        GridPreview(
            spacing = with(density) { spacing.dp.toPx() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun SnapToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable Snap",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Snap to grid lines and angles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun HapticsToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Haptic Feedback",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Vibrate when snapping to guides",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun CalibrationCard(
    calibrationSettings: CalibrationSettings,
    onCalibrateClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Screen Scale",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (calibrationSettings.isCalibrated)
                        "${calibrationSettings.pixelsPerMm.roundToInt()} px/mm"
                    else
                        "Not calibrated",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (calibrationSettings.isCalibrated)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onCalibrateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Calibrate")
            }
        }

        if (!calibrationSettings.isCalibrated) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ Calibrate your screen for accurate measurements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun GridPreview(
    spacing: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .background(Color.White)
    ) {
        val gridColor = Color.Gray.copy(alpha = 0.4f)
        val strokeWidth = 1.dp.toPx()

        // Draw vertical lines
        var x = spacing
        while (x < size.width) {
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = strokeWidth
            )
            x += spacing
        }

        // Draw horizontal lines
        var y = spacing
        while (y < size.height) {
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = strokeWidth
            )
            y += spacing
        }
    }
}

@Composable
private fun CalibrationDialog(
    currentPixelsPerMm: Float,
    onCalibrationComplete: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var measuredLength by remember { mutableStateOf("") }
    var actualLength by remember { mutableStateOf("50") } // Default 5cm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibrate Screen Scale") },
        text = {
            Column {
                Text(
                    text = "Measure the line below with a real ruler:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Calibration line (50mm at current scale)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = actualLength,
                    onValueChange = { actualLength = it },
                    label = { Text("Actual length (mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Measure the blue line above and enter its actual length in millimeters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actual = actualLength.toFloatOrNull()
                    if (actual != null && actual > 0) {
                        val linePixels = 50 * currentPixelsPerMm // 50mm line in pixels
                        val newPixelsPerMm = linePixels / actual
                        onCalibrationComplete(newPixelsPerMm)
                    }
                },
                enabled = actualLength.toFloatOrNull()?.let { it > 0 } ?: false
            ) {
                Text("Save Calibration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Utility Functions
