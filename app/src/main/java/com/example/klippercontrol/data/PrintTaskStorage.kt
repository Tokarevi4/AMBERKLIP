package com.example.klippercontrol.data

import android.content.Context
import com.example.klippercontrol.model.PrintTask
import org.json.JSONArray
import org.json.JSONObject

class PrintTaskStorage(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "print_tasks",
            Context.MODE_PRIVATE
        )

    fun load(): MutableList<PrintTask> {

        val json =
            preferences.getString(
                "tasks",
                null
            )
                ?: return mutableListOf()

        val array =
            JSONArray(json)

        val tasks =
            mutableListOf<PrintTask>()

        for (i in 0 until array.length()) {

            val item =
                array.getJSONObject(i)

            tasks.add(
                PrintTask(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    printDate = item.getLong("printDate"),
                    modifiedDate = item.getLong("modifiedDate"),
                    fileSize = item.getLong("fileSize")
                )
            )
        }

        return tasks
    }

    fun save(
        tasks: List<PrintTask>
    ) {

        val array =
            JSONArray()

        tasks.forEach { task ->

            val item =
                JSONObject()

            item.put(
                "id",
                task.id
            )

            item.put(
                "name",
                task.name
            )

            item.put(
                "printDate",
                task.printDate
            )

            item.put(
                "modifiedDate",
                task.modifiedDate
            )

            item.put(
                "fileSize",
                task.fileSize
            )

            array.put(item)
        }

        preferences
            .edit()
            .putString(
                "tasks",
                array.toString()
            )
            .apply()
    }
}
