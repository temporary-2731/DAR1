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
import com.dar.app.data.AppDatabase
import com.dar.app.data.GeneralActionEntity
import com.dar.app.data.SuperActionEntity
import com.dar.app.data.SuperActionGeneralCrossRef
import com.dar.app.databinding.FragmentSuperActionListBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        binding.btnAddSuperAction.setOnClickListener { showSuperActionDialog(null) }

        observeSuperActions()
    }

    private fun todayString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun observeSuperActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.superActionDao().getActiveForDsla(dslaId).collect { superActions ->
                renderSuperActionList(superActions)
            }
        }
    }

    private fun renderSuperActionList(superActions: List<SuperActionEntity>) {
        binding.superActionListContainer.removeAllViews()

        for (superAction in superActions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_super_action, binding.superActionListContainer, false) as TextView

            itemView.text = superAction.name
            itemView.setOnClickListener { showSuperActionDetail(superAction) }
            itemView.setOnLongClickListener {
                showItemMenu(
                    onEdit = { showSuperActionDialog(superAction) },
                    onDelete = { deleteSuperAction(superAction) }
                )
                true
            }
            binding.superActionListContainer.addView(itemView)
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

    private fun deleteSuperAction(superAction: SuperActionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.superActionDao().softDelete(superAction.id, todayString())
        }
    }

    private fun showSuperActionDetail(superAction: SuperActionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val memberGenerals = db.superActionDao()
                .getGeneralActionsInSuper(superAction.id)
                .first()

            val generalNames = if (memberGenerals.isEmpty()) {
                getString(R.string.detail_none)
            } else {
                memberGenerals.joinToString(", ") { it.name }
            }

            val allActionNames = mutableSetOf<String>()
            for (generalAction in memberGenerals) {
                val actionsInGeneral = db.generalActionDao()
                    .getActionsInGeneral(generalAction.id)
                    .first()
                allActionNames.addAll(actionsInGeneral.map { it.name })
            }
            val actionNamesText = if (allActionNames.isEmpty()) {
                getString(R.string.detail_none)
            } else {
                allActionNames.joinToString(", ")
            }

            val desc = superAction.description.ifEmpty { getString(R.string.detail_no_description) }

            val builder = StringBuilder()
            builder.append(
                getString(
                    R.string.super_action_detail_format,
                    superAction.id,
                    superAction.name,
                    desc,
                    generalNames,
                    actionNamesText
                )
            )
            builder.append("\n")
            builder.append(getString(R.string.label_created, superAction.createdDate.ifEmpty { getString(R.string.detail_none) }))
            if (superAction.deletedDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_deleted, superAction.deletedDate))
            }
            if (superAction.recoveredDate != null) {
                builder.append("\n")
                builder.append(getString(R.string.label_recovered, superAction.recoveredDate))
            }

            AlertDialog.Builder(requireContext())
                .setTitle(superAction.name)
                .setMessage(builder.toString())
                .setPositiveButton(R.string.detail_close, null)
                .show()
        }
    }

    private fun showSuperActionDialog(existing: SuperActionEntity?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val allGeneralActions: List<GeneralActionEntity> =
                db.generalActionDao().getActiveForDsla(dslaId).first()

            if (allGeneralActions.size < 2) {
                Toast.makeText(
                    requireContext(),
                    R.string.super_action_no_generals_available,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val preselectedIds: Set<Long> = if (existing != null) {
                db.superActionDao().getGeneralActionsInSuper(existing.id).first().map { it.id }.toSet()
            } else {
                emptySet()
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_super_action, null)
            val nameField = dialogView.findViewById<EditText>(R.id.edit_super_action_name)
            val descField = dialogView.findViewById<EditText>(R.id.edit_super_action_description)
            val checkboxContainer = dialogView.findViewById<LinearLayout>(R.id.checkbox_container)

            if (existing != null) {
                nameField.setText(existing.name)
                descField.setText(existing.description)
            }

            val checkBoxes = mutableListOf<Pair<CheckBox, GeneralActionEntity>>()
            for (generalAction in allGeneralActions) {
                val checkBox = CheckBox(requireContext())
                checkBox.text = generalAction.name
                checkBox.setTextColor(android.graphics.Color.BLACK)
                checkBox.isChecked = preselectedIds.contains(generalAction.id)
                checkboxContainer.addView(checkBox)
                checkBoxes.add(checkBox to generalAction)
            }

            AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.super_action_save) { _, _ ->
                    val name = nameField.text.toString().trim()
                    val description = descField.text.toString().trim()
                    val selectedGenerals = checkBoxes.filter { it.first.isChecked }.map { it.second }

                    when {
                        name.isEmpty() -> {
                            Toast.makeText(requireContext(), R.string.super_action_name_required, Toast.LENGTH_SHORT).show()
                        }
                        selectedGenerals.size < 2 -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.super_action_min_generals_required,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {
                            validateAndSave(existing, name, description, selectedGenerals)
                        }
                    }
                }
                .setNegativeButton(R.string.super_action_cancel, null)
                .show()
        }
    }

    private fun validateAndSave(
        existing: SuperActionEntity?,
        name: String,
        description: String,
        selectedGenerals: List<GeneralActionEntity>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val actionSets = selectedGenerals.map { generalAction ->
                db.generalActionDao().getActionsInGeneral(generalAction.id).first()
                    .map { it.id }
                    .toSet()
            }

            for (i in actionSets.indices) {
                for (j in i + 1 until actionSets.size) {
                    if (actionSets[i].intersect(actionSets[j]).isNotEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            R.string.super_action_not_mutually_exclusive,
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }
            }

            val allSupers = db.superActionDao().getAllForDsla(dslaId).first()
            val duplicate = allSupers.any {
                it.name.equals(name, ignoreCase = true) && it.id != (existing?.id ?: -1L)
            }
            if (duplicate) {
                Toast.makeText(requireContext(), R.string.super_action_name_duplicate, Toast.LENGTH_SHORT).show()
                return@launch
            }

            saveSuperAction(existing, name, description, selectedGenerals)
        }
    }

    private fun saveSuperAction(
        existing: SuperActionEntity?,
        name: String,
        description: String,
        selectedGenerals: List<GeneralActionEntity>
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val id: Long
            if (existing == null) {
                id = db.superActionDao().insert(
                    SuperActionEntity(
                        dslaId = dslaId,
                        name = name,
                        description = description,
                        createdDate = todayString()
                    )
                )
            } else {
                id = existing.id
                db.superActionDao().update(existing.copy(name = name, description = description))
                db.superActionDao().clearGeneralsForSuper(id)
            }
            for (generalAction in selectedGenerals) {
                db.superActionDao().addGeneralToSuper(
                    SuperActionGeneralCrossRef(
                        superActionId = id,
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
