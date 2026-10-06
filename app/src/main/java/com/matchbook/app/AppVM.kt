package com.matchbook.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

val TN = mapOf("clubs" to "Clubs", "leagues" to "Leagues", "add" to "Add", "stats" to "Stats")

class Draft(val eid: String?, init: Map<String, String>) {
    private val m = mutableStateMapOf<String, String>().also { it.putAll(init) }
    operator fun get(k: String) = m[k] ?: ""
    operator fun set(k: String, v: String) { m[k] = v }
}

sealed interface Screen {
    data class ClubS(val id: String) : Screen
    data class LeagueS(val id: String) : Screen
    data class TableS(val id: String) : Screen
    data class FixturesS(val lid: String?) : Screen
    data object SettingsS : Screen
    data object CompareS : Screen
    data class FormL(val draft: Draft) : Screen
    data class FormC(val draft: Draft) : Screen
    data class FormF(val draft: Draft) : Screen
}

class AppVM(app: Application) : AndroidViewModel(app) {
    private val d0 = Store.load(app)
    var settings by mutableStateOf(d0.s); private set
    var leagues by mutableStateOf(d0.leagues); private set
    var clubs by mutableStateOf(d0.clubs); private set
    var fixtures by mutableStateOf(d0.fixtures); private set
    var pick by mutableStateOf(d0.pick); private set
    var tab by mutableStateOf("clubs"); private set

    private val stack = mutableStateListOf<Screen>()
    val screen: Screen? get() = stack.lastOrNull()
    val backLabel: String get() = if (stack.size >= 2) labelOf(stack[stack.size - 2]) else (TN[tab] ?: "Back")

    var statsLeague by mutableStateOf("all")
    var clubFilter by mutableStateOf("all")
    var st by mutableIntStateOf(1)
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var tableView by mutableStateOf("standings")
    var cmpA by mutableStateOf("")
    var cmpB by mutableStateOf("")

    var splashDone by mutableStateOf(false)
    var pending by mutableStateOf<PendingRestore?>(null)
    var msg by mutableStateOf<String?>(null)
    var hasPrev by mutableStateOf(Store.hasPrev(app))
    var lastBackup by mutableStateOf(Store.lastBackup(app))

    private fun snapshot() = Data(settings, leagues, clubs, fixtures, pick)
    private fun persist() = Store.save(getApplication(), snapshot())
    private fun uid() = UUID.randomUUID().toString().take(6)

    fun lg(id: String) = leagues.find { it.id == id }
    fun cl(id: String) = clubs.find { it.id == id }
    fun pts(c: Club) = c.w * (lg(c.lid)?.pw ?: 3) + c.d * (lg(c.lid)?.pd ?: 1)
    fun nm(c: Club) = if (settings.nm == "short") c.short.ifBlank { c.name.take(3).uppercase() } else c.name
    fun table(lid: String) = clubs.filter { it.lid == lid }
        .sortedWith(compareByDescending<Club> { pts(it) }.thenByDescending { it.gf - it.ga }.thenByDescending { it.gf })
    fun rank(c: Club) = table(c.lid).indexOf(c) + 1

    /** Colour of the last opened club, used for the glow on list pages. */
    fun pickColor() = (cl(pick) ?: clubs.firstOrNull())?.color ?: "#3b3b44"

    fun shownClubs(): List<Club> {
        val q = query.trim()
        return clubs.filter { c ->
            (clubFilter == "all" || c.lid == clubFilter) &&
                (q.isEmpty() || listOf(c.name, c.short, c.nick, c.city, c.stadium, lg(c.lid)?.name ?: "").any { it.contains(q, true) })
        }
    }

    fun shownLeagues(): List<League> {
        val q = query.trim()
        return if (q.isEmpty()) leagues else leagues.filter { l -> listOf(l.name, l.country, l.season).any { it.contains(q, true) } }
    }

    /** Earliest fixture that has not finished yet (kick-off up to 2 hours ago counts as still on). */
    fun nextFixture(lid: String? = null, club: String? = null): Fixture? {
        val from = System.currentTimeMillis() - 2 * 3_600_000L
        return fixtures.filter {
            it.at >= from && (lid == null || it.lid == lid) && (club == null || it.home == club || it.away == club)
        }.minByOrNull { it.at }
    }

    fun fixturesOf(lid: String?): List<Fixture> = fixtures.filter { lid == null || it.lid == lid }.sortedBy { it.at }

