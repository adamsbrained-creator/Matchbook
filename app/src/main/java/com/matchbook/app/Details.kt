package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================== club
private class Stat(val key: String, val label: String, val v: (Club) -> Int)

private val STATS = listOf(
    Stat("P", "Played") { it.played() },
    Stat("W", "Won") { it.w },
    Stat("D", "Drawn") { it.d },
    Stat("L", "Lost") { it.l },
    Stat("GF", "Goals for") { it.gf },
    Stat("GA", "Goals against") { it.ga },
    Stat("GD", "Goal difference") { it.gf - it.ga },
)

@Composable
fun InfoRow(label: String, last: Boolean, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    val u = LocalU.current
    KvRow(last, if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Tx(label, 14 * u.fs, 500, u.mu)
        Spacer(Modifier.width(14.dp))
        value()
    }
}

@Composable
fun ClubDetail(vm: AppVM, id: String) {
    val u = LocalU.current
    val h = LocalHdr.current
    val c = vm.cl(id) ?: return
    val l = vm.lg(c.lid)
    val B = u.s.blocks
    val F = u.s.fields
    val strip = STATS.filter { it.key in listOf("P", "W", "D", "L") || "k_stripx" in B }
    val sel = STATS.getOrNull(vm.st)?.takeIf { it in strip } ?: STATS[1]
    val sub = listOf(c.city, if (c.founded.isNotBlank()) "founded ${c.founded}" else "").filter { it.isNotBlank() }.joinToString(" · ")
    val colMain = hex(c.color)
    val next = vm.nextFixture(club = c.id)

    TopRow(vm.backLabel, { vm.goBack() }) { CircleBtn("edit", { vm.editClub(c.id) }) }
    Ctr(c.name, sub, 26f, "Rank ${ordinal(vm.rank(c))}") {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 88.dp, true, backdrop = c.logoBg)
    }

    if ("k_strip" in B) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            val n = strip.size
            val w = minOf(64.dp, (maxWidth - 4.dp * (n - 1)) / n)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                strip.forEach { s ->
                    val on = s === sel
                    Column(
                        Modifier.width(w).clip(RoundedCornerShape(26.dp)).background(if (on) h.chip else Color.Transparent)
                            .then(if (on) Modifier.border(1.dp, h.chipEdge, RoundedCornerShape(26.dp)) else Modifier)
                            .clickable { vm.st = STATS.indexOf(s) }.padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Tx(s.key, 13 * u.fs, 700, h.fg.copy(alpha = .9f))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) h.fg else Color.Transparent), contentAlignment = Alignment.Center) {
                            Tx("${s.v(c)}", 13 * u.fs, 800, if (on) (if (h.light) Color(0xFF111111) else Color.White) else h.fg)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Hd("${sel.v(c)}", 60f, h.fg, w = if (u.cond) 400 else 500)
            Tx(sel.label, 15 * u.fs, 600, h.fg.copy(alpha = .9f))
        }
    } else Spacer(Modifier.height(18.dp))

    val glow = if ("k_glow" in B) Modifier.drawBehind {
        drawRect(Brush.radialGradient(listOf(colMain.copy(alpha = .38f), Color.Transparent), center = Offset(size.width / 2f, size.height * .18f), radius = size.width * .8f))
    } else Modifier

    Column(Modifier.fillMaxWidth().then(glow).padding(horizontal = 16.dp)) {
        if ("k_info" in B) Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
            InfoRow("League", next == null, { l?.let { vm.openLeague(it.id) } }) { Tx(l?.name ?: "-", 14 * u.fs, 700, maxLines = 1, align = TextAlign.End, modifier = Modifier.weight(1f, false)) }
            InfoRow("Colours", next == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(colMain))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(hex(c.c2)))
                    Tx(c.color.lowercase(), 14 * u.fs, 700)
                }
            }
            if (next != null) {
                val home = next.home == c.id
                val opp = vm.cl(if (home) next.away else next.home)
                InfoRow("Next fixture", true, { vm.openFixtures(next.lid) }) {
                    Tx("${if (home) "vs" else "at"} ${opp?.let { vm.nm(it) } ?: "?"} · ${fmtFixture(next.at)}", 14 * u.fs, 700, align = TextAlign.End, modifier = Modifier.weight(1f, false), maxLines = 2)
                }
            }
        }
        if ("k_tiles" in B) TileGrid(listOf("Goals for" to "${c.gf}", "Against" to "${c.ga}", "Titles" to "${c.titles}"), if (u.s.tc == "2") 2 else 3)
        if ("k_tiles2" in B) TileGrid(listOf("Points" to "${vm.pts(c)}", "Played" to "${c.played()}", "Goal diff" to "${c.gf - c.ga}"), if (u.s.tc == "2") 2 else 3)
        if ("k_neighbours" in B) NeighboursCard(vm, c)
        if ("k_details" in B) DetailsCard(vm, c)
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun DetailsCard(vm: AppVM, c: Club) {
    val u = LocalU.current
    val F = u.s.fields
    val rows = listOfNotNull(
        if ("stadium" in F && c.stadium.isNotBlank()) "Stadium" to c.stadium else null,
        if ("nick" in F && c.nick.isNotBlank()) "Nickname" to c.nick else null,
        if ("mgr" in F && c.mgr.isNotBlank()) "Manager" to c.mgr else null,
        if ("chair" in F && c.chair.isNotBlank()) "Chairman" to c.chair else null,
        if ("cap" in F && c.cap > 0) "Capacity" to "%,d".format(c.cap) else null,
    )
    val hon = if ("hon" in F) c.hon else ""
    val about = if ("about" in F) c.info else ""
    if (rows.isEmpty() && hon.isBlank() && about.isBlank()) return
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
        rows.forEachIndexed { i, (k, v) ->
            KvRow(i == rows.lastIndex && hon.isBlank() && about.isBlank()) {
                Tx(k, 14 * u.fs, 500, u.mu)
                Spacer(Modifier.width(14.dp))
                Tx(v, 14 * u.fs, 700, modifier = Modifier.weight(1f, false), align = TextAlign.End)
            }
        }
        if (hon.isNotBlank()) TextSection("Honours", hon, about.isNotBlank())
        if (about.isNotBlank()) TextSection("About", about, false)
    }
}

