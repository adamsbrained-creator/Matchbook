package com.matchbook.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

// =====================================================================================================
// Online catalog (2.6): player names, renders, dated photos and recent stats from free sources.
//   TheSportsDB        names, positions, cut-outs / renders / photos (free test key)
//   Wikimedia Commons  dated photos, so one player gets a catalog by year (free licences)
//   Fantasy Premier League  official PL render + this season / last matches / past seasons (Premier League only)
// Nothing here is stored except the picture you choose, which goes through the normal crop dialog and
// ImageStore, so it lives in the backup ZIP like any uploaded image.
// =====================================================================================================

data class OlRender(val url: String, val label: String, val sub: String = "", val big: String = url)

data class OlPlayer(
    val name: String, val team: String, val pos: String, val nationality: String, val born: String,
    val renders: List<OlRender>,
)

data class OlStats(
    val who: String, val team: String, val pos: String, val photo: String,
    val season: List<Pair<String, String>>, val recent: List<String>, val past: List<String>,
)

sealed interface OlState<out T> {
    data object Busy : OlState<Nothing>
    data class Ok<T>(val v: T) : OlState<T>
    data class Err(val msg: String) : OlState<Nothing>
}

/** Maps free-text positions ("Centre-Back", "Attacking Midfield", "Left Winger") to GK / DEF / MID / FWD, or "" if unknown. */
fun olPos(p: String): String {
    val s = p.lowercase()
    return when {
        "keeper" in s || s == "gk" -> "GK"
        "back" in s || "defen" in s || "sweeper" in s -> "DEF"
        "mid" in s -> "MID"
        "forward" in s || "striker" in s || "wing" in s || "attack" in s -> "FWD"
        else -> ""
    }
}

private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "").trim()

private fun olMsg(e: Exception): String = when {
    e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
        "Can't reach the server. Check your connection and try again."
    e.message?.startsWith("HTTP 429") == true -> "The free service is rate limiting. Wait a minute and try again."
    else -> e.message ?: "Something went wrong."
}

// ------------------------------------------------------------------------------------------ network
object OlNet {
    private const val UA = "Matchbook/2.5 (personal football app)"

    fun bytes(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "*/*")
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun json(url: String): JSONObject = JSONObject(String(bytes(url), Charsets.UTF_8))
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

object OlImages {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String, maxSide: Int, useCache: Boolean = true): Bitmap? {
        val key = "$maxSide|$url"
        if (useCache) cache.get(key)?.let { return it }
        val data = runCatching { OlNet.bytes(url) }.getOrNull() ?: return null
        val bmp = decode(data, maxSide) ?: return null
        if (useCache) cache.put(key, bmp)
        return bmp
    }

    private fun decode(data: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}

// ------------------------------------------------------------------------------------- TheSportsDB
object OlSportsDb {
    private const val KEY = "3" // free test key; limited, but fine for one-player-at-a-time lookups

    fun search(q: String): List<OlPlayer> {
        val j = OlNet.json("https://www.thesportsdb.com/api/v1/json/$KEY/searchplayers.php?p=${OlNet.enc(q)}")
        val arr = j.optJSONArray("player") ?: return emptyList()
        val out = ArrayList<OlPlayer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sport = o.s("strSport")
            if (sport.isNotEmpty() && !sport.equals("Soccer", true)) continue
            val rs = ArrayList<OlRender>()
            fun add(k: String, label: String) {
                val u = o.s(k).replace("http://", "https://")
                if (u.startsWith("https://")) rs.add(OlRender(u, label, "TheSportsDB"))
            }
            add("strCutout", "Cut-out")
            add("strRender", "Render")
            add("strThumb", "Photo")
            for (n in 1..4) add("strFanart$n", "Fan art $n")
            out.add(
                OlPlayer(
                    o.s("strPlayer"), o.s("strTeam"), o.s("strPosition"), o.s("strNationality"), o.s("dateBorn").take(10),
                    rs.distinctBy { it.url },
                ),
            )
        }
        return out.filter { it.name.isNotBlank() }
    }
}

