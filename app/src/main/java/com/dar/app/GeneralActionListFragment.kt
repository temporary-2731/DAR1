package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        binding.btnAddGeneralAction.setOnClickListener { showGeneralActionDialog(null) }

        observeGeneralActions()
    }

    private fun todayString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun observeGeneralActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.generalActionDao().getActiveForDsla(dslaId).collect { generalActions ->
                renderGeneralActionList(generalActions)
            }
        }
    }

    private fun renderGeneralActionList(generalActions: List<GeneralActionEntity>) {
        binding.generalActionListContainer.removeAllViews()

        for (generalAction in generalActions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_general_action, binding.generalActionListContainer, false) as TextView

            itemView.text = generalAction.name
            itemView.setOnClickListener { showGeneralActionDetail(generalAction) }
            itemView.setOnLongClickListener {
                showItemMenu(
                    onEdit = { showGeneralActionDialog(generalAction) },
                    onDelete = { deleteGeneralAction(generalAction) }
                )
                true
            }
            binding.generalActionListContainer.addView(itemView)
        }
    }

    private fun showItemMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_library_item_menu, null)
        val btnEdit = dialogView.findViewById<Button>(R.id.btn_item_edit)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_item_delete)

        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(dialogView)

        btnEdit.setOnClickListener {
            sheet.dismiss()
            onEdit()
        }
        btnDelete.setOnClickListener {
            sheet.dismiss()
            onDelete()
        }
        sheet.show()
    }

    private fun deleteGeneralAction(generalAction: GeneralActionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.generalActionDao().softDelete(generalAction.id, todayString())
        }
    }

    private fun showGeneralActionDetail(generalAction: GeneralActionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val memberActions = db.generalActionDao()
                .getActionsInGeneral(generalAction.id)
                .first()
            val memberNames = if (memberActions.isEmpty()) {
                getString(R.string.detail_none)
            } else {
                memberActions.joinToString(", ") { it.name }
            }
            val desc = generalAction.description.ifEmpty { getString(R.string.detail_no_description) }

            val builder = StringBuilder()
            builder.append(
                getString(
                    R.string.general_action_detail_format,
                    generalAction.id,
                    generalAction.name,
                    desc,
                    memberNames
                )
            )
            builder.append("\n")
            builder.append(getString(R.string.label_created, generalAction.createdDate.ifEmpty { getString(R.string.detail_none) }))
            if (generalAction.deletedDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_deleted, generalAction.deletedDate))
            }
            if (generalAction.recoveredDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_recovered, generalAction.recoveredDate))
            }

            AlertDialog.Builder(requireContext())
                .setTitle(generalAction.name)
                .setMessage(builder.toString())
                .setPositiveButton(R.string.detail_close, null)
                .show()
        }
    }

    private fun showGeneralActionDialog(existing: GeneralActionEntity?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val allActions: List<ActionEntity> = db.actionDao().getActiveForDsla(dslaId).first()

            if (allActions.size < 2) {
                Toast.makeText(
                    requireContext(),
                    R.string.general_action_no_actions_available,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val preselectedIds: Set<Long> = if (existing != null) {
                db.generalActionDao().getActionsInGeneral(existing.id).first().map { it.id }.toSet()
            } else {
                emptySet()
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_general_action, null)
            val nameField = dialogView.findViewById<EditText>(R.id.edit_general_action_name)
            val descField = dialogView.findViewById<EditText>(R.id.edit_general_action_description)
            val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)

            if (existing != null) {
                nameField.setText(existing.name)
                descField.setText(existing.description)
            }

            val checkBoxes = mutableListOf<Pair<CheckBox, ActionEntity>>()
            for (action in allActions) {
                val checkBox = CheckBox(requireContext())
                checkBox.text = action.name
                checkBox.setTextColor(android.graphics.Color.BLACK)
                checkBox.isChecked = preselectedIds.contains(action.id)
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
                        viewLifecycleOwner.lifecycleScope.launch {
                            val allGenerals = db.generalActionDao().getAllForDsla(dslaId).first()
                            val duplicate = allGenerals.any {
                                it.name.equals(name, ignoreCase = true) && it.id != (existing?.id ?: -1L)
                            }
                            if (duplicate) {
                                Toast.makeText(requireContext(), R.string.general_action_name_duplicate, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            saveGeneralAction(existing, name, description, selectedActions)
                        }
                    }
                }
                .setNegativeButton(R.string.general_action_cancel, null)
                .show()
        }
    }

    private fun saveGeneralAction(
        existing: GeneralActionEntity?,
        name: String,
        description: String,
        selectedActions: List<ActionEntity>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val id: Long
            if (existing == null) {
                id = db.generalActionDao().insert(
                    GeneralActionEntity(
                        dslaId = dslaId,
                        name = name,
                        description = description,
                        createdDate = todayString()
                    )
                )
            } else {
                id = existing.id
                db.generalActionDao().update(existing.copy(name = name, description = description))
                db.generalActionDao().clearActionsForGeneral(id)
            }
            for (action in selectedActions) {
                db.generalActionDao().addActionToGeneral(
                    GeneralActionActionCrossRef(
                        generalActionId = id,
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
