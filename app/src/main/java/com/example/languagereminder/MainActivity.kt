package com.example.languagereminder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.languagereminder.data.WeekdayTextStore
import com.example.languagereminder.overlay.OverlayService
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {

    private var overlayPermissionGranted by mutableStateOf(false)
    private lateinit var weekdayTextStore: WeekdayTextStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        weekdayTextStore = WeekdayTextStore(this)
        enableEdgeToEdge()
        setContent {
            val savedTexts by weekdayTextStore.weekdayTexts.collectAsState(
                initial = DayOfWeek.entries.associateWith { WeekdayTextStore.defaultText(it) }
            )
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(
                        overlayPermissionGranted = overlayPermissionGranted,
                        savedTexts = savedTexts,
                        onGrantPermissionClick = ::requestOverlayPermission,
                        onStartOverlayClick = { OverlayService.start(this) },
                        onStopOverlayClick = { OverlayService.stop(this) },
                        onSaveTexts = { values ->
                            lifecycleScope.launch { weekdayTextStore.saveAll(values) }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = hasOverlayPermission()
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
private fun LauncherScreen(
    overlayPermissionGranted: Boolean,
    savedTexts: Map<DayOfWeek, String>,
    onGrantPermissionClick: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStopOverlayClick: () -> Unit,
    onSaveTexts: (Map<DayOfWeek, String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var editableTexts by remember(savedTexts) { mutableStateOf(savedTexts) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Floating Weekday Widget",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = if (overlayPermissionGranted) {
                "Overlay permission is granted."
            } else {
                "Grant overlay permission to show widget above other apps."
            }
        )

        if (!overlayPermissionGranted) {
            Button(onClick = onGrantPermissionClick) {
                Text("Grant Overlay Permission")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStartOverlayClick,
                enabled = overlayPermissionGranted
            ) {
                Text("Start Floating Widget")
            }

            Button(onClick = onStopOverlayClick) {
                Text("Stop Floating Widget")
            }
        }

        Text(
            text = "Day to text mapping",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        DayOfWeek.entries.forEach { day ->
            OutlinedTextField(
                value = editableTexts[day].orEmpty(),
                onValueChange = { newValue ->
                    editableTexts = editableTexts.toMutableMap().apply { put(day, newValue) }
                },
                label = { Text(dayLabel(day)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                scope.launch { onSaveTexts(editableTexts) }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Save Mapping")
        }
    }
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}
