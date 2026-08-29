package com.dar.app

data class ActionSeasonStat(
    val actionId: Long,
    val actionName: String,
    val occurrenceCount: Int,
    val avgStdDevTime: Double?,
    val avgStdDevDuration: Double?,
    val totalDurationSum: Double,
    val durationPercentage: Double?,
    val avgStdDevQuan1: Double?,
    val totalQuan1Sum: Double,
    val quan1Percentage: Double?
)

data class GeneralActionSeasonStat(
    val seasonLabel: String,
    val weekdayName: String,
    val actionStats: List<ActionSeasonStat>,
    val generalDurationTotal: Double,
    val generalDurationAvgStdDev: Double?,
    val generalQuan1Total: Double,
    val generalQuan1AvgStdDev: Double?
)

fun List<ActionSeasonStat>.sortedByDurationDesc() = sortedByDescending { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByDurationAsc() = sortedBy { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByFrequencyDesc() = sortedByDescending { it.occurrenceCount }
fun List<ActionSeasonStat>.sortedByFrequencyAsc() = sortedBy { it.occurrenceCount }