// --------------------------------------------------------------------------------- Wikimedia Commons
object OlCommons {
    private class Hit(val year: Int, val idx: Int, val r: OlRender)

    /** Photos of a person from Commons, newest first, each labelled with the year it was taken. */
    fun photos(name: String): List<OlRender> {
        val q = "\"" + name.replace("\"", "") + "\" filetype:bitmap"
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrsearch=${OlNet.enc(q)}&gsrnamespace=6&gsrlimit=40&prop=imageinfo" +
            "&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiextmetadatafilter=DateTimeOriginal%7CDateTime%7CLicenseShortName" +
            "&iiurlwidth=500"
        val pages = OlNet.json(url).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val hits = ArrayList<Hit>()
        for (k in pages.keys()) {
            val p = pages.getJSONObject(k)
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = ii.s("mime")
            if (mime != "image/jpeg" && mime != "image/png") continue
            if (ii.optInt("width") < 400) continue
            val thumb = ii.s("thumburl").ifBlank { ii.s("url") }
            if (thumb.isBlank()) continue
            val meta = ii.optJSONObject("extmetadata")
            val date = (meta?.optJSONObject("DateTimeOriginal")?.s("value") ?: "")
                .ifBlank { meta?.optJSONObject("DateTime")?.s("value") ?: "" }
            val year = Regex("(19|20)\\d{2}").find(date)?.value ?: ""
            val lic = meta?.optJSONObject("LicenseShortName")?.s("value") ?: ""
            hits.add(
                Hit(
                    year.toIntOrNull() ?: 0, p.optInt("index", 999),
                    OlRender(thumb, year.ifEmpty { "Photo" }, lic, thumb.replace("/500px-", "/960px-")),
                ),
            )
        }
        return hits.sortedWith(compareByDescending<Hit> { it.year }.thenBy { it.idx }).take(30).map { it.r }
    }
}

// ------------------------------------------------------------------------------ Fantasy Premier League
object OlFpl {
    private const val BASE = "https://fantasy.premierleague.com/api"
    @Volatile private var boot: JSONObject? = null
    @Volatile private var bootAt = 0L

