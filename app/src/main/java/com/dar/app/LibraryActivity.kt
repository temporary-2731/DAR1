package com.dar.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.dar.app.databinding.ActivityLibraryBinding

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding

    companion object {
        const val EXTRA_DSLA_ID = "extra_dsla_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dslaId = intent.getLongExtra(EXTRA_DSLA_ID, -1L)

        val pagerAdapter = LibraryPagerAdapter(this, dslaId)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_action)
                1 -> getString(R.string.tab_general_action)
                2 -> getString(R.string.tab_super_action)
                else -> ""
            }
        }.attach()
    }
}
