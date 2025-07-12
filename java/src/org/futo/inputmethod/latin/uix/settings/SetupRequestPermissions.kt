package org.futo.inputmethod.latin.uix.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.futo.inputmethod.latin.R

// Helper function to check if accessibility service is enabled
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    val packageName = context.packageName
    return enabledServices?.contains("$packageName/org.futo.inputmethod.latin.uix.services.QuickSwitchService") == true
}

@Composable
@Preview
fun SetupRequestPermissions(onFinished: () -> Unit = { }) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPermissionGranted by remember { mutableStateOf(false) }
    
    // Function to check permission status
    fun checkPermissionStatus() {
        isPermissionGranted = isAccessibilityServiceEnabled(context)
    }
    
    // Check permission status when composable first loads
    LaunchedEffect(Unit) {
        checkPermissionStatus()
    }
    
    // Re-check permission when app resumes (user returns from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Auto-continue if permission is granted
    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            onFinished()
        }
    }
    
    SetupContainer {
        Column {
            Step(fraction = 4.0f / 5.0f, text = stringResource(R.string.setup_step_3))

            Text(
                text = if (isPermissionGranted) {
                    stringResource(R.string.setup_permissions_granted_text)
                } else {
                    stringResource(R.string.setup_permissions_text)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (isPermissionGranted) {
                        onFinished()
                    } else {
                        // Open accessibility settings
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isPermissionGranted) {
                        stringResource(R.string.setup_continue_button)
                    } else {
                        stringResource(R.string.setup_grant_permissions_button)
                    }
                )
            }
        }
    }
}

// Alternative version if you want manual control (without auto-continue)
@Composable
fun SetupRequestPermissionsManual(onFinished: () -> Unit = { }) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPermissionGranted by remember { mutableStateOf(false) }
    
    fun checkPermissionStatus() {
        isPermissionGranted = isAccessibilityServiceEnabled(context)
    }
    
    LaunchedEffect(Unit) {
        checkPermissionStatus()
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    SetupContainer {
        Column {
            Step(fraction = 4.0f / 5.0f, text = stringResource(R.string.setup_step_3))

            Text(
                text = if (isPermissionGranted) {
                    stringResource(R.string.setup_permissions_granted_text)
                } else {
                    stringResource(R.string.setup_permissions_text)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (!isPermissionGranted) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.setup_grant_permissions_button))
                }
            }
            
            Button(
                onClick = onFinished,
                enabled = isPermissionGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_continue_button))
            }
        }
    }
}