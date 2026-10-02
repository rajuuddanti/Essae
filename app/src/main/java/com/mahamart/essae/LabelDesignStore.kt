package com.mahamart.essae

import android.content.Context
import android.net.Uri
import java.io.File

object LabelDesignStore {

    private const val PREFS = "label_design_store"
    private const val KEY_LABEL_TEXT = "label_text"
    private const val KEY_FSSAI = "fssai"
    private const val DEFAULT_LABEL_TEXT = "MAHALAXMI MAHA MART"

    enum class Slot(
        val title: String,
        val bundledAsset: String,
        val localFile: String
    ) {
        WEIGHT_ONLY(
            title = "Weight Only",
            bundledAsset = "Weight Only.LFT",
            localFile = "weight_only.LFT"
        ),
        WEIGHT_PRICE(
            title = "Weight + ₹ Price",
            bundledAsset = "Weight + ₹ Price.LFT",
            localFile = "weight_price.LFT"
        ),
        WEIGHT_PRICE_2(
            title = "Weight + ₹ Price 2",
            bundledAsset = "Weight + ₹ Price 2.LFT",
            localFile = "weight_price_2.LFT"
        )
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "label_designs").apply { mkdirs() }

    fun getLabelText(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LABEL_TEXT, DEFAULT_LABEL_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LABEL_TEXT
    }

    fun getFssai(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_FSSAI, "").orEmpty().trim()
        if (saved.length == 14 && saved.all(Char::isDigit)) {
            return saved
        }

        ensureBundled(context)

        for (slot in Slot.values()) {
            val content = File(dir(context), slot.localFile)
                .takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                ?: continue

            val match = Regex("(?i)FSSAI\\s*:\\s*(\\d{14})").find(content)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        return ""
    }

    fun hasFssai(context: Context): Boolean {
        ensureBundled(context)
        return Slot.values().any { slot ->
            val file = File(dir(context), slot.localFile)
            if (!file.exists() || file.length() == 0L) {
                false
            } else {
                Regex("(?i)FSSAI\\s*:").containsMatchIn(
                    file.readText(Charsets.UTF_8)
                )
            }
        }
    }

    fun saveLabelText(context: Context, value: String) {
        val newText = value.trim()
        require(newText.isNotBlank()) { "Label text is required" }
        ensureBundled(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldText = getLabelText(context)

        for (slot in Slot.values()) {
            val file = File(dir(context), slot.localFile)
            if (!file.exists() || file.length() == 0L) continue

            val content = file.readText(Charsets.UTF_8)
            val replacedName = content
                .replace(oldText, newText)
                .replace("MAHALAXMI MAHA MART", newText)
                .replace("MAHALAXMI MAHAMART", newText)

            val updated = replaceStoreNameSizing(replacedName, newText)

            if (updated != content) {
                file.writeText(updated, Charsets.UTF_8)
            }
        }

        prefs.edit()
            .putString(KEY_LABEL_TEXT, newText)
            .apply()
    }

    fun saveFssai(context: Context, value: String) {
        val newFssai = value.trim()
        require(newFssai.length == 14 && newFssai.all(Char::isDigit)) {
            "FSSAI must be exactly 14 digits"
        }
        ensureBundled(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        for (slot in Slot.values()) {
            val file = File(dir(context), slot.localFile)
            if (!file.exists() || file.length() == 0L) continue

            val content = file.readText(Charsets.UTF_8)
            val updated = content.replace(
                Regex("(?i)FSSAI\\s*:\\s*\\d{14}"),
                "FSSAI: $newFssai"
            )

            if (updated != content) {
                file.writeText(updated, Charsets.UTF_8)
            }
        }

        prefs.edit()
            .putString(KEY_FSSAI, newFssai)
            .apply()
    }

    fun ensureBundled(context: Context) {
        for (slot in Slot.values()) {
            val target = File(dir(context), slot.localFile)
            if (!target.exists() || target.length() == 0L) {
                context.assets.open(slot.bundledAsset).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    fun read(context: Context, slot: Slot): ByteArray {
        ensureBundled(context)
        val savedText = getLabelText(context)
        val savedFssai = getFssai(context)

        val content = File(dir(context), slot.localFile).readText(Charsets.UTF_8)
        var updated = content
            .replace("MAHALAXMI MAHA MART", savedText)
            .replace("MAHALAXMI MAHAMART", savedText)

        updated = replaceStoreNameSizing(updated, savedText)

        if (savedFssai.length == 14 && savedFssai.all(Char::isDigit)) {
            updated = updated.replace(
                Regex("(?i)FSSAI\\s*:\\s*\\d{14}"),
                "FSSAI: $savedFssai"
            )
        }

        return updated.toByteArray(Charsets.UTF_8)
    }

    fun importInto(context: Context, slot: Slot, uri: Uri) {
        ensureBundled(context)
        context.contentResolver.openInputStream(uri).use { input ->
            require(input != null) { "Could not read label design" }
            File(dir(context), slot.localFile).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun displayFileName(slot: Slot): String = when (slot) {
        Slot.WEIGHT_ONLY -> "Weight Only.LFT"
        Slot.WEIGHT_PRICE -> "Weight + ₹ Price.LFT"
        Slot.WEIGHT_PRICE_2 -> "Weight + ₹ Price 2.LFT"
    }

    private fun replaceStoreNameSizing(
        content: String,
        labelText: String
    ): String {
        val target = if (labelText.length > 19) "1,1" else "2,2"
        val old = if (labelText.isNotBlank()) {
            listOf(",2,2,$labelText,", ",1,1,$labelText,")
        } else {
            emptyList()
        }

        return content.lineSequence()
            .joinToString("\n") { line ->
                if (!line.startsWith("~T,") || !line.contains(",$labelText,")) {
                    line
                } else {
                    old.fold(line) { acc, token ->
                        acc.replace(token, ",$target,$labelText,")
                    }
                }
            }
    }
}
