package com.dar.app

data class OccurrenceMetric(
    val date: String,
    val distance: Double,
    val variation: Double,
    val rate: Double?,
    val total: Double?,       // this single occurrence's own daily total (level 1) — null for Time
    val paramTotal: Double?,
    val comp: Double?
)

/**
 * Rolled-up stats for one grouping (a season, a week, or "all-time"). totSum/avgTot are
 * THIS grouping's own total/average (level 2 for a season/week; same fields double as
 * level 3 when this aggregate represents the all-time pooled grouping itself).
 * grandTotSum/grandAvgTot are the all-time (level 3) total/average, attached here too so
 * every season/week-level result shows both its own total AND the all-time total side by
 * side, rather than all-time being a separate disconnected entry.
 */
data class MetricAggregate(
    val occurrenceCount: Int,
    val avgDistance: Double?,
    val avgVariation: Double?,
    val avgRate: Double?,
    val occurrenceTotals: List<Double> = emptyList(), // level 1 — raw per-day totals, for transparency
    val totSum: Double?,          // level 2 — this season/week's own total
    val avgTot: Double?,          // level 2 — this season/week's own average
    val avgParamTotal: Double?,
    val compAvgTot: Double?,
    val grandTotSum: Double? = null,   // level 3 — all-time total (same value on every entry for this weekday/DSLA)
    val grandAvgTot: Double? = null,   // level 3 — all-time average
    val grandAvgParamTotal: Double? = null,
    val grandCompAvgTot: Double? = null
)

data class ActionPeriodStat(
    val actionId: Long,
    val actionName: String,
    val time: MetricAggregate,
    val duration: MetricAggregate,
    val quan1: MetricAggregate
)

data class GeneralPeriodStat(
    val label: String,
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