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

class DailyAnalysisEngine(private val db: AppDatabase) {

    var debugEnabled: Boolean = false
    val debugLog: StringBuilder = StringBuilder()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun log(line: String) {
        if (debugEnabled) debugLog.appendLine(line)
    }

    data class SeasonRange(val label: String, val start: Date, val end: Date)
    data class ParamKey(val formId: Long, val actionId: Long, val weekday: Int)

    private data class DateMetrics(
        val date: String,
        val time: OccurrenceMetric,
        val duration: OccurrenceMetric,
        val quan1: OccurrenceMetric
    )

    suspend fun computeAllTime(dslaId: Long, generalActionId: Long): List<GeneralPeriodStat> {
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

        val forms = db.analysisFormDao().getFormsForOnce(generalActionId, "DAILY")
        log("Forms found: ${forms.size}")
        if (forms.isEmpty()) {
            log("No forms exist — nothing to compute.")
            return emptyList()
        }

        val paramCache = buildParamCache(forms)
        val allRows = db.recordingDao().getAllRowsForDsla(dslaId)
        log("Total recorded rows for this DSLA: ${allRows.size}")

        val seasons = enumerateSeasons(rangeStart, rangeEnd)
        log("Seasons enumerated: ${seasons.size}")

        val results = mutableListOf<GeneralPeriodStat>()

        for (weekday in 1..7) {
            val allDates = weekdayOccurrencesAcrossRange(weekday, rangeStart, rangeEnd)
            if (allDates.isEmpty()) continue

            // Per-action metrics for EVERY occurrence of this weekday across the whole DSLA.
            val perActionDateMetrics: Map<Long, List<DateMetrics>> = actions.associate { action ->
                action.id to computeActionDateMetrics(action, allDates, forms, paramCache, allRows, weekday)
            }

            // Grand (all-time, level 3) totals per action for this weekday — computed once,
            // then attached to every season-level entry below.
            val grandDurationByAction = actions.associate { it.id to grandAggregate(perActionDateMetrics[it.id].orEmpty().map { dm -> dm.duration }) }
            val grandQuan1ByAction = actions.associate { it.id to grandAggregate(perActionDateMetrics[it.id].orEmpty().map { dm -> dm.quan1 }) }

            for (season in seasons) {
                val actionStats = mutableListOf<ActionPeriodStat>()
                for (action in actions) {
                    val inSeason = perActionDateMetrics[action.id].orEmpty().filter { dm ->
                        val d = dateFormat.parse(dm.date)
                        d != null && !d.before(season.start) && !d.after(season.end)
                    }
                    if (inSeason.isEmpty()) continue

                    val durationAgg = aggregate(inSeason.map { it.duration }, hasTotal = true)
                    val quan1Agg = aggregate(inSeason.map { it.quan1 }, hasTotal = true)
                    val grandDuration = grandDurationByAction[action.id]
                    val grandQuan1 = grandQuan1ByAction[action.id]

                    actionStats.add(
                        ActionPeriodStat(
                            actionId = action.id,
                            actionName = action.name,
                            time = aggregate(inSeason.map { it.time }, hasTotal = false),
                            duration = attachGrand(durationAgg, grandDuration),
                            quan1 = attachGrand(quan1Agg, grandQuan1)
                        )
                    )
                }
                if (actionStats.isEmpty()) continue

                val generalStat = buildGeneralStat(season.label, weekdayNames[weekday - 1], actionStats, perActionDateMetrics, dateSetInRange(season.start, season.end), grandDurationByAction, grandQuan1ByAction, actions)
                results.add(generalStat)
                log("Season '${season.label}' ${weekdayNames[weekday - 1]}: ${actionStats.size} action(s) with data.")
            }

            // All-time (level 3) entry, shown as its own labeled result too.
            val allTimeActionStats = mutableListOf<ActionPeriodStat>()
            for (action in actions) {
                val all = perActionDateMetrics[action.id].orEmpty()
                if (all.isEmpty()) continue
                val durationAgg = aggregate(all.map { it.duration }, hasTotal = true)
                val quan1Agg = aggregate(all.map { it.quan1 }, hasTotal = true)
                val grandDuration = grandDurationByAction[action.id]
                val grandQuan1 = grandQuan1ByAction[action.id]

                allTimeActionStats.add(
                    ActionPeriodStat(
                        actionId = action.id,
                        actionName = action.name,
                        time = aggregate(all.map { it.time }, hasTotal = false),
                        duration = attachGrand(durationAgg, grandDuration),
                        quan1 = attachGrand(quan1Agg, grandQuan1)
                    )
                )
            }
            if (allTimeActionStats.isNotEmpty()) {
                val allDateStrings = allDates.map { dateFormat.format(it) }.toSet()
                val allTimeGeneral = buildGeneralStat("ALL TIME", weekdayNames[weekday - 1], allTimeActionStats, perActionDateMetrics, allDateStrings, grandDurationByAction, grandQuan1ByAction, actions)
                results.add(allTimeGeneral)
                log("ALL TIME ${weekdayNames[weekday - 1]}: ${allTimeActionStats.size} action(s) with data, pooled across every season.")
            }
        }

        log("Computed ${results.size} total (period, weekday) result groups.")
        return results
    }

    /** Computes the level-3 (grand, all-time) total/average for one action's full metric list. */
    private fun grandAggregate(metrics: List<OccurrenceMetric>): Pair<Double?, Double?> {
        if (metrics.isEmpty()) return null to null
        val totals = metrics.mapNotNull { it.total }
        if (totals.isEmpty()) return null to null
        val sum = totals.sum()
        return sum to (sum / totals.size)
    }

