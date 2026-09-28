package com.clapfinder.app.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clapfinder.app.BuildConfig
import com.clapfinder.app.R
import com.clapfinder.app.ads.AdaptiveBannerAd
import com.clapfinder.app.ads.AppOpenAdManager
import com.clapfinder.app.service.ClapDetectionService
import com.clapfinder.app.ui.components.BackTopBar
import com.clapfinder.app.ui.components.CfIcon
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.plainClickable
import com.clapfinder.app.ui.components.rememberPrefsVersion
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.ui.theme.CardTitle
import com.clapfinder.app.util.PermissionHelper
import com.clapfinder.app.util.Prefs

/** Was SettingActivity + activity_setting.xml. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val version = rememberPrefsVersion(prefs)
    val sensitivity = remember(version) { prefs.sensitivity }
    val scheduleEnabled = remember(version) { prefs.scheduleEnabled }
    val scheduleStart = remember(version) { prefs.scheduleStart }
    val scheduleEnd = remember(version) { prefs.scheduleEnd }

    // onResume(): battery status
    var batteryOptimised by remember { mutableStateOf(PermissionHelper.isIgnoringBattery(context)) }
    LifecycleResumeEffect(Unit) {
        batteryOptimised = PermissionHelper.isIgnoringBattery(context)
        onPauseOrDispose { }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun setSensitivity(level: Int) {
        prefs.sensitivity = level
        if (prefs.serviceRunning) {
            ClapDetectionService.stop(context)
            ClapDetectionService.start(context)
        }
    }

    fun pickTime(isStart: Boolean) {
        val cur = if (isStart) prefs.scheduleStart else prefs.scheduleEnd
        TimePickerDialog(context, { _, h, m ->
            val mins = h * 60 + m
            if (isStart) prefs.scheduleStart = mins else prefs.scheduleEnd = mins
        }, cur / 60, cur % 60, true).show()
    }

    fun openBatterySettings() {
        if (!PermissionHelper.isIgnoringBattery(context)) {
            runCatching { context.startActivity(PermissionHelper.batteryIntent(context)) }
                .onFailure { context.startActivity(PermissionHelper.appDetailsIntent(context)) }
            AppOpenAdManager.skipNextForeground()
        } else {
            toast("Already optimised for background use")
        }
    }

    fun openPlayStore() {
        val pkg = context.packageName
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        }.onFailure {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            )
        }
        AppOpenAdManager.skipNextForeground()
    }

    fun openPrivacyPolicy() {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://logicworms.com/privacy-policy/".toUri()))
        AppOpenAdManager.skipNextForeground()
    }

    SystemBarIcons(dark = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
            .systemBarsPadding()
    ) {
        BackTopBar(title = stringResource(R.string.setting), backSize = 35.dp, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            SectionLabel(stringResource(R.string.command), Modifier.padding(bottom = 10.dp))

            // Sound Sensitivity
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CF.Card, CF.CardShape)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CfIcon(R.drawable.ic_music, 24.dp, tint = CF.GreenPrimaryDark)
                    Text(
                        text = stringResource(R.string.sound_sensitivity),
                        modifier = Modifier.padding(start = 12.dp),
                        style = CardTitle
                    )
                }
                SensitivityRow(
                    label = stringResource(R.string.very_high),
                    selected = sensitivity == 0,
                    modifier = Modifier.padding(top = 14.dp)
                ) { setSensitivity(0) }
                SensitivityRow(stringResource(R.string.high), selected = sensitivity == 1) { setSensitivity(1) }
                SensitivityRow(stringResource(R.string.average), selected = sensitivity == 2) { setSensitivity(2) }
            }

            // Schedule
            Column(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .background(CF.Card, CF.CardShape)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CfIcon(R.drawable.ic_clock, 24.dp, tint = CF.GreenPrimaryDark)
                    Text(
                        text = stringResource(R.string.schedule),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        style = CardTitle
                    )
                    Switch(
                        checked = scheduleEnabled,
                        onCheckedChange = { prefs.scheduleEnabled = it }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TimeColumn(
                        label = stringResource(R.string.start),
                        time = formatTime(scheduleStart),
                        modifier = Modifier.weight(1f)
                    ) { pickTime(true) }
                    TimeColumn(
                        label = stringResource(R.string.end),
                        time = formatTime(scheduleEnd),
                        modifier = Modifier.weight(1f)
                    ) { pickTime(false) }
                }
            }

            // Battery
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(CF.CardShape)
                    .background(CF.Card)
                    .clickable { openBatterySettings() }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CfIcon(R.drawable.ic_battery, 24.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(text = stringResource(R.string.battery_saver), style = CardTitle)
                    Text(
                        text = if (batteryOptimised) "Optimised — running reliably"
                        else "Tap to allow background running",
                        modifier = Modifier.padding(top = 2.dp),
                        color = CF.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                CfIcon(R.drawable.ic_chevron, 22.dp)
            }

            SectionLabel(stringResource(R.string.about), Modifier.padding(top = 24.dp, bottom = 10.dp))

            // About (the Language row stays hidden, as before — its divider was visible)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CF.Card, CF.CardShape)
                    .padding(4.dp)
            ) {
                RowDivider()
                AboutRow(R.drawable.ic_star, CF.GreenPrimaryDark, stringResource(R.string.rate_me)) {
                    openPlayStore()
                }
                RowDivider()
                AboutRow(R.drawable.ic_feedback, CF.GreenPrimaryDark, stringResource(R.string.feedback)) {
                    toast("Send feedback")
                }
                RowDivider()
                AboutRow(R.drawable.ic_shield, CF.GreenPrimaryDark, stringResource(R.string.privacy_policy)) {
                    openPrivacyPolicy()
                }
                RowDivider()
                AboutRow(
                    icon = R.drawable.ic_help,
                    iconTint = null,
                    label = stringResource(R.string.version),
                    trailing = {
                        Text(
                            text = "v" + BuildConfig.VERSION_NAME,
                            color = CF.TextSecondary,
                            fontSize = 15.sp
                        )
                    },
                    onClick = { }
                )
            }
        }

        AdaptiveBannerAd(screenKey = "ads_settings_banner")
    }
}

private fun formatTime(mins: Int) = "%02d:%02d".format(mins / 60, mins % 60)

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = CF.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.05.em
    )
}

/** Row with radio_on / radio_off (no ripple, like the old LinearLayout rows). */
@Composable
private fun SensitivityRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .plainClickable(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDot(selected)
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            color = CF.TextPrimary,
            fontSize = 15.sp
        )
    }
}

/** radio_on.xml / radio_off.xml */
@Composable
private fun RadioDot(selected: Boolean) {
    val ring = if (selected) CF.GreenPrimary else CF.RadioOff
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(color = ring, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
        if (selected) drawCircle(color = CF.GreenPrimary, radius = size.minDimension / 2f - 5.dp.toPx())
    }
}

@Composable
private fun TimeColumn(label: String, time: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = CF.TextSecondary, fontSize = 16.sp)
        Text(
            text = time,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .plainClickable(onClick)
                .padding(vertical = 12.dp),
            color = CF.GreenPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** style/AboutRow (+ AboutIcon / AboutLabel / AboutChevron) */
@Composable
private fun AboutRow(
    @DrawableRes icon: Int,
    iconTint: Color?,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CfIcon(icon, 24.dp, tint = iconTint)
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            color = CF.TextPrimary,
            fontSize = 16.sp
        )
        if (trailing != null) trailing() else CfIcon(R.drawable.ic_chevron, 22.dp)
    }
}

/** style/RowDivider */
@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 54.dp)
            .height(1.dp)
            .background(CF.Divider)
    )
}