@Composable
private fun TextSection(title: String, body: String, line: Boolean) {
    val u = LocalU.current
    Column(
        Modifier.fillMaxWidth()
            .then(if (line) Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) } else Modifier)
            .padding(vertical = 14.dp),
    ) {
        Tx(title, 14 * u.fs, 800)
        Tx(body, 14 * u.fs, 500, u.mu, Modifier.padding(top = 6.dp), lh = 14 * u.fs * 1.55f)
    }
}

@Composable
fun NeighboursCard(vm: AppVM, c: Club) {
    val u = LocalU.current
    val t = vm.table(c.lid)
    val i = t.indexOf(c)
    val above = t.getOrNull(i - 1)
    val below = t.getOrNull(i + 1)
    if (above == null && below == null) return
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Tx("Table neighbours", 12 * u.fs, 500, u.mu)
        listOfNotNull(above?.let { Triple("Above", it, vm.pts(it) - vm.pts(c)) }, below?.let { Triple("Below", it, vm.pts(c) - vm.pts(it)) })
            .forEachIndexed { k, (lab, o, gap) ->
                KvRow(k == (if (above != null && below != null) 1 else 0), Modifier.clickable { vm.openClub(o.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Crest(o.color, o.c2, o.logo, ab(o.name, o.short), 34.dp, backdrop = o.logoBg)
                        Column {
                            Tx(vm.nm(o), 14 * u.fs, 800, maxLines = 1)
                            Tx("$lab · ${ordinal(t.indexOf(o) + 1)}", 12 * u.fs, 500, u.mu)
                        }
                    }
                    Tx(if (gap == 0) "level" else "$gap pts", 14 * u.fs, 800, if (lab == "Above") u.red else u.acc)
                }
            }
    }
}

// =============================================================== tables
class TCol(val key: String, val label: String, val w: Dp, val v: (AppVM, Club) -> String)

private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

