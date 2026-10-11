package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val POS_NAMES = listOf("GK" to "Goalkeeper", "DEF" to "Defender", "MID" to "Midfielder", "FWD" to "Forward")
private val POS_GROUPS = listOf("GK" to "Goalkeepers", "DEF" to "Defenders", "MID" to "Midfielders", "FWD" to "Forwards")

/** Round player picture: the render if there is one (top aligned, so the face shows), otherwise the shirt number. */
@Composable
fun PlayerAvatar(img: String, label: String, size: Dp, tint: Color? = null) {
    val u = LocalU.current
    val bmp = rememberBitmap(img)
    Box(
        Modifier.size(size).clip(CircleShape).background(tint ?: (if (u.coral) u.track else u.fieldFill)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else Tx(label, size.value * .36f, 800, if (tint != null) Color.White else u.ink, maxLines = 1)
    }
}

@Composable
fun SquadPage(vm: AppVM, cid: String) {
    val u = LocalU.current
    val c = vm.cl(cid) ?: return
    val all = vm.playersOf(cid)
    TopRow(vm.backLabel, { vm.goBack() }) { if (!u.coral) CircleBtn("plus", { vm.newPlayer(cid) }) }
    Tp("Squad", "${c.name} · ${all.size} players")
    Column(Modifier.bd(top = 16.dp)) {
        if (all.isEmpty()) EmptyBlock("No players in this squad yet.") { SmallBtn("Add a player") { vm.newPlayer(cid) } }
        POS_GROUPS.forEach { (k, title) ->
            val g = all.filter { it.pos == k }
            if (g.isNotEmpty()) {
                H3(title)
                Glass(Modifier.fillMaxWidth().padding(bottom = 6.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
                    g.forEachIndexed { i, p ->
                        KvRow(i == g.lastIndex, Modifier.clickable { vm.editPlayer(p.id) }) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                PlayerAvatar(p.img, if (p.num > 0) "${p.num}" else "?", 40.dp)
                                Column {
                                    Tx(p.name, 15 * u.fs, 700, maxLines = 1)
                                    Tx(POS_NAMES.firstOrNull { it.first == p.pos }?.second ?: p.pos, 12 * u.fs, 500, u.mu)
                                }
                            }
                            if (p.num > 0) Tx("#${p.num}", 14 * u.fs, 700, u.mu)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassBtn("Lineup", "pitch", Modifier.weight(1f)) { vm.openLineup(cid) }
            GlassBtn("Tactics", "route", Modifier.weight(1f)) { vm.openLineup(cid, true) }
        }
    }
}
