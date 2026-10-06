package com.matchbook.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Version of the saved-data / backup format. Bump when the JSON layout changes (and add a migration). */
const val DATA_FORMAT = 2

val ALL_COLS = listOf("w" to "Wins", "d" to "Draws", "l" to "Losses", "gf" to "Goals for", "ga" to "Goals against", "gd" to "Goal difference")
val ALL_FIELDS = listOf(
    "stadium" to "Stadium", "nick" to "Nickname", "mgr" to "Manager", "chair" to "Chairman",
    "cap" to "Capacity", "hon" to "Honours", "about" to "About",
)
val PRESETS = listOf("Lime", "Light", "Mono", "Pop", "Classic")

class BlockDef(val key: String, val label: String, val def: Boolean)
class BlockGroup(val page: String, val items: List<BlockDef>)

/** Optional cards and blocks that can be switched on or off per page (Settings > Page blocks). */
val BLOCK_GROUPS = listOf(
    BlockGroup("Clubs", listOf(
        BlockDef("c_fixture", "Next fixture card", true),
        BlockDef("c_actions", "Add club and Compare buttons", true),
        BlockDef("c_leaders", "League leaders card", false),
    )),
    BlockGroup("Leagues", listOf(
        BlockDef("l_summary", "Season summary strip", true),
        BlockDef("l_tables", "Mini tables", false),
        BlockDef("l_pyramid", "League pyramid", false),
        BlockDef("l_add", "Add league button", true),
    )),
    BlockGroup("League page", listOf(
        BlockDef("g_table", "Table card", true),
        BlockDef("g_rules", "Rule tiles", true),
        BlockDef("g_fixture", "Next fixture card", true),
        BlockDef("g_links", "Promotion and relegation links", true),
        BlockDef("g_leaders", "Attack and defence leaders", false),
    )),
    BlockGroup("Club page", listOf(
        BlockDef("k_strip", "Stat strip", true),
        BlockDef("k_stripx", "Extra strip stats (GF, GA, GD)", false),
        BlockDef("k_glow", "Colour glow", true),
        BlockDef("k_info", "Info card", true),
        BlockDef("k_tiles", "Stat tiles", true),
        BlockDef("k_tiles2", "More stat tiles", false),
        BlockDef("k_details", "Club details card", true),
        BlockDef("k_neighbours", "Table neighbours", false),
    )),
    BlockGroup("Stats", listOf(
        BlockDef("s_overview", "Overview tiles", true),
        BlockDef("s_top", "Top lists", true),
    )),
)
val DEFAULT_BLOCKS: Set<String> = BLOCK_GROUPS.flatMap { it.items }.filter { it.def }.map { it.key }.toSet()
val DEFAULT_FIELDS: Set<String> = ALL_FIELDS.map { it.first }.toSet()

data class Settings(
    val theme: String = "dark",       // auto | dark | light
    val acc: String = "#c8f169",
    val g: String = "bold",           // header glow: bold | soft | mono
    val gh: String = "m",             // header glow height: s | m | l
    val surf: String = "glass",       // glass | solid | outline
    val r: Int = 28,                  // card corner radius
    val ring: Int = 8,
    val c: String = "circle",         // crest shape
    val f: String = "manrope",        // manrope | condensed | rounded | serif | mono
    val fs: String = "m",
    val d: String = "cozy",
    val nm: String = "full",          // club names: full | short
    val navs: String = "float",       // float | dock
    val lbl: String = "off",          // nav labels: on | off
    val cg: String = "g2",            // clubs layout: list | g2 | g3 | comp
    val lg: String = "cards",         // leagues layout: cards | list | g2
    val sg: String = "list",          // stats layout: list | g2
    val tc: String = "3",             // tile columns: 2 | 3
    val splash: Boolean = true,
    val cols: Set<String> = emptySet(),
    val fields: Set<String> = DEFAULT_FIELDS,
    val blocks: Set<String> = DEFAULT_BLOCKS,
)

