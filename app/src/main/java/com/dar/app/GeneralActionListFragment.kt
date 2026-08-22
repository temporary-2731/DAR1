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
import com.dar.app.data.ActionEntity
import com.dar.app.data.AppDatabase
import com.dar.app.data.GeneralActionActionCrossRef
import com.dar.app.data.GeneralActionEntity
import com.dar.app.databinding.FragmentGeneralActionListBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GeneralActionListFragment : Fragment() {

    private var _binding: FragmentGeneralActionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L

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
        _binding = FragmentGeneralActionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dslaId = arguments?.getLong(ARG_DSLA_ID) ?: -1L
        db = AppDatabase.getInstance(requireContext().applicationContext)

        binding.btnAddGeneralAction.setOnClickListener { showCreateGeneralActionDialog() }

        observeGeneralActions()
    }

    private fun observeGeneralActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.generalActionDao().getAllForDsla(dslaId).collect { generalActions ->
                renderGeneralActionList(generalActions)
            }
        }
    }

    private fun renderGeneralActionList(generalActions: List<GeneralActionEntity>) {
        binding.generalActionListContainer.removeAllViews()

        for (generalAction in generalActions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_general_action, binding.generalActionListContainer, false) as TextView

            val status = if (generalAction.endDate != null) " (deleted)" else ""
            itemView.text = "${generalAction.name}$status"
            binding.generalActionListContainer.addView(itemView)
        }
    }

    private fun showCreateGeneralActionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allActions: List<ActionEntity> = db.actionDao().getAllForDsla(dslaId).first()

            if (allActions.size < 2) {
                Toast.makeText(
                    requireContext(),
                    R.string.general_action_no_actions_available,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_general_action, null)
            val nameField = dialogView.findViewById<EditText>(R.id.edit_general_action_name)
            val descField = dialogView.findViewById<EditText>(R.id.edit_general_action_description)
            val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)

            val checkBoxes = mutableListOf<Pair<CheckBox, ActionEntity>>()
            for (action in allActions) {
                val checkBox = CheckBox(requireContext())
                checkBox.text = action.name
                checkBox.setTextColor(android.graphics.Color.BLACK)
                checkboxContainer.addView(checkBox)
                checkBoxes.add(checkBox to action)
            }

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.general_action_save) { _, _ ->
                    val name = nameField.text.toString().trim()
                    val description = descField.text.toString().trim()
                    val selectedActions = checkBoxes.filter { it.first.isChecked }.map { it.second }

                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.general_action_name_required, Toast.LENGTH_SHORT).show()
                    } else if (selectedActions.size < 2) {
                        Toast.makeText(
                            requireContext(),
                            R.string.general_action_min_actions_required,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        saveGeneralAction(name, description, selectedActions)
                    }
                }
                .setNegativeButton(R.string.general_action_cancel, null)
                .show()
        }
    }

    private fun saveGeneralAction(name: String, description: String, selectedActions: List<ActionEntity>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newId = db.generalActionDao().insert(
                GeneralActionEntity(
                    dslaId = dslaId,
                    name = name,
                    description = description
                )
            )
            for (action in selectedActions) {
                db.generalActionDao().addActionToGeneral(
                    GeneralActionActionCrossRef(
                        generalActionId = newId,
                        actionId = action.id
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