    fun labelOf(s: Screen): String = when (s) {
        is Screen.ClubS -> cl(s.id)?.name ?: "Club"
        is Screen.LeagueS -> lg(s.id)?.name ?: "League"
        is Screen.TableS -> "Table"
        is Screen.FixturesS -> "Fixtures"
        Screen.SettingsS -> "Settings"
        Screen.CompareS -> "Compare"
        is Screen.FormL, is Screen.FormC, is Screen.FormF -> "Edit"
    }

    // ---- settings
    fun update(f: (Settings) -> Settings) { settings = f(settings); persist() }
    fun set(k: String, v: String) = update { it.put(k, v) }
    fun toggleCol(k: String) = update { it.copy(cols = if (k in it.cols) it.cols - k else it.cols + k) }
    fun toggleField(k: String) = update { it.copy(fields = if (k in it.fields) it.fields - k else it.fields + k) }
    fun toggleBlock(k: String) = update { it.copy(blocks = if (k in it.blocks) it.blocks - k else it.blocks + k) }
    fun applyPreset(id: String) = update { it.preset(id) }
    fun reset() = update { Settings() }

    fun loadSample() {
        val b = seed(); leagues = b.leagues; clubs = b.clubs; fixtures = b.fixtures
        persist()
    }

    fun clearAll() {
        leagues = emptyList(); clubs = emptyList(); fixtures = emptyList(); pick = ""
        clubFilter = "all"; statsLeague = "all"; stack.clear(); persist()
    }

    // ---- navigation (back stack)
    fun selectTab(t: String) { tab = t; stack.clear(); searchOpen = false; query = "" }
    fun open(s: Screen) { if (stack.lastOrNull() != s) stack.add(s) }
    fun goBack() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun openSettings() { stack.clear(); stack.add(Screen.SettingsS) }
    fun openClub(id: String) { pick = id; persist(); open(Screen.ClubS(id)) }
    fun openLeague(id: String) = open(Screen.LeagueS(id))
    fun openTable(id: String) = open(Screen.TableS(id))
    fun openFixtures(lid: String? = null) = open(Screen.FixturesS(lid))
    fun openCompare(a: String? = null) {
        if (a != null) cmpA = a
        if (cmpA.isBlank() || cl(cmpA) == null) cmpA = clubs.firstOrNull()?.id ?: ""
        if (cmpB.isBlank() || cl(cmpB) == null || cmpB == cmpA) cmpB = clubs.firstOrNull { it.id != cmpA }?.id ?: ""
        open(Screen.CompareS)
    }
    private fun resetTo(s: Screen, t: String) { tab = t; stack.clear(); stack.add(s) }

    // ---- new / edit
    fun newLeague() = open(Screen.FormL(Draft(null, mapOf("color" to "#2da678", "pw" to "3", "pd" to "1", "promo" to "1", "rel" to "1"))))
    fun editLeague(id: String) { lg(id)?.let { open(Screen.FormL(Draft(id, it.toMap()))) } }
    fun newClub(lid: String? = null) = open(Screen.FormC(Draft(null, mapOf(
        "lid" to (lid ?: leagues.firstOrNull()?.id ?: ""), "color" to "#0ea5e9", "c2" to "#ffffff",
        "w" to "0", "d" to "0", "l" to "0", "gf" to "0", "ga" to "0", "cap" to "0", "titles" to "0"))))
    fun editClub(id: String) { cl(id)?.let { open(Screen.FormC(Draft(id, it.toMap()))) } }
    fun newFixture(lid: String? = null, club: String? = null) {
        val league = lid ?: club?.let { cl(it)?.lid } ?: leagues.firstOrNull()?.id ?: ""
        val home = club ?: ""
        val soon = (System.currentTimeMillis() / 3_600_000L + 24) * 3_600_000L
        open(Screen.FormF(Draft(null, mapOf("lid" to league, "home" to home, "away" to "", "at" to soon.toString(),
            "venue" to (cl(home)?.stadium ?: "")))))
    }
    fun editFixture(id: String) { fixtures.find { it.id == id }?.let { open(Screen.FormF(Draft(id, it.toMap()))) } }

    // ---- save / delete
    fun saveLeague(d: Draft): Boolean {
        if (d["name"].isBlank()) return false
        val id = d.eid ?: uid()
        val l = League(id, d["name"].trim(), d["country"], d["season"], d["color"], d["logo"], d["banner"],
            d["pw"].i(), d["pd"].i(), d["promo"].i(), d["rel"].i(),
            d["promoTo"].takeIf { it != id } ?: "", d["relTo"].takeIf { it != id } ?: "")
        leagues = if (d.eid != null) leagues.map { if (it.id == id) l else it } else leagues + l
        persist(); resetTo(Screen.LeagueS(id), "leagues")
        return true
    }

