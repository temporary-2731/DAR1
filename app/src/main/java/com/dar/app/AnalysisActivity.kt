package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import kotlinx.coroutines.launch

class AnalysisActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L
    private var currentMode: String = "MODE1"
    private var isChoosingMode = false
    private var pendingMode: String = "MODE1"

    private lateinit var btnChangeMode: Button
    private lateinit var btnMode1: Button
    private lateinit var btnMode2: Button
    private lateinit var btnModeManual: Button

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        db = AppDatabase.getInstance(applicationContext)

        btnChangeMode = findViewById(R.id.btn_change_mode)
        btnMode1 = findViewById(R.id.btn_mode1)
        btnMode2 = findViewById(R.id.btn_mode2)
        btnModeManual = findViewById(R.id.btn_mode_manual)

        btnChangeMode.setOnClickListener {
            if (isChoosingMode) {
                lifecycleScope.launch {
                    db.dslaDao().updateAnalysisMode(dslaId, pendingMode)
                    currentMode = pendingMode
                    isChoosingMode = false
                    refreshUi()
                }
            } else {
                isChoosingMode = true
                pendingMode = currentMode
                refreshUi()
            }
        }

        btnMode1.setOnClickListener { onModeButtonTapped("MODE1") }
        btnMode2.setOnClickListener { onModeButtonTapped("MODE2") }
        btnModeManual.setOnClickListener { onModeButtonTapped("MANUAL") }

        loadCurrentMode()
    }

    private fun onModeButtonTapped(mode: String) {
        if (isChoosingMode) {
            pendingMode = mode
            refreshUi()
        } else if (mode == currentMode) {
            openMode(mode)
        }
        // Tapping a greyed-out, non-chosen mode while not choosing does nothing.
    }

    private fun openMode(mode: String) {
        when (mode) {
            "MODE1" -> {
                val intent = Intent(this, AnalysisMode1Activity::class.java).apply {
                    putExtra(AnalysisMode1Activity.EXTRA_DSLA_ID, dslaId)
                }
                startActivity(intent)
            }
            else -> {
                Toast.makeText(this, R.string.analysis_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurrentMode() {
        lifecycleScope.launch {
            val dsla = db.dslaDao().getById(dslaId)
            currentMode = dsla?.analysisMode ?: "MODE1"
            refreshUi()
        }
    }

    private fun refreshUi() {
        btnChangeMode.text = if (isChoosingMode) {
            getString(R.string.analysis_save_mode)
        } else {
            getString(R.string.analysis_change_mode)
        }

        val highlightMode = if (isChoosingMode) pendingMode else currentMode
        styleModeButton(btnMode1, "MODE1", highlightMode)
        styleModeButton(btnMode2, "MODE2", highlightMode)
        styleModeButton(btnModeManual, "MANUAL", highlightMode)
    }

    private fun styleModeButton(button: Button, thisMode: String, activeMode: String) {
        val isAccessible = isChoosingMode || thisMode == currentMode
        val isSelected = thisMode == activeMode

        button.alpha = if (isAccessible) 1f else 0.35f
        button.isEnabled = isChoosingMode || thisMode == currentMode
        if (isChoosingMode) {
            button.isEnabled = true
        }
        if (isSelected && isChoosingMode) {
            button.alpha = 1f
        }
    }
}
