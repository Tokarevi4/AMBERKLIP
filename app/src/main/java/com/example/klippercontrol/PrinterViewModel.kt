package com.example.klippercontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.klippercontrol.data.PrinterStorage
import com.example.klippercontrol.model.Printer
import com.example.klippercontrol.moonraker.MoonrakerClient
import java.util.UUID

class PrinterViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val printerStorage =
        PrinterStorage(application)

    val printers =
        mutableListOf<Printer>()

    val clients =
        mutableMapOf<String, MoonrakerClient>()

    var selectedPrinter =
        0

    init {
        loadPrinters()
    }

    private fun loadPrinters() {

        printers.clear()

        printers.addAll(
            printerStorage.load()
        )

        if (printers.isEmpty()) {

            printers.add(
                Printer(
                    id = UUID.randomUUID().toString(),
                    name = "Test",
                    host = "",
                    port = 7125,
                    apiKey = ""
                )
            )

            printerStorage.save(
                printers
            )
        }
    }
}
