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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// =====================================================================================================
// Settings (2.6): a short hub of grouped rows, each opening its own small page (same idea as Paperback's
// settings). Hub = Screen.SettingsS, pages = Screen.SettingsSubS(page), so the back button and the back
// label work like everywhere else.
// =====================================================================================================

private val TYPEFACES = listOf("manrope" to "Manrope", "outfit" to "Outfit", "condensed" to "Condensed", "rounded" to "Rounded", "serif" to "Serif", "mono" to "Mono")
private val TEXT_SIZES = listOf("s" to "Small", "m" to "Medium", "l" to "Large")
private val CLUB_LAYOUTS = listOf("list" to "List", "g2" to "Grid 2", "g3" to "Grid 3", "comp" to "Compact")
private val THEMES = listOf("auto" to "Auto", "dark" to "Dark", "light" to "Light")

private val SETTINGS_PAGES = mapOf(
    "look" to ("Appearance" to "Theme, accent and presets"),
    "style" to ("Style" to "Header, surfaces and shapes"),
    "text" to ("Text and spacing" to "Typeface and size"),
    "nav" to ("Navigation" to "Bars and menus"),
    "pages" to ("Pages and grids" to "Layouts"),
    "blocks" to ("Page blocks" to "What each page shows"),
    "cols" to ("Table columns" to "League page card"),
    "fields" to ("Club details" to "Fields shown"),
    "backup" to ("Backup and restore" to "Keep your data safe"),
    "data" to ("Data" to "Reset and sample data"),
    "about" to ("About" to "Matchbook"),
)

fun settingsTitle(page: String): String = SETTINGS_PAGES[page]?.first ?: "Settings"

private fun label(opts: List<Pair<String, String>>, key: String): String = opts.firstOrNull { it.first == key }?.second ?: ""

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

