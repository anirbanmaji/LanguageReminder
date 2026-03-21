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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.languagereminder.overlay.OverlayService

class MainActivity : ComponentActivity() {

    private var overlayPermissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(
                        overlayPermissionGranted = overlayPermissionGranted,
                        onGrantPermissionClick = ::requestOverlayPermission,
                        onStartOverlayClick = { OverlayService.start(this) },
                        onStopOverlayClick = { OverlayService.stop(this) }
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
    onGrantPermissionClick: () -> Unit,
    onStartOverlayClick: () -> Unit,
    onStopOverlayClick: () -> Unit
) {
    LaunchedEffect(overlayPermissionGranted) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
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
}