    private fun bootstrap(): JSONObject {
        val now = System.currentTimeMillis()
        boot?.let { if (now - bootAt < 30 * 60_000L) return it }
        val j = OlNet.json("$BASE/bootstrap-static/")
        boot = j
        bootAt = now
        return j
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")

    /** Premier League only. Returns null when the player isn't in the FPL data. */
    fun lookup(name: String): OlStats? {
        val b = bootstrap()
        val q = norm(name)
        if (q.isBlank()) return null
        val qt = q.split(" ")
        val els = b.getJSONArray("elements")
        var best: JSONObject? = null
        var bestKey = 0L
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val full = norm(e.s("first_name") + " " + e.s("second_name"))
            val web = norm(e.s("web_name"))
            val toks = (full + " " + web).split(" ").toSet()
            val sc = when {
                full == q -> 100
                web == q -> 90
                qt.all { it in toks } -> 70 - (full.split(" ").size - qt.size).coerceAtLeast(0)
                else -> 0
            }
            if (sc == 0) continue
            val key = sc * 100_000L + e.optInt("minutes")
            if (key > bestKey) { bestKey = key; best = e }
        }
        val e = best ?: return null

        val teams = b.getJSONArray("teams")
        val short = HashMap<Int, String>()
        val tname = HashMap<Int, String>()
        for (i in 0 until teams.length()) {
            val t = teams.getJSONObject(i)
            short[t.optInt("id")] = t.s("short_name")
            tname[t.optInt("id")] = t.s("name")
        }
        val pos = when (e.optInt("element_type")) { 1 -> "GK"; 2 -> "DEF"; 3 -> "MID"; else -> "FWD" }

        val season = ArrayList<Pair<String, String>>()
        season.add("Minutes" to e.optInt("minutes").toString())
        if (e.has("starts")) season.add("Starts" to e.optInt("starts").toString())
        season.add("Goals" to e.optInt("goals_scored").toString())
        season.add("Assists" to e.optInt("assists").toString())
        if (pos == "GK" || pos == "DEF") season.add("Clean sheets" to e.optInt("clean_sheets").toString())
        if (pos == "GK") season.add("Saves" to e.optInt("saves").toString())
        season.add("Yellow cards" to e.optInt("yellow_cards").toString())
        season.add("Red cards" to e.optInt("red_cards").toString())
        season.add("Bonus points" to e.optInt("bonus").toString())
        season.add("FPL points" to e.optInt("total_points").toString())
        if (e.s("form").isNotBlank()) season.add("Form" to e.s("form"))

        var recent: List<String> = emptyList()
        var past: List<String> = emptyList()
        runCatching {
            val sum = OlNet.json("$BASE/element-summary/${e.optInt("id")}/")
            val h = sum.optJSONArray("history") ?: JSONArray()
            recent = (0 until h.length()).map { h.getJSONObject(it) }.takeLast(5).reversed().map { r ->
                val home = r.optBoolean("was_home")
                val own = if (home) r.optInt("team_h_score") else r.optInt("team_a_score")
                val opp = if (home) r.optInt("team_a_score") else r.optInt("team_h_score")
                "GW${r.optInt("round")} v ${short[r.optInt("opponent_team")] ?: "?"} (${if (home) "H" else "A"}) $own-$opp" +
                    " · ${r.optInt("minutes")}' · ${r.optInt("goals_scored")}G ${r.optInt("assists")}A · ${r.optInt("total_points")} pts"
            }
            val hp = sum.optJSONArray("history_past") ?: JSONArray()
            past = (0 until hp.length()).map { hp.getJSONObject(it) }.takeLast(3).reversed().map { r ->
                "${r.s("season_name")} · ${r.optInt("minutes")}' · ${r.optInt("goals_scored")}G ${r.optInt("assists")}A · ${r.optInt("total_points")} pts"
            }
        }

        val code = e.optInt("code")
        val photo = if (code > 0) "https://resources.premierleague.com/premierleague/photos/players/250x250/p$code.png" else ""
        val who = (e.s("first_name") + " " + e.s("second_name")).trim()
        return OlStats(who, tname[e.optInt("team")] ?: "", pos, photo, season, recent, past)
    }
}

// ================================================================================================ UI
@Composable
fun <T> olLoad(key: Any?, block: () -> T): OlState<T> {
    val st = produceState<OlState<T>>(OlState.Busy, key) {
        value = OlState.Busy
        value = withContext(Dispatchers.IO) {
            val r: OlState<T> = try {
                OlState.Ok(block())
            } catch (e: Exception) {
                OlState.Err(olMsg(e))
            }
            r
        }
    }
    return st.value
}

/** A picture from a URL, downloaded once and kept in a small memory cache. */
@Composable
fun OlThumb(url: String, modifier: Modifier = Modifier, round: Boolean = false) {
    val u = LocalU.current
    val st = produceState<Any?>(null, url) { value = withContext(Dispatchers.IO) { OlImages.load(url, 400) ?: false } }
    val shape: Shape = if (round) CircleShape else u.rs(10)
    Box(modifier.clip(shape).background(u.fieldFill), contentAlignment = Alignment.Center) {
        when (val v = st.value) {
            is Bitmap -> Image(
                v.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = if (round) ContentScale.Crop else ContentScale.Fit,
                alignment = if (round) Alignment.TopCenter else Alignment.Center,
            )
            is Boolean -> Tx("-", 16f, 700, u.mu)
            else -> {}
        }
    }
}

/** Button + sheet. [onPlayer] gets the chosen name and position (GK/DEF/MID/FWD or ""), [onImage] gets a saved image path. */
@Composable
fun OnlineLookup(
    name: String,
    onPlayer: (String, String) -> Unit,
    onImage: (String) -> Unit,
) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Lbl("Online catalog")
    SmallBtn("Find online", "s") { open = true }
    Tx(
        "Names, renders, photos by year and recent stats from free sources.", 12 * u.fs, 500, u.mu,
        Modifier.padding(start = 4.dp, top = 8.dp),
    )
    if (open) OnlineSheet(name, onPlayer, onImage) { open = false }
}

