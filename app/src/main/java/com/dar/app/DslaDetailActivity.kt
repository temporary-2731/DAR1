package com.dar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dar.app.databinding.ActivityDslaDetailBinding

class DslaDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDslaDetailBinding
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

        dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        dslaName = intent.getStringExtra(EXTRA_DSLA_NAME) ?: ""

        binding.titleDslaName.text = dslaName

        binding.btnRecording.setOnClickListener { sectionComingSoon("Recording") }
        binding.btnLibrary.setOnClickListener { openLibrary() }
        binding.btnAnalysis.setOnClickListener { sectionComingSoon("Analysis") }
        binding.btnReport.setOnClickListener { sectionComingSoon("Report") }
        binding.btnHistory.setOnClickListener { sectionComingSoon("History") }
        binding.btnTools.setOnClickListener { sectionComingSoon("Tools") }
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
