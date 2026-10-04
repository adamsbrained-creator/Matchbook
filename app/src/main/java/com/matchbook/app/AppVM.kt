package com.matchbook.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
    data object SettingsS : Screen
    data class FormL(val draft: Draft) : Screen
    data class FormC(val draft: Draft) : Screen
}

class Back(val label: String, val to: Screen?)

class AppVM(app: Application) : AndroidViewModel(app) {
    private val d0 = Store.load(app)
    var settings by mutableStateOf(d0.s); private set
    var leagues by mutableStateOf(d0.leagues); private set
    var clubs by mutableStateOf(d0.clubs); private set
    var pick by mutableStateOf(d0.pick); private set
    var tab by mutableStateOf("clubs"); private set
    var screen by mutableStateOf<Screen?>(null); private set
    var back by mutableStateOf<Back?>(null); private set
    var statsLeague by mutableStateOf("all")
    var clubFilter by mutableStateOf("all")
    var st by mutableIntStateOf(1)
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")

    private fun persist() = Store.save(getApplication(), Data(settings, leagues, clubs, pick))
    private fun uid() = UUID.randomUUID().toString().take(6)
    private fun tb() = Back(TN[tab] ?: "", null)

    fun lg(id: String) = leagues.find { it.id == id }
    fun cl(id: String) = clubs.find { it.id == id }
    fun pts(c: Club) = c.w * (lg(c.lid)?.pw ?: 3) + c.d * (lg(c.lid)?.pd ?: 1)
    fun nm(c: Club) = if (settings.nm == "short") c.short.ifBlank { c.name.take(3).uppercase() } else c.name
    fun table(lid: String) = clubs.filter { it.lid == lid }
        .sortedWith(compareByDescending<Club> { pts(it) }.thenByDescending { it.gf - it.ga })
    fun rank(c: Club) = table(c.lid).indexOf(c) + 1

    /** Colour of the last opened club, used for list-page glow. */
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

    // ---- settings
    fun update(f: (Settings) -> Settings) { settings = f(settings); persist() }
    fun set(k: String, v: String) = update { it.put(k, v) }
    fun toggleCol(k: String) = update { it.copy(cols = if (k in it.cols) it.cols - k else it.cols + k) }
    fun toggleField(k: String) = update { it.copy(fields = if (k in it.fields) it.fields - k else it.fields + k) }
    fun applyPreset(id: String) = update { it.preset(id) }
    fun reset() = update { Settings() }
    fun loadSample() { val b = seed(); leagues = b.leagues; clubs = b.clubs; persist() }
    fun clearAll() { leagues = emptyList(); clubs = emptyList(); pick = ""; clubFilter = "all"; statsLeague = "all"; persist() }

    // ---- navigation
    fun selectTab(t: String) { tab = t; screen = null; back = null; searchOpen = false; query = "" }
    fun toggleSearch() { searchOpen = !searchOpen; if (!searchOpen) query = "" }
    fun openClub(id: String) {
        val cur = screen
        back = if (cur is Screen.LeagueS) Back(lg(cur.id)?.name ?: "", cur) else tb()
        screen = Screen.ClubS(id)
        pick = id; persist()
    }
    fun openLeague(id: String) { back = tb(); screen = Screen.LeagueS(id) }
    fun openSettings() { back = tb(); screen = Screen.SettingsS }
    fun goBack() {
        val to = back?.to
        screen = to
        back = if (to != null) tb() else null
    }

    fun newLeague() {
        back = tb()
        screen = Screen.FormL(Draft(null, mapOf("color" to "#2da678", "pw" to "3", "pd" to "1", "promo" to "1", "rel" to "1")))
    }
    fun editLeague(id: String) {
        val l = lg(id) ?: return
        back = Back(l.name, Screen.LeagueS(id)); screen = Screen.FormL(Draft(id, l.toMap()))
    }
    fun newClub(lid: String? = null) {
        back = lid?.let { id -> lg(id)?.let { Back(it.name, Screen.LeagueS(id)) } } ?: tb()
        screen = Screen.FormC(Draft(null, mapOf(
            "lid" to (lid ?: leagues.firstOrNull()?.id ?: ""), "color" to "#0ea5e9", "c2" to "#ffffff",
            "w" to "0", "d" to "0", "l" to "0", "gf" to "0", "ga" to "0", "cap" to "0", "titles" to "0")))
    }
    fun editClub(id: String) {
        val c = cl(id) ?: return
        back = Back(c.name, Screen.ClubS(id)); screen = Screen.FormC(Draft(id, c.toMap()))
    }

    // ---- save / delete
    fun saveLeague(d: Draft): Boolean {
        if (d["name"].isBlank()) return false
        val id = d.eid ?: uid()
        val l = League(id, d["name"].trim(), d["country"], d["season"], d["color"], d["logo"], d["banner"],
            d["pw"].i(), d["pd"].i(), d["promo"].i(), d["rel"].i())
        leagues = if (d.eid != null) leagues.map { if (it.id == id) l else it } else leagues + l
        persist(); tab = "leagues"; screen = Screen.LeagueS(id); back = Back("Leagues", null)
        return true
    }

    fun saveClub(d: Draft): Boolean {
        if (d["name"].isBlank() || d["lid"].isBlank()) return false
        val id = d.eid ?: uid()
        val c = Club(id, d["lid"], d["name"].trim(), d["short"].ifBlank { d["name"].trim().take(3) }.uppercase().take(4),
            d["nick"], d["city"], d["founded"], d["stadium"], d["cap"].i(), d["mgr"], d["chair"], d["color"], d["c2"], d["logo"],
            d["titles"].i(), d["w"].i(), d["d"].i(), d["l"].i(), d["gf"].i(), d["ga"].i(), d["hon"], d["info"], d["logoBg"] == "1")
        clubs = if (d.eid != null) clubs.map { if (it.id == id) c else it } else clubs + c
        pick = id; persist(); tab = "clubs"; screen = Screen.ClubS(id); back = Back("Clubs", null)
        return true
    }

    fun deleteClub(id: String) { clubs = clubs.filter { it.id != id }; persist(); tab = "clubs"; screen = null; back = null }
    fun deleteLeague(id: String) {
        leagues = leagues.filter { it.id != id }; clubs = clubs.filter { it.lid != id }
        if (clubFilter == id) clubFilter = "all"
        if (statsLeague == id) statsLeague = "all"
        persist(); tab = "leagues"; screen = null; back = null
    }
}
