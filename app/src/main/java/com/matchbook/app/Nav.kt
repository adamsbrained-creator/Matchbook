package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalVm = staticCompositionLocalOf<AppVM> { error("AppVM not provided") }

/** Extra space reserved at the end of pages (used by the edge rail navigation). */
val LocalNavPad = compositionLocalOf { 0.dp }

class Dest(val key: String, val icon: String, val label: String)

val DESTS = listOf(
    Dest("clubs", "shield", "Clubs"), Dest("leagues", "trophy", "Leagues"), Dest("add", "plus", "Add"),
    Dest("stats", "bars", "Stats"), Dest("settings", "gear", "Settings"),
)

/** Navigation choices shown in Settings (key, label). The first is the 2.0 floating bar. */
val NAV_CHOICES = listOf(
    "float" to "Floating", "labeled" to "Labeled dock", "capsule" to "Capsule", "tabs" to "Header tabs",
    "dial" to "Speed-dial", "pill" to "Section pill", "rail" to "Edge rail", "bubble" to "Bubble bar",
    "arc" to "Arc menu", "dots" to "Swipe dots", "drawer" to "Side drawer", "islands" to "Two islands",
)

fun navOn(vm: AppVM, k: String): Boolean {
    val inSettings = vm.screen is Screen.SettingsS || vm.screen is Screen.SettingsSubS
    return if (k == "settings") inSettings else (vm.tab == k && !inSettings)
}

fun navGo(vm: AppVM, k: String) {
    vm.navOpen = false
    if (k == "settings") vm.openSettings() else vm.selectTab(k)
}

fun navCount(vm: AppVM, k: String): String = when (k) {
    "clubs" -> "${vm.clubs.size}"
    "leagues" -> "${vm.leagues.size}"
    else -> ""
}

@Composable
private fun navInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
private fun barModifier(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val u = LocalU.current
    return Modifier
        .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .35f))
        .clip(shape).background(u.navFill)
        .border(1.dp, Brush.verticalGradient(listOf(u.glassTop, u.glassEdge)), shape)
}

@Composable
private fun Scrim(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}

@Composable
fun NavHost(vm: AppVM) {
    when (LocalU.current.s.navs) {
        "labeled" -> NavLabeled(vm)
        "capsule" -> NavCapsule(vm)
        "tabs" -> NavTabsFab(vm)
        "dial" -> NavDial(vm)
        "pill" -> NavPill(vm)
        "rail" -> NavRail(vm)
        "bubble" -> NavBubble(vm)
        "arc" -> NavArc(vm)
        "dots" -> NavDots(vm)
        "drawer" -> NavDrawer(vm)
        "islands" -> NavIslands(vm)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { NavBar(vm, false) }
    }
}

// =============================================================== 0. floating bar (2.0) and the landscape side bar
@Composable
fun NavItem(icon: String, on: Boolean, ring: Boolean, label: String?, onClick: () -> Unit) {
    val u = LocalU.current
    val glow = if (on) Modifier.shadow(10.dp, CircleShape, ambientColor = u.acc.copy(alpha = .55f), spotColor = u.acc.copy(alpha = .55f)) else Modifier
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(if (label != null) 46.dp else 52.dp).then(glow).clip(CircleShape)
                .background(if (on) u.acc else Color.Transparent)
                .then(if (ring && !on) Modifier.border(1.dp, u.outline, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Ico(icon, 22.dp, if (on) u.on else u.ink, 1.9f) }
        if (label != null) Tx(label, 10f, 700, if (on) u.ink else u.mu, maxLines = 1)
    }
}

