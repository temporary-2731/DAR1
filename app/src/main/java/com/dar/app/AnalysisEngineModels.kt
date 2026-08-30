package com.dar.app

/** One occurrence's (e.g. one Monday's) computed metric for a single vector comparison. */
data class OccurrenceMetric(
    val date: String,
    val distance: Double,           // sqrt(Σ(R-P)²)
    val variation: Double,          // Σ|R-P|  (L1 norm — always >= distance, by the L2<=L1 inequality)
    val rate: Double?,              // distance/variation as a percentage; null if variation is 0
    val total: Double?,             // Σ R elements; null for Time (Time has no "total" concept)
    val paramTotal: Double?,        // Σ P elements; null for Time
    val comp: Double?               // total/paramTotal as a percentage; null if paramTotal is 0 or Time
)

/** Rolled-up stats across a set of occurrences (either one season's 8-9 weekdays, or the
 *  entire pooled history for that weekday across every season — same shape either way). */
data class MetricAggregate(
    val occurrenceCount: Int,
    val avgDistance: Double?,
    val avgVariation: Double?,
    val avgRate: Double?,           // average of each occurrence's own rate
    val totSum: Double?,            // sum of totals across occurrences; null for Time
    val avgTot: Double?,            // totSum / occurrenceCount; null for Time
    val avgParamTotal: Double?,     // average of paramTotal across occurrences; null for Time
    val compAvgTot: Double?         // avgTot / avgParamTotal as a percentage; null for Time
)

data class ActionPeriodStat(
    val actionId: Long,
    val actionName: String,
    val time: MetricAggregate,
    val duration: MetricAggregate,
    val quan1: MetricAggregate
)

data class GeneralPeriodStat(
    val label: String,         // e.g. "May & Jun 2026" or "ALL TIME"
    val weekdayName: String,
    val actionStats: List<ActionPeriodStat>,
    val generalTime: MetricAggregate,
    val generalDuration: MetricAggregate,
    val generalQuan1: MetricAggregate
)

fun List<ActionPeriodStat>.sortedByDurationTotalDesc() = sortedByDescending { it.duration.totSum ?: 0.0 }
fun List<ActionPeriodStat>.sortedByDurationTotalAsc() = sortedBy { it.duration.totSum ?: 0.0 }
fun List<ActionPeriodStat>.sortedByFrequencyDesc() = sortedByDescending { it.duration.occurrenceCount }
fun List<ActionPeriodStat>.sortedByFrequencyAsc() = sortedBy { it.duration.occurrenceCount }