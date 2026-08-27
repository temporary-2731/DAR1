package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import com.dar.app.databinding.ActivityDslaDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DslaDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDslaDetailBinding
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private var dslaName: String = ""

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_DSLA_NAME = "extra_dsla_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDslaDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(applicationContext)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        dslaName = intent.getStringExtra(EXTRA_DSLA_NAME) ?: ""

        binding.titleDslaName.text = dslaName

        binding.btnRecording.setOnClickListener { checkAndOpenRecording() }
        binding.btnLibrary.setOnClickListener { openLibrary() }
        binding.btnAnalysis.setOnClickListener { sectionComingSoon("Analysis") }
        binding.btnReport.setOnClickListener { sectionComingSoon("Report") }
        binding.btnHistory.setOnClickListener { openHistory() }
        binding.btnTools.setOnClickListener { sectionComingSoon("Tools") }
    }

    private fun checkAndOpenRecording() {
        lifecycleScope.launch {
            val dsla = db.dslaDao().getById(dslaId)
            val end = dsla?.endDate
            if (end != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val endParsed = sdf.parse(end)
                val today = sdf.parse(sdf.format(Date()))
                if (endParsed != null && today != null && endParsed.before(today)) {
                    Toast.makeText(
                        this@DslaDetailActivity,
                        getString(R.string.recording_dsla_ended_message, end),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }
            openRecording()
        }
    }

    private fun openRecording() {
        val intent = Intent(this, RecordingActivity::class.java).apply {
            putExtra(RecordingActivity.EXTRA_DSLA_ID, dslaId)
        }
        startActivity(intent)
    }

    private fun openHistory() {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val intent = Intent(this, RecordingActivity::class.java).apply {
            putExtra(RecordingActivity.EXTRA_DSLA_ID, dslaId)
            putExtra(RecordingActivity.EXTRA_MODE, "HISTORY")
            putExtra(RecordingActivity.EXTRA_TARGET_DATE, today)
        }
        startActivity(intent)
    }

    private fun openLibrary() {
        val intent = Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_DSLA_ID, dslaId)
        }
        startActivity(intent)
    }

    private fun sectionComingSoon(sectionName: String) {
        Toast.makeText(this, "$sectionName — coming in the next build", Toast.LENGTH_SHORT).show()
    }
}
