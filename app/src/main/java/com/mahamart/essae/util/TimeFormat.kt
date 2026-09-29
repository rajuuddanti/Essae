package com.mahamart.essae.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormat {
    private val IST = ZoneId.of("Asia/Kolkata")

    private val fullFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(IST)

    private val dayMonthFormatter =
        DateTimeFormatter.ofPattern("dd-MM")
            .withZone(IST)

    private val lastChangedFormatter =
        DateTimeFormatter.ofPattern("dd-MM-yyyy, HH:mm")
            .withZone(IST)

    fun ist(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        return runCatching { fullFormatter.format(Instant.parse(value)) }
            .getOrDefault("—")
    }

    fun istDayMonth(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { dayMonthFormatter.format(Instant.parse(value)) }
            .getOrNull()
    }

    fun istLastChanged(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        return runCatching { lastChangedFormatter.format(Instant.parse(value)) }
            .getOrDefault("—")
    }
}
