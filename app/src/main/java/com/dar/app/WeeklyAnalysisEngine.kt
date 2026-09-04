package com.dar.app

import com.dar.app.data.ActionEntity
import com.dar.app.data.AnalysisForm
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import com.dar.app.data.RecordingRow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Weekly analysis engine. Structurally inverted from Daily: instead of fixing the weekday
 * and varying the instance (Mon1, Mon2...within a season), Weekly fixes the WEEK and varies
 * the weekday within it (Mon, Tue, ...Sun of that week). Every single day in the DSLA's
 * history gets its own occurrence, grouped by the Monday-anchored week it falls in. The
 * Grand Total naturally pools the ENTIRE DSLA history in one go, since there's no per-weekday
 * restriction at the top level — exactly as specified.
 */
class WeeklyAnalysisEngine(private val db: AppDatabase) {

    var debugEnabled: Boolean = false
    val debugLog: StringBuilder = StringBuilder()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun log(line: String) {
        if (debugEnabled) debugLog.appendLine(line)
    }

    data class ParamKey(val formId: Long, val actionId: Long, val weekday: Int)

    private data class DateMetrics(
        val date: String,
        val time: OccurrenceMetric,
        val duration: OccurrenceMetric,
        val quan1: OccurrenceMetric
    )

    suspend fun computeAll(dslaId: Long, generalActionId: Long): List<GeneralPeriodStat> {
        debugLog.clear()
        val dsla = db.dslaDao().getById(dslaId)
        if (dsla == null) {
            log("ERROR: DSLA not found.")
            return emptyList()
        }
        val rangeStart = dateFormat.parse(dsla.beginDate)
        if (rangeStart == null) {
            log("ERROR: could not parse DSLA beginDate '${dsla.beginDate}'.")
            return emptyList()
        }
        val rangeEnd = dsla.endDate?.let { dateFormat.parse(it) } ?: Date()
        log("DSLA range: ${dateFormat.format(rangeStart)} to ${dateFormat.format(rangeEnd)}")

        val actions = db.generalActionDao().getActionsInGeneral(generalActionId).first()
        log("Actions in this General Action: ${actions.joinToString { it.name }}")

        val forms = db.analysisFormDao().getFormsForOnce(generalActionId, "WEEKLY")
        log("Weekly forms found: ${forms.size}")
        if (forms.isEmpty()) {
            log("No Weekly forms exist — nothing to compute.")
            return emptyList()
        }

        val paramCache = buildParamCache(forms)
        val allRows = db.recordingDao().getAllRowsForDsla(dslaId)
        log("Total recorded rows for this DSLA: ${allRows.size}")

        val allDates = allDatesInRange(rangeStart, rangeEnd)
        log("Total days in DSLA span: ${allDates.size}")

        // Per-action metrics for EVERY day in the DSLA's history.
        val perActionDateMetrics: Map<Long, List<DateMetrics>> = actions.associate { action ->
            action.id to computeActionDateMetrics(action, allDates, forms, paramCache, allRows)
        }

        val results = mutableListOf<GeneralPeriodStat>()

        // Group into weeks (Monday-anchored).
        val weekGroups = allDates.groupBy { mondayOfWeek(it) }.toSortedMap()
        for ((weekStart, datesInWeek) in weekGroups) {
            val weekLabel = "Week of ${dateFormat.format(weekStart)}"
            val actionStats = mutableListOf<ActionPeriodStat>()
            for (action in actions) {
                val dateStrings = datesInWeek.map { dateFormat.format(it) }.toSet()
                val inWeek = perActionDateMetrics[action.id].orEmpty().filter { it.date in dateStrings }
                if (inWeek.isEmpty()) continue
                actionStats.add(buildActionStat(action, inWeek))
            }
            if (actionStats.isEmpty()) continue
            results.add(buildGeneralStat(weekLabel, "—", actionStats, perActionDateMetrics, datesInWeek.map { dateFormat.format(it) }.toSet()))
            log("$weekLabel: ${actionStats.size} action(s) with data.")
        }

        // Grand total: pool every single day across the entire DSLA history.
        val allTimeActionStats = mutableListOf<ActionPeriodStat>()
        for (action in actions) {
            val all = perActionDateMetrics[action.id].orEmpty()
            if (all.isEmpty()) continue
            allTimeActionStats.add(buildActionStat(action, all))
        }
        if (allTimeActionStats.isNotEmpty()) {
            val allDateStrings = allDates.map { dateFormat.format(it) }.toSet()
            results.add(buildGeneralStat("GRAND TOTAL (entire DSLA history)", "—", allTimeActionStats, perActionDateMetrics, allDateStrings))
            log("GRAND TOTAL: ${allTimeActionStats.size} action(s) with data, pooled across the whole DSLA span.")
        }

        log("Computed ${results.size} total (week / grand total) result groups.")
        return results
    }

