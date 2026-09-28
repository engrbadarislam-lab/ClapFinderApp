package com.clapfinder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.clapfinder.app.R
import com.clapfinder.app.model.SoundItem
import com.clapfinder.app.ui.components.BackTopBar
import com.clapfinder.app.ui.components.CfIcon
import com.clapfinder.app.ui.components.CfSlider
import com.clapfinder.app.ui.components.SystemBarIcons
import com.clapfinder.app.ui.components.plainClickable
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.ui.theme.CardTitle
import com.clapfinder.app.util.Prefs
import com.clapfinder.app.util.SoundSynth
import kotlin.math.roundToInt

/** Was SoundActivity + SoundAdapter (volume, alert duration, 3-column sound grid). */
@Composable
fun SoundScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val synth = remember { SoundSynth() }

    var volume by remember { mutableIntStateOf(prefs.volume) }
    var durationSec by remember { mutableIntStateOf(prefs.durationSec) }
    var selectedId by remember { mutableIntStateOf(prefs.soundId) }

    // old onStop() → synth.stop(); also when the screen is closed
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { synth.stop() }
    DisposableEffect(Unit) { onDispose { synth.stop() } }

    SystemBarIcons(dark = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
            .systemBarsPadding()
    ) {
        BackTopBar(title = stringResource(R.string.sound), backSize = 34.dp, onBack = onBack) {
            // Apply (bg_btn_outline) — just closes the screen, values are saved live
            Text(
                text = stringResource(R.string.apply),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, CF.GreenPrimary, RoundedCornerShape(20.dp))
                    .plainClickable(onBack)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                color = CF.GreenPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Volume card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CF.Card, CF.CardShape)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CfIcon(R.drawable.ic_volume, 24.dp, tint = CF.GreenPrimaryDark)
                    Text(
                        text = stringResource(R.string.volume),
                        modifier = Modifier.padding(start = 12.dp),
                        style = CardTitle
                    )
                }
                CfSlider(
                    value = volume.toFloat(),
                    onValueChange = {
                        volume = it.roundToInt()
                        prefs.volume = volume
                    },
                    valueRange = 0f..100f,
                    trackHeight = 6.dp,
                    activeColor = CF.GreenPrimary,
                    inactiveColor = CF.SeekTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .padding(horizontal = 6.dp)
                )
            }

            // Duration card
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(CF.Card, CF.CardShape)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CfIcon(R.drawable.ic_clock, 24.dp, tint = CF.GreenPrimaryDark)
                    Text(
                        text = stringResource(R.string.duration),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        style = CardTitle
                    )
                    Text(
                        text = durationLabel(durationSec),
                        color = CF.TextSecondary,
                        fontSize = 15.sp
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5 to "5s", 15 to "15s", 30 to "30s", 60 to "1m").forEach { (sec, label) ->
                        DurationChip(
                            label = label,
                            selected = durationSec == sec,
                            modifier = Modifier.weight(1f)
                        ) {
                            durationSec = sec
                            prefs.durationSec = sec
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.tap_to_play),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 22.dp, bottom = 10.dp),
                color = CF.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Sound grid (3 columns)
            SoundItem.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    row.forEach { item ->
                        SoundCell(
                            item = item,
                            selected = item.id == selectedId,
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedId = item.id
                            prefs.soundId = item.id
                            synth.play(item, prefs.volume, 1500L) // short preview
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun durationLabel(sec: Int) = when (sec) {
    60 -> "1 Minute"
    else -> "$sec Seconds"
}

/** style/DurationChip + bg_chip_selector + chip_text_color */
@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) CF.GreenPrimary else CF.Sheet)
            .then(if (selected) Modifier else Modifier.border(1.dp, CF.Divider, shape))
            .plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) CF.White else CF.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** item_sound.xml */
@Composable
private fun SoundCell(
    item: SoundItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .plainClickable(onClick)
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 130.dp)
                .background(if (selected) CF.Sheet else CF.Card, shape)
                .then(if (selected) Modifier.border(2.5.dp, CF.GreenPrimary, shape) else Modifier)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = item.emoji, fontSize = 46.sp)
            Text(
                text = item.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = if (selected) CF.TextPrimary else CF.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp)
                    .background(CF.GreenSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CfIcon(R.drawable.ic_play, 15.dp)
            }
        }
    }
}
