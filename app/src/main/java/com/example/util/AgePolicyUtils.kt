package com.example.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object AgePolicyUtils {
    private val DOB_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    data class AgeCheckResult(
        val isValidFormat: Boolean,
        val ageYears: Int,
        val isAdult: Boolean,
        val normalizedDob: String,
        val errorMessage: String? = null
    )

    fun evaluateDob(
        dobInput: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AgeCheckResult {
        val trimmed = normalizeDigits(dobInput.trim())
        val match = DOB_REGEX.matchEntire(trimmed)
            ?: return AgeCheckResult(
                isValidFormat = false,
                ageYears = 0,
                isAdult = false,
                normalizedDob = trimmed,
                errorMessage = "يرجى إدخال تاريخ الميلاد بصيغة صحيحة (YYYY-MM-DD)، مثال: 1998-05-14"
            )

        val year = match.groupValues[1].toIntOrNull() ?: 0
        val month = match.groupValues[2].toIntOrNull() ?: 0
        val day = match.groupValues[3].toIntOrNull() ?: 0

        if (year < 1920 || month !in 1..12) {
            return AgeCheckResult(
                isValidFormat = false,
                ageYears = 0,
                isAdult = false,
                normalizedDob = trimmed,
                errorMessage = "تاريخ الميلاد المدخل غير صالح."
            )
        }

        val dobCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            isLenient = false
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val validCalendar = runCatching {
            dobCal.timeInMillis
            true
        }.getOrDefault(false)

        if (!validCalendar) {
            return AgeCheckResult(
                isValidFormat = false,
                ageYears = 0,
                isAdult = false,
                normalizedDob = trimmed,
                errorMessage = "اليوم غير مطابق للشهر المحدد."
            )
        }

        val nowCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = nowMillis
        }

        if (dobCal.timeInMillis > nowCal.timeInMillis) {
            return AgeCheckResult(
                isValidFormat = false,
                ageYears = 0,
                isAdult = false,
                normalizedDob = trimmed,
                errorMessage = "لا يمكن أن يكون تاريخ الميلاد في المستقبل."
            )
        }

        var age = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
        val nowMonth = nowCal.get(Calendar.MONTH)
        val dobMonth = dobCal.get(Calendar.MONTH)
        if (nowMonth < dobMonth || (nowMonth == dobMonth && nowCal.get(Calendar.DAY_OF_MONTH) < dobCal.get(Calendar.DAY_OF_MONTH))) {
            age--
        }

        val normalized = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        return if (age < 18) {
            AgeCheckResult(
                isValidFormat = true,
                ageYears = age,
                isAdult = false,
                normalizedDob = normalized,
                errorMessage = "عذراً، هذا التطبيق مخصص للبالغين (+18 سنة) فقط وفق سياسات المتجر والامتثال القانوني."
            )
        } else {
            AgeCheckResult(
                isValidFormat = true,
                ageYears = age,
                isAdult = true,
                normalizedDob = normalized,
                errorMessage = null
            )
        }
    }

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
