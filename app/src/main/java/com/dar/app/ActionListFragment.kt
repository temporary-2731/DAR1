package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.ActionEntity
import com.dar.app.data.AppDatabase
import com.dar.app.databinding.FragmentActionListBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActionListFragment : Fragment() {

    private var _binding: FragmentActionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var dslaId: Long = -1L

    companion object {
        private const val ARG_DSLA_ID = "arg_dsla_id"

        fun newInstance(dslaId: Long): ActionListFragment {
            val fragment = ActionListFragment()
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
        _binding = FragmentActionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dslaId = arguments?.getLong(ARG_DSLA_ID) ?: -1L
        db = AppDatabase.getInstance(requireContext().applicationContext)

        binding.btnAddAction.setOnClickListener { showActionDialog(null) }

        observeActions()
    }

    private fun todayString(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.actionDao().getActiveForDsla(dslaId).collect { actions ->
                renderActionList(actions)
            }
        }
    }

    private fun renderActionList(actions: List<ActionEntity>) {
        binding.actionListContainer.removeAllViews()

        for (action in actions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_action, binding.actionListContainer, false) as TextView

            itemView.text = action.name
            itemView.setOnClickListener { showActionDetail(action) }
            itemView.setOnLongClickListener {
                showItemMenu(
                    onEdit = { showActionDialog(action) },
                    onDelete = { deleteAction(action) }
                )
                true
            }
            binding.actionListContainer.addView(itemView)
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

    private fun showActionDetail(action: ActionEntity) {
        val desc = action.description.ifEmpty { getString(R.string.detail_no_description) }
        val builder = StringBuilder()
        builder.append(getString(R.string.action_detail_format, action.id, action.name, desc))
        builder.append("\n")
        builder.append(getString(R.string.label_created, action.createdDate.ifEmpty { getString(R.string.detail_none) }))
        if (action.deletedDate != null) {
            builder.append("\n")
            builder.append(getString(R.string.label_deleted, action.deletedDate))
        }
        if (action.recoveredDate != null) {
            builder.append("\n")
            builder.append(getString(R.string.label_recovered, action.recoveredDate))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(action.name)
            .setMessage(builder.toString())
            .setPositiveButton(R.string.detail_close, null)
            .show()
    }

    private fun deleteAction(action: ActionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.actionDao().softDelete(action.id, todayString())
        }
    }

    private fun showActionDialog(existing: ActionEntity?) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_create_action, null)
        val nameField = dialogView.findViewById<EditText>(R.id.edit_action_name)
        val descField = dialogView.findViewById<EditText>(R.id.edit_action_description)

        if (existing != null) {
            nameField.setText(existing.name)
            descField.setText(existing.description)
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = nameField.text.toString().trim()
                val description = descField.text.toString().trim()
                if (name.isEmpty()) {
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val allActions = db.actionDao().getAllForDsla(dslaId).first()
                    val duplicate = allActions.any {
                        it.name.equals(name, ignoreCase = true) && it.id != (existing?.id ?: -1L)
                    }
                    if (duplicate) {
                        Toast.makeText(requireContext(), R.string.action_name_duplicate, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (existing == null) {
                        db.actionDao().insert(
                            ActionEntity(
                                dslaId = dslaId,
                                name = name,
                                description = description,
                                createdDate = todayString()
                            )
                        )
                    } else {
                        db.actionDao().update(existing.copy(name = name, description = description))
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