val TCOLS: Map<String, TCol> = listOf(
    TCol("p", "P", 36.dp) { _, c -> "${c.played()}" },
    TCol("w", "W", 36.dp) { _, c -> "${c.w}" },
    TCol("d", "D", 36.dp) { _, c -> "${c.d}" },
    TCol("l", "L", 36.dp) { _, c -> "${c.l}" },
    TCol("gf", "GF", 40.dp) { _, c -> "${c.gf}" },
    TCol("ga", "GA", 40.dp) { _, c -> "${c.ga}" },
    TCol("gd", "GD", 42.dp) { _, c -> signed(c.gf - c.ga) },
    TCol("pts", "Pts", 44.dp) { vm, c -> "${vm.pts(c)}" },
    TCol("agf", "GF/G", 52.dp) { _, c -> if (c.played() > 0) "%.1f".format(c.gf.toFloat() / c.played()) else "-" },
    TCol("aga", "GA/G", 52.dp) { _, c -> if (c.played() > 0) "%.1f".format(c.ga.toFloat() / c.played()) else "-" },
).associateBy { it.key }

@Composable
fun TableRow(vm: AppVM, rank: Int, c: Club, cols: List<TCol>, edge: Color?, last: Boolean, head: Boolean = false) {
    val u = LocalU.current
    val strong = cols.lastOrNull()?.key
    Row(
        Modifier.fillMaxWidth().height(if (head) 34.dp else 52.dp)
            .then(if (head || last) Modifier else Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .then(if (head) Modifier else Modifier.clickable { vm.openClub(c.id) })
            .drawBehind { if (edge != null) drawRect(edge, topLeft = Offset(0f, 10.dp.toPx()), size = Size(3.dp.toPx(), size.height - 20.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tx(if (head) "#" else "$rank", if (head) 12 * u.fs else 14 * u.fs, 800, if (head) u.mu else u.ink, Modifier.width(30.dp).padding(start = 8.dp), maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!head) Box(Modifier.size(10.dp).clip(CircleShape).background(hex(c.color)))
            Tx(if (head) "Club" else vm.nm(c), if (head) 12 * u.fs else 14 * u.fs, if (head) 700 else 600, if (head) u.mu else u.ink, maxLines = 1)
        }
        cols.forEach { col ->
            Tx(
                if (head) col.label else col.v(vm, c), if (head) 12 * u.fs else 14 * u.fs,
                if (head) 700 else if (col.key == strong) 800 else 500, if (head) u.mu else u.ink,
                Modifier.width(col.w), align = TextAlign.Center, maxLines = 1,
            )
        }
    }
}

/** League standings card. [limit] = 0 shows every club. */
@Composable
fun StandingsCard(vm: AppVM, l: League, limit: Int) {
    val u = LocalU.current
    val all = vm.table(l.id)
    val cs = if (limit > 0) all.take(limit) else all
    val keys = listOf("p") + u.s.cols.sortedBy { k -> ALL_COLS.indexOfFirst { it.first == k } } + "pts"
    val cols = keys.mapNotNull { TCOLS[it] }
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hd("Table", 20f, modifier = Modifier.weight(1f), w = if (u.cond) 400 else 800)
            Box(Modifier.clip(CircleShape).background(u.acc).clickable { vm.newClub(l.id) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Tx("Add club", 13 * u.fs, 800, u.on)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (cs.isEmpty()) Tx("No clubs in this league yet.", 14 * u.fs, 500, u.mu, Modifier.padding(vertical = 18.dp))
        else {
            TableRow(vm, 0, cs[0], cols, null, false, head = true)
            cs.forEachIndexed { i, c ->
                val edge = when {
                    i < l.promo -> u.acc
                    l.rel > 0 && i >= all.size - l.rel -> u.red
                    else -> null
                }
                TableRow(vm, i + 1, c, cols, edge, i == cs.lastIndex)
            }
        }
        if (limit > 0 && all.size > limit) Tx("Open full table", 13 * u.fs, 800, u.acc,
            Modifier.padding(top = 12.dp).clickable { vm.openTable(l.id) })
    }
}

private class TView(val key: String, val label: String, val cols: List<String>, val sort: (AppVM, Club) -> Int)

private val TVIEWS = listOf(
    TView("standings", "Standings", listOf("p", "w", "d", "l", "gf", "ga", "gd", "pts")) { vm, c -> -vm.pts(c) },
    TView("attack", "Attack", listOf("p", "gf", "agf")) { _, c -> -c.gf },
    TView("defence", "Defence", listOf("p", "ga", "aga")) { _, c -> c.ga },
    TView("wins", "Wins", listOf("p", "w", "d", "l")) { _, c -> -c.w },
    TView("gd", "Goal diff", listOf("gf", "ga", "gd")) { _, c -> -(c.gf - c.ga) },
)

/** Full-screen league table with several views (standings, attack, defence, wins, goal difference). */
@Composable
fun TablePage(vm: AppVM, lid: String) {
    val u = LocalU.current
    val l = vm.lg(lid) ?: return
    val view = TVIEWS.find { it.key == vm.tableView } ?: TVIEWS[0]
    TopRow(vm.backLabel, { vm.goBack() })
    Tp("Table", "${flag(l.country)}${l.name}")
    HChips(TVIEWS.map { it.key to it.label }, view.key) { vm.tableView = it }
    val all = vm.table(lid)
    val cs = if (view.key == "standings") all else all.sortedBy { view.sort(vm, it) }
    val cols = view.cols.mapNotNull { TCOLS[it] }
    Column(Modifier.bd(top = 16.dp)) {
        Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
            if (cs.isEmpty()) Tx("No clubs in this league yet.", 14 * u.fs, 500, u.mu, Modifier.padding(vertical = 18.dp))
            else BoxWithConstraints(Modifier.fillMaxWidth()) {
                val need = 160.dp + cols.fold(0.dp) { a, c -> a + c.w }
                val w = maxOf(maxWidth, need)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(w)) {
                        TableRow(vm, 0, cs[0], cols, null, false, head = true)
                        cs.forEachIndexed { i, c ->
                            val edge = if (view.key != "standings") null else when {
                                i < l.promo -> u.acc
                                l.rel > 0 && i >= all.size - l.rel -> u.red
                                else -> null
                            }
                            TableRow(vm, i + 1, c, cols, edge, i == cs.lastIndex)
                        }
                    }
                }
            }
        }
        if (view.key == "standings") ZonesCard(vm, l)
        GlassBtn("League page", "trophy", Modifier.fillMaxWidth()) { vm.openLeague(lid) }
    }
}

@Composable
fun ZonesCard(vm: AppVM, l: League) {
    val u = LocalU.current
    if (l.promo <= 0 && l.rel <= 0) return
    val up = vm.lg(l.promoTo)
    val down = vm.lg(l.relTo)
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
        if (l.promo > 0) KvRow(l.rel <= 0, if (up != null) Modifier.clickable { vm.openLeague(up.id) } else Modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(u.acc))
                Tx("Top ${l.promo} · promotion", 14 * u.fs, 600)
            }
            Tx(up?.let { "to ${it.name}" } ?: "no league set", 13 * u.fs, 700, if (up != null) u.acc else u.mu, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
        }
        if (l.rel > 0) KvRow(true, if (down != null) Modifier.clickable { vm.openLeague(down.id) } else Modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(u.red))
                Tx("Bottom ${l.rel} · relegation", 14 * u.fs, 600)
            }
            Tx(down?.let { "to ${it.name}" } ?: "no league set", 13 * u.fs, 700, if (down != null) u.red else u.mu, maxLines = 1, modifier = Modifier.widthIn(max = 160.dp))
        }
    }
}

