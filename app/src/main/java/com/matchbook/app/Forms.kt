package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FRow(gap: Int = 12, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp), content = content)

/** A glass card that groups a few form fields under a title. */
@Composable
fun FormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val u = LocalU.current
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Tx(title, 15 * u.fs, 800)
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun LeagueForm(vm: AppVM, d: Draft) {
    val ed = d.eid != null
    val others = vm.leagues.filter { it.id != d.eid }.map { it.id to it.name }
    TopRow(vm.backLabel, { vm.goBack() })
    Tp(if (ed) "Edit league" else "New league", "League details")
    Column(Modifier.bd(top = 18.dp, bottom = 20.dp)) {
        FormCard("Basics") {
            Fld("League name", d["name"], { d["name"] = it }, ph = "Premier League")
            FRow {
                CountryField(d["country"], { d["country"] = it }, Modifier.weight(1f))
                Fld("Season", d["season"], { d["season"] = it }, Modifier.weight(1f), ph = "2026/27")
            }
        }
        FormCard("Look") {
            Upload("Logo", d, "logo")
            Upload("Banner", d, "banner", 4f)
            ColorField("League colour", d["color"]) { d["color"] = it }
        }
        FormCard("Rules") {
            FRow {
                Fld("Points per win", d["pw"], { d["pw"] = it }, Modifier.weight(1f), number = true)
                Fld("Points per draw", d["pd"], { d["pd"] = it }, Modifier.weight(1f), number = true)
            }
            FRow {
                Fld("Promotion spots", d["promo"], { d["promo"] = it }, Modifier.weight(1f), number = true)
                Fld("Relegation spots", d["rel"], { d["rel"] = it }, Modifier.weight(1f), number = true)
            }
        }
        FormCard("League links") {
            DropSelect("Promotion to", vm.lg(d["promoTo"])?.name ?: "", others, { d["promoTo"] = it }, none = "None")
            DropSelect("Relegation to", vm.lg(d["relTo"])?.name ?: "", others, { d["relTo"] = it }, none = "None")
        }
        if (ed) DeleteBtn("Delete league and its clubs", 0.dp) { vm.deleteLeague(d.eid!!) }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun ClubForm(vm: AppVM, d: Draft) {
    val u = LocalU.current
    val ed = d.eid != null
    TopRow(vm.backLabel, { vm.goBack() })
    Tp(if (ed) "Edit club" else "New club", "Club details")
    Column(Modifier.bd(top = 18.dp, bottom = 20.dp)) {
        FormCard("Identity") {
            Fld("Club name", d["name"], { d["name"] = it }, ph = "Metalist")
            FRow {
                Fld("Short name", d["short"], { d["short"] = it }, Modifier.weight(1f), ph = "ME")
                Fld("Nickname", d["nick"], { d["nick"] = it }, Modifier.weight(1f))
            }
            DropSelect("League", vm.lg(d["lid"])?.name ?: "", vm.leagues.map { it.id to it.name }, { d["lid"] = it }, none = null)
        }
        FormCard("Basics") {
            FRow {
                Fld("City", d["city"], { d["city"] = it }, Modifier.weight(1f))
                Fld("Founded", d["founded"], { d["founded"] = it }, Modifier.weight(1f), number = true, ph = "1925")
            }
            FRow {
                Fld("Stadium", d["stadium"], { d["stadium"] = it }, Modifier.weight(1f))
                Fld("Capacity", d["cap"], { d["cap"] = it }, Modifier.weight(1f), number = true)
            }
            FRow {
                Fld("Manager", d["mgr"], { d["mgr"] = it }, Modifier.weight(1f))
                Fld("Chairman", d["chair"], { d["chair"] = it }, Modifier.weight(1f))
            }
        }
        FormCard("Crest and colours") {
            Upload("Crest image (optional)", d, "logo")
            if (d["logo"].isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tx("Show club colours behind logo", 14 * u.fs, 600, u.mu, Modifier.weight(1f))
                    Toggle(d["logoBg"] == "1") { d["logoBg"] = if (d["logoBg"] == "1") "0" else "1" }
                }
            }
            ColorField("Main colour", d["color"]) { d["color"] = it }
            ColorField("Second colour", d["c2"]) { d["c2"] = it }
        }
        FormCard("Season record") {
            FRow(10) { for ((k, t) in listOf("w" to "Won", "d" to "Drawn", "l" to "Lost")) Fld(t, d[k], { d[k] = it }, Modifier.weight(1f), number = true) }
            FRow(10) { for ((k, t) in listOf("gf" to "Goals for", "ga" to "Against", "titles" to "Titles")) Fld(t, d[k], { d[k] = it }, Modifier.weight(1f), number = true) }
        }
        FormCard("Story") {
            Fld("Honours", d["hon"], { d["hon"] = it }, ph = "One per line", multi = true)
            Fld("About", d["info"], { d["info"] = it }, multi = true)
        }
        if (ed) DeleteBtn("Delete club", 0.dp) { vm.deleteClub(d.eid!!) }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun FixtureForm(vm: AppVM, d: Draft) {
    val ed = d.eid != null
    val inLeague = vm.clubs.filter { it.lid == d["lid"] }.map { it.id to it.name }
    TopRow(vm.backLabel, { vm.goBack() })
    Tp(if (ed) "Edit fixture" else "New fixture", "Schedule a match")
    Column(Modifier.bd(top = 18.dp, bottom = 20.dp)) {
        FormCard("Match") {
            DropSelect("League", vm.lg(d["lid"])?.name ?: "", vm.leagues.map { it.id to it.name }, {
                d["lid"] = it; d["home"] = ""; d["away"] = ""
            }, none = null)
            DropSelect("Home club", vm.cl(d["home"])?.name ?: "", inLeague, {
                d["home"] = it
                if (d["venue"].isBlank()) d["venue"] = vm.cl(it)?.stadium ?: ""
            }, none = null)
            DropSelect("Away club", vm.cl(d["away"])?.name ?: "", inLeague, { d["away"] = it }, none = null)
        }
        FormCard("When and where") {
            DateTimeField(d["at"].toLongOrNull() ?: System.currentTimeMillis()) { d["at"] = it.toString() }
            Fld("Venue", d["venue"], { d["venue"] = it }, ph = "Stadium")
        }
        if (ed) DeleteBtn("Delete fixture", 0.dp) { vm.deleteFixture(d.eid!!) }
        Spacer(Modifier.height(100.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(at: Long, onChange: (Long) -> Unit) {
    val u = LocalU.current
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(at).atZone(zone)
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val surface = if (u.dark) Color(0xFF16181D) else Color.White

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.4f)) {
            Lbl("Date")
            Box(Modifier.fillMaxWidth().field().clickable { showDate = true }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Tx(dt.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())), 15 * u.fs, 600, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            Lbl("Time")
            Box(Modifier.fillMaxWidth().field().clickable { showTime = true }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Tx(dt.format(DateTimeFormatter.ofPattern("HH:mm")), 15 * u.fs, 600, maxLines = 1)
            }
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(day.atTime(dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }

    if (showTime) {
        val ts = rememberTimePickerState(dt.hour, dt.minute, true)
        Dialog(onDismissRequest = { showTime = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = RoundedCornerShape(28.dp), color = surface) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = ts)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTime = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            onChange(dt.toLocalDate().atTime(ts.hour, ts.minute).atZone(zone).toInstant().toEpochMilli())
                            showTime = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerForm(vm: AppVM, d: Draft) {
    val ed = d.eid != null
    val club = vm.cl(d["cid"])
    TopRow(vm.backLabel, { vm.goBack() })
    Tp(if (ed) "Edit player" else "New player", club?.name ?: "Squad")
    Column(Modifier.bd(top = 18.dp, bottom = 20.dp)) {
        FormCard("Player") {
            Fld("Name", d["name"], { d["name"] = it }, ph = "Player name")
            FRow {
                Fld("Number", d["num"], { d["num"] = it }, Modifier.weight(1f), number = true)
                DropSelect("Position", POS_NAMES.firstOrNull { it.first == d["pos"] }?.second ?: "", POS_NAMES, { d["pos"] = it }, Modifier.weight(1.4f))
            }
            Upload("Render (picture or cut-out)", d, "img")
        }
        if (ed) DeleteBtn("Delete player", 0.dp) { vm.deletePlayer(d.eid!!) }
        Spacer(Modifier.height(100.dp))
    }
}