    private fun buildActionStat(action: ActionEntity, dateMetrics: List<DateMetrics>): ActionPeriodStat {
        return ActionPeriodStat(
            actionId = action.id,
            actionName = action.name,
            time = aggregate(dateMetrics.map { it.time }, hasTotal = false),
            duration = aggregate(dateMetrics.map { it.duration }, hasTotal = true),
            quan1 = aggregate(dateMetrics.map { it.quan1 }, hasTotal = true)
        )
    }

    private fun buildGeneralStat(
        label: String,
        weekdayName: String,
        actionStats: List<ActionPeriodStat>,
        perActionDateMetrics: Map<Long, List<DateMetrics>>,
        relevantDates: Set<String>
    ): GeneralPeriodStat {
        val actionIds = actionStats.map { it.actionId }.toSet()
        val relevantLists = perActionDateMetrics.filterKeys { it in actionIds }
            .mapValues { (_, list) -> list.filter { it.date in relevantDates } }

        val allDates = relevantLists.values.flatten().map { it.date }.toSortedSet()

        val generalTimeOccurrences = mutableListOf<OccurrenceMetric>()
        val generalDurationOccurrences = mutableListOf<OccurrenceMetric>()
        val generalQuan1Occurrences = mutableListOf<OccurrenceMetric>()

        for (date in allDates) {
            val contributingTime = relevantLists.values.mapNotNull { list -> list.firstOrNull { it.date == date }?.time }
            val contributingDuration = relevantLists.values.mapNotNull { list -> list.firstOrNull { it.date == date }?.duration }
            val contributingQuan1 = relevantLists.values.mapNotNull { list -> list.firstOrNull { it.date == date }?.quan1 }

            if (contributingTime.isNotEmpty()) generalTimeOccurrences.add(combineGeneral(date, contributingTime, hasTotal = false))
            if (contributingDuration.isNotEmpty()) generalDurationOccurrences.add(combineGeneral(date, contributingDuration, hasTotal = true))
            if (contributingQuan1.isNotEmpty()) generalQuan1Occurrences.add(combineGeneral(date, contributingQuan1, hasTotal = true))
        }

        return GeneralPeriodStat(
            label = label,
            weekdayName = weekdayName,
            actionStats = actionStats,
            generalTime = aggregate(generalTimeOccurrences, hasTotal = false),
            generalDuration = aggregate(generalDurationOccurrences, hasTotal = true),
            generalQuan1 = aggregate(generalQuan1Occurrences, hasTotal = true)
        )
    }

    private fun combineGeneral(date: String, contributing: List<OccurrenceMetric>, hasTotal: Boolean): OccurrenceMetric {
        val distanceG = sqrt(contributing.sumOf { it.distance * it.distance })
        val variationG = contributing.sumOf { it.variation }
        val rateG = if (variationG > 0.0) (distanceG / variationG) * 100.0 else null

        val totalG = if (hasTotal) contributing.sumOf { it.total ?: 0.0 } else null
        val paramTotalG = if (hasTotal) contributing.sumOf { it.paramTotal ?: 0.0 } else null
        val compG = if (hasTotal && paramTotalG != null && paramTotalG > 0.0) (totalG!! / paramTotalG) * 100.0 else null

        return OccurrenceMetric(date, distanceG, variationG, rateG, totalG, paramTotalG, compG)
    }

    private fun aggregate(occurrences: List<OccurrenceMetric>, hasTotal: Boolean): MetricAggregate {
        if (occurrences.isEmpty()) {
            return MetricAggregate(0, null, null, null, null, null, null, null)
        }

        val avgDistance = occurrences.map { it.distance }.average()
        val avgVariation = occurrences.map { it.variation }.average()
        val rates = occurrences.mapNotNull { it.rate }
        val avgRate = if (rates.isEmpty()) null else rates.average()

        if (!hasTotal) {
            return MetricAggregate(occurrences.size, avgDistance, avgVariation, avgRate, null, null, null, null)
        }

        val totSum = occurrences.sumOf { it.total ?: 0.0 }
        val avgTot = totSum / occurrences.size
        val paramTotals = occurrences.mapNotNull { it.paramTotal }
        val avgParamTotal = if (paramTotals.isEmpty()) null else paramTotals.average()
        val compAvgTot = if (avgParamTotal != null && avgParamTotal > 0.0) (avgTot / avgParamTotal) * 100.0 else null

        return MetricAggregate(occurrences.size, avgDistance, avgVariation, avgRate, totSum, avgTot, avgParamTotal, compAvgTot)
    }

    private suspend fun buildParamCache(forms: List<AnalysisForm>): Map<ParamKey, AnalysisFormActionParam> {
        val map = mutableMapOf<ParamKey, AnalysisFormActionParam>()
        for (form in forms) {
            val params = db.analysisFormDao().getParamsForForm(form.id).first()
            for (p in params) {
                map[ParamKey(form.id, p.actionId, p.weekday)] = p
            }
        }
        return map
    }