// =============================================================== league
@Composable
fun LinksCard(vm: AppVM, l: League) {
    val u = LocalU.current
    val up = vm.lg(l.promoTo)
    val down = vm.lg(l.relTo)
    val fromBelow = vm.leagues.filter { it.promoTo == l.id }
    val fromAbove = vm.leagues.filter { it.relTo == l.id }
    class Row3(val icon: String, val tint: Color, val label: String, val ids: List<League>)
    val rows = listOfNotNull(
        if (up != null) Row3("up", u.acc, "Promotion to", listOf(up)) else null,
        if (down != null) Row3("down", u.red, "Relegation to", listOf(down)) else null,
        if (fromBelow.isNotEmpty()) Row3("up", u.acc, "Promoted from", fromBelow) else null,
        if (fromAbove.isNotEmpty()) Row3("down", u.red, "Relegated from", fromAbove) else null,
    )
    if (rows.isEmpty()) return
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Tx("League links", 12 * u.fs, 500, u.mu)
        rows.forEachIndexed { i, r ->
            KvRow(i == rows.lastIndex) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(r.icon, 18.dp, r.tint, 2.2f)
                    Tx(r.label, 14 * u.fs, 500, u.mu)
                }
                Row(Modifier.weight(1f, false).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.ids.forEach { o ->
                        Tx(o.name, 14 * u.fs, 800, modifier = Modifier.clickable { vm.openLeague(o.id) }, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderTiles(vm: AppVM, l: League) {
    val u = LocalU.current
    val cs = vm.clubs.filter { it.lid == l.id }
    if (cs.isEmpty()) return
    val atk = cs.maxByOrNull { it.gf }
    val def = cs.minByOrNull { it.ga }
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("Best attack" to atk, "Best defence" to def).forEach { (t, c) ->
            Column(Modifier.weight(1f).fillMaxHeight().glass().clickable { c?.let { vm.openClub(it.id) } }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tx(t, 12 * u.fs, 500, u.mu)
                if (c != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 34.dp, backdrop = c.logoBg)
                    Column {
                        Tx(vm.nm(c), 14 * u.fs, 800, maxLines = 1)
                        Tx(if (t == "Best attack") "${c.gf} goals" else "${c.ga} conceded", 12 * u.fs, 500, u.mu)
                    }
                }
            }
        }
    }
}

@Composable
fun LeagueDetail(vm: AppVM, id: String) {
    val u = LocalU.current
    val l = vm.lg(id) ?: return
    val B = u.s.blocks
    val n = vm.clubs.count { it.lid == id }
    val sub = listOf(listOf(l.country, l.season).filter { it.isNotBlank() }.joinToString(" · "), "$n clubs").filter { it.isNotBlank() }.joinToString(" · ")
    TopRow(vm.backLabel, { vm.goBack() }) { CircleBtn("edit", { vm.editLeague(l.id) }) }
    Ctr(l.name, sub.let { flag(l.country) + it }, 24f) { Crest(l.color, "", l.logo, ab(l.name, ""), 76.dp, true) }
    val banner = rememberBitmap(l.banner)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        if (banner != null) Image(banner, null, Modifier.fillMaxWidth().height(96.dp).padding(bottom = 14.dp).clip(RoundedCornerShape(minOf(u.r, 24.dp))), contentScale = ContentScale.Crop)
        if ("g_table" in B) StandingsCard(vm, l, 5)
        if ("g_rules" in B) TileGrid(listOf("Points per win" to "${l.pw}", "Points per draw" to "${l.pd}", "Promotion spots" to "${l.promo}", "Relegation spots" to "${l.rel}"), 2)
        if ("g_fixture" in B) vm.nextFixture(lid = id)?.let { NextFixtureCard(vm, it) }
        if ("g_links" in B) LinksCard(vm, l)
        if ("g_leaders" in B) LeaderTiles(vm, l)
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassBtn("Full table", "bars", Modifier.weight(1f)) { vm.openTable(id) }
            GlassBtn("Fixtures", "cal", Modifier.weight(1f)) { vm.openFixtures(id) }
        }
        Spacer(Modifier.height(120.dp))
    }
}

