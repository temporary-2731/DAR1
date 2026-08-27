package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import com.dar.app.data.Dsla
import com.dar.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(applicationContext)

        binding.btnMenu.setOnClickListener { view -> showTopMenu(view) }
        binding.btnAddDsla.setOnClickListener { showCreateDslaDialog() }

        observeDslaList()
    }

    private fun todayString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun observeDslaList() {
        lifecycleScope.launch {
            db.dslaDao().getAll().collect { dslaList ->
                renderDslaList(dslaList)
            }
        }
    }

    private fun renderDslaList(dslaList: List<Dsla>) {
        binding.dslaListContainer.removeAllViews()

        for (dsla in dslaList) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_dsla, binding.dslaListContainer, false) as TextView
            itemView.text = dsla.name
            itemView.setOnClickListener {
                val intent = Intent(this, DslaDetailActivity::class.java).apply {
                    putExtra(DslaDetailActivity.EXTRA_DSLA_ID, dsla.id)
                    putExtra(DslaDetailActivity.EXTRA_DSLA_NAME, dsla.name)
                }
                startActivity(intent)
            }
            binding.dslaListContainer.addView(itemView)
        }
    }

    private fun showCreateDslaDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_create_dsla, null)
        val nameField = dialogView.findViewById<EditText>(R.id.edit_dsla_name)
        val timeSwitch = dialogView.findViewById<Switch>(R.id.switch_time_enabled)
        val beginField = dialogView.findViewById<EditText>(R.id.edit_dsla_begin)
        val endField = dialogView.findViewById<EditText>(R.id.edit_dsla_end)
        val modeGroup = dialogView.findViewById<RadioGroup>(R.id.radio_analysis_mode)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.dsla_save) { _, _ ->
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.dsla_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    val beginInput = beginField.text.toString().trim()
                    val endInput = endField.text.toString().trim()
                    val begin = beginInput.ifEmpty { todayString() }
                    val end = endInput.ifEmpty { null }
                    val mode = when (modeGroup.checkedRadioButtonId) {
                        R.id.radio_mode2 -> "MODE2"
                        R.id.radio_mode_manual -> "MANUAL"
                        else -> "MODE1"
                    }
                    saveDsla(name, timeSwitch.isChecked, begin, end, mode)
                }
            }
            .setNegativeButton(R.string.dsla_cancel, null)
            .show()
    }

    private fun saveDsla(name: String, timeEnabled: Boolean, beginDate: String, endDate: String?, analysisMode: String) {
        lifecycleScope.launch {
            db.dslaDao().insert(
                Dsla(
                    name = name,
                    timeEnabled = timeEnabled,
                    beginDate = beginDate,
                    endDate = endDate,
                    analysisMode = analysisMode
                )
            )
        }
    }

    private fun showTopMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.top_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_alarm -> {
                    Toast.makeText(this, "Alarm section — coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_settings -> {
                    Toast.makeText(this, "General Setting — coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_about -> {
                    Toast.makeText(this, "About App — coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}