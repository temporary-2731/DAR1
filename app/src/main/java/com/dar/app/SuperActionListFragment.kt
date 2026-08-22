package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.AppDatabase
import com.dar.app.data.GeneralActionEntity
import com.dar.app.data.SuperActionEntity
import com.dar.app.data.SuperActionGeneralCrossRef
import com.dar.app.databinding.FragmentSuperActionListBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SuperActionListFragment : Fragment() {

    private var _binding: FragmentSuperActionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L

    companion object {
        private const val ARG_DSLA_ID = "arg_dsla_id"

        fun newInstance(dslaId: Long): SuperActionListFragment {
            val fragment = SuperActionListFragment()
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
        _binding = FragmentSuperActionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dslaId = arguments?.getLong(ARG_DSLA_ID) ?: -1L
        db = AppDatabase.getInstance(requireContext().applicationContext)

        binding.btnAddSuperAction.setOnClickListener { showCreateSuperActionDialog() }

        observeSuperActions()
    }

    private fun observeSuperActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.superActionDao().getAllForDsla(dslaId).collect { superActions ->
                renderSuperActionList(superActions)
            }
        }
    }

    private fun renderSuperActionList(superActions: List<SuperActionEntity>) {
        binding.superActionListContainer.removeAllViews()

        for (superAction in superActions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_super_action, binding.superActionListContainer, false) as TextView

            val status = if (superAction.endDate != null) " (deleted)" else ""
            itemView.text = "Super Action #${superAction.id}$status"
            binding.superActionListContainer.addView(itemView)

            viewLifecycleOwner.lifecycleScope.launch {
                val generalsInGroup = db.superActionDao()
                    .getGeneralActionsInSuper(superAction.id)
                    .first()
                val labels = generalsInGroup.joinToString(", ") { "GA#${it.id}" }
                itemView.text = "${labels.ifEmpty { "Super Action #${superAction.id}" }}$status"
            }
        }
    }

    private fun showCreateSuperActionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allGeneralActions: List<GeneralActionEntity> =
                db.generalActionDao().getAllForDsla(dslaId).first()

            if (allGeneralActions.size < 2) {
                Toast.makeText(
                    requireContext(),
                    R.string.super_action_no_generals_available,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_super_action, null)
            val descField = dialogView.findViewById<EditText>(R.id.edit_super_action_description)
            val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)

            val checkBoxes = mutableListOf<Pair<CheckBox, GeneralActionEntity>>()
            for (generalAction in allGeneralActions) {
                val checkBox = CheckBox(requireContext())
                checkBox.text = "General Action #${generalAction.id}"
                checkBox.setTextColor(android.graphics.Color.BLACK)
                checkboxContainer.addView(checkBox)
                checkBoxes.add(checkBox to generalAction)
            }

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.super_action_save) { _, _ ->
                    val description = descField.text.toString().trim()
                    val selectedGenerals = checkBoxes.filter { it.first.isChecked }.map { it.second }

                    if (selectedGenerals.size < 2) {
                        Toast.makeText(
                            requireContext(),
                            R.string.super_action_min_generals_required,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        saveSuperAction(description, selectedGenerals)
                    }
                }
                .setNegativeButton(R.string.super_action_cancel, null)
                .show()
        }
    }

    private fun saveSuperAction(description: String, selectedGenerals: List<GeneralActionEntity>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newId = db.superActionDao().insert(
                SuperActionEntity(
                    dslaId = dslaId,
                    description = description
                )
            )
            for (generalAction in selectedGenerals) {
                db.superActionDao().addGeneralToSuper(
                    SuperActionGeneralCrossRef(
                        superActionId = newId,
                        generalActionId = generalAction.id
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
