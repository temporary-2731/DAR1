package com.dar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dar.app.data.ActionEntity
import com.dar.app.data.AppDatabase
import com.dar.app.databinding.FragmentActionListBinding
import kotlinx.coroutines.launch

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

        binding.btnAddAction.setOnClickListener { showCreateActionDialog() }

        observeActions()
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.actionDao().getAllForDsla(dslaId).collect { actions ->
                renderActionList(actions)
            }
        }
    }

    private fun renderActionList(actions: List<ActionEntity>) {
        binding.actionListContainer.removeAllViews()

        for (action in actions) {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_action, binding.actionListContainer, false) as TextView

            val status = if (action.endDate != null) " (deleted)" else ""
            itemView.text = "${action.name}$status"
            itemView.setOnClickListener { showActionDetail(action) }
            binding.actionListContainer.addView(itemView)
        }
    }

    private fun showActionDetail(action: ActionEntity) {
        val desc = action.description.ifEmpty { getString(R.string.detail_no_description) }
        val message = getString(
            R.string.action_detail_format,
            action.id,
            action.name,
            desc
        )
        AlertDialog.Builder(requireContext())
            .setTitle(action.name)
            .setMessage(message)
            .setPositiveButton(R.string.detail_close, null)
            .show()
    }

    private fun showCreateActionDialog() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_create_action, null)
        val nameField = dialogView.findViewById<EditText>(R.id.edit_action_name)
        val descField = dialogView.findViewById<EditText>(R.id.edit_action_description)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = nameField.text.toString().trim()
                val description = descField.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveAction(name, description)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveAction(name: String, description: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.actionDao().insert(
                ActionEntity(
                    dslaId = dslaId,
                    name = name,
                    description = description
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
