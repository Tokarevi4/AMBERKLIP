package com.example.klippercontrol.data

import android.content.Context
import com.example.klippercontrol.model.Printer
import org.json.JSONArray
import org.json.JSONObject

class PrinterStorage(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "printers",
            Context.MODE_PRIVATE
        )

    fun load(): MutableList<Printer> {

        val json =
            preferences.getString(
                "printers",
                null
            )
                ?: return mutableListOf()

        val array =
            JSONArray(json)

        val printers =
            mutableListOf<Printer>()

        for (i in 0 until array.length()) {

            val item =
                array.getJSONObject(i)

            printers.add(
                Printer(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    host = item.getString("host"),
                    port = item.getInt("port"),
                    apiKey =
                        if (item.has("apiKey") &&
                            !item.isNull("apiKey")
                        ) {
                            item.getString("apiKey")
                        } else {
                            null
                        }
                )
            )
        }

        return printers
    }

    fun save(
        printers: List<Printer>
    ) {

        val array =
            JSONArray()

        printers.forEach { printer ->

            val item =
                JSONObject()

            item.put(
                "id",
                printer.id
            )

            item.put(
                "name",
                printer.name
            )

            item.put(
                "host",
                printer.host
            )

            item.put(
                "port",
                printer.port
            )

            if (printer.apiKey == null) {
                item.put(
                    "apiKey",
                    JSONObject.NULL
                )
            } else {
                item.put(
                    "apiKey",
                    printer.apiKey
                )
            }

            array.put(item)
        }

        preferences
            .edit()
            .putString(
                "printers",
                array.toString()
            )
            .apply()
    }

    fun saveTargets(
        printerId: String,
        hotendTarget: Int?,
        bedTarget: Int?
    ) {
        preferences
            .edit()
            .apply {
                if (hotendTarget == null) {
                    remove("hotendTarget_$printerId")
                } else {
                    putInt(
                        "hotendTarget_$printerId",
                        hotendTarget
                    )
                }

                if (bedTarget == null) {
                    remove("bedTarget_$printerId")
                } else {
                    putInt(
                        "bedTarget_$printerId",
                        bedTarget
                    )
                }
            }
            .apply()
    }

    fun loadTargets(
        printerId: String
    ): Pair<Int?, Int?> {

        val hotendKey =
            "hotendTarget_$printerId"

        val bedKey =
            "bedTarget_$printerId"

        val hotendTarget =
            if (preferences.contains(hotendKey)) {
                preferences.getInt(
                    hotendKey,
                    0
                )
            } else {
                null
            }

        val bedTarget =
            if (preferences.contains(bedKey)) {
                preferences.getInt(
                    bedKey,
                    0
                )
            } else {
                null
            }

        return Pair(
            hotendTarget,
            bedTarget
        )
    }
}