@Composable
fun NavBar(vm: AppVM, land: Boolean) {
    val u = LocalU.current
    val labels = u.s.lbl == "on"
    val barShape = if (labels) RoundedCornerShape(34.dp) else RoundedCornerShape(36.dp)
    val bar = barModifier(barShape)
    if (land) {
        Box(
            Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(bar.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DESTS.forEach { d -> NavItem(d.icon, navOn(vm, d.key), d.key == "add", if (labels) d.label else null) { navGo(vm, d.key) } }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(bar.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DESTS.forEach { d -> NavItem(d.icon, navOn(vm, d.key), d.key == "add", if (labels) d.label else null) { navGo(vm, d.key) } }
            }
        }
    }
}

// =============================================================== 1. labeled dock
@Composable
fun NavLabeled(vm: AppVM) {
    val u = LocalU.current
    val inset = navInset()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(u.navFill)
                .drawBehind { drawLine(u.glassEdge, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(bottom = inset),
        ) {
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                DESTS.forEach { d ->
                    val on = navOn(vm, d.key)
                    if (d.key == "add") Spacer(Modifier.weight(1f))
                    else Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.width(26.dp).height(3.dp).clip(CircleShape).background(if (on) u.acc else Color.Transparent))
                        Spacer(Modifier.height(5.dp))
                        Ico(d.icon, 24.dp, if (on) u.ink else u.mu, 1.9f)
                        Spacer(Modifier.height(3.dp))
                        Tx(d.label, 10.5f, 700, if (on) u.ink else u.mu, maxLines = 1)
                    }
                }
            }
        }
        // raised Add button
        Box(
            Modifier.padding(bottom = inset + 28.dp).size(58.dp).clip(CircleShape).background(u.acc)
                .border(5.dp, u.bg, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, "add") },
            contentAlignment = Alignment.Center,
        ) { Ico("plus", 24.dp, u.on, 2.1f) }
    }
}

// =============================================================== 2. expanding capsule
@Composable
fun NavCapsule(vm: AppVM) {
    val u = LocalU.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                barModifier(CircleShape).weight(1f).height(60.dp).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DESTS.filter { it.key != "add" }.forEach { d ->
                    val on = navOn(vm, d.key)
                    Row(
                        Modifier.height(48.dp).widthIn(min = 48.dp).clip(CircleShape).background(if (on) u.acc else Color.Transparent)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) }
                            .padding(horizontal = if (on) 16.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Ico(d.icon, 22.dp, if (on) u.on else u.ink, 1.9f)
                        if (on) Tx(d.label, 14f, 800, u.on, maxLines = 1)
                    }
                }
            }
            Box(
                Modifier.size(60.dp).shadow(10.dp, CircleShape, ambientColor = u.acc.copy(alpha = .5f), spotColor = u.acc.copy(alpha = .5f))
                    .clip(CircleShape).background(u.acc)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, "add") },
                contentAlignment = Alignment.Center,
            ) { Ico("plus", 24.dp, u.on, 2.1f) }
        }
    }
}

// =============================================================== 3. header tabs and one button (tabs live in the header, see Tp)
@Composable
fun NavTabsFab(vm: AppVM) {
    val u = LocalU.current
    val hide = vm.tab == "add" && vm.screen == null
    if (hide) return
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(end = 16.dp, bottom = 22.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.height(54.dp).shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f))
                .clip(CircleShape).background(u.acc).clickable { navGo(vm, "add") }.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ico("plus", 22.dp, u.on, 2.1f)
            Tx("Add", 15f, 800, u.on)
        }
    }
}

/** Text tabs shown under the page title in the "Header tabs" navigation. */
@Composable
fun HeaderTabs(vm: AppVM) {
    val u = LocalU.current
    val h = LocalHdr.current
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf("clubs" to "Clubs", "leagues" to "Leagues", "stats" to "Stats").forEach { (k, t) ->
            val on = navOn(vm, k)
            Column(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, k) }) {
                Tx(t, 17 * u.fs, if (on) 800 else 700, if (on) h.fg else h.fg.copy(alpha = .62f))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(if (on) 26.dp else 0.dp).height(2.dp).background(h.fg))
            }
        }
    }
}

// =============================================================== 4. speed-dial
@Composable
fun NavDial(vm: AppVM) {
    val u = LocalU.current
    val cur = DESTS.firstOrNull { navOn(vm, it.key) } ?: DESTS[0]
    Box(Modifier.fillMaxSize()) {
        if (vm.navOpen) Scrim { vm.navOpen = false }
        Column(
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.navOpen) DESTS.forEach { d ->
                val on = navOn(vm, d.key)
                Row(
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.clip(CircleShape).background(u.navFill).border(1.dp, u.glassEdge, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Tx(d.label, 14f, 800, u.ink)
                    }
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (on) u.acc else u.navFill).border(1.dp, u.glassEdge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Ico(d.icon, 22.dp, if (on) u.on else u.ink, 1.9f) }
                }
            }
            Box(
                Modifier.size(62.dp).shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f))
                    .clip(CircleShape).background(u.acc).clickable { vm.navOpen = !vm.navOpen },
                contentAlignment = Alignment.Center,
            ) { Ico(if (vm.navOpen) "close" else cur.icon, 26.dp, u.on, 2f) }
        }
    }
}

