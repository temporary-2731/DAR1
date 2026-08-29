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
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Daily analysis engine per your spec: Gregorian 2-month "seasons," grouped by weekday,
 * comparing recorded data against each covering form's parameter vectors as a
 * "deviation from goal." 24-hour time and Gregorian calendar only — 12-hour and Ethiopian
 * calendar are deferred, per our agreed sequencing.
 *
 * Set [debugEnabled] = true before calling to populate [debugLog] with a full step-by-step
 * trace, used by the temporary Engine Check screen.
 */
class DailyAnalysisEngine(private val db: AppDatabase) {

    var debugEnabled: Boolean = false
    val debugLog: StringBuilder = StringBuilder()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun log(line: String) {
        if (debugEnabled) debugLog.appendLine(line)
    }

    data class SeasonRange(val label: String, val start: Date, val end: Date)

    private data class MetricResult(val avgStdDev: Double?, val totalSum: Double, val percentage: Double?)

    suspend fun computeAllTime(dslaId: Long, generalActionId: Long): List<GeneralActionSeasonStat> {
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

        val results = mutableListOf<GeneralActionSeasonStat>()
        for (season in seasons) {
            for (weekday in 1..7) {
                val stat = computeSeasonWeekday(
                    actions, forms, paramCache, allRows, season, weekday, rangeStart, rangeEnd
                )
                if (stat.actionStats.any { it.occurrenceCount > 0 }) {
                    results.add(stat)
                }
            }
        }
        log("Computed ${results.size} non-empty (season, weekday) results.")
        return results
    }

    private suspend fun buildParamCache(forms: List<AnalysisForm>): Map<Pair<Long, Long>, AnalysisFormActionParam> {
        val map = mutableMapOf<Pair<Long, Long>, AnalysisFormActionParam>()
        for (form in forms) {
            val params = db.analysisFormDao().getParamsForForm(form.id).first()
            for (p in params) {
                map[form.id to p.actionId] = p
            }
        }
        return map
    }

    fun computeSeasonWeekday(
        actions: List<ActionEntity>,
        forms: List<AnalysisForm>,
        paramCache: Map<Pair<Long, Long>, AnalysisFormActionParam>,
        allRows: List<RecordingRow>,
        season: SeasonRange,
        weekday: Int,
        rangeStart: Date,
        rangeEnd: Date
    ): GeneralActionSeasonStat {
        val occurrenceDates = weekdayOccurrencesInSeason(season, weekday, rangeStart, rangeEnd)
        if (occurrenceDates.isNotEmpty()) {
            log("--- ${season.label}, ${weekdayNames[weekday - 1]}: ${occurrenceDates.size} occurrence date(s): ${occurrenceDates.joinToString { dateFormat.format(it) }}")
        }

        val actionStats = mutableListOf<ActionSeasonStat>()
        for (action in actions) {
            actionStats.add(computeActionForOccurrences(action, occurrenceDates, forms, paramCache, allRows))
        }

        val generalDurationTotal = actionStats.sumOf { it.totalDurationSum }
        val generalDurationStdDev = averageOf(actionStats.mapNotNull { it.avgStdDevDuration })
        val generalQuan1Total = actionStats.sumOf { it.totalQuan1Sum }
        val generalQuan1StdDev = averageOf(actionStats.mapNotNull { it.avgStdDevQuan1 })

        if (actionStats.any { it.occurrenceCount > 0 }) {
            log("General rollup — duration total: $generalDurationTotal, avg std dev: $generalDurationStdDev, quan1 total: $generalQuan1Total, avg std dev: $generalQuan1StdDev")
        }

        return GeneralActionSeasonStat(
            seasonLabel = season.label,
            weekdayName = weekdayNames[weekday - 1],
            actionStats = actionStats,
            generalDurationTotal = generalDurationTotal,
            generalDurationAvgStdDev = generalDurationStdDev,
            generalQuan1Total = generalQuan1Total,
            generalQuan1AvgStdDev = generalQuan1StdDev
        )
    }

    private fun computeActionForOccurrences(
        action: ActionEntity,
        occurrenceDates: List<Date>,
        forms: List<AnalysisForm>,
        paramCache: Map<Pair<Long, Long>, AnalysisFormActionParam>,
        allRows: List<RecordingRow>
    ): ActionSeasonStat {
        var maxDimension = 1
        val perOccurrenceTime = mutableListOf<Pair<List<Double>, List<Double>>>()
        val perOccurrenceDuration = mutableListOf<Pair<List<Double>, List<Double>>>()
        val perOccurrenceQuan1 = mutableListOf<Pair<List<Double>, List<Double>>>()

        for (date in occurrenceDates) {
            val dateStr = dateFormat.format(date)
            val form = forms.firstOrNull { form ->
                val begin = dateFormat.parse(form.beginDate)
                val end = form.endDate?.let { dateFormat.parse(it) }
                begin != null && !date.before(begin) && (end == null || !date.after(end))
            }
            if (form == null) {
                log("  [${action.name}] $dateStr: no covering form — gap, excluded per spec.")
                continue // missing day (gap between forms) — excluded per spec
            }

            val param = paramCache[form.id to action.id]
            if (param == null) {
                log("  [${action.name}] $dateStr: form ${form.id} has no parameters entered yet — excluded.")
                continue
            }
            maxDimension = maxOf(maxDimension, param.dimension)

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

            perOccurrenceTime.add(timeGrouped to timeParam)
            perOccurrenceDuration.add(durationGrouped to durationParam)
            perOccurrenceQuan1.add(quan1Grouped to quan1Param)

            log("  [${action.name}] $dateStr: recorded duration=$rawDurations -> grouped(dim=${param.dimension})=$durationGrouped, param=$durationParam")
        }

        if (perOccurrenceDuration.isEmpty()) {
            return ActionSeasonStat(
                action.id, action.name, 0, null, null, 0.0, null, null, 0.0, null
            )
        }

        val timeResult = computeMetric(padAllTo(perOccurrenceTime, maxDimension), needsPercentage = false)
        val durationResult = computeMetric(padAllTo(perOccurrenceDuration, maxDimension), needsPercentage = true)
        val quan1Result = computeMetric(padAllTo(perOccurrenceQuan1, maxDimension), needsPercentage = true)

        log("  [${action.name}] TIME stdDev=${timeResult.avgStdDev} | DURATION total=${durationResult.totalSum} stdDev=${durationResult.avgStdDev} pct=${durationResult.percentage} | QUAN1 total=${quan1Result.totalSum} stdDev=${quan1Result.avgStdDev} pct=${quan1Result.percentage}")

        return ActionSeasonStat(
            actionId = action.id,
            actionName = action.name,
            occurrenceCount = perOccurrenceDuration.size,
            avgStdDevTime = timeResult.avgStdDev,
            avgStdDevDuration = durationResult.avgStdDev,
            totalDurationSum = durationResult.totalSum,
            durationPercentage = durationResult.percentage,
            avgStdDevQuan1 = quan1Result.avgStdDev,
            totalQuan1Sum = quan1Result.totalSum,
            quan1Percentage = quan1Result.percentage
        )
    }

    private fun computeMetric(perOccurrence: List<Pair<List<Double>, List<Double>>>, needsPercentage: Boolean): MetricResult {
        if (perOccurrence.isEmpty()) return MetricResult(null, 0.0, null)

        val deviationVectors = perOccurrence.map { (recorded, param) ->
            recorded.indices.map { recorded[it] - param[it] }
        }
        val stdDevVector = elementwiseStdDev(deviationVectors)
        val avgStdDev = averageOf(stdDevVector)
        val totalSum = perOccurrence.sumOf { it.first.sum() }

        val percentage = if (!needsPercentage) {
            null
        } else {
            val paramSum = perOccurrence.sumOf { it.second.sum() }
            if (paramSum == 0.0) null else (totalSum / paramSum) * 100.0
        }
        return MetricResult(avgStdDev, totalSum, percentage)
    }

    private fun padAllTo(list: List<Pair<List<Double>, List<Double>>>, dimension: Int): List<Pair<List<Double>, List<Double>>> {
        return list.map { (recorded, param) -> padTo(recorded, dimension) to padTo(param, dimension) }
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

    private fun padTo(values: List<Double>, length: Int): List<Double> {
        return if (values.size >= length) values.take(length) else values + List(length - values.size) { 0.0 }
    }

    private fun parseVector(csv: String, dimension: Int, parser: (String) -> Double?): List<Double> {
        if (csv.isBlank()) return List(dimension) { 0.0 }
        val parsed = csv.split(",").map { parser(it.trim()) ?: 0.0 }
        return padTo(parsed, dimension)
    }

    private fun elementwiseStdDev(vectors: List<List<Double>>): List<Double> {
        if (vectors.isEmpty()) return emptyList()
        val dim = vectors[0].size
        return (0 until dim).map { d ->
            val values = vectors.map { it[d] }
            val mean = values.average()
            sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        }
    }

    private fun averageOf(values: List<Double>): Double? {
        return if (values.isEmpty()) null else values.average()
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

    private fun weekdayOccurrencesInSeason(season: SeasonRange, weekday: Int, rangeStart: Date, rangeEnd: Date): List<Date> {
        val effectiveStart = if (season.start.before(rangeStart)) rangeStart else season.start
        val effectiveEnd = if (season.end.after(rangeEnd)) rangeEnd else season.end
        if (effectiveStart.after(effectiveEnd)) return emptyList()

        val dates = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        cal.time = effectiveStart
        while (!cal.time.after(effectiveEnd)) {
            if (cal.get(Calendar.DAY_OF_WEEK) == weekday) {
                dates.add(cal.time)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return dates
    }
}