package com.gawasu.sillyn.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ItemTaskBinding
import com.gawasu.sillyn.domain.model.Task
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date // Import Date

class TaskAdapter(private val listener: OnItemClickListener) :
    ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    interface OnItemClickListener {
        fun onItemClick(task: Task)
        fun onCheckboxClick(task: Task, isChecked: Boolean)
        fun onItemLongClick(task: Task, view: View): Boolean // Thêm View để gắn PopupMenu
    }

    // Biến để kiểm soát việc hiển thị mô tả (hiện tại chưa dùng theo yêu cầu)
    // var showDescription: Boolean = false
    //    set(value) {
    //        field = value
    //        notifyDataSetChanged() // Cần notify để refresh UI
    //    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Setup click listeners for the entire item and checkbox
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position))
                }
            }

            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemLongClick(getItem(position), binding.root) // Truyền View gốc của item
                    true // Consume the long click
                } else {
                    false
                }
            }

            binding.checkboxTaskCompleted.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val task = getItem(position)
                    val isChecked = binding.checkboxTaskCompleted.isChecked
                    listener.onCheckboxClick(task, isChecked)
                    // UI update happens immediately based on isChecked for better UX
                    applyCompletionStrikeThrough(binding.textviewTaskTitle, isChecked)
                    applyCompletionStrikeThrough(binding.textviewTaskDueDate, isChecked)
                    // Note: The actual task status update is done via ViewModel after checkbox click
                    // and the new list from ViewModel will eventually update the UI again via DiffUtil
                }
            }
        }

        fun bind(task: Task) {
            binding.apply {
                val isCompleted = task.status == Task.TaskStatus.COMPLETED.name
                checkboxTaskCompleted.isChecked = isCompleted
                textviewTaskTitle.text = task.title

                // Format Due Date and Time
                textviewTaskDueDate.text = task.dueDate?.let { date ->
                    val calendar = java.util.Calendar.getInstance().apply { time = date }
                    // Check if time component is non-midnight or non-zero seconds/milliseconds
                    val hasTime = calendar.get(java.util.Calendar.HOUR_OF_DAY) != 0 ||
                            calendar.get(java.util.Calendar.MINUTE) != 0 ||
                            calendar.get(java.util.Calendar.SECOND) != 0 ||
                            calendar.get(java.util.Calendar.MILLISECOND) != 0

                    val format = if (hasTime) {
                        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    } else {
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    }
                    "Due: ${format.format(date)}" // Prefix with "Due:"
                } ?: root.context.getString(R.string.no_due_date) // Use String resource

                // TODO: Description visibility based on showDescription flag (if implemented later)
                // textviewTaskDescription.visibility = if (showDescription && task.description != null) View.VISIBLE else View.GONE
                // textviewTaskDescription.text = task.description

                viewPriorityIndicator.setBackgroundResource(getPriorityColor(task.priority))

                // Apply strike-through effect based on completion status
                applyCompletionStrikeThrough(textviewTaskTitle, isCompleted)
                applyCompletionStrikeThrough(textviewTaskDueDate, isCompleted)

                // Optional: Dim completed tasks
                val alpha = if (isCompleted) 0.6f else 1.0f
                root.alpha = alpha
            }
        }

        private fun applyCompletionStrikeThrough(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }


        private fun getPriorityColor(priority: String): Int {
            return when (priority) {
                Task.Priority.HIGH.name -> R.drawable.priority_indicator_high // Assume these drawables exist
                Task.Priority.MEDIUM.name -> R.drawable.priority_indicator_medium
                Task.Priority.LOW.name -> R.drawable.priority_indicator_low
                else -> R.drawable.priority_indicator_background // Use a default for NONE, e.g., a gray circle
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Task, newItem: Task) =
            oldItem == newItem
    }
}