// =============================================================== 5. section pill with a sheet
@Composable
fun NavPill(vm: AppVM) {
    val u = LocalU.current
    val cur = DESTS.firstOrNull { navOn(vm, it.key) } ?: DESTS[0]
    Box(Modifier.fillMaxSize()) {
        if (vm.navOpen) Scrim { vm.navOpen = false }
        if (vm.navOpen) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(u.sf).border(1.dp, u.glassEdge, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(u.outline))
            Spacer(Modifier.height(16.dp))
            Tx("Go to", 13 * u.fs, 700, u.mu, Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))
            DESTS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { d ->
                        val on = navOn(vm, d.key)
                        Column(
                            Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(24.dp)).background(if (on) u.acc else u.fieldFill)
                                .clickable { navGo(vm, d.key) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Ico(d.icon, 26.dp, if (on) u.on else u.ink, 1.9f)
                            Spacer(Modifier.height(8.dp))
                            Tx(d.label, 13 * u.fs, 800, if (on) u.on else u.ink, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (!vm.navOpen) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
            Row(
                barModifier(CircleShape).height(54.dp).clickable { vm.navOpen = true }.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Ico(cur.icon, 20.dp, u.ink, 1.9f)
                Tx(cur.label, 15 * u.fs, 800, u.ink)
                Ico("chevu", 16.dp, u.mu, 2f)
            }
        }
    }
}

// =============================================================== 6. edge rail
@Composable
fun NavRail(vm: AppVM) {
    val u = LocalU.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
        Column(
            barModifier(RoundedCornerShape(28.dp)).width(54.dp).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DESTS.forEach { d ->
                val on = navOn(vm, d.key)
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(if (on) u.acc else Color.Transparent)
                        .then(if (d.key == "add" && !on) Modifier.drawBehind {
                            drawRoundRect(u.outline, cornerRadius = CornerRadius(size.width / 2f), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                        } else Modifier)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                    contentAlignment = Alignment.Center,
                ) { Ico(d.icon, 20.dp, if (on) u.on else u.ink, 1.9f) }
            }
        }
    }
}

// =============================================================== 7. bubble bar
@Composable
fun NavBubble(vm: AppVM) {
    val u = LocalU.current
    val inset = navInset()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().background(u.navFill)
                .drawBehind { drawLine(u.glassEdge, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .height(66.dp + inset).padding(bottom = inset),
        ) {
            DESTS.forEach { d ->
                val on = navOn(vm, d.key)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-4).dp)) {
                        Box(
                            Modifier.offset(y = (-14).dp).size(54.dp).clip(CircleShape).background(u.acc).border(5.dp, u.bg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Ico(d.icon, 24.dp, u.on, 2f) }
                        Tx(d.label, 10.5f, 800, u.ink, Modifier.offset(y = (-10).dp), maxLines = 1)
                    } else Ico(d.icon, 26.dp, u.mu, 1.9f)
                }
            }
        }
    }
}

// =============================================================== 8. arc menu
@Composable
fun NavArc(vm: AppVM) {
    val u = LocalU.current
    val cur = DESTS.firstOrNull { navOn(vm, it.key) } ?: DESTS[0]
    Box(Modifier.fillMaxSize()) {
        if (vm.navOpen) Scrim { vm.navOpen = false }
        Box(
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 16.dp, bottom = 26.dp).size(62.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.navOpen) DESTS.forEachIndexed { i, d ->
                val a = i * (PI / 2.0) / (DESTS.size - 1)
                val on = navOn(vm, d.key)
                Column(
                    Modifier.offset(x = (-134.0 * cos(a)).dp, y = (-134.0 * sin(a)).dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (on) u.acc else u.navFill).border(1.dp, u.glassEdge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Ico(d.icon, 20.dp, if (on) u.on else u.ink, 1.9f) }
                    Tx(d.label, 9.5f, 800, Color.White, maxLines = 1)
                }
            }
            Box(
                Modifier.size(62.dp).shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f))
                    .clip(CircleShape).background(u.acc).clickable { vm.navOpen = !vm.navOpen },
                contentAlignment = Alignment.Center,
            ) { Ico(if (vm.navOpen) "close" else cur.icon, 26.dp, u.on, 2f) }
        }
    }
}

