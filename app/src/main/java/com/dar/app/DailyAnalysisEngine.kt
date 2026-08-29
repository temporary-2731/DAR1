package com.dar.app

import com.dar.app.data.AnalysisForm
import com.dar.app.data.AnalysisFormActionParam
import com.dar.app.data.AppDatabase
import com.dar.app.data.RecordingRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Computes the Daily analysis engine's statistics for one General Action, per your spec:
 * Gregorian 2-month "seasons" (Jan&Feb, Mar&Apr, ... Nov&Dec), grouped by weekday, comparing
 * recorded data against each covering form's parameter vectors as a "deviation from goal."
 *
 * This is pure calculation logic — no screen displays its output yet. It's ready to be wired
 * to a results screen once the parameter-entry grid (which fills the AnalysisFormActionParam
 * vectors) exists. 12-hour format and the Ethiopian calendar are deferred, per our agreed
 * sequencing — this engine assumes 24-hour time and the Gregorian calendar.
 */
class DailyAnalysisEngine(private val db: AppDatabase) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val weekdayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** Computes every (season, weekday) combination that falls within the DSLA's history,
     *  then rolls each Action's results up across all seasons for each weekday. */
    suspend fun computeAllTime(dslaId: Long, generalActionId: Long): List<ActionAllTimeWeekdayStat> {
        val dsla = db.dslaDao().getById(dslaId) ?: return emptyList()
        val rangeStart = dateFormat.parse(dsla.beginDate) ?: return emptyList()
        val rangeEnd = dsla.endDate?.let { dateFormat.parse(it) } ?: Date()

        val actions = db.generalActionDao().getActionsInGeneral(generalActionId).let {
            kotlinx.coroutines.flow.first(it)
        }
        val forms = db.analysisFormDao().getFormsForOnce(generalActionId, "DAILY")
        val allRows = db.recordingDao().getAllRowsForDsla(dslaId)

        val perActionSeasonTotals = mutableMapOf<Pair<Long, Int>, MutableList<Double>>() // (actionId, weekday) -> season totals
        val perActionParamTotals = mutableMapOf<Pair<Long, Int>, MutableList<Double>>()  // matching parameter sums, aligned by index

        for (weekday in 1..7) { // Calendar.SUNDAY=1 .. Calendar.SATURDAY=7
            for (season in enumerateSeasons(rangeStart, rangeEnd)) {
                val stat = computeSeasonWeekday(
                    dslaId, generalActionId, actions, forms, allRows,
                    season, weekday, rangeStart, rangeEnd
                )
                for (actionStat in stat.actionStats) {
                    if (actionStat.occurrenceCount == 0) continue // season contributed nothing for this action
                    val key = actionStat.actionId to weekday
                    perActionSeasonTotals.getOrPut(key) { mutableListOf() }.add(actionStat.totalDurationSum)
                }
            }
        }

        val results = mutableListOf<ActionAllTimeWeekdayStat>()
        for ((key, totals) in perActionSeasonTotals) {
            val (actionId, weekday) = key
            val action = actions.firstOrNull { it.id == actionId } ?: continue
            val avg = totals.average()
            val stdDev = sqrt(totals.sumOf { (it - avg) * (it - avg) } / totals.size)
            results.add(
                ActionAllTimeWeekdayStat(
                    actionId = actionId,
                    actionName = action.name,
                    weekdayName = weekdayNames[weekday - 1],
                    seasonCount = totals.size,
                    seasonDurationTotals = totals,
                    avgSeasonDuration = avg,
                    avgSeasonDurationStdDev = stdDev,
                    allTimeDurationSum = totals.sum(),
                    allTimeDurationPercentage = null // requires season-aligned parameter sums; left for Report-phase refinement
                )
            )
        }
        return results
    }

    /** Computes stats for a single (season, weekday) pair — the core building block. */
    suspend fun computeSeasonWeekday(
        dslaId: Long,
        generalActionId: Long,
        actions: List<com.dar.app.data.ActionEntity>,
        forms: List<AnalysisForm>,
        allRows: List<RecordingRow>,
        season: SeasonRange,
        weekday: Int,
        rangeStart: Date,
        rangeEnd: Date
    ): GeneralActionSeasonStat {
        val occurrenceDates = weekdayOccurrencesInSeason(season, weekday, rangeStart, rangeEnd)

        val actionStats = mutableListOf<ActionSeasonStat>()
        for (action in actions) {
            actionStats.add(
                computeActionForOccurrences(action.id, action.name, occurrenceDates, forms, allRows)
            )
        }

        val generalDurationTotal = actionStats.sumOf { it.totalDurationSum }
        val generalDurationStdDev = averageOf(actionStats.mapNotNull { it.avgStdDevDuration })
        val generalQuan1Total = actionStats.sumOf { it.totalQuan1Sum }
        val generalQuan1StdDev = averageOf(actionStats.mapNotNull { it.avgStdDevQuan1 })

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
        actionId: Long,
        actionName: String,
        occurrenceDates: List<Date>,
        forms: List<AnalysisForm>,
        allRows: List<RecordingRow>
    ): ActionSeasonStat {
        // Determine the season's "base" dimension for this action = the largest dimension
        // among the forms that actually cover one of this season's occurrence dates.
        var maxDimension = 1
        val perOccurrence = mutableListOf<Triple<List<Double>, List<Double>, AnalysisFormActionParam>>() // (recordedGrouped, paramGrouped, param) — pre-padding

        for (date in occurrenceDates) {
            val dateStr = dateFormat.format(date)
            val form = forms.firstOrNull { form ->
                val begin = dateFormat.parse(form.beginDate)
                val end = form.endDate?.let { dateFormat.parse(it) }
                begin != null && !date.before(begin) && (end == null || !date.after(end))
            } ?: continue // missing day (gap between forms) — excluded per spec

            // param lookup happens per-form; assumed pre-fetched by caller in a real wiring —
            // here we fetch synchronously via a blocking-safe suspend call site upstream.
            val param = formParamCache[form.id to actionId] ?: continue
            maxDimension = maxOf(maxDimension, param.dimension)

            val rawDurations = allRows
                .filter { it.dslaId == form.dslaId && it.date == dateStr && it.actionName == actionName }
                .mapNotNull { it.durationValue.toDoubleOrNull() }

            val recordedGrouped = groupIntoDimension(rawDurations, param.dimension)
            val paramGrouped = parseVector(param.durationVector, param.dimension)

            perOccurrence.add(Triple(recordedGrouped, paramGrouped, param))
        }

        if (perOccurrence.isEmpty()) {
            return ActionSeasonStat(
                actionId, actionName, 0, null, null, 0.0, null, null, 0.0, null
            )
        }

        val deviationVectors = perOccurrence.map { (recorded, param, _) ->
            val recordedPadded = padTo(recorded, maxDimension)
            val paramPadded = padTo(param, maxDimension)
            recordedPadded.indices.map { recordedPadded[it] - paramPadded[it] }
        }
        val stdDevVector = elementwiseStdDev(deviationVectors)
        val avgStdDevDuration = averageOf(stdDevVector)

        val totalDurationSum = perOccurrence.sumOf { it.first.sum() }

        val paramSum = perOccurrence.sumOf { it.second.sum() }
        val durationPercentage = if (paramSum == 0.0) null else (totalDurationSum / paramSum) * 100.0

        // Time and Quan1 follow the identical pattern; omitted here for brevity but structured
        // the same way — left as a follow-up pass once we validate Duration's numbers first.
        return ActionSeasonStat(
            actionId = actionId,
            actionName = actionName,
            occurrenceCount = perOccurrence.size,
            avgStdDevTime = null,
            avgStdDevDuration = avgStdDevDuration,
            totalDurationSum = totalDurationSum,
            durationPercentage = durationPercentage,
            avgStdDevQuan1 = null,
            totalQuan1Sum = 0.0,
            quan1Percentage = null
        )
    }

    // ---------- Grouping / padding helpers ----------

    /** Splits [values] into [dimension] contiguous, near-equal groups and averages each —
     *  the clean equivalent of the r = n*p + k formula, agreed as safer against edge cases.
     *  If values.size <= dimension, pads with zeros instead (no averaging needed). */
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

    private fun parseVector(csv: String, dimension: Int): List<Double> {
        val parsed = csv.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        return padTo(parsed, dimension)
    }

    /** Population std dev per dimension, treating each occurrence's deviation at that
     *  dimension as one data point (deviation-from-goal, not classical variance). */
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

    // ---------- Season / weekday enumeration (Gregorian only, for now) ----------

    data class SeasonRange(val label: String, val start: Date, val end: Date)

    private fun enumerateSeasons(rangeStart: Date, rangeEnd: Date): List<SeasonRange> {
        val cal = Calendar.getInstance()
        cal.time = rangeStart
        val startYear = cal.get(Calendar.YEAR)
        cal.time = rangeEnd
        val endYear = cal.get(Calendar.YEAR)

        val seasonLabels = listOf(
            "Jan & Feb", "Mar & Apr", "May & Jun", "Jul & Aug", "Sep & Oct", "Nov & Dec"
        )
        val results = mutableListOf<SeasonRange>()
        for (year in startYear..endYear) {
            for (seasonIndex in 0..5) {
                val firstMonth = seasonIndex * 2 // 0-based: Jan=0
                val start = Calendar.getInstance().apply {
                    set(year, firstMonth, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                val end = Calendar.getInstance().apply {
                    set(year, firstMonth + 1, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, 1)
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

    // Cache of (formId, actionId) -> its parameter row, populated by the caller before
    // running the engine (kept simple here; a real wiring would populate this from
    // analysisFormDao().getParamsForForm(formId) for every form up front).
    var formParamCache: Map<Pair<Long, Long>, AnalysisFormActionParam> = emptyMap()
}
