package com.mahamart.essae.util

import com.mahamart.essae.data.Plu

object CsvImporter {
    fun parse(text: String): List<Plu> {
        val rows = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .map { parseLine(it) }
            .toList()

        if (rows.isEmpty()) return emptyList()

        val first = rows.first().map { it.trim().lowercase() }
        val hasHeader = first.any { it == "pluno" || it == "plu_no" || it == "plunumber" || it == "plu number" }
        val start = if (hasHeader) 1 else 0
        val header = if (hasHeader) first else emptyList()

        val numberIndex = header.indexOfFirst { it in setOf("pluno", "plu_no", "plunumber", "plu number") }.let { if (it >= 0) it else 0 }
        val nameIndex = header.indexOfFirst { it in setOf("pluname", "name", "product", "productname") }.let { if (it >= 0) it else 1 }
        val codeIndex = header.indexOfFirst { it in setOf("plucode", "code", "barcode") }.let { if (it >= 0) it else 2 }
        val uomIndex = header.indexOfFirst { it in setOf("uom", "unit", "unitofmeasure") }.let { if (it >= 0) it else 3 }
        val priceIndex = header.indexOfFirst { it in setOf("unitprice", "price", "unit price") }.let { if (it >= 0) it else 4 }

        return rows.drop(start).mapNotNull { row ->
            if (row.size < 4) return@mapNotNull null
            val number = row.getOrNull(numberIndex)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            if (number !in 1..9999) return@mapNotNull null
            val name = row.getOrNull(nameIndex)?.trim().orEmpty().ifBlank { "ITEM $number" }
            val code = row.getOrNull(codeIndex)?.trim().orEmpty()
            val uom = parseUom(row.getOrNull(uomIndex).orEmpty())
            val rawPrice = row.getOrNull(priceIndex)?.trim().orEmpty()
            val price = rawPrice
                .replace("₹", "")
                .replace(",", "")
                .trim()
                .toDoubleOrNull() ?: 0.0
            Plu(number = number, name = name, code = code, uom = uom, unitPrice = price)
        }.distinctBy { it.number }
    }

    private fun parseUom(value: String): Int {
        return when (value.trim().uppercase()) {
            "1", "PCS", "PIECE", "PIECES" -> 1
            else -> 0
        }
    }

    private fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += current.toString(); current.setLength(0) }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }
}