@Composable
fun OnlineSheet(initial: String, onPlayer: (String, String) -> Unit, onImage: (String) -> Unit, onClose: () -> Unit) {
    val u = LocalU.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<OlPlayer?>(null) }
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    fun choose(r: OlRender) {
        if (busy) return
        busy = true
        note = ""
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { OlImages.load(r.big, 2048, useCache = false) }
            busy = false
            if (bmp == null) note = "Couldn't download that image." else pending = bmp
        }
    }

    val p = picked
    Dialog(
        onDismissRequest = { if (picked != null) picked = null else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(u.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hd(if (p == null) "Find online" else p.name, 24f, modifier = Modifier.weight(1f), maxLines = 1)
                Chip(if (p == null) "Close" else "Back", false) { if (p == null) onClose() else picked = null }
            }
            if (busy) Tx("Downloading...", 13 * u.fs, 600, u.mu, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (note.isNotBlank()) Tx(note, 13 * u.fs, 600, u.red, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (p == null) {
                OlSearchPane(initial) { found ->
                    picked = found
                    onPlayer(found.name, olPos(found.pos))
                }
            } else {
                OlDetailPane(p, ::choose)
            }
        }
    }

    pending?.let { bmp ->
        ImageEditor(
            bmp, 1f,
            onDone = { out ->
                pending = null
                scope.launch {
                    val path = withContext(Dispatchers.IO) { ImageStore.save(ctx, out) }
                    onImage(path)
                    onClose()
                }
            },
            onCancel = { pending = null },
        )
    }
}

@Composable
private fun ColumnScope.OlSearchPane(initial: String, onFound: (OlPlayer) -> Unit) {
    val u = LocalU.current
    var q by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf(initial.trim()) }
    var tick by remember { mutableIntStateOf(0) }
    val res = olLoad<List<OlPlayer>>(query to tick) { if (query.isBlank()) emptyList() else OlSportsDb.search(query) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Fld("Player name", q, { q = it }, ph = "e.g. Lionel Messi")
        SmallBtn("Search") { query = q.trim(); tick++ }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 40.dp)) {
        when (val r = res) {
            OlState.Busy -> item { Tx("Searching...", 14 * u.fs, 500, u.mu, Modifier.padding(vertical = 12.dp)) }
            is OlState.Err -> item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Tx(r.msg, 14 * u.fs, 600, u.red)
                    SmallBtn("Try again", "s") { tick++ }
                }
            }
            is OlState.Ok -> {
                if (query.isBlank()) {
                    item { Tx("Type a name and press Search.", 14 * u.fs, 500, u.mu, Modifier.padding(vertical = 12.dp)) }
                } else if (r.v.isEmpty()) {
                    item { Tx("Nothing found on TheSportsDB for \"$query\".", 14 * u.fs, 500, u.mu, Modifier.padding(vertical = 12.dp)) }
                }
                items(r.v) { p0 ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onFound(p0) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val face = p0.renders.firstOrNull { it.label == "Photo" } ?: p0.renders.firstOrNull()
                        if (face != null) OlThumb(face.url, Modifier.size(46.dp), round = true)
                        else Box(Modifier.size(46.dp).clip(CircleShape).background(u.fieldFill))
                        Column(Modifier.weight(1f)) {
                            Tx(p0.name, 15 * u.fs, 700, maxLines = 1)
                            Tx(
                                listOf(p0.team, p0.pos, p0.nationality).filter { it.isNotBlank() }.joinToString(" · "),
                                12 * u.fs, 500, u.mu, maxLines = 1,
                            )
                        }
                        if (p0.born.length >= 4) Tx(p0.born.take(4), 12 * u.fs, 600, u.mu)
                    }
                }
            }
        }
        if (query.isNotBlank()) item {
            SmallBtn("Browse photos and stats for \"$query\" anyway", "s") { onFound(OlPlayer(query, "", "", "", "", emptyList())) }
        }
    }
}

