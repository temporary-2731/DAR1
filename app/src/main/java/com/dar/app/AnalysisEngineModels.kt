package com.dar.app

/** Corrected per your formula fix: avgDistance = Euclidean distance sqrt(Σ(R-P)²) between
 *  the recorded and parameter vectors, averaged across occurrences. avgVariation = mean
 *  of |R-P| across the vector's own elements, also averaged across occurrences. */
data class ActionSeasonStat(
    val actionId: Long,
    val actionName: String,
    val occurrenceCount: Int,
    val avgDistanceTime: Double?,
    val avgVariationTime: Double?,
    val avgDistanceDuration: Double?,
    val avgVariationDuration: Double?,
    val totalDurationSum: Double,
    val durationPercentage: Double?,
    val avgDistanceQuan1: Double?,
    val avgVariationQuan1: Double?,
    val totalQuan1Sum: Double,
    val quan1Percentage: Double?
)

data class GeneralActionSeasonStat(
    val seasonLabel: String,
    val weekdayName: String,
    val actionStats: List<ActionSeasonStat>,
    val generalDurationTotal: Double,
    val generalDurationAvgVariation: Double?,
    val generalQuan1Total: Double,
    val generalQuan1AvgVariation: Double?
)

fun List<ActionSeasonStat>.sortedByDurationDesc() = sortedByDescending { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByDurationAsc() = sortedBy { it.totalDurationSum }
fun List<ActionSeasonStat>.sortedByFrequencyDesc() = sortedByDescending { it.occurrenceCount }
fun List<ActionSeasonStat>.sortedByFrequencyAsc() = sortedBy { it.occurrenceCount }