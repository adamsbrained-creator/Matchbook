package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Coral look (2.5): round white buttons, the "matchbook" wordmark, a black floating action button and a menu page.

@Composable
fun Wordmark() {
    val u = LocalU.current
    Tx("matchbook", 24f, 600, u.ink)
}

/** Top bar: a round button on each side and the wordmark in the middle. */
@Composable
fun CoralBar(left: @Composable () -> Unit, right: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp)) { left() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Wordmark() }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { right?.invoke() }
    }
}

@Composable
fun CoralFab(label: String, icon: String, onClick: () -> Unit) {
    val u = LocalU.current
    Row(
        Modifier.height(56.dp)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .28f), spotColor = Color.Black.copy(alpha = .28f))
            .clip(CircleShape).background(u.pri).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico(icon, 20.dp, u.onPri, 1.9f)
        Tx(label, 16 * u.fs, 500, u.onPri)
    }
}

/** The floating action button of the current Coral page (New club, Add club, ...). */
@Composable
fun CoralOverlay(vm: AppVM) {
    val sc = vm.screen
    val spec: Triple<String, String, () -> Unit>? = when {
        sc == null && vm.tab == "clubs" -> Triple("New club", "edit", { vm.newClub(vm.clubFilter.takeIf { it != "all" }) })
        sc == null && vm.tab == "leagues" -> Triple("New league", "edit", { vm.newLeague() })
        sc is Screen.LeagueS -> Triple("Add club", "plus", { vm.newClub(sc.id) })
        sc is Screen.SquadS -> Triple("Add player", "plus", { vm.newPlayer(sc.cid) })
        else -> null
    }
    if (spec != null) Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(end = 20.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomEnd,
    ) { CoralFab(spec.first, spec.second, spec.third) }
}

@Composable
private fun FilterText(label: String, onClick: () -> Unit) {
    val u = LocalU.current
    Row(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tx(label, 16 * u.fs, 500, u.mu, Modifier.widthIn(max = 160.dp), maxLines = 1)
        Ico("chevd", 18.dp, u.mu, 2f)
    }
}

/** Coral clubs page filter row: league menu, sort menu and a list / grid switch. */
@Composable
fun CoralClubFilter(vm: AppVM) {
    val u = LocalU.current
    var lOpen by remember { mutableStateOf(false) }
    var sOpen by remember { mutableStateOf(false) }
    val leagueName = if (vm.clubFilter == "all") "All leagues" else (vm.lg(vm.clubFilter)?.name ?: "All leagues")
    val sortName = when (vm.clubSort) { "pts" -> "Points"; "lg" -> "League"; else -> "A–Z" }
    val asList = u.s.cg == "list" || u.s.cg == "comp"
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            FilterText(leagueName) { lOpen = true }
            DropdownMenu(lOpen, { lOpen = false }) {
                DropdownMenuItem(text = { Text("All leagues") }, onClick = { vm.clubFilter = "all"; lOpen = false })
                vm.leagues.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { vm.clubFilter = l.id; lOpen = false }) }
            }
        }
        Box {
            FilterText(sortName) { sOpen = true }
            DropdownMenu(sOpen, { sOpen = false }) {
                listOf("az" to "A–Z", "pts" to "Points", "lg" to "League").forEach { (k, t) ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { vm.clubSort = k; sOpen = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        listOf("list" to true, "grid" to false).forEach { (icon, isList) ->
            val on = asList == isList
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (on) u.sf else Color.Transparent)
                    .clickable { vm.update { it.copy(cg = if (isList) "list" else "g2") } },
                contentAlignment = Alignment.Center,
            ) { Ico(icon, 22.dp, if (on) u.ink else u.mu, 1.9f) }
        }
    }
}

/** Round "more" button that opens a small menu. */
@Composable
fun MoreMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        CircleBtn("more", { open = true })
        DropdownMenu(open, { open = false }) {
            items.forEach { (t, f) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; f() }) }
        }
    }
}

private class MenuRow(val icon: String, val label: String, val count: String, val go: () -> Unit)

/** Coral menu page: jump to any section. */
@Composable
fun MenuPage(vm: AppVM) {
    val u = LocalU.current
    CoralBar(
        left = { CircleBtn("close", { vm.goBack() }) },
        right = { Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF46C4E), Color(0xFFF9A24F))))) },
    )
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 14.dp)) {
        Hd("Menu", 38f, maxLines = 1)
        Tx("Jump to any section", 14 * u.fs, 500, u.mu)
    }
    val rows = listOf(
        MenuRow("shield", "Clubs", "${vm.clubs.size}") { vm.selectTab("clubs") },
        MenuRow("trophy", "Leagues", "${vm.leagues.size}") { vm.selectTab("leagues") },
        MenuRow("cal", "Fixtures", "${vm.fixtures.size}") { vm.selectTab("clubs"); vm.openFixtures(null) },
        MenuRow("compare", "Compare", "") { vm.selectTab("clubs"); vm.openCompare() },
        MenuRow("plus", "Add", "") { vm.selectTab("add") },
        MenuRow("bars", "Stats", "") { vm.selectTab("stats") },
        MenuRow("gear", "Settings", "") { vm.openSettings() },
    )
    Column(Modifier.padding(horizontal = 20.dp)) {
        rows.forEach { r ->
            Glass(Modifier.fillMaxWidth().padding(bottom = 12.dp), radius = 34.dp, pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp), onClick = r.go) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(u.fieldFill), contentAlignment = Alignment.Center) { Ico(r.icon, 22.dp, u.ink, 1.9f) }
                    Tx(r.label, 18 * u.fs, 500, modifier = Modifier.weight(1f))
                    if (r.count.isNotEmpty()) Tx(r.count, 15 * u.fs, 500, u.mu)
                }
            }
        }
        Tx("Matchbook ${vm.appVersion()}", 13 * u.fs, 500, u.mu, Modifier.fillMaxWidth().padding(top = 14.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}
