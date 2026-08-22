package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class GeneralActionListFragment : Fragment() {

    companion object {
        private const val ARG_DSLA_ID = "arg_dsla_id"

        fun newInstance(dslaId: Long): GeneralActionListFragment {
            val fragment = GeneralActionListFragment()
            val args = Bundle()
            args.putLong(ARG_DSLA_ID, dslaId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val textView = TextView(requireContext())
        textView.text = getString(R.string.general_action_coming_soon)
        textView.textSize = 18f
        textView.setTextColor(android.graphics.Color.BLACK)
        textView.setPadding(32, 64, 32, 32)
        return textView
    }
}
