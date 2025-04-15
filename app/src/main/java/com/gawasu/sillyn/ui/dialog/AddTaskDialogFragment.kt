package com.gawasu.sillyn.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gawasu.sillyn.databinding.DialogAddTaskBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.viewmodel.TaskViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date

@AndroidEntryPoint
class AddTaskDialogFragment : DialogFragment() {

    private var _binding: DialogAddTaskBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by activityViewModels() // Sử dụng activityViewModels để chia sẻ ViewModel với Fragment cha
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            // Đặt style cho dialog nếu muốn
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        binding.buttonAddTask.setOnClickListener {
            val title = binding.editTextTaskTitle.text.toString()
            val description = binding.editTextTaskDescription.text.toString()
            //TODO: Lấy dữ liệu từ các input khác (priority, category, dueDate, ...)

            if (title.isNotEmpty()) {
                val newTask = Task(
                    title = title,
                    description = description,
                    dueDate = Date() //TODO: Lấy từ input date picker
                    //TODO: Set các trường khác
                )
                currentUserId?.let {
                    viewModel.addTask(it, newTask)
                }
                dismiss() // Đóng dialog sau khi thêm task
            } else {
                binding.editTextTaskTitle.error = "Tiêu đề không được để trống"
            }
        }

        binding.buttonCancel.setOnClickListener {
            dismiss() // Đóng dialog
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddTaskDialogFragment"
    }
}