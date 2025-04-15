package com.gawasu.sillyn.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.ItemTaskBinding
import com.gawasu.sillyn.domain.model.Task
import java.text.SimpleDateFormat
import java.util.Locale

class TaskAdapter(private val listener: OnItemClickListener) :
    ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    interface OnItemClickListener {
        fun onItemClick(task: Task)
        fun onCheckboxClick(task: Task, isChecked: Boolean)
    }

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

        fun bind(task: Task) {
            binding.apply {
                checkboxTaskCompleted.isChecked = task.status == Task.TaskStatus.COMPLETED.name
                textviewTaskTitle.text = task.title
                textviewTaskDueDate.text = task.dueDate?.let {
                    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) // Định dạng ngày tháng
                    "Due Date: ${formatter.format(it)}"
                } ?: "No Due Date" // Hiển thị "No Due Date" nếu dueDate là null

                viewPriorityIndicator.setBackgroundResource(getPriorityColor(task.priority))

                checkboxTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                    listener.onCheckboxClick(task, isChecked)
                }

                root.setOnClickListener {
                    listener.onItemClick(task)
                }

                if (checkboxTaskCompleted.isChecked) {
                    textviewTaskTitle.paintFlags = textviewTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textviewTaskDueDate.paintFlags = textviewTaskDueDate.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textviewTaskTitle.paintFlags = textviewTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textviewTaskDueDate.paintFlags = textviewTaskDueDate.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            }
        }

        private fun getPriorityColor(priority: String): Int {
            return when (priority) {
                Task.Priority.HIGH.name -> R.drawable.priority_indicator_high
                Task.Priority.MEDIUM.name -> R.drawable.priority_indicator_medium
                Task.Priority.LOW.name -> R.drawable.priority_indicator_low
                else -> R.drawable.priority_indicator_background
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