/** One glass card holding a page's controls. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
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
    "Settings reorganised into short pages",
    "Find players online (names, renders, photos, stats)",
    "Renders on the lineup pitch drawn without the circle",
)

// ------------------------------------------------------------------------------------------- backup
private class BackupActions(val create: () -> Unit, val load: () -> Unit)

@Composable
private fun rememberBackup(vm: AppVM): BackupActions {
    val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.createBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBackup(uri)
    }
    return BackupActions(
        { create.launch("Matchbook-backup-$stamp.zip") },
        { open.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*")) },
    )
}

// ---------------------------------------------------------------------------------------------- hub
private class SetRow(val icon: String, val title: String, val value: String, val page: String)

@Composable
private fun SetGroup(vm: AppVM, heading: String?, rows: List<SetRow>) {
    val u = LocalU.current
    if (heading != null) Tx(heading, 12 * u.fs, 700, u.mu, Modifier.padding(start = 6.dp, top = 24.dp, bottom = 10.dp))
    else Spacer(Modifier.height(24.dp))
    Glass(Modifier.fillMaxWidth(), pad = PaddingValues(0.dp)) {
        Column(Modifier.clip(u.rs(0))) {
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable { vm.openSettingsPage(r.page) }
                        .then(
                            if (i == 0) Modifier else Modifier.drawBehind {
                                drawLine(u.ln, Offset(70.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
                            },
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(u.rs(10)).background(u.fieldFill), contentAlignment = Alignment.Center) {
                        Ico(r.icon, 22.dp, u.ink, 1.7f)
                    }
                    Tx(r.title, 16 * u.fs, 600, modifier = Modifier.weight(1f), maxLines = 1)
                    if (r.value.isNotBlank()) Tx(r.value, 14 * u.fs, 500, u.mu, maxLines = 1)
                    Ico("chev", 18.dp, u.mu, 2f)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    val u = LocalU.current
    val s = u.s
    val backup = rememberBackup(vm)
    val card = if (u.dark) Color(0xFF1F1F1F) else Color(0xFF111111)

    Tp("Settings", "Make it yours", lead = true)
    Column(Modifier.bd(top = 14.dp)) {
        // summary card (like Paperback's daily goal): what's in the app and when it was last backed up
        Row(
            Modifier.fillMaxWidth().clip(u.rs(0)).background(card).padding(start = 18.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PitchMark(Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Tx("Matchbook", 17 * u.fs, 800, Color.White, maxLines = 1)
                Tx("${vm.leagues.size} leagues · ${vm.clubs.size} clubs · ${vm.players.size} players", 12 * u.fs, 500, Color(0xFFC4C4C4), Modifier.padding(top = 2.dp), maxLines = 1)
                Tx(
                    if (vm.lastBackup > 0) "Backed up ${fmtDateTime(vm.lastBackup)}" else "No backup yet",
                    12 * u.fs, 500, Color(0xFFC4C4C4), maxLines = 1,
                )
            }
            Box(Modifier.height(40.dp).clip(CircleShape).background(Color.White).clickable { backup.create() }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Tx("Back up", 14 * u.fs, 800, Color(0xFF161616), maxLines = 1)
            }
        }

        val navName = if (u.coral) "Coral menu" else label(NAV_CHOICES, s.navs)
        SetGroup(vm, "LOOK", listOf(
            SetRow("brush", "Appearance", "${label(THEMES, s.at("theme"))} · ${if (u.coral) "Coral" else "Glass"}", "look"),
            SetRow("shape", "Style", "Radius ${s.r}", "style"),
            SetRow("type", "Text and spacing", "${label(TYPEFACES, s.at("f"))} · ${label(TEXT_SIZES, s.at("fs"))}", "text"),
            SetRow("compass", "Navigation", navName, "nav"),
        ))
        SetGroup(vm, "PAGES", listOf(
            SetRow("grid", "Pages and grids", "Clubs: ${label(CLUB_LAYOUTS, s.at("cg"))}", "pages"),
            SetRow("list", "Page blocks", "${s.blocks.size} on", "blocks"),
            SetRow("bars", "Table columns", "${s.cols.size} on", "cols"),
            SetRow("shield", "Club details", "${s.fields.size} on", "fields"),
        ))
        SetGroup(vm, "DATA", listOf(
            SetRow("save", "Backup and restore", if (vm.lastBackup > 0) fmtDateTime(vm.lastBackup) else "Never", "backup"),
            SetRow("db", "Data", "", "data"),
        ))
        SetGroup(vm, null, listOf(SetRow("info", "About Matchbook", vm.appVersion(), "about")))
        Spacer(Modifier.height(10.dp))
    }
}

// ------------------------------------------------------------------------------------------ sub pages
@Composable
private fun StylePreview(vm: AppVM) {
    val pc = vm.clubs.firstOrNull() ?: Club("x", "", "Sample FC", "SFC", color = "#0ea5e9", c2 = "#ffffff", w = 3, d = 1)
    ClubCard(vm, pc, "list", Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSub(vm: AppVM, page: String) {
    val u = LocalU.current
    val s = u.s
    val backup = rememberBackup(vm)
    val (title, sub) = SETTINGS_PAGES[page] ?: ("Settings" to "")

    TopRow(vm.backLabel, { vm.goBack() })
    Tp(title, sub)
    if (page == "look") HChips(PRESETS.map { it to it }, null) { vm.applyPreset(it) }
    Column(Modifier.bd(top = 14.dp)) {
        when (page) {
            "look" -> {
                StylePreview(vm)
                Panel {
                    Seg(vm, "Interface style", "ui", listOf("glass" to "Glass (2.0)", "coral" to "Coral"))
                    Seg(vm, "Theme", "theme", THEMES)
                    Lbl("Accent")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENTS.forEach { c -> Swatch(c, s.acc.equals(c, true)) { vm.set("acc", c) } }
                        CustomSwatch(s.acc) { vm.set("acc", it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    KvRow(true) {
                        Tx("Launch animation", 14 * u.fs, 600, modifier = Modifier.weight(1f))
                        Toggle(s.splash) { vm.update { it.copy(splash = !it.splash) } }
                    }
                }
            }
            "style" -> {
                StylePreview(vm)
                Panel {
                    Seg(vm, "Header glow", "g", listOf("bold" to "Bold", "soft" to "Soft", "mono" to "Mono"))
                    Seg(vm, "Header height", "gh", listOf("s" to "Short", "m" to "Normal", "l" to "Tall"))
                    Seg(vm, "Surface", "surf", listOf("glass" to "Glass", "solid" to "Solid", "outline" to "Outline"))
                    Rg("Corner radius", s.r, 8, 36) { v -> vm.update { it.copy(r = v) } }
                    Seg(vm, "Crest shape", "c", listOf("circle" to "Circle", "square" to "Soft", "shield" to "Shield", "hex" to "Hex"))
                    Rg("Crest ring", s.ring, 0, 14) { v -> vm.update { it.copy(ring = v) } }
                }
            }
            "text" -> {
                StylePreview(vm)
                Panel {
                    Seg(vm, "Typeface", "f", TYPEFACES)
                    Seg(vm, "Text size", "fs", TEXT_SIZES)
                    Seg(vm, "Spacing", "d", listOf("cozy" to "Cozy", "compact" to "Compact"))
                }
            }
            "nav" -> Panel {
                Tx("Glass interface only. Coral uses its menu button and a floating action button.", 12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp))
                Seg(vm, "Style", "navs", NAV_CHOICES)
                if (s.navs == "float") Seg(vm, "Labels (floating bar)", "lbl", listOf("off" to "Icons only", "on" to "With labels"))
            }
            "pages" -> Panel {
                Seg(vm, "Clubs layout", "cg", CLUB_LAYOUTS)
                Seg(vm, "Leagues layout", "lg", listOf("cards" to "Cards", "list" to "List", "g2" to "Grid"))
                Seg(vm, "Stats layout", "sg", listOf("list" to "List", "g2" to "Grid"))
                Seg(vm, "Club stat tile columns", "tc", listOf("2" to "2", "3" to "3"))
                Seg(vm, "Club names", "nm", listOf("full" to "Full", "short" to "Short"))
            }
            "blocks" -> BLOCK_GROUPS.forEach { g ->
                Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Tx(g.page, 13 * u.fs, 800, u.mu)
                    Toggles(g.items.map { it.key to it.label }, { it in s.blocks }) { vm.toggleBlock(it) }
                }
            }
            "cols" -> Panel {
                Tx("Shown on the league page card. The full table page always has its own views.", 12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp, bottom = 4.dp))
                Toggles(ALL_COLS, { it in s.cols }) { vm.toggleCol(it) }
            }
            "fields" -> Panel { Toggles(ALL_FIELDS, { it in s.fields }) { vm.toggleField(it) } }
            "backup" -> Panel {
                Tx(
                    "Saves everything (clubs, leagues, fixtures, squads, lineups, images and settings) into one file. Backups keep working with newer versions of the app.",
                    12 * u.fs, 500, u.mu, Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                Tx(
                    if (vm.lastBackup > 0) "Last backup: ${fmtDateTime(vm.lastBackup)}" else "No backup made yet",
                    13 * u.fs, 700, modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassBtn("Create", "save", Modifier.weight(1f)) { backup.create() }
                    GlassBtn("Load", "load", Modifier.weight(1f)) { backup.load() }
                }
                if (vm.hasPrev) {
                    Spacer(Modifier.height(12.dp))
                    Btn("Undo last restore", "s", 0.dp, 0.dp) { vm.undoRestore() }
                }
            }
            "data" -> Panel {
                Spacer(Modifier.height(10.dp))
                Btn("Reset style", "s", 0.dp) { vm.reset() }
                Btn("Load sample data", "s", 0.dp) { vm.loadSample() }
                DeleteBtn("Clear all data", 0.dp) { vm.clearAll() }
            }
            "about" -> Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PitchMark(Modifier.size(64.dp))
                    Column {
                        Hd("Matchbook", 24f)
                        Tx("Version ${vm.appVersion()}", 14 * u.fs, 700)
                        Tx("Build ${vm.buildNumber()} · data format $DATA_FORMAT", 12 * u.fs, 500, u.mu)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Tx("What's new", 13 * u.fs, 800)
                WHATS_NEW.forEach { line ->
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(u.acc))
                        Tx(line, 13 * u.fs, 500, u.mu)
                    }
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
