package nl.part66l.logbook.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How close something is to running out. Drives colour only — the app never gates certification on it. */
enum class WarningLevel { NONE, AMBER, RED }

/**
 * How many days ahead of a licence expiry or a recency lapse to start warning. User-configurable
 * in Settings, because how much notice is useful depends on how quickly the holder can arrange a
 * renewal or find work to log.
 */
data class WarningThresholds(
    val licenceAmberDays: Int = DEFAULT_LICENCE_AMBER_DAYS,
    val licenceRedDays: Int = DEFAULT_LICENCE_RED_DAYS,
    val recencyAmberDays: Int = DEFAULT_RECENCY_AMBER_DAYS,
    val recencyRedDays: Int = DEFAULT_RECENCY_RED_DAYS,
) {
    companion object {
        const val DEFAULT_LICENCE_AMBER_DAYS = 90
        const val DEFAULT_LICENCE_RED_DAYS = 30
        const val DEFAULT_RECENCY_AMBER_DAYS = 60
        const val DEFAULT_RECENCY_RED_DAYS = 30
    }
}

/**
 * Warning levels for the licence's own expiry date and for a subcategory's recency lapse.
 *
 * These describe only what this device's records show. The app cannot see work logged on paper or
 * before install, so a warning means "nothing here keeps you current past this date", never "you
 * are not current" — see the EULA position in the spec's privacy and liability section.
 */
object ExpiryWarning {

    /** [expiry] null (never entered) warns about nothing — there's no date to count down to. */
    fun forLicence(expiry: LocalDate?, today: LocalDate, thresholds: WarningThresholds): WarningLevel {
        if (expiry == null) return WarningLevel.NONE
        return level(ChronoUnit.DAYS.between(today, expiry), thresholds.licenceAmberDays, thresholds.licenceRedDays)
    }

    /**
     * [current] false means the records already show a lapse, which is red regardless of dates.
     * Otherwise counts down to [lapseDate] — the last day the subcategory stays current if nothing
     * further is logged, so it moves outward every time work is entered.
     */
    fun forRecency(current: Boolean, lapseDate: LocalDate?, today: LocalDate, thresholds: WarningThresholds): WarningLevel {
        if (!current) return WarningLevel.RED
        if (lapseDate == null) return WarningLevel.NONE
        return level(ChronoUnit.DAYS.between(today, lapseDate), thresholds.recencyAmberDays, thresholds.recencyRedDays)
    }

    /** The most severe of [levels] — what a single shared indicator (the Recency tab icon) should show. */
    fun highest(levels: Iterable<WarningLevel>): WarningLevel = levels.maxByOrNull { it.ordinal } ?: WarningLevel.NONE

    private fun level(daysRemaining: Long, amberDays: Int, redDays: Int): WarningLevel = when {
        daysRemaining <= redDays -> WarningLevel.RED
        daysRemaining <= amberDays -> WarningLevel.AMBER
        else -> WarningLevel.NONE
    }
}
