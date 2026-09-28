package com.clapfinder.app.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.clapfinder.app.R
import com.clapfinder.app.ads.AppOpenAdManager
import com.clapfinder.app.service.ClapDetectionService
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.util.PermissionHelper
import com.clapfinder.app.util.Prefs

/** What the activate ring calls. Returned by [rememberFinderActivation]. */
class FinderActivation internal constructor(private val onClick: () -> Unit) {
    fun onActivateClicked() = onClick()
}

/**
 * The activate chain from the old MainActivity / WhistleActivity, unchanged:
 * running → stop. Otherwise: permission sheet (if mic/notification missing) →
 * runtime dialog → overlay screen (optional) → battery prompt (optional) → start
 * the ONE shared foreground service in clap or whistle mode.
 *
 * Also draws the permission bottom sheet and the "open settings" dialog.
 */
@Composable
fun rememberFinderActivation(mode: String): FinderActivation {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prefs = remember { Prefs.get(context) }
    val whistle = mode == ClapDetectionService.MODE_WHISTLE

    var showSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun toast(msg: String, long: Boolean) =
        Toast.makeText(context, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    fun startListening() {
        if (!PermissionHelper.hasMic(context)) {
            toast("Microphone permission is required", long = true)
            return
        }
        // Starting in this mode; the service swaps detectors in place if the other mode was running.
        ClapDetectionService.start(context, mode)
        if (whistle) {
            prefs.whistleServiceRunning = true
            prefs.serviceRunning = false
        } else {
            prefs.serviceRunning = true
            prefs.whistleServiceRunning = false
        }
        toast(
            if (whistle) "Listening… whistle to find your phone" else "Listening… clap to find your phone",
            long = false
        )
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { startListening() }

    fun proceedBattery() {
        if (!PermissionHelper.isIgnoringBattery(context)) {
            runCatching {
                batteryLauncher.launch(PermissionHelper.batteryIntent(context))
                AppOpenAdManager.skipNextForeground()   // coming back from the system prompt ≠ app open
            }.onFailure { startListening() }
        } else {
            startListening()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { proceedBattery() }

    fun proceedOptional() {
        if (!PermissionHelper.hasOverlay(context)) {
            // (runCatching: some Android Go phones have no overlay settings screen)
            runCatching {
                overlayLauncher.launch(PermissionHelper.overlayIntent(context))
                AppOpenAdManager.skipNextForeground()
            }.onFailure { proceedBattery() }
        } else {
            proceedBattery()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micGranted = result[Manifest.permission.RECORD_AUDIO] ?: PermissionHelper.hasMic(context)

        if (micGranted) {
            // Mic is enough to run, but without notifications the ongoing
            // foreground notification is silently hidden on Android 13+.
            if (!PermissionHelper.hasNotifications(context)) {
                toast(
                    if (whistle) "Allow notifications so you can see and stop the whistle listener"
                    else "Allow notifications so you can see and stop the clap listener",
                    long = true
                )
            }
            proceedOptional()
        } else if (activity != null && PermissionHelper.isMicPermanentlyDenied(activity)) {
            showSettingsDialog = true
        } else {
            toast(
                if (whistle) "Microphone permission is required to detect whistles"
                else "Microphone permission is required to detect claps",
                long = true
            )
        }
    }

    if (showSheet) {
        PermissionSheet(
            onDismiss = { showSheet = false },
            onContinue = {
                showSheet = false
                permissionLauncher.launch(PermissionHelper.runtimePermissions())
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { },                       // setCancelable(false)
            title = { Text("Microphone Permission Required") },
            text = {
                Text(
                    if (whistle) "WhistleFinder needs microphone access to detect whistles. Please enable it in App Settings."
                    else "ClapFinder needs microphone access to detect claps. Please enable it in App Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    context.startActivity(PermissionHelper.appDetailsIntent(context))
                    AppOpenAdManager.skipNextForeground()
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    return FinderActivation {
        val running = if (whistle) prefs.whistleServiceRunning else prefs.serviceRunning
        if (running) {
            ClapDetectionService.stop(context)
            if (whistle) prefs.whistleServiceRunning = false else prefs.serviceRunning = false
        } else if (!PermissionHelper.hasEssential(context)) {
            showSheet = true
        } else {
            proceedOptional()
        }
    }
}

/** res/layout/sheet_permission.xml as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(onDismiss: () -> Unit, onContinue: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = CF.GreenPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // handle bar (dot_active)
            Box(
                Modifier
                    .size(width = 44.dp, height = 5.dp)
                    .background(CF.GreenPrimary, RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .size(90.dp)
                    .background(CF.GreenSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CfIcon(R.drawable.ic_mic, 46.dp, tint = CF.GreenPrimary)
            }
            Text(
                text = stringResource(R.string.permission_required),
                modifier = Modifier.padding(top = 20.dp),
                color = CF.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.permission_body),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = CF.White,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .padding(top = 26.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(CF.DarkOrange)
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.continue_btn),
                    color = CF.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