fun Settings.at(k: String) = when (k) {
    "theme" -> theme; "acc" -> acc; "g" -> g; "gh" -> gh; "surf" -> surf; "c" -> c; "f" -> f; "fs" -> fs; "d" -> d
    "nm" -> nm; "navs" -> navs; "lbl" -> lbl; "cg" -> cg; "lg" -> lg; "sg" -> sg; "tc" -> tc; else -> ""
}

fun Settings.put(k: String, v: String) = when (k) {
    "theme" -> copy(theme = v); "acc" -> copy(acc = v); "g" -> copy(g = v); "gh" -> copy(gh = v); "surf" -> copy(surf = v)
    "c" -> copy(c = v); "f" -> copy(f = v); "fs" -> copy(fs = v); "d" -> copy(d = v); "nm" -> copy(nm = v)
    "navs" -> copy(navs = v); "lbl" -> copy(lbl = v); "cg" -> copy(cg = v); "lg" -> copy(lg = v); "sg" -> copy(sg = v)
    "tc" -> copy(tc = v); else -> this
}

/** Presets only change the look, never your data or your block / column / layout choices. */
fun Settings.preset(id: String): Settings {
    val b = Settings(cols = cols, fields = fields, blocks = blocks, splash = splash, cg = cg, lg = lg, sg = sg, tc = tc)
    return when (id) {
        "Light" -> b.copy(theme = "light", acc = "#16a34a", g = "soft")
        "Mono" -> b.copy(acc = "#ffffff", g = "mono", r = 22, ring = 3, surf = "outline")
        "Pop" -> b.copy(acc = "#ff5ca8", r = 32, f = "rounded", c = "hex")
        "Classic" -> b.copy(acc = "#e5c07b", r = 18, f = "serif", c = "shield", g = "soft", surf = "solid")
        else -> b
    }
}

data class League(
    val id: String, val name: String, val country: String = "", val season: String = "",
    val color: String = "#2da678", val logo: String = "", val banner: String = "",
    val pw: Int = 3, val pd: Int = 1, val promo: Int = 1, val rel: Int = 1,
    val promoTo: String = "", val relTo: String = "",
)

data class Club(
    val id: String, val lid: String, val name: String, val short: String = "", val nick: String = "",
    val city: String = "", val founded: String = "", val stadium: String = "", val cap: Int = 0,
    val mgr: String = "", val chair: String = "", val color: String = "#f5d400", val c2: String = "#1a7a2e",
    val logo: String = "", val titles: Int = 0, val w: Int = 0, val d: Int = 0, val l: Int = 0,
    val gf: Int = 0, val ga: Int = 0, val hon: String = "", val info: String = "", val logoBg: Boolean = false,
) {
    fun played() = w + d + l
}

data class Fixture(
    val id: String, val lid: String, val home: String, val away: String, val at: Long, val venue: String = "",
)

data class Data(
    val s: Settings, val leagues: List<League>, val clubs: List<Club>,
    val fixtures: List<Fixture> = emptyList(), val pick: String = "",
)

fun String.i() = toIntOrNull() ?: 0
fun ab(name: String, short: String) = short.ifBlank { name.take(3) }.uppercase()

val COUNTRIES = listOf(
    "Ukraine" to "UA", "England" to "GB", "Scotland" to "GB", "Spain" to "ES", "Italy" to "IT", "Germany" to "DE",
    "France" to "FR", "Portugal" to "PT", "Netherlands" to "NL", "Belgium" to "BE", "Turkey" to "TR", "Poland" to "PL",
    "Greece" to "GR", "Switzerland" to "CH", "Austria" to "AT", "Sweden" to "SE", "Norway" to "NO", "Denmark" to "DK",
    "Croatia" to "HR", "Serbia" to "RS", "Czechia" to "CZ", "Romania" to "RO", "Hungary" to "HU", "Ireland" to "IE",
    "Brazil" to "BR", "Argentina" to "AR", "Mexico" to "MX", "USA" to "US", "Canada" to "CA", "Japan" to "JP",
    "South Korea" to "KR", "China" to "CN", "Saudi Arabia" to "SA", "Egypt" to "EG", "Morocco" to "MA",
    "Nigeria" to "NG", "South Africa" to "ZA", "Australia" to "AU",
)

