package com.dar.app

/** One weekday-in-a-season's worth of stats for a single Action. */
data class ActionSeasonStat(
    val actionId: Long,
    val actionName: String,
    val occurrenceCount: Int,
    val avgStdDevTime: Double?,       // null if zero occurrences that season
    val avgStdDevDuration: Double?,
    val totalDurationSum: Double,     // summed grouped-vector duration, across occurrences
    val durationPercentage: Double?,  // null if the parameter's duration sum was zero (skipped, not rated)
    val avgStdDevQuan1: Double?,
    val totalQuan1Sum: Double,
    val quan1Percentage: Double?
)

/** The General Action's rollup for one weekday-in-a-season (sum across its Actions). */
data class GeneralActionSeasonStat(
    val seasonLabel: String,      // e.g. "May & Jun 2026"
    val weekdayName: String,      // "Monday", "Tuesday", ...
    val actionStats: List<ActionSeasonStat>,
    val generalDurationTotal: Double,
    val generalDurationAvgStdDev: Double?,
    val generalQuan1Total: Double,
    val generalQuan1AvgStdDev: Double?
)

/** One Action's history rolled up across every season for one weekday (e.g. every Monday ever). */
data class ActionAllTimeWeekdayStat(
    val actionId: Long,
    val actionName: String,
    val weekdayName: String,
    val seasonCount: Int,                 // how many seasons contributed data
    val seasonDurationTotals: List<Double>,
    val avgSeasonDuration: Double?,
    val avgSeasonDurationStdDev: Double?,  // std dev of the season-level totals around their own average
    val allTimeDurationSum: Double,
    val allTimeDurationPercentage: Double? // vs. summed parameter target across all contributing seasons; null if that sum is zero
)

/** Sort helpers — reused later by Report for "sort by size" / "sort by frequency" views. */
fun List<ActionSeasonStat>.sortedByDurationDesc() = sortedByDescending { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByDurationAsc() = sortedBy { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByFrequencyDesc() = sortedByDescending { it.occurrenceCount }
fun List<ActionSeasonStat>.sortedByFrequencyAsc() = sortedBy { it.occurrenceCount }
