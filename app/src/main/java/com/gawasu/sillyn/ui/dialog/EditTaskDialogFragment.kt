package com.gawasu.sillyn.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gawasu.sillyn.databinding.DialogAddTaskBinding // Tái sử dụng layout, hoặc tạo layout riêng cho edit
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.viewmodel.TaskViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date

@AndroidEntryPoint
class EditTaskDialogFragment : DialogFragment() {

    private var _binding: DialogAddTaskBinding? = null // Tái sử dụng layout add, hoặc tạo dialog_edit_task.xml
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by activityViewModels()
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String? = null
    private var task: Task? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            // Đặt style cho dialog nếu muốn
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogAddTaskBinding.inflate(inflater, container, false) // Tái sử dụng layout add
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid
        task = arguments?.getParcelable(ARG_TASK)

        task?.let { currentTask ->
            binding.editTextTaskTitle.setText(currentTask.title)
            binding.editTextTaskDescription.setText(currentTask.description)
            //TODO: Điền các trường khác vào dialog
        }

        binding.buttonAddTask.text = "Cập Nhật Nhiệm Vụ" // Đổi text button
        binding.buttonAddTask.setOnClickListener {
            val title = binding.editTextTaskTitle.text.toString()
            val description = binding.editTextTaskDescription.text.toString()
            //TODO: Lấy dữ liệu từ các input khác

            if (title.isNotEmpty()) {
                val updatedTask = task?.copy(
                    title = title,
                    description = description,
                    dueDate = Date() //TODO: Lấy từ input date picker
                    //TODO: Cập nhật các trường khác
                )
                updatedTask?.let { taskToUpdate ->
                    currentUserId?.let { userId ->
                        viewModel.updateTask(userId, taskToUpdate)
                    }
                }
                dismiss()
            } else {
                binding.editTextTaskTitle.error = "Tiêu đề không được để trống"
            }
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditTaskDialogFragment"
        private const val ARG_TASK = "arg_task"

        fun newInstance(task: Task): EditTaskDialogFragment {
            return EditTaskDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_TASK, task)
                }
            }
        }
    }
}