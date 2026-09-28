package com.mahamart.essae

import android.content.Context
import android.net.Uri
import java.io.File

object LabelDesignStore {

    private const val PREFS = "label_design_store"
    private const val KEY_LABEL_TEXT = "label_text"
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
            val updated = content
                .replace(oldText, newText)
                .replace("MAHALAXMI MAHA MART", newText)
                .replace("MAHALAXMI MAHAMART", newText)

            if (updated != content) {
                file.writeText(updated, Charsets.UTF_8)
            }
        }

        prefs.edit()
            .putString(KEY_LABEL_TEXT, newText)
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
        return File(dir(context), slot.localFile).readBytes()
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
    }
}