// =============================================================== compare
@Composable
fun ComparePage(vm: AppVM) {
    val u = LocalU.current
    TopRow(vm.backLabel, { vm.goBack() })
    Tp("Compare", "Pick two clubs")
    val a = vm.cl(vm.cmpA)
    val b = vm.cl(vm.cmpB)
    val opts = vm.clubs.map { it.id to it.name }
    Column(Modifier.bd(top = 8.dp)) {
        if (vm.clubs.size < 2) {
            EmptyBlock("You need at least two clubs to compare.") { SmallBtn("Add a club") { vm.newClub() } }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DropSelect("Club A", a?.name ?: "", opts, { vm.cmpA = it }, Modifier.weight(1f))
            DropSelect("Club B", b?.name ?: "", opts, { vm.cmpB = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (a != null && b != null) {
            Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for ((idx, c) in listOf(a, b).withIndex()) {
                        Column(Modifier.weight(1f).clickable { vm.openClub(c.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 56.dp, backdrop = c.logoBg)
                            Tx(vm.nm(c), 14 * u.fs, 800, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp), maxLines = 2)
                        }
                        if (idx == 0) Tx("vs", 13 * u.fs, 700, u.mu)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    Triple("Points", vm.pts(a) to vm.pts(b), true), Triple("Played", a.played() to b.played(), true),
                    Triple("Won", a.w to b.w, true), Triple("Drawn", a.d to b.d, true), Triple("Lost", a.l to b.l, false),
                    Triple("Goals for", a.gf to b.gf, true), Triple("Goals against", a.ga to b.ga, false),
                    Triple("Goal difference", (a.gf - a.ga) to (b.gf - b.ga), true), Triple("Titles", a.titles to b.titles, true),
                )
                rows.forEachIndexed { i, (label, v, higher) ->
                    val (x, y) = v
                    val aWin = if (higher) x > y else x < y
                    val bWin = if (higher) y > x else y < x
                    KvRow(i == rows.lastIndex) {
                        Tx("$x", 16 * u.fs, if (aWin) 800 else 500, if (aWin) u.acc else u.ink, Modifier.weight(1f))
                        Tx(label, 13 * u.fs, 500, u.mu, align = TextAlign.Center, modifier = Modifier.weight(1.4f))
                        Tx("$y", 16 * u.fs, if (bWin) 800 else 500, if (bWin) u.acc else u.ink, Modifier.weight(1f), align = TextAlign.End)
                    }
                }
            }
        }
    }
}

// =============================================================== fixtures
@Composable
fun FixtureRow(vm: AppVM, f: Fixture, showLeague: Boolean) {
    val u = LocalU.current
    val h = vm.cl(f.home)
    val a = vm.cl(f.away)
    Glass(
        Modifier.fillMaxWidth().padding(bottom = 12.dp), radius = minOf(u.r, 26.dp),
        pad = PaddingValues(horizontal = 14.dp, vertical = 14.dp), onClick = { vm.editFixture(f.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FixCrest(h)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Tx(fmtFixture(f.at), 16 * u.fs, 800)
                Tx("${h?.let { vm.nm(it) } ?: "?"} vs ${a?.let { vm.nm(it) } ?: "?"}", 13 * u.fs, 600, align = TextAlign.Center, maxLines = 2)
                val extra = listOf(f.venue, if (showLeague) vm.lg(f.lid)?.name ?: "" else "").filter { it.isNotBlank() }.joinToString(" · ")
                if (extra.isNotBlank()) Tx(extra, 12 * u.fs, 500, u.mu, align = TextAlign.Center, maxLines = 1)
            }
            FixCrest(a)
        }
    }
}

@Composable
fun FixturesPage(vm: AppVM, lid: String?) {
    val u = LocalU.current
    val l = lid?.let { vm.lg(it) }
    TopRow(vm.backLabel, { vm.goBack() }) { CircleBtn("plus", { vm.newFixture(lid) }) }
    Tp("Fixtures", l?.name ?: "All leagues")
    val now = System.currentTimeMillis() - 2 * 3_600_000L
    val all = vm.fixturesOf(lid)
    val upcoming = all.filter { it.at >= now }
    val past = all.filter { it.at < now }.sortedByDescending { it.at }
    Column(Modifier.bd(top = 18.dp)) {
        if (all.isEmpty()) EmptyBlock("No fixtures yet.") { SmallBtn("Add a fixture") { vm.newFixture(lid) } }
        if (upcoming.isNotEmpty()) {
            Tx("Upcoming", 15 * u.fs, 800, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            upcoming.forEach { FixtureRow(vm, it, lid == null) }
        }
        if (past.isNotEmpty()) {
            Tx("Past", 15 * u.fs, 800, modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 10.dp))
            past.forEach { FixtureRow(vm, it, lid == null) }
        }
    }
}