    fun saveClub(d: Draft): Boolean {
        if (d["name"].isBlank() || d["lid"].isBlank()) return false
        val id = d.eid ?: uid()
        val c = Club(id, d["lid"], d["name"].trim(), d["short"].ifBlank { d["name"].trim().take(3) }.uppercase().take(4),
            d["nick"], d["city"], d["founded"], d["stadium"], d["cap"].i(), d["mgr"], d["chair"], d["color"], d["c2"], d["logo"],
            d["titles"].i(), d["w"].i(), d["d"].i(), d["l"].i(), d["gf"].i(), d["ga"].i(), d["hon"], d["info"], d["logoBg"] == "1")
        clubs = if (d.eid != null) clubs.map { if (it.id == id) c else it } else clubs + c
        pick = id; persist(); resetTo(Screen.ClubS(id), "clubs")
        return true
    }

    /** Returns an error message, or null when saved. */
    fun saveFixture(d: Draft): String? {
        if (d["lid"].isBlank()) return "Pick a league first"
        if (d["home"].isBlank() || d["away"].isBlank()) return "Pick both clubs"
        if (d["home"] == d["away"]) return "Pick two different clubs"
        val at = d["at"].toLongOrNull() ?: return "Pick a date and time"
        val id = d.eid ?: uid()
        val f = Fixture(id, d["lid"], d["home"], d["away"], at, d["venue"].trim())
        fixtures = if (d.eid != null) fixtures.map { if (it.id == id) f else it } else fixtures + f
        persist(); goBack()
        return null
    }

    fun deleteFixture(id: String) { fixtures = fixtures.filter { it.id != id }; persist(); goBack() }

    fun deleteClub(id: String) {
        clubs = clubs.filter { it.id != id }
        fixtures = fixtures.filter { it.home != id && it.away != id }
        if (cmpA == id) cmpA = ""
        if (cmpB == id) cmpB = ""
        persist(); tab = "clubs"; stack.clear()
    }

    fun deleteLeague(id: String) {
        leagues = leagues.filter { it.id != id }.map {
            it.copy(promoTo = if (it.promoTo == id) "" else it.promoTo, relTo = if (it.relTo == id) "" else it.relTo)
        }
        clubs = clubs.filter { it.lid != id }
        fixtures = fixtures.filter { it.lid != id }
        if (clubFilter == id) clubFilter = "all"
        if (statsLeague == id) statsLeague = "all"
        persist(); tab = "leagues"; stack.clear()
    }

    // ---- backup and restore
    fun appVersion(): String {
        val ctx = getApplication<Application>()
        return runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    fun buildNumber(): Long {
        val ctx = getApplication<Application>()
        return runCatching { PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)) }.getOrDefault(0L)
    }

    fun createBackup(uri: Uri) {
        val ctx = getApplication<Application>()
        val snap = snapshot()
        val ver = appVersion()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.write(ctx, uri, snap, ver) }
            if (ok) { val t = System.currentTimeMillis(); Store.setLastBackup(ctx, t); lastBackup = t }
            msg = if (ok) "Backup saved" else "Couldn't save the backup"
        }
    }

    fun readBackup(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Backup.read(ctx, uri) }
            r.onSuccess { pending = it }.onFailure { msg = it.message ?: "Couldn't read that file" }
        }
    }

    fun cancelRestore() { pending = null }

    fun confirmRestore() {
        val p = pending ?: return
        val ctx = getApplication<Application>()
        Store.savePrev(ctx, snapshot()); hasPrev = true
        Backup.applyImages(ctx, p)
        applyData(p.data)
        pending = null
        msg = "Backup restored"
    }

    fun undoRestore() {
        val ctx = getApplication<Application>()
        val prev = Store.loadPrev(ctx) ?: return
        applyData(prev)
        Store.clearPrev(ctx); hasPrev = false
        msg = "Restore undone"
    }

    private fun applyData(d: Data) {
        settings = d.s; leagues = d.leagues; clubs = d.clubs; fixtures = d.fixtures; pick = d.pick
        clubFilter = "all"; statsLeague = "all"; cmpA = ""; cmpB = ""; stack.clear()
        persist()
    }
}
