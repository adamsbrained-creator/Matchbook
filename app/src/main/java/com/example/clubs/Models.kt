package com.example.clubs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

val ALL_COLS = setOf("w", "d", "l", "gd")
val ALL_FIELDS = setOf("stadium", "colours", "titles", "points", "nick", "mgr", "chair", "cap", "played", "rec", "goals", "hon", "about")
val PRESETS = listOf("classic", "minimal", "night", "pitch", "warm")

data class Settings(
    val theme: String = "auto", val acc: String = "#2da678", val tint: String = "neutral",
    val r: Int = 22, val ring: Int = 8, val f: String = "sans", val fs: String = "m", val tw: String = "bold",
    val c: String = "circle", val d: String = "cozy", val lay: String = "cards", val cl: String = "list",
    val nm: String = "full", val navs: String = "float", val lbl: String = "on",
    val cols: Set<String> = ALL_COLS, val fields: Set<String> = ALL_FIELDS,
)

fun Settings.at(k: String) = when (k) {
    "theme" -> theme; "acc" -> acc; "tint" -> tint; "f" -> f; "fs" -> fs; "tw" -> tw; "c" -> c; "d" -> d
    "lay" -> lay; "cl" -> cl; "nm" -> nm; "navs" -> navs; "lbl" -> lbl; else -> ""
}

fun Settings.put(k: String, v: String) = when (k) {
    "theme" -> copy(theme = v); "acc" -> copy(acc = v); "tint" -> copy(tint = v); "f" -> copy(f = v)
    "fs" -> copy(fs = v); "tw" -> copy(tw = v); "c" -> copy(c = v); "d" -> copy(d = v)
    "lay" -> copy(lay = v); "cl" -> copy(cl = v); "nm" -> copy(nm = v); "navs" -> copy(navs = v)
    "lbl" -> copy(lbl = v); else -> this
}

fun Settings.preset(id: String): Settings {
    val b = Settings(cols = cols, fields = fields)
    return when (id) {
        "minimal" -> b.copy(theme = "light", acc = "#111111", r = 10, ring = 3, tw = "light", navs = "bar", lbl = "off", lay = "list")
        "night" -> b.copy(theme = "dark", acc = "#4ade80", tint = "cool", r = 20, ring = 6, f = "rounded", tw = "regular")
        "pitch" -> b.copy(theme = "dark", acc = "#a3e635", tint = "accent", r = 8, ring = 10, c = "hex", f = "mono", cl = "grid")
        "warm" -> b.copy(theme = "light", acc = "#d9480f", tint = "warm", r = 28, ring = 5, f = "serif", tw = "regular", c = "shield")
        else -> b
    }
}

data class League(
    val id: String, val name: String, val country: String = "", val season: String = "",
    val color: String = "#2da678", val logo: String = "", val banner: String = "",
    val pw: Int = 3, val pd: Int = 1, val promo: Int = 1, val rel: Int = 1,
)

data class Club(
    val id: String, val lid: String, val name: String, val short: String = "", val nick: String = "",
    val city: String = "", val founded: String = "", val stadium: String = "", val cap: Int = 0,
    val mgr: String = "", val chair: String = "", val color: String = "#f5d400", val c2: String = "#1a7a2e",
    val logo: String = "", val titles: Int = 0, val w: Int = 0, val d: Int = 0, val l: Int = 0,
    val gf: Int = 0, val ga: Int = 0, val hon: String = "", val info: String = "",
) {
    fun played() = w + d + l
}

data class Data(val s: Settings, val leagues: List<League>, val clubs: List<Club>)

fun String.i() = toIntOrNull() ?: 0
fun ab(name: String, short: String) = short.ifBlank { name.take(3) }.uppercase()

fun League.toMap() = mapOf(
    "name" to name, "country" to country, "season" to season, "color" to color, "logo" to logo, "banner" to banner,
    "pw" to pw.toString(), "pd" to pd.toString(), "promo" to promo.toString(), "rel" to rel.toString(),
)

fun Club.toMap() = mapOf(
    "lid" to lid, "name" to name, "short" to short, "nick" to nick, "city" to city, "founded" to founded,
    "stadium" to stadium, "cap" to cap.toString(), "mgr" to mgr, "chair" to chair, "color" to color, "c2" to c2,
    "logo" to logo, "titles" to titles.toString(), "w" to w.toString(), "d" to d.toString(), "l" to l.toString(),
    "gf" to gf.toString(), "ga" to ga.toString(), "hon" to hon, "info" to info,
)

fun seed() = Data(
    Settings(),
    listOf(
        League("a", "Premier League", "Ukraine", "2026/27", "#2da678", promo = 2, rel = 1),
        League("b", "Coastal League", "Spain", "2026/27", "#c2410c", promo = 1, rel = 0),
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
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("clubs", Context.MODE_PRIVATE)
    private fun JSONArray.objs() = List(length()) { getJSONObject(it) }

    fun load(c: Context): Data = runCatching {
        val o = JSONObject(prefs(c).getString("data", null)!!)
        val b = Settings()
        val s = o.getJSONObject("s")
        fun set(k: String) = s.optString(k).split(",").filter { it.isNotEmpty() }.toSet()
        Data(
            Settings(
                theme = s.optString("theme", b.theme), acc = s.optString("acc", b.acc), tint = s.optString("tint", b.tint),
                r = s.optInt("r", b.r), ring = s.optInt("ring", b.ring), f = s.optString("f", b.f), fs = s.optString("fs", b.fs),
                tw = s.optString("tw", b.tw), c = s.optString("c", b.c), d = s.optString("d", b.d), lay = s.optString("lay", b.lay),
                cl = s.optString("cl", b.cl), nm = s.optString("nm", b.nm), navs = s.optString("navs", b.navs),
                lbl = s.optString("lbl", b.lbl), cols = set("cols"), fields = set("fields"),
            ),
            o.getJSONArray("leagues").objs().map {
                League(it.getString("id"), it.getString("name"), it.optString("country"), it.optString("season"), it.optString("color"),
                    it.optString("logo"), it.optString("banner"), it.optInt("pw"), it.optInt("pd"), it.optInt("promo"), it.optInt("rel"))
            },
            o.getJSONArray("clubs").objs().map {
                Club(it.getString("id"), it.getString("lid"), it.getString("name"), it.optString("short"), it.optString("nick"),
                    it.optString("city"), it.optString("founded"), it.optString("stadium"), it.optInt("cap"), it.optString("mgr"),
                    it.optString("chair"), it.optString("color"), it.optString("c2"), it.optString("logo"), it.optInt("titles"),
                    it.optInt("w"), it.optInt("d"), it.optInt("l"), it.optInt("gf"), it.optInt("ga"), it.optString("hon"), it.optString("info"))
            },
        )
    }.getOrElse { seed() }

    fun save(c: Context, d: Data) {
        val s = d.s
        val o = JSONObject().put("s", JSONObject()
            .put("theme", s.theme).put("acc", s.acc).put("tint", s.tint).put("r", s.r).put("ring", s.ring).put("f", s.f)
            .put("fs", s.fs).put("tw", s.tw).put("c", s.c).put("d", s.d).put("lay", s.lay).put("cl", s.cl).put("nm", s.nm)
            .put("navs", s.navs).put("lbl", s.lbl).put("cols", s.cols.joinToString(",")).put("fields", s.fields.joinToString(",")))
            .put("leagues", JSONArray(d.leagues.map {
                JSONObject().put("id", it.id).put("name", it.name).put("country", it.country).put("season", it.season)
                    .put("color", it.color).put("logo", it.logo).put("banner", it.banner).put("pw", it.pw).put("pd", it.pd)
                    .put("promo", it.promo).put("rel", it.rel)
            }))
            .put("clubs", JSONArray(d.clubs.map {
                JSONObject().put("id", it.id).put("lid", it.lid).put("name", it.name).put("short", it.short).put("nick", it.nick)
                    .put("city", it.city).put("founded", it.founded).put("stadium", it.stadium).put("cap", it.cap)
                    .put("mgr", it.mgr).put("chair", it.chair).put("color", it.color).put("c2", it.c2).put("logo", it.logo)
                    .put("titles", it.titles).put("w", it.w).put("d", it.d).put("l", it.l).put("gf", it.gf).put("ga", it.ga)
                    .put("hon", it.hon).put("info", it.info)
            }))
        prefs(c).edit().putString("data", o.toString()).apply()
    }
}