private fun LazyListScope.olGrid(list: List<OlRender>, onPick: (OlRender) -> Unit) {
    items(list.chunked(3)) { row ->
        val u = LocalU.current
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { r ->
                Column(Modifier.weight(1f).clickable { onPick(r) }) {
                    OlThumb(r.url, Modifier.fillMaxWidth().aspectRatio(1f))
                    Tx(r.label, 12 * u.fs, 700, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    if (r.sub.isNotBlank()) Tx(r.sub, 11 * u.fs, 500, u.mu, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ColumnScope.OlDetailPane(p: OlPlayer, onPick: (OlRender) -> Unit) {
    val u = LocalU.current
    val stats = olLoad<OlStats?>("s:" + p.name) { OlFpl.lookup(p.name) }
    val photos = olLoad<List<OlRender>>("c:" + p.name) { OlCommons.photos(p.name) }
    val official = (stats as? OlState.Ok<OlStats?>)?.v?.photo?.takeIf { it.isNotBlank() }?.let { OlRender(it, "Official", "Premier League") }
    val renders = (p.renders + listOfNotNull(official)).distinctBy { it.url }
    val tight = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)

    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 40.dp)) {
        val sub = listOf(p.team, p.pos, p.nationality, if (p.born.isNotBlank()) "b. ${p.born}" else "").filter { it.isNotBlank() }
        if (sub.isNotEmpty()) item { Tx(sub.joinToString(" · "), 13 * u.fs, 500, u.mu, tight) }
        item { Tx("Tap a picture to crop it and use it as the render.", 13 * u.fs, 500, u.mu, tight) }

        item { H3("Renders and cut-outs", 4.dp) }
        if (renders.isEmpty()) {
            item {
                val wait = stats is OlState.Busy
                Tx(
                    if (wait) "Looking for renders..." else "No renders for this player on TheSportsDB.",
                    13 * u.fs, 500, u.mu, tight,
                )
            }
        } else olGrid(renders, onPick)

        item { H3("Photos by year", 4.dp) }
        item { Tx("Real photos from Wikimedia Commons (free licences), newest first. Not cut-outs.", 12 * u.fs, 500, u.mu, tight) }
        when (val r = photos) {
            OlState.Busy -> item { Tx("Loading photos...", 13 * u.fs, 500, u.mu, tight) }
            is OlState.Err -> item { Tx(r.msg, 13 * u.fs, 600, u.red, tight) }
            is OlState.Ok -> if (r.v.isEmpty()) item { Tx("No photos found for this name.", 13 * u.fs, 500, u.mu, tight) } else olGrid(r.v, onPick)
        }

        item { H3("Recent stats", 4.dp) }
        when (val r = stats) {
            OlState.Busy -> item { Tx("Loading stats...", 13 * u.fs, 500, u.mu, tight) }
            is OlState.Err -> item { Tx(r.msg, 13 * u.fs, 600, u.red, tight) }
            is OlState.Ok -> {
                val s = r.v
                if (s == null) {
                    item {
                        Tx(
                            "No stats found. The free stats source (Fantasy Premier League) only covers Premier League players.",
                            13 * u.fs, 500, u.mu, tight,
                        )
                    }
                } else {
                    item {
                        Tx("${s.who} · ${s.team} · ${s.pos}", 14 * u.fs, 700, modifier = tight)
                        Tx("This season, Premier League", 12 * u.fs, 500, u.mu, Modifier.padding(horizontal = 4.dp))
                    }
                    items(s.season) { (k, v) ->
                        KvRow(false, Modifier.padding(horizontal = 4.dp)) {
                            Tx(k, 14 * u.fs, 500, u.mu)
                            Tx(v, 14 * u.fs, 700)
                        }
                    }
                    if (s.recent.isNotEmpty()) {
                        item { H3("Last matches", 4.dp) }
                        items(s.recent) { Tx(it, 13 * u.fs, 500, modifier = tight) }
                    }
                    if (s.past.isNotEmpty()) {
                        item { H3("Past seasons", 4.dp) }
                        items(s.past) { Tx(it, 13 * u.fs, 500, modifier = tight) }
                    }
                }
            }
        }
    }
}