// =============================================================== 9. swipe dots
@Composable
fun NavDots(vm: AppVM) {
    val u = LocalU.current
    val idx = DESTS.indexOfFirst { navOn(vm, it.key) }.coerceAtLeast(0)
    fun step(d: Int) = navGo(vm, DESTS[(idx + d + DESTS.size) % DESTS.size].key)
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            barModifier(RoundedCornerShape(28.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { step(-1) }, contentAlignment = Alignment.Center) { Ico("back", 20.dp, u.ink, 2f) }
                Tx(DESTS[idx].label, 16 * u.fs, 800, u.ink, Modifier.widthIn(min = 96.dp), align = TextAlign.Center)
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { step(1) }, contentAlignment = Alignment.Center) { Ico("chev", 20.dp, u.ink, 2f) }
            }
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                DESTS.forEachIndexed { i, d ->
                    Box(
                        Modifier.height(6.dp).width(if (i == idx) 22.dp else 6.dp).clip(CircleShape)
                            .background(if (i == idx) u.acc else u.outline).clickable { navGo(vm, d.key) },
                    )
                }
            }
        }
    }
}

// =============================================================== 10. side drawer (menu button lives in the header, see Tp)
@Composable
fun NavDrawer(vm: AppVM) {
    val u = LocalU.current
    if (!vm.navOpen) return
    Box(Modifier.fillMaxSize()) {
        Scrim { vm.navOpen = false }
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(.8f).widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)).background(u.sf)
                .border(1.dp, u.glassEdge, RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp))
                .windowInsetsPadding(WindowInsets.systemBars).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PitchMark(Modifier.size(52.dp))
                Column {
                    Hd("Matchbook", 24f)
                    Tx("${vm.clubs.size} clubs · ${vm.leagues.size} leagues", 12 * u.fs, 600, u.mu)
                }
            }
            Spacer(Modifier.height(22.dp))
            DESTS.forEach { d ->
                val on = navOn(vm, d.key)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (on) u.acc else Color.Transparent).clickable { navGo(vm, d.key) }.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Ico(d.icon, 24.dp, if (on) u.on else u.ink, 1.9f)
                    Tx(d.label, 17 * u.fs, 800, if (on) u.on else u.ink, Modifier.weight(1f))
                    val n = navCount(vm, d.key)
                    if (n.isNotEmpty()) Tx(n, 14 * u.fs, 700, if (on) u.on else u.mu)
                }
            }
            Spacer(Modifier.weight(1f))
            Tx("Matchbook ${vm.appVersion()}", 12 * u.fs, 600, u.mu, Modifier.padding(start = 6.dp))
        }
    }
}

// =============================================================== 11. two islands
@Composable
fun NavIslands(vm: AppVM) {
    val u = LocalU.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            @Composable
            fun island(keys: List<String>, mod: Modifier) = Row(
                barModifier(CircleShape).then(mod).height(60.dp).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DESTS.filter { it.key in keys }.forEach { d ->
                    val on = navOn(vm, d.key)
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (on) u.acc else Color.Transparent)
                            .then(if (d.key == "add" && !on) Modifier.border(1.dp, u.outline, CircleShape) else Modifier)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { navGo(vm, d.key) },
                        contentAlignment = Alignment.Center,
                    ) { Ico(d.icon, 22.dp, if (on) u.on else u.ink, 1.9f) }
                }
            }
            island(listOf("clubs", "leagues", "stats"), Modifier.weight(1f))
            island(listOf("add", "settings"), Modifier.width(126.dp))
        }
    }
}
