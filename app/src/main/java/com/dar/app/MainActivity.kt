package com.dar.app

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dar.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMenu.setOnClickListener { view -> showTopMenu(view) }

        binding.btnAddDsla.setOnClickListener {
            Toast.makeText(this, "Create DSLA — coming in the next build", Toast.LENGTH_SHORT).show()
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
