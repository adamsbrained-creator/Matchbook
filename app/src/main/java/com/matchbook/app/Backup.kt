package com.matchbook.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A backup that has been read and checked, waiting for the user to confirm the restore. */
class PendingRestore(
    val data: Data, val images: Map<String, ByteArray>, val created: Long, val appVersion: String, val format: Int,
)

/**
 * Backup file = a ZIP with "data.json" (everything except image bytes) and "images/<name>" files.
 * data.json carries a format number ("v"). Newer apps keep reading older formats (see Store.fromJson);
 * a backup from a newer format than this app understands is refused with a clear message.
 */
object Backup {
    fun write(ctx: Context, uri: Uri, d: Data, appVersion: String): Boolean = runCatching {
        val names = LinkedHashMap<String, String>()
        fun nm(p: String): String {
            if (p.isBlank()) return ""
            val f = File(p)
            if (!f.exists()) return ""
            return names.getOrPut(p) { f.name }
        }
        val json = Store.toJson(d) { nm(it) }
        json.put("appVersion", appVersion).put("created", System.currentTimeMillis())
        ctx.contentResolver.openOutputStream(uri)!!.use { out ->
            ZipOutputStream(out.buffered()).use { z ->
                z.putNextEntry(ZipEntry("data.json"))
                z.write(json.toString(2).toByteArray(Charsets.UTF_8))
                z.closeEntry()
                for ((path, name) in names) {
                    z.putNextEntry(ZipEntry("images/$name"))
                    File(path).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        true
    }.getOrDefault(false)

    fun read(ctx: Context, uri: Uri): Result<PendingRestore> = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        var jsonText: String? = null
        val images = HashMap<String, ByteArray>()
        if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
            jsonText = String(bytes, Charsets.UTF_8)
        } else {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e: ZipEntry? = z.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == "data.json") jsonText = String(z.readBytes(), Charsets.UTF_8)
                    else if (name.startsWith("images/") && !e.isDirectory) images[File(name).name] = z.readBytes()
                    e = z.nextEntry
                }
            }
        }
        val text = jsonText ?: error("That file isn't a Matchbook backup.")
        val o = JSONObject(text)
        if (o.optString("app") != "Matchbook") error("That file isn't a Matchbook backup.")
        val fmt = o.optInt("v", 1)
        if (fmt > DATA_FORMAT) error("This backup was made by a newer version of Matchbook. Update the app, then try again.")
        val dir = ctx.filesDir
        val data = Store.fromJson(o) { n -> if (n.isBlank()) "" else File(dir, n).absolutePath }
        PendingRestore(data, images, o.optLong("created"), o.optString("appVersion"), fmt)
    }

    fun applyImages(ctx: Context, p: PendingRestore) {
        for ((name, bytes) in p.images) runCatching { File(ctx.filesDir, name).writeBytes(bytes) }
    }
}