    private fun computeActionDateMetrics(
        action: ActionEntity,
        allDates: List<Date>,
        forms: List<AnalysisForm>,
        paramCache: Map<ParamKey, AnalysisFormActionParam>,
        allRows: List<RecordingRow>
    ): List<DateMetrics> {
        val result = mutableListOf<DateMetrics>()

        for (date in allDates) {
            val dateStr = dateFormat.format(date)
            val cal = Calendar.getInstance()
            cal.time = date
            val weekday = cal.get(Calendar.DAY_OF_WEEK)

            val form = forms.firstOrNull { form ->
                val begin = dateFormat.parse(form.beginDate)
                val end = form.endDate?.let { dateFormat.parse(it) }
                begin != null && !date.before(begin) && (end == null || !date.after(end))
            }
            if (form == null) {
                continue // gap — excluded, same rule as Daily
            }

            val param = paramCache[ParamKey(form.id, action.id, weekday)]
            if (param == null) {
                continue // no parameters entered for this weekday — excluded
            }

            val rowsThatDay = allRows.filter { it.dslaId == form.dslaId && it.date == dateStr && it.actionName == action.name }

            val rawTimes = rowsThatDay.mapNotNull { parseTimeToMinutesStandalone(it.timeValue) }
            val rawDurations = rowsThatDay.mapNotNull { it.durationValue.toDoubleOrNull() }
            val rawQuan1 = rowsThatDay.mapNotNull { it.quan1.toDoubleOrNull() }

            val timeGrouped = groupIntoDimension(rawTimes, param.dimension)
            val durationGrouped = groupIntoDimension(rawDurations, param.dimension)
            val quan1Grouped = groupIntoDimension(rawQuan1, param.dimension)

            val timeParam = parseVector(param.timeVector, param.dimension) { parseTimeToMinutesStandalone(it) }
            val durationParam = parseVector(param.durationVector, param.dimension) { it.toDoubleOrNull() }
            val quan1Param = parseVector(param.quan1Vector, param.dimension) { it.toDoubleOrNull() }

            val timeMetric = computeOccurrenceMetric(dateStr, timeGrouped, timeParam, hasTotal = false)
            val durationMetric = computeOccurrenceMetric(dateStr, durationGrouped, durationParam, hasTotal = true)
            val quan1Metric = computeOccurrenceMetric(dateStr, quan1Grouped, quan1Param, hasTotal = true)

            result.add(DateMetrics(dateStr, timeMetric, durationMetric, quan1Metric))
        }
        return result
    }

    private fun computeOccurrenceMetric(date: String, recorded: List<Double>, param: List<Double>, hasTotal: Boolean): OccurrenceMetric {
        val n = minOf(recorded.size, param.size)
        val diffs = (0 until n).map { recorded[it] - param[it] }
        val distance = sqrt(diffs.sumOf { it * it })
        val variation = diffs.sumOf { abs(it) }
        val rate = if (variation > 0.0) (distance / variation) * 100.0 else null

        val total = if (hasTotal) recorded.sum() else null
        val paramTotal = if (hasTotal) param.sum() else null
        val comp = if (hasTotal && paramTotal != null && paramTotal > 0.0) (total!! / paramTotal) * 100.0 else null

        return OccurrenceMetric(date, distance, variation, rate, total, paramTotal, comp)
    }

    private fun groupIntoDimension(values: List<Double>, dimension: Int): List<Double> {
        if (dimension <= 0) return emptyList()
        if (values.size <= dimension) {
            return values + List(dimension - values.size) { 0.0 }
        }
        val base = values.size / dimension
        val remainder = values.size % dimension
        val result = mutableListOf<Double>()
        var index = 0
        for (g in 0 until dimension) {
            val groupSize = base + if (g < remainder) 1 else 0
            val group = values.subList(index, index + groupSize)
            result.add(if (group.isEmpty()) 0.0 else group.average())
            index += groupSize
        }
        return result
    }

    private fun parseVector(csv: String, dimension: Int, parser: (String) -> Double?): List<Double> {
        if (csv.isBlank()) return List(dimension) { 0.0 }
        val parsed = csv.split(",").map { parser(it.trim()) ?: 0.0 }
        return if (parsed.size >= dimension) parsed.take(dimension) else parsed + List(dimension - parsed.size) { 0.0 }
    }

    private fun parseTimeToMinutesStandalone(value: String): Double? {
        if (value.isBlank()) return null
        val f = value.trim().toDoubleOrNull() ?: return null
        if (f < 0) return null
        val hours = floor(f).toInt()
        val fractional = f - hours
        val minutes = Math.round(fractional * 100.0)
        if (minutes >= 60) return null
        return (hours * 60 + minutes).toDouble()
    }

    private fun allDatesInRange(start: Date, end: Date): List<Date> {
        if (start.after(end)) return emptyList()
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = start
        while (!cal.time.after(end)) {
            dates.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }

    /** Returns the Monday on/before the given date, used as that week's identifying key. */
    private fun mondayOfWeek(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.firstDayOfWeek = Calendar.MONDAY
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
