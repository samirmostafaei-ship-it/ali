package com.assistant.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.assistant.app.receiver.ReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String,
    val scheduledTime: Long,
    val isCompleted: Boolean = false
)

class TaskRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("app_tasks_prefs", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _tasks = MutableStateFlow<List<TaskItem>>(loadTasks())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    fun addTask(title: String, description: String, triggerMillis: Long) {
        val id = (System.currentTimeMillis() % 100000).toInt()
        val newTask = TaskItem(id, title, description, triggerMillis, false)
        val updated = _tasks.value + newTask
        saveTasks(updated)
        _tasks.value = updated

        // ثبت در AlarmManager برای یادآوری دقیق
        scheduleAlarm(newTask)
    }

    fun toggleTask(id: Int) {
        val updated = _tasks.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveTasks(updated)
        _tasks.value = updated
    }

    fun deleteTask(id: Int) {
        cancelAlarm(id)
        val updated = _tasks.value.filter { it.id != id }
        saveTasks(updated)
        _tasks.value = updated
    }

    private fun scheduleAlarm(task: TaskItem) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.assistant.app.ACTION_REMINDER"
            putExtra("EXTRA_ID", task.id)
            putExtra("EXTRA_TITLE", task.title)
            putExtra("EXTRA_DESC", task.description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.scheduledTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                task.scheduledTime,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(taskId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun saveTasks(list: List<TaskItem>) {
        val editor = sharedPrefs.edit()
        editor.putInt("tasks_count", list.size)
        list.forEachIndexed { i, t ->
            editor.putInt("task_${i}_id", t.id)
            editor.putString("task_${i}_title", t.title)
            editor.putString("task_${i}_desc", t.description)
            editor.putLong("task_${i}_time", t.scheduledTime)
            editor.putBoolean("task_${i}_comp", t.isCompleted)
        }
        editor.apply()
    }

    private fun loadTasks(): List<TaskItem> {
        val count = sharedPrefs.getInt("tasks_count", 0)
        val list = mutableListOf<TaskItem>()
        for (i in 0 until count) {
            val id = sharedPrefs.getInt("task_${i}_id", 0)
            val title = sharedPrefs.getString("task_${i}_title", "") ?: ""
            val desc = sharedPrefs.getString("task_${i}_desc", "") ?: ""
            val time = sharedPrefs.getLong("task_${i}_time", 0L)
            val comp = sharedPrefs.getBoolean("task_${i}_comp", false)
            if (title.isNotEmpty()) {
                list.add(TaskItem(id, title, desc, time, comp))
            }
        }
        return list
    }
}
