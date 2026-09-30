package com.dar.app

import com.dar.app.data.AnalysisForm
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScalarActionResult(
    val actionName: String,
    val recordedDurationTotal: Double,
    val recordedDurationDailyAvg: Double,
    val durationParam: Double?,
    val durationCompPct: Double?,
    val recordedQuan1Total: Double,
    val recordedQuan1DailyAvg: Double,
    val quan1Param: Double?,
    val quan1CompPct: Double?
)

data class ScalarFormResult(val form: AnalysisForm, val actionResults: List<ScalarActionResult>)

/** Computes recorded-data totals for Monthly/Yearly/All-time forms: sums each day's
 *  recorded duration/quan1 (grouped via whichever Daily/Weekly form's dimension covers
 *  that day) across the form's date range, then daily average and % vs. the form's own
 *  saved scalar parameter. */
class ScalarPeriodEngine(private val db: AppDatabase) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    suspend fun computeAll(dslaId: Long, generalActionId: Long, periodType: String): List<ScalarFormResult> {
        val forms = db.analysisFormDao().getFormsForOnce(generalActionId, periodType)
        if (forms.isEmpty()) return emptyList()

        val actions = db.generalActionDao().getActionsInGeneral(generalActionId).first()
        val dailyForms = db.analysisFormDao().getFormsForOnce(generalActionId, "DAILY")
        val weeklyForms = db.analysisFormDao().getFormsForOnce(generalActionId, "WEEKLY")
        val dailyParamsByForm = dailyForms.associateWith { db.analysisFormDao().getParamsForFormOnce(it.id) }
        val weeklyParamsByForm = weeklyForms.associateWith { db.analysisFormDao().getParamsForFormOnce(it.id) }
        val allRows = db.recordingDao().getAllRowsForDsla(dslaId)

        val results = mutableListOf<ScalarFormResult>()

        for (form in forms) {
            val start = dateFormat.parse(form.beginDate) ?: continue
            val end = form.endDate?.let { dateFormat.parse(it) } ?: Date()
            val scalarParams = db.analysisFormDao().getScalarParamsForFormOnce(form.id)

            val actionResults = mutableListOf<ScalarActionResult>()

            for (action in actions) {
                var durationTotal = 0.0
                var quan1Total = 0.0
                var daysWithData = 0

                val cal = Calendar.getInstance()
                cal.time = start
                while (!cal.time.after(end)) {
                    val date = cal.time
                    val dateStr = dateFormat.format(date)
                    val weekday = cal.get(Calendar.DAY_OF_WEEK)

                    val coveringDaily = dailyForms.firstOrNull { f ->
                        val b = dateFormat.parse(f.beginDate); val e = f.endDate?.let { dateFormat.parse(it) }
                        b != null && !date.before(b) && (e == null || !date.after(e))
                    }
                    val coveringWeekly = weeklyForms.firstOrNull { f ->
                        val b = dateFormat.parse(f.beginDate); val e = f.endDate?.let { dateFormat.parse(it) }
                        b != null && !date.before(b) && (e == null || !date.after(e))
                    }
                    val param = coveringDaily?.let { f -> dailyParamsByForm[f]?.firstOrNull { it.actionId == action.id && it.weekday == weekday } }
                        ?: coveringWeekly?.let { f -> weeklyParamsByForm[f]?.firstOrNull { it.actionId == action.id && it.weekday == weekday } }

                    val dimension = param?.dimension ?: 1
                    val rowsThatDay = allRows.filter { it.dslaId == dslaId && it.date == dateStr && it.actionName == action.name }
                    val rawDurations = rowsThatDay.mapNotNull { it.durationValue.toDoubleOrNull() }
                    val rawQuan1 = rowsThatDay.mapNotNull { it.quan1.toDoubleOrNull() }

                    if (rowsThatDay.isNotEmpty()) {
                        daysWithData++
                        durationTotal += groupAndSum(rawDurations, dimension)
                        quan1Total += groupAndSum(rawQuan1, dimension)
                    }

                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }

                val avgDivisor = if (daysWithData > 0) daysWithData else 1
                val durationAvg = durationTotal / avgDivisor
                val quan1Avg = quan1Total / avgDivisor

                val scalarParam = scalarParams.firstOrNull { it.actionId == action.id }
                val durationCompPct = if (scalarParam?.durationValue != null && scalarParam.durationValue > 0.0) {
                    (durationTotal / scalarParam.durationValue) * 100.0
                } else null
                val quan1CompPct = if (scalarParam?.quan1Value != null && scalarParam.quan1Value > 0.0) {
                    (quan1Total / scalarParam.quan1Value) * 100.0
                } else null

                actionResults.add(
                    ScalarActionResult(
                        actionName = action.name,
                        recordedDurationTotal = durationTotal,
                        recordedDurationDailyAvg = durationAvg,
                        durationParam = scalarParam?.durationValue,
                        durationCompPct = durationCompPct,
                        recordedQuan1Total = quan1Total,
                        recordedQuan1DailyAvg = quan1Avg,
                        quan1Param = scalarParam?.quan1Value,
                        quan1CompPct = quan1CompPct
                    )
                )
            }

            results.add(ScalarFormResult(form, actionResults))
        }

        return results
    }

    private fun groupAndSum(values: List<Double>, dimension: Int): Double {
        // The recorded total for a single day is just the sum of that day's values —
        // grouping into "dimension" only matters for the vector-level engines; here we
        // only need the scalar day-total, so dimension isn't actually needed for summing.
        return values.sum()
    }
}
