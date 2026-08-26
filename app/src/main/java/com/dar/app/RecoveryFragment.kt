package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.ActionEntity
import com.dar.app.data.AppDatabase
import com.dar.app.data.GeneralActionEntity
import com.dar.app.data.SuperActionEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecoveryFragment : Fragment() {

    private var dslaId: Long = -1L
    private lateinit var db: AppDatabase

    private lateinit var actionsContainer: LinearLayout
    private lateinit var generalsContainer: LinearLayout
    private lateinit var supersContainer: LinearLayout

    companion object {
        private const val ARG_DSLA_ID = "arg_dsla_id"

        fun newInstance(dslaId: Long): RecoveryFragment {
            val fragment = RecoveryFragment()
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
        return inflater.inflate(R.layout.fragment_recovery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dslaId = arguments?.getLong(ARG_DSLA_ID) ?: -1L
        db = AppDatabase.getInstance(requireContext().applicationContext)

        actionsContainer = view.findViewById(R.id.recovery_actions_container)
        generalsContainer = view.findViewById(R.id.recovery_generals_container)
        supersContainer = view.findViewById(R.id.recovery_supers_container)

        observeDeletedActions()
        observeDeletedGenerals()
        observeDeletedSupers()
    }

    private fun todayString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun observeDeletedActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.actionDao().getDeletedForDsla(dslaId).collect { actions ->
                renderActions(actions)
            }
        }
    }

    private fun renderActions(actions: List<ActionEntity>) {
        actionsContainer.removeAllViews()
        for (action in actions) {
            val row = LayoutInflater.from(context).inflate(R.layout.item_recovery_entry, actionsContainer, false)
            val info = row.findViewById<TextView>(R.id.recovery_entry_info)
            val btn = row.findViewById<Button>(R.id.btn_recover)

            val builder = StringBuilder(action.name)
            builder.append("\n")
            builder.append(getString(R.string.label_created, action.createdDate.ifEmpty { getString(R.string.detail_none) }))
            if (action.deletedDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_deleted, action.deletedDate))
            }
            info.text = builder.toString()

            btn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.actionDao().recover(action.id, todayString())
                }
            }
            actionsContainer.addView(row)
        }
    }

    private fun observeDeletedGenerals() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.generalActionDao().getDeletedForDsla(dslaId).collect { generals ->
                renderGenerals(generals)
            }
        }
    }

    private fun renderGenerals(generals: List<GeneralActionEntity>) {
        generalsContainer.removeAllViews()
        for (generalAction in generals) {
            val row = LayoutInflater.from(context).inflate(R.layout.item_recovery_entry, generalsContainer, false)
            val info = row.findViewById<TextView>(R.id.recovery_entry_info)
            val btn = row.findViewById<Button>(R.id.btn_recover)

            val builder = StringBuilder(generalAction.name)
            builder.append("\n")
            builder.append(getString(R.string.label_created, generalAction.createdDate.ifEmpty { getString(R.string.detail_none) }))
            if (generalAction.deletedDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_deleted, generalAction.deletedDate))
            }
            info.text = builder.toString()

            btn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.generalActionDao().recover(generalAction.id, todayString())
                }
            }
            generalsContainer.addView(row)
        }
    }

    private fun observeDeletedSupers() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.superActionDao().getDeletedForDsla(dslaId).collect { supers ->
                renderSupers(supers)
            }
        }
    }

    private fun renderSupers(supers: List<SuperActionEntity>) {
        supersContainer.removeAllViews()
        for (superAction in supers) {
            val row = LayoutInflater.from(context).inflate(R.layout.item_recovery_entry, supersContainer, false)
            val info = row.findViewById<TextView>(R.id.recovery_entry_info)
            val btn = row.findViewById<Button>(R.id.btn_recover)

            val builder = StringBuilder(superAction.name)
            builder.append("\n")
            builder.append(getString(R.string.label_created, superAction.createdDate.ifEmpty { getString(R.string.detail_none) }))
            if (superAction.deletedDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_deleted, superAction.deletedDate))
            }
            info.text = builder.toString()

            btn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    db.superActionDao().recover(superAction.id, todayString())
                }
            }
            supersContainer.addView(row)
        }
    }
}
