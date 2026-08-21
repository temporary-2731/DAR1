package com.dar.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dar.app.databinding.ActivityDslaDetailBinding

class DslaDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDslaDetailBinding

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
        const val EXTRA_DSLA_NAME = "extra_dsla_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDslaDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)
        val dslaName = intent.getStringExtra(EXTRA_DSLA_NAME) ?: ""

        binding.titleDslaName.text = dslaName

        binding.btnRecording.setOnClickListener { sectionComingSoon("Recording") }
        binding.btnLibrary.setOnClickListener { sectionComingSoon("Library") }
        binding.btnAnalysis.setOnClickListener { sectionComingSoon("Analysis") }
        binding.btnReport.setOnClickListener { sectionComingSoon("Report") }
        binding.btnHistory.setOnClickListener { sectionComingSoon("History") }
        binding.btnTools.setOnClickListener { sectionComingSoon("Tools") }
    }

    private fun sectionComingSoon(sectionName: String) {
        Toast.makeText(this, "$sectionName — coming in the next build", Toast.LENGTH_SHORT).show()
    }
}
