package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
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
                Toast.makeText(
                    this,
                    getString(R.string.dsla_opened_placeholder, dsla.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            binding.dslaListContainer.addView(itemView)
        }
    }

    private fun showCreateDslaDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_create_dsla, null)
        val nameField = dialogView.findViewById<EditText>(R.id.edit_dsla_name)
        val timeSwitch = dialogView.findViewById<Switch>(R.id.switch_time_enabled)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.dsla_save) { _, _ ->
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.dsla_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    saveDsla(name, timeSwitch.isChecked)
                }
            }
            .setNegativeButton(R.string.dsla_cancel, null)
            .show()
    }

    private fun saveDsla(name: String, timeEnabled: Boolean) {
        lifecycleScope.launch {
            db.dslaDao().insert(Dsla(name = name, timeEnabled = timeEnabled))
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