/** Flag emoji for a known country name (with a trailing space), or "". */
fun flag(name: String): String {
    val code = COUNTRIES.find { it.first.equals(name.trim(), true) }?.second ?: return ""
    return buildString { code.forEach { appendCodePoint(127397 + it.code) } } + " "
}

/** "Today 19:00", "Tomorrow 19:00", "Sun 19:00" within a week, otherwise "12 Oct 19:00". */
fun fmtFixture(at: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(at).atZone(zone)
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), dt.toLocalDate())
    val time = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when {
        days == 0L -> "Today $time"
        days == 1L -> "Tomorrow $time"
        days in 2..6 -> dt.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) + " " + time
        else -> dt.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) + " " + time
    }
}

fun fmtDateTime(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))

fun League.toMap() = mapOf(
    "name" to name, "country" to country, "season" to season, "color" to color, "logo" to logo, "banner" to banner,
    "pw" to pw.toString(), "pd" to pd.toString(), "promo" to promo.toString(), "rel" to rel.toString(),
    "promoTo" to promoTo, "relTo" to relTo,
)

fun Club.toMap() = mapOf(
    "lid" to lid, "name" to name, "short" to short, "nick" to nick, "city" to city, "founded" to founded,
    "stadium" to stadium, "cap" to cap.toString(), "mgr" to mgr, "chair" to chair, "color" to color, "c2" to c2,
    "logo" to logo, "titles" to titles.toString(), "w" to w.toString(), "d" to d.toString(), "l" to l.toString(),
    "gf" to gf.toString(), "ga" to ga.toString(), "hon" to hon, "info" to info,
    "logoBg" to if (logoBg) "1" else "0",
)

fun Fixture.toMap() = mapOf("lid" to lid, "home" to home, "away" to away, "at" to at.toString(), "venue" to venue)

fun seed(): Data {
    val sunday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atTime(19, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Data(
        Settings(),
        listOf(
            League("a", "Premier League", "Ukraine", "2026/27", "#2da678", promo = 2, rel = 1, relTo = "b"),
            League("b", "Coastal League", "Spain", "2026/27", "#c2410c", promo = 1, rel = 0, promoTo = "a"),
        ),
        listOf(
            Club("1", "a", "Metalist", "ME", city = "Kharkiv", founded = "1925", color = "#f5d400", c2 = "#1a7a2e", w = 4, d = 2, l = 2, gf = 12, ga = 9),
            Club("2", "a", "Harbor City", "HAR", "The Anchors", "Harbor", "1894", "Dockside Arena", 42000, "Elena Marsh", "J. Okafor",
                "#0ea5e9", "#ffffff", "", 3, 6, 1, 1, 19, 7, "3x League champions\n2x Cup winners",
                "Founded by dock workers. Known for fast wing play and a loud south stand."),
            Club("3", "a", "Ironbridge United", "IRU", "The Smiths", "Ironbridge", "1878", "Forge Park", 36500, "Tom Reyes", "A. Lindqvist",
                "#b91c1c", "#111111", "", 4, 5, 2, 1, 15, 8, "4x League champions", "A famously tight defence."),
            Club("4", "a", "Millfield Rovers", "MIL", "The Millers", "Millfield", "1921", "The Mill Ground", 18200, "Sam Boateng", "R. Hale",
                "#15803d", "#facc15", "", 0, 3, 3, 2, 11, 10, "", "A community club with a loyal following."),
            Club("5", "b", "Costa Azul", "CAZ", "Los Azules", "Valdora", "1909", "Estadio del Mar", 51000, "Lucia Ferrer", "M. Duarte",
                "#7c3aed", "#ffffff", "", 7, 4, 2, 1, 13, 6, "7x League champions", "Seaside giants with a famous academy."),
        ),
        listOf(Fixture("f1", "a", "2", "3", sunday, "Dockside Arena")),
        "",
    )
}

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("matchbook", Context.MODE_PRIVATE)
    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    fun load(c: Context): Data = runCatching { fromJson(JSONObject(prefs(c).getString("data", null)!!)) }.getOrElse { seed() }
    fun save(c: Context, d: Data) { prefs(c).edit().putString("data", toJson(d).toString()).apply() }

    // safety copy taken right before a backup is restored, so the restore can be undone
    fun savePrev(c: Context, d: Data) { prefs(c).edit().putString("prev", toJson(d).toString()).apply() }
    fun loadPrev(c: Context): Data? = runCatching { fromJson(JSONObject(prefs(c).getString("prev", null)!!)) }.getOrNull()
    fun hasPrev(c: Context) = prefs(c).getString("prev", null) != null
    fun clearPrev(c: Context) { prefs(c).edit().remove("prev").apply() }
    fun lastBackup(c: Context) = prefs(c).getLong("lastBackup", 0L)
    fun setLastBackup(c: Context, t: Long) { prefs(c).edit().putLong("lastBackup", t).apply() }

    fun settingsToJson(s: Settings) = JSONObject()
        .put("theme", s.theme).put("acc", s.acc).put("g", s.g).put("gh", s.gh).put("surf", s.surf)
        .put("r", s.r).put("ring", s.ring).put("c", s.c).put("f", s.f).put("fs", s.fs).put("d", s.d)
        .put("nm", s.nm).put("navs", s.navs).put("lbl", s.lbl).put("cg", s.cg).put("lg", s.lg).put("sg", s.sg)
        .put("tc", s.tc).put("splash", s.splash)
        .put("cols", s.cols.joinToString(",")).put("fields", s.fields.joinToString(","))
        .put("blocks", s.blocks.joinToString(","))

    /** Reads settings from either format. [v] is the data format the JSON was written with (1 = Matchbook 1.x). */
    fun settingsFromJson(s: JSONObject, v: Int): Settings {
        val b = Settings()
        fun set(k: String, def: Set<String>) = if (s.has(k)) s.optString(k).split(",").filter { it.isNotEmpty() }.toSet() else def
        if (v >= 2) return Settings(
            theme = s.optString("theme", b.theme), acc = s.optString("acc", b.acc), g = s.optString("g", b.g),
            gh = s.optString("gh", b.gh), surf = s.optString("surf", b.surf), r = s.optInt("r", b.r),
            ring = s.optInt("ring", b.ring), c = s.optString("c", b.c), f = s.optString("f", b.f), fs = s.optString("fs", b.fs),
            d = s.optString("d", b.d), nm = s.optString("nm", b.nm), navs = s.optString("navs", b.navs),
            lbl = s.optString("lbl", b.lbl), cg = s.optString("cg", b.cg), lg = s.optString("lg", b.lg),
            sg = s.optString("sg", b.sg), tc = s.optString("tc", b.tc), splash = s.optBoolean("splash", b.splash),
            cols = set("cols", b.cols), fields = set("fields", b.fields), blocks = set("blocks", b.blocks),
        )
        // ---- migration from Matchbook 1.x settings
        val oldAcc = s.optString("acc", "#ffffff")
        val oldF = s.optString("f", "sans")
        val oldR = s.optInt("r", 22)
        val oldNav = s.optString("navs", "float")
        return Settings(
            theme = s.optString("theme", b.theme),
            acc = if (oldAcc.equals("#ffffff", true)) b.acc else oldAcc,
            g = s.optString("g", b.g),
            r = if (oldR == 22) b.r else oldR,
            ring = s.optInt("ring", b.ring),
            c = s.optString("c", b.c),
            f = if (oldF == "sans") "manrope" else oldF,
            fs = s.optString("fs", b.fs),
            d = s.optString("d", b.d),
            nm = s.optString("nm", b.nm),
            navs = if (oldNav == "bar") "dock" else "float",
            cg = if (s.optString("cl", "list") == "grid") "g2" else "list",
            lg = s.optString("lay", "cards"),
            cols = set("cols", b.cols).filter { k -> ALL_COLS.any { it.first == k } }.toSet(),
            fields = set("fields", b.fields).filter { k -> ALL_FIELDS.any { it.first == k } }.toSet(),
        )
    }

    fun toJson(d: Data, imageName: (String) -> String = { it }): JSONObject = JSONObject()
        .put("app", "Matchbook").put("v", DATA_FORMAT)
        .put("s", settingsToJson(d.s)).put("pick", d.pick)
        .put("leagues", JSONArray(d.leagues.map {
            JSONObject().put("id", it.id).put("name", it.name).put("country", it.country).put("season", it.season)
                .put("color", it.color).put("logo", imageName(it.logo)).put("banner", imageName(it.banner))
                .put("pw", it.pw).put("pd", it.pd).put("promo", it.promo).put("rel", it.rel)
                .put("promoTo", it.promoTo).put("relTo", it.relTo)
        }))
        .put("clubs", JSONArray(d.clubs.map {
            JSONObject().put("id", it.id).put("lid", it.lid).put("name", it.name).put("short", it.short).put("nick", it.nick)
                .put("city", it.city).put("founded", it.founded).put("stadium", it.stadium).put("cap", it.cap)
                .put("mgr", it.mgr).put("chair", it.chair).put("color", it.color).put("c2", it.c2).put("logo", imageName(it.logo))
                .put("titles", it.titles).put("w", it.w).put("d", it.d).put("l", it.l).put("gf", it.gf).put("ga", it.ga)
                .put("hon", it.hon).put("info", it.info).put("logoBg", it.logoBg)
        }))
        .put("fixtures", JSONArray(d.fixtures.map {
            JSONObject().put("id", it.id).put("lid", it.lid).put("home", it.home).put("away", it.away)
                .put("at", it.at).put("venue", it.venue)
        }))

    /** Reads both Matchbook 1.x data (no "v" key) and current data. Unknown or missing fields fall back to defaults. */
    fun fromJson(o: JSONObject, imagePath: (String) -> String = { it }): Data {
        val v = o.optInt("v", 1)
        val sObj = o.optJSONObject("s") ?: JSONObject()
        return Data(
            settingsFromJson(sObj, v),
            o.optJSONArray("leagues").objs().map {
                League(it.getString("id"), it.getString("name"), it.optString("country"), it.optString("season"),
                    it.optString("color", "#2da678"), imagePath(it.optString("logo")), imagePath(it.optString("banner")),
                    it.optInt("pw", 3), it.optInt("pd", 1), it.optInt("promo", 1), it.optInt("rel", 1),
                    it.optString("promoTo"), it.optString("relTo"))
            },
            o.optJSONArray("clubs").objs().map {
                Club(it.getString("id"), it.getString("lid"), it.getString("name"), it.optString("short"), it.optString("nick"),
                    it.optString("city"), it.optString("founded"), it.optString("stadium"), it.optInt("cap"), it.optString("mgr"),
                    it.optString("chair"), it.optString("color", "#f5d400"), it.optString("c2", "#1a7a2e"),
                    imagePath(it.optString("logo")), it.optInt("titles"), it.optInt("w"), it.optInt("d"), it.optInt("l"),
                    it.optInt("gf"), it.optInt("ga"), it.optString("hon"), it.optString("info"), it.optBoolean("logoBg"))
            },
            o.optJSONArray("fixtures").objs().map {
                Fixture(it.getString("id"), it.getString("lid"), it.getString("home"), it.getString("away"),
                    it.optLong("at"), it.optString("venue"))
            },
            o.optString("pick"),
        )
    }
}