    private fun attachGrand(agg: MetricAggregate, grand: Pair<Double?, Double?>?): MetricAggregate {
        return agg.copy(grandTotSum = grand?.first, grandAvgTot = grand?.second)
    }

    private fun dateSetInRange(start: Date, end: Date): Set<String> {
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = start
        while (!cal.time.after(end)) {
            dates.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates.map { dateFormat.format(it) }.toSet()
    }

    private fun buildGeneralStat(
        label: String,
        weekdayName: String,
        actionStats: List<ActionPeriodStat>,
        perActionDateMetrics: Map<Long, List<DateMetrics>>,
        relevantDates: Set<String>,
        grandDurationByAction: Map<Long, Pair<Double?, Double?>>,
        grandQuan1ByAction: Map<Long, Pair<Double?, Double?>>,
        actions: List<ActionEntity>
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

        // General's own grand (all-time) total = sum of every contributing action's grand total.
        val grandGeneralDurationSum = actionIds.mapNotNull { grandDurationByAction[it]?.first }.sumOf { it }
        val grandGeneralDurationCount = actionIds.mapNotNull { grandDurationByAction[it] }.count { it.first != null }
        val grandGeneralDurationAvg = if (grandGeneralDurationCount > 0) grandGeneralDurationSum / grandGeneralDurationCount else null

        val grandGeneralQuan1Sum = actionIds.mapNotNull { grandQuan1ByAction[it]?.first }.sumOf { it }
        val grandGeneralQuan1Count = actionIds.mapNotNull { grandQuan1ByAction[it] }.count { it.first != null }
        val grandGeneralQuan1Avg = if (grandGeneralQuan1Count > 0) grandGeneralQuan1Sum / grandGeneralQuan1Count else null

        return GeneralPeriodStat(
            label = label,
            weekdayName = weekdayName,
            actionStats = actionStats,
            generalTime = aggregate(generalTimeOccurrences, hasTotal = false),
            generalDuration = aggregate(generalDurationOccurrences, hasTotal = true).copy(grandTotSum = grandGeneralDurationSum, grandAvgTot = grandGeneralDurationAvg),
            generalQuan1 = aggregate(generalQuan1Occurrences, hasTotal = true).copy(grandTotSum = grandGeneralQuan1Sum, grandAvgTot = grandGeneralQuan1Avg)
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
            return MetricAggregate(0, null, null, null, emptyList(), null, null, null, null)
        }

        val avgDistance = occurrences.map { it.distance }.average()
        val avgVariation = occurrences.map { it.variation }.average()
        val rates = occurrences.mapNotNull { it.rate }
        val avgRate = if (rates.isEmpty()) null else rates.average()

        if (!hasTotal) {
            return MetricAggregate(occurrences.size, avgDistance, avgVariation, avgRate, emptyList(), null, null, null, null)
        }

        val occurrenceTotals = occurrences.mapNotNull { it.total }
        val totSum = occurrences.sumOf { it.total ?: 0.0 }
        val avgTot = totSum / occurrences.size
        val paramTotals = occurrences.mapNotNull { it.paramTotal }
        val avgParamTotal = if (paramTotals.isEmpty()) null else paramTotals.average()
        val compAvgTot = if (avgParamTotal != null && avgParamTotal > 0.0) (avgTot / avgParamTotal) * 100.0 else null

        return MetricAggregate(occurrences.size, avgDistance, avgVariation, avgRate, occurrenceTotals, totSum, avgTot, avgParamTotal, compAvgTot)
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
        allRows: List<RecordingRow>,
        weekday: Int
    ): List<DateMetrics> {
        val result = mutableListOf<DateMetrics>()

        for (date in allDates) {
            val dateStr = dateFormat.format(date)
            val form = forms.firstOrNull { form ->
                val begin = dateFormat.parse(form.beginDate)
                val end = form.endDate?.let { dateFormat.parse(it) }
                begin != null && !date.before(begin) && (end == null || !date.after(end))
            }
            if (form == null) {
                log("  [${action.name}] $dateStr: no covering form — gap, excluded per spec.")
                continue
            }

            val param = paramCache[ParamKey(form.id, action.id, weekday)]
            if (param == null) {
                log("  [${action.name}] $dateStr: no parameters entered for ${weekdayNames[weekday - 1]} in form ${form.id} — excluded.")
                continue
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

            log("  [${action.name}] $dateStr: duration daily total=${durationMetric.total}")

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

    private fun enumerateSeasons(rangeStart: Date, rangeEnd: Date): List<SeasonRange> {
        val cal = Calendar.getInstance()
        cal.time = rangeStart
        val startYear = cal.get(Calendar.YEAR)
        cal.time = rangeEnd
        val endYear = cal.get(Calendar.YEAR)

        val seasonLabels = listOf("Jan & Feb", "Mar & Apr", "May & Jun", "Jul & Aug", "Sep & Oct", "Nov & Dec")
        val results = mutableListOf<SeasonRange>()
        for (year in startYear..endYear) {
            for (seasonIndex in 0..5) {
                val firstMonth = seasonIndex * 2
                val start = Calendar.getInstance().apply {
                    set(year, firstMonth, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                val end = Calendar.getInstance().apply {
                    set(year, firstMonth, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, 2)
                    add(Calendar.DAY_OF_MONTH, -1)
                }.time
                results.add(SeasonRange("${seasonLabels[seasonIndex]} $year", start, end))
            }
        }
        return results
    }

    private fun weekdayOccurrencesAcrossRange(weekday: Int, rangeStart: Date, rangeEnd: Date): List<Date> {
        if (rangeStart.after(rangeEnd)) return emptyList()
        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = rangeStart
        while (!cal.time.after(rangeEnd)) {
            if (cal.get(Calendar.DAY_OF_WEEK) == weekday) {
                dates.add(cal.time)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }
}