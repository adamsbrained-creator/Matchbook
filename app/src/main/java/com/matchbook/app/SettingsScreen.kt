package com.matchbook.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Seg(vm: AppVM, label: String, key: String, opts: List<Pair<String, String>>) {
    val u = LocalU.current
    val cur = u.s.at(key)
    Lbl(label)
    if (opts.size <= 3) {
        SegRow(opts, cur) { vm.set(key, it) }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { (v, t) -> Chip(t, v == cur) { vm.set(key, v) } }
        }
    }
}

@Composable
private fun Rg(label: String, v: Int, min: Int, max: Int, onV: (Int) -> Unit) {
    val u = LocalU.current
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Tx(label, 13 * u.fs, 700, u.mu)
        Tx("$v", 13 * u.fs, 800)
    }
    Slider(v.toFloat(), { onV(it.roundToInt()) }, valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = u.acc, inactiveTrackColor = u.ln))
}

@Composable
private fun Sec(title: String, content: @Composable ColumnScope.() -> Unit) {
    val u = LocalU.current
    H3(title)
    Glass(Modifier.fillMaxWidth().padding(bottom = 4.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Toggles(items: List<Pair<String, String>>, on: (String) -> Boolean, flip: (String) -> Unit) {
    val u = LocalU.current
    items.forEachIndexed { i, (k, t) ->
        KvRow(i == items.lastIndex) {
            Tx(t, 14 * u.fs, 600, modifier = Modifier.weight(1f))
            Toggle(on(k)) { flip(k) }
        }
    }
}

private val ACCENTS = listOf("#c8f169", "#ffffff", "#ff8a3d", "#38bdf8", "#f472b6", "#a78bfa", "#34d399")

private val WHATS_NEW = listOf(
    "Squad, lineup builder and tactics mode",
    "Coral interface as an alternative look",
    "Eleven extra navigation bars",
    "Pitch app icon and a refreshed icon set",
    "Backups now include squads and lineups",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppVM) {
    val u = LocalU.current
    val s = u.s
    val pc = vm.clubs.firstOrNull() ?: Club("x", "", "Sample FC", "SFC", color = "#0ea5e9", c2 = "#ffffff", w = 3, d = 1)
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.createBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBackup(uri)
    }

    Tp("Settings", "Make it yours", lead = true)
    HChips(PRESETS.map { it to it }, null) { vm.applyPreset(it) }
    Column(Modifier.bd(top = 14.dp)) {
        ClubCard(vm, pc, "list", Modifier.fillMaxWidth().padding(bottom = 4.dp))

        Sec("Appearance") {
            Seg(vm, "Interface style", "ui", listOf("glass" to "Glass (2.0)", "coral" to "Coral"))
            Seg(vm, "Theme", "theme", listOf("auto" to "Auto", "dark" to "Dark", "light" to "Light"))
            Lbl("Accent")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEach { c -> Swatch(c, s.acc.equals(c, true)) { vm.set("acc", c) } }
                CustomSwatch(s.acc) { vm.set("acc", it) }
            }
            Seg(vm, "Header glow", "g", listOf("bold" to "Bold", "soft" to "Soft", "mono" to "Mono"))
            Seg(vm, "Header height", "gh", listOf("s" to "Short", "m" to "Normal", "l" to "Tall"))
            Seg(vm, "Surface", "surf", listOf("glass" to "Glass", "solid" to "Solid", "outline" to "Outline"))
            Rg("Corner radius", s.r, 8, 36) { v -> vm.update { it.copy(r = v) } }
            Seg(vm, "Crest shape", "c", listOf("circle" to "Circle", "square" to "Soft", "shield" to "Shield", "hex" to "Hex"))
            Rg("Crest ring", s.ring, 0, 14) { v -> vm.update { it.copy(ring = v) } }
        }

        Sec("Type") {
            Seg(vm, "Typeface", "f", listOf("manrope" to "Manrope", "outfit" to "Outfit", "condensed" to "Condensed", "rounded" to "Rounded", "serif" to "Serif", "mono" to "Mono"))
            Seg(vm, "Text size", "fs", listOf("s" to "Small", "m" to "Medium", "l" to "Large"))
            Seg(vm, "Spacing", "d", listOf("cozy" to "Cozy", "compact" to "Compact"))
        }

        Sec("Navigation") {
            Tx("Glass interface only. Coral uses its menu button and a floating action button.", 12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp))
            Seg(vm, "Style", "navs", NAV_CHOICES)
            if (s.navs == "float") Seg(vm, "Labels (floating bar)", "lbl", listOf("off" to "Icons only", "on" to "With labels"))
        }

        Sec("Pages and grids") {
            Seg(vm, "Clubs layout", "cg", listOf("list" to "List", "g2" to "Grid 2", "g3" to "Grid 3", "comp" to "Compact"))
            Seg(vm, "Leagues layout", "lg", listOf("cards" to "Cards", "list" to "List", "g2" to "Grid"))
            Seg(vm, "Stats layout", "sg", listOf("list" to "List", "g2" to "Grid"))
            Seg(vm, "Club stat tile columns", "tc", listOf("2" to "2", "3" to "3"))
            Seg(vm, "Club names", "nm", listOf("full" to "Full", "short" to "Short"))
        }

        H3("Page blocks")
        BLOCK_GROUPS.forEach { g ->
            Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Tx(g.page, 13 * u.fs, 800, u.mu)
                Toggles(g.items.map { it.key to it.label }, { it in s.blocks }) { vm.toggleBlock(it) }
            }
        }

        Sec("League table columns") {
            Tx("Shown on the league page card. The full table page always has its own views.", 12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp))
            Toggles(ALL_COLS, { it in s.cols }) { vm.toggleCol(it) }
        }

        Sec("Club details") {
            Toggles(ALL_FIELDS, { it in s.fields }) { vm.toggleField(it) }
        }

        Sec("Launch") {
            KvRow(true) {
                Tx("Launch animation", 14 * u.fs, 600, modifier = Modifier.weight(1f))
                Toggle(s.splash) { vm.update { it.copy(splash = !it.splash) } }
            }
        }

        Sec("Backup and restore") {
            Tx(
                "Saves everything (clubs, leagues, fixtures, images and settings) into one file. Backups keep working with newer versions of the app.",
                12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            Tx(
                if (vm.lastBackup > 0) "Last backup: ${fmtDateTime(vm.lastBackup)}" else "No backup made yet",
                13 * u.fs, 700, modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassBtn("Create", "save", Modifier.weight(1f)) { create.launch("Matchbook-backup-$stamp.zip") }
                GlassBtn("Load", "load", Modifier.weight(1f)) { open.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*")) }
            }
            if (vm.hasPrev) {
                Spacer(Modifier.height(12.dp))
                Btn("Undo last restore", "s", 0.dp, 0.dp) { vm.undoRestore() }
            }
        }

        Sec("Data") {
            Spacer(Modifier.height(10.dp))
            Btn("Reset style", "s", 0.dp) { vm.reset() }
            Btn("Load sample data", "s", 0.dp) { vm.loadSample() }
            DeleteBtn("Clear all data", 0.dp) { vm.clearAll() }
        }

        H3("About")
        Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PitchMark(Modifier.size(64.dp))
                Column {
                    Hd("Matchbook", 24f)
                    Tx("Version ${vm.appVersion()}", 14 * u.fs, 700)
                    Tx("Build ${vm.buildNumber()} · data format $DATA_FORMAT", 12 * u.fs, 500, u.mu)
                }
            }
            Spacer(Modifier.height(14.dp))
            Tx("What's new in 2.5", 13 * u.fs, 800)
            WHATS_NEW.forEach { line ->
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(u.acc))
                    Tx(line, 13 * u.fs, 500, u.mu)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun RestoreDialog(vm: AppVM, p: PendingRestore) {
    val u = LocalU.current
    val whenText = if (p.created > 0) fmtDateTime(p.created) else "unknown date"
    val ver = if (p.appVersion.isNotBlank()) " (app ${p.appVersion})" else ""
    AlertDialog(
        onDismissRequest = { vm.cancelRestore() },
        containerColor = if (u.dark) Color(0xFF16181D) else Color.White,
        title = { Text("Restore this backup?", color = u.ink) },
        text = {
            Text(
                "Backup from $whenText$ver\n${p.data.clubs.size} clubs, ${p.data.leagues.size} leagues, ${p.data.fixtures.size} fixtures.\n\n" +
                    "This replaces everything currently in the app. You can undo it right afterwards from Settings.",
                color = u.mu,
            )
        },
        confirmButton = { TextButton(onClick = { vm.confirmRestore() }) { Text("Restore", color = u.ink) } },
        dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text("Cancel", color = u.mu) } },
    )
}
