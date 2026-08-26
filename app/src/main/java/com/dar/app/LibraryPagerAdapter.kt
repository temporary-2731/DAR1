package com.dar.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LibraryPagerAdapter(
    activity: FragmentActivity,
    private val dslaId: Long
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ActionListFragment.newInstance(dslaId)
            1 -> GeneralActionListFragment.newInstance(dslaId)
            2 -> SuperActionListFragment.newInstance(dslaId)
            3 -> RecoveryFragment.newInstance(dslaId)
            else -> ActionListFragment.newInstance(dslaId)
        }
    }
}
