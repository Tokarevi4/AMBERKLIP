package com.example.klippercontrol

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.example.klippercontrol.model.Printer
import com.example.klippercontrol.model.PrinterStatus
import com.example.klippercontrol.moonraker.MoonrakerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private val printers = mutableListOf(
        Printer(
            id = "printer-1",
            name = "Printer 1",
            host = "10.19.84.68",
            port = 7125,
            apiKey = null
        )
    )

    private var selectedPrinter = 0

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var printerList: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var apiText: TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        actionBar?.hide()

        setContentView(
            R.layout.activity_main
        )

        drawerLayout = findViewById(
            R.id.drawerLayout
        )

        printerList = findViewById(
            R.id.printerList
        )

        content = findViewById(
            R.id.content
        )

        apiText = findViewById(
            R.id.apiText
        )

        findViewById<ImageButton>(
            R.id.menuButton
        ).setOnClickListener {
            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        findViewById<Button>(
            R.id.uploadButton
        ).setOnClickListener {
            openFilePicker()
        }

        findViewById<Button>(
            R.id.addPrinterButton
        ).setOnClickListener {
            drawerLayout.closeDrawer(
                Gravity.START
            )

            showPrinterManager()
        }

        buildPrinterList()
        refresh()
    }

    private fun buildPrinterList() {
        printerList.removeAllViews()

        printers.forEachIndexed { index, printer ->

            val selected =
                index == selectedPrinter

            val row = LinearLayout(this)

            row.orientation =
                LinearLayout.HORIZONTAL

            row.gravity =
                Gravity.CENTER_VERTICAL

            row.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_printer_selected
                } else {
                    R.drawable.bg_printer
                }
            )

            row.setPadding(
                4,
                2,
                4,
                2
            )

            val printerView =
                TextView(this)

            printerView.text =
                printer.name

            printerView.textSize =
                16f

            printerView.setTextColor(
                resources.getColor(
                    if (selected) {
                        R.color.text_primary
                    } else {
                        R.color.text_secondary
                    }
                )
            )

            printerView.gravity =
                Gravity.CENTER_VERTICAL

            printerView.setPadding(
                16,
                0,
                8,
                0
            )

            printerView.setBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )

            printerView.setOnClickListener {

                selectedPrinter =
                    index

                buildPrinterList()

                drawerLayout.closeDrawer(
                    Gravity.START
                )

                refresh()
            }

            row.addView(
                printerView,
                LinearLayout.LayoutParams(
                    0,
                    52,
                    1f
                )
            )

            val editButton =
                ImageButton(this)

            editButton.setImageResource(
                R.drawable.ic_edit
            )

            editButton.contentDescription =
                "Edit printer"

            editButton.setBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )

            editButton.setPadding(
                12,
                12,
                12,
                12
            )

            editButton.setColorFilter(
                resources.getColor(
                    if (selected) {
                        R.color.text_primary
                    } else {
                        R.color.text_secondary
                    }
                )
            )

            editButton.setOnClickListener {
                showEditPrinterDialog(
                    index
                )
            }

            row.addView(
                editButton,
                LinearLayout.LayoutParams(
                    52,
                    52
                )
            )

            val deleteButton =
                ImageButton(this)

            deleteButton.setImageResource(
                R.drawable.ic_delete
            )

            deleteButton.contentDescription =
                "Delete printer"

            deleteButton.setBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )

            deleteButton.setPadding(
                12,
                12,
                12,
                12
            )

            deleteButton.setColorFilter(
                resources.getColor(
                    if (selected) {
                        R.color.text_primary
                    } else {
                        R.color.text_secondary
                    }
                )
            )

            deleteButton.setOnClickListener {
                showDeletePrinterDialog(
                    index
                )
            }

            row.addView(
                deleteButton,
                LinearLayout.LayoutParams(
                    52,
                    52
                )
            )

            printerList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    52
                )
            )
        }
    }

    private fun installSafeLongPressCopy(
        editText: EditText,
        fieldName: String
    ) {
        editText.setOnLongClickListener {

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val currentText =
                editText.text
                    .toString()

            if (currentText.isNotEmpty()) {

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        fieldName,
                        currentText
                    )
                )

                Toast.makeText(
                    this,
                    "$fieldName copied",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                if (clipboard.hasPrimaryClip()) {

                    val clip =
                        clipboard.primaryClip

                    if (
                        clip != null &&
                        clip.itemCount > 0
                    ) {

                        val pastedText =
                            clip.getItemAt(0)
                                .coerceToText(this)
                                .toString()

                        editText.setText(
                            pastedText
                        )

                        editText.setSelection(
                            editText.text.length
                        )

                        Toast.makeText(
                            this,
                            "$fieldName pasted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            true
        }
    }

    private fun configureEditFields(
        nameEdit: EditText,
        hostEdit: EditText,
        portEdit: EditText,
        apiKeyEdit: EditText
    ) {
        installSafeLongPressCopy(
            nameEdit,
            "Printer name"
        )

        installSafeLongPressCopy(
            hostEdit,
            "Moonraker address"
        )

        installSafeLongPressCopy(
            apiKeyEdit,
            "API key"
        )

        configurePortField(
            portEdit
        )
    }

    private fun configurePortField(
        portEdit: EditText
    ) {
        portEdit.filters = arrayOf(
            InputFilter.LengthFilter(5)
        )

        portEdit.setOnLongClickListener {

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val currentText =
                portEdit.text
                    .toString()

            if (currentText.isNotEmpty()) {

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "AMBERKLIP",
                        currentText
                    )
                )

                Toast.makeText(
                    this,
                    "Port copied",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                if (clipboard.hasPrimaryClip()) {

                    val clip =
                        clipboard.primaryClip

                    if (
                        clip != null &&
                        clip.itemCount > 0
                    ) {

                        val clipboardText =
                            clip.getItemAt(0)
                                .coerceToText(this)
                                .toString()
                                .trim()

                        val validDigits =
                            clipboardText.isNotEmpty() &&
                            clipboardText.length <= 5 &&
                            clipboardText.all {
                                it in '0'..'9'
                            }

                        val port =
                            if (validDigits) {
                                clipboardText.toIntOrNull()
                            } else {
                                null
                            }

                        if (
                            port != null &&
                            port in 0..65535
                        ) {

                            portEdit.setText(
                                port.toString()
                            )

                            portEdit.setSelection(
                                portEdit.text.length
                            )

                            Toast.makeText(
                                this,
                                "Port pasted",
                                Toast.LENGTH_SHORT
                            ).show()

                        } else {

                            Toast.makeText(
                                this,
                                "Port must be an integer from 0 to 65535",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            true
        }
    }

    private fun showEditPrinterDialog(
        index: Int
    ) {
        val printer =
            printers[index]

        val dialog =
            Dialog(this)

        dialog.requestWindowFeature(
            android.view.Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_printer
        )

        val title =
            dialog.findViewById<TextView>(
                R.id.dialogTitle
            )

        val nameEdit =
            dialog.findViewById<EditText>(
                R.id.nameEdit
            )

        val hostEdit =
            dialog.findViewById<EditText>(
                R.id.hostEdit
            )

        val portEdit =
            dialog.findViewById<EditText>(
                R.id.portEdit
            )

        val apiKeyEdit =
            dialog.findViewById<EditText>(
                R.id.apiKeyEdit
            )

        val scanQrButton =
            dialog.findViewById<ImageButton>(
                R.id.scanQrButton
            )

        val cancelButton =
            dialog.findViewById<TextView>(
                R.id.cancelButton
            )

        val actionButton =
            dialog.findViewById<TextView>(
                R.id.actionButton
            )

        title.text =
            "Edit printer"

        actionButton.text =
            "Save"

        nameEdit.setText(
            printer.name
        )

        hostEdit.setText(
            printer.host
        )

        portEdit.setText(
            printer.port.toString()
        )

        apiKeyEdit.setText(
            printer.apiKey ?: ""
        )

        configureEditFields(
            nameEdit,
            hostEdit,
            portEdit,
            apiKeyEdit
        )

        scanQrButton.setOnClickListener {

            Toast.makeText(
                this,
                "QR scanner will be added next",
                Toast.LENGTH_SHORT
            ).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        actionButton.setOnClickListener {

            val name =
                nameEdit.text
                    .toString()
                    .trim()

            val host =
                hostEdit.text
                    .toString()
                    .trim()

            val portText =
                portEdit.text
                    .toString()
                    .trim()

            val apiKey =
                apiKeyEdit.text
                    .toString()
                    .trim()

            if (name.isEmpty()) {

                nameEdit.requestFocus()

                return@setOnClickListener
            }

            if (host.isEmpty()) {

                hostEdit.requestFocus()

                return@setOnClickListener
            }

            val validPortText =
                portText.isNotEmpty() &&
                portText.length <= 5 &&
                portText.all {
                    it in '0'..'9'
                }

            val port =
                if (validPortText) {
                    portText.toIntOrNull()
                } else {
                    null
                }

            if (
                port == null ||
                port !in 0..65535
            ) {

                portEdit.requestFocus()

                Toast.makeText(
                    this,
                    "Port must be an integer from 0 to 65535",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            printers[index] =
                Printer(
                    id = printer.id,
                    name = name,
                    host = host,
                    port = port,
                    apiKey = apiKey.ifBlank {
                        null
                    }
                )

            buildPrinterList()

            dialog.dismiss()

            refresh()
        }

        dialog.show()

        configureDialogWindow(
            dialog
        )
    }

    private fun showDeletePrinterDialog(
        index: Int
    ) {
        val printer =
            printers[index]

        val dialog =
            Dialog(this)

        dialog.requestWindowFeature(
            android.view.Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_printer
        )

        val title =
            dialog.findViewById<TextView>(
                R.id.dialogTitle
            )

        val deleteMessage =
            dialog.findViewById<TextView>(
                R.id.deleteMessage
            )

        val cancelButton =
            dialog.findViewById<TextView>(
                R.id.cancelButton
            )

        val actionButton =
            dialog.findViewById<TextView>(
                R.id.actionButton
            )

        val printerForm =
            dialog.findViewById<LinearLayout>(
                R.id.printerForm
            )

        title.text =
            "Delete printer"

        deleteMessage.text =
            "Delete \"${printer.name}\"?\n\nThis action cannot be undone."

        deleteMessage.visibility =
            View.VISIBLE

        printerForm.visibility =
            View.GONE

        actionButton.text =
            "Delete"

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        actionButton.setOnClickListener {

            printers.removeAt(index)

            if (printers.isEmpty()) {

                selectedPrinter = 0

                content.removeAllViews()

                apiText.text =
                    "No printer"

                addText(
                    "No printers configured",
                    18
                )

            } else {

                if (selectedPrinter > index) {

                    selectedPrinter--

                } else if (
                    selectedPrinter >= printers.size
                ) {

                    selectedPrinter =
                        printers.lastIndex
                }

                buildPrinterList()

                refresh()
            }

            dialog.dismiss()
        }

        dialog.show()

        configureDialogWindow(
            dialog
        )
    }

    private fun configureDialogWindow(
        dialog: Dialog
    ) {
        val window =
            dialog.window
                ?: return

        window.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        val width =
            (
                resources.displayMetrics.widthPixels *
                    0.90f
                ).toInt()

        window.setLayout(
            width,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun refresh() {

        if (printers.isEmpty()) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            MoonrakerClient(
                printer
            )

        apiText.text =
            if (printer.apiKey.isNullOrBlank()) {
                "${printer.displayAddress}\nAPI key: not set"
            } else {
                "${printer.displayAddress}\nAPI key: configured"
            }

        scope.launch {

            try {

                val status =
                    withContext(
                        Dispatchers.IO
                    ) {
                        client.getStatus()
                    }

                render(
                    status
                )

            } catch (e: Exception) {

                renderError(
                    e.message
                        ?: "Connection error"
                )
            }
        }
    }

    private fun render(
        status: PrinterStatus
    ) {
        content.removeAllViews()

        addText(
            "Klipper: ${status.klippyState}",
            18
        )

        addText(
            "Print: ${status.printState}",
            16
        )

        addText(
            "Hotend: " +
                    "${status.hotendTemperature?.let { "%.1f".format(it) } ?: "--"} / " +
                    "${status.hotendTarget?.let { "%.1f".format(it) } ?: "--"} C",
            16
        )

        addText(
            "Bed: " +
                    "${status.bedTemperature?.let { "%.1f".format(it) } ?: "--"} / " +
                    "${status.bedTarget?.let { "%.1f".format(it) } ?: "--"} C",
            16
        )

        addText(
            "File: ${status.filename ?: "--"}",
            15
        )

        addText(
            "Progress: ${(status.progress * 100).toInt()}%",
            15
        )

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

        progress.max =
            100

        progress.progress =
            (status.progress * 100).toInt()

        content.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                12
            )
        )

        val controls =
            LinearLayout(this)

        controls.orientation =
            LinearLayout.HORIZONTAL

        addButton(
            controls,
            "Pause"
        ) {
            runCommand {
                MoonrakerClient(
                    printers[selectedPrinter]
                ).pausePrint()
            }
        }

        addButton(
            controls,
            "Resume"
        ) {
            runCommand {
                MoonrakerClient(
                    printers[selectedPrinter]
                ).resumePrint()
            }
        }

        addButton(
            controls,
            "Cancel"
        ) {
            runCommand {
                MoonrakerClient(
                    printers[selectedPrinter]
                ).cancelPrint()
            }
        }

        content.addView(
            controls
        )

        val stop =
            Button(this)

        stop.text =
            "EMERGENCY STOP"

        stop.setOnClickListener {
            runCommand {
                MoonrakerClient(
                    printers[selectedPrinter]
                ).emergencyStop()
            }
        }

        content.addView(
            stop
        )
    }

    private fun renderError(
        message: String
    ) {
        content.removeAllViews()

        addText(
            "Offline",
            22
        )

        addText(
            message,
            15
        )
    }

    private fun addText(
        text: String,
        size: Int
    ) {
        val view =
            TextView(this)

        view.text =
            text

        view.textSize =
            size.toFloat()

        view.setPadding(
            8,
            10,
            8,
            10
        )

        content.addView(
            view
        )
    }

    private fun addButton(
        parent: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        val button =
            Button(this)

        button.text =
            text

        button.setOnClickListener {
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
    }

    private fun runCommand(
        action: () -> Unit
    ) {
        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    action()
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: "Command failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openFilePicker() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {

                type =
                    "*/*"

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
            }

        startActivityForResult(
            intent,
            REQUEST_FILE
        )
    }

    @Deprecated(
        "Deprecated in Android API"
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode != REQUEST_FILE ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data
                ?: return

        uploadUri(
            uri
        )
    }

    private fun uploadUri(
        uri: Uri
    ) {
        val printer =
            printers[selectedPrinter]

        val client =
            MoonrakerClient(
                printer
            )

        scope.launch {

            try {

                val file =
                    copyUriToCache(
                        uri
                    )

                withContext(
                    Dispatchers.IO
                ) {
                    client.uploadGcode(
                        file,
                        false
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    "Uploaded: ${file.name}",
                    Toast.LENGTH_LONG
                ).show()

                file.delete()

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: "Upload failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun copyUriToCache(
        uri: Uri
    ): File =
        withContext(
            Dispatchers.IO
        ) {

            val name =
                uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "upload.gcode"

            val file =
                File(
                    cacheDir,
                    name
                )

            contentResolver
                .openInputStream(uri)
                .use { input ->

                    requireNotNull(input) {
                        "Cannot open selected file"
                    }

                    FileOutputStream(
                        file
                    ).use { output ->

                        input.copyTo(
                            output
                        )
                    }
                }

            file
        }

    private fun showPrinterManager() {

        val dialog =
            Dialog(this)

        dialog.requestWindowFeature(
            android.view.Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_printer
        )

        val title =
            dialog.findViewById<TextView>(
                R.id.dialogTitle
            )

        val nameEdit =
            dialog.findViewById<EditText>(
                R.id.nameEdit
            )

        val hostEdit =
            dialog.findViewById<EditText>(
                R.id.hostEdit
            )

        val portEdit =
            dialog.findViewById<EditText>(
                R.id.portEdit
            )

        val apiKeyEdit =
            dialog.findViewById<EditText>(
                R.id.apiKeyEdit
            )

        val scanQrButton =
            dialog.findViewById<ImageButton>(
                R.id.scanQrButton
            )

        val cancelButton =
            dialog.findViewById<TextView>(
                R.id.cancelButton
            )

        val actionButton =
            dialog.findViewById<TextView>(
                R.id.actionButton
            )

        title.text =
            "Add printer"

        actionButton.text =
            "Add"

        configureEditFields(
            nameEdit,
            hostEdit,
            portEdit,
            apiKeyEdit
        )

        scanQrButton.setOnClickListener {

            Toast.makeText(
                this,
                "QR scanner will be added next",
                Toast.LENGTH_SHORT
            ).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        actionButton.setOnClickListener {

            val name =
                nameEdit.text
                    .toString()
                    .trim()

            val host =
                hostEdit.text
                    .toString()
                    .trim()

            val portText =
                portEdit.text
                    .toString()
                    .trim()

            val apiKey =
                apiKeyEdit.text
                    .toString()
                    .trim()

            if (name.isEmpty()) {

                nameEdit.requestFocus()

                return@setOnClickListener
            }

            if (host.isEmpty()) {

                hostEdit.requestFocus()

                return@setOnClickListener
            }

            val validPortText =
                portText.isNotEmpty() &&
                portText.length <= 5 &&
                portText.all {
                    it in '0'..'9'
                }

            val port =
                if (validPortText) {
                    portText.toIntOrNull()
                } else {
                    null
                }

            if (
                port == null ||
                port !in 0..65535
            ) {

                portEdit.requestFocus()

                Toast.makeText(
                    this,
                    "Port must be an integer from 0 to 65535",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            printers.add(
                Printer(
                    id = "printer-${printers.size + 1}",
                    name = name,
                    host = host,
                    port = port,
                    apiKey = apiKey.ifBlank {
                        null
                    }
                )
            )

            selectedPrinter =
                printers.lastIndex

            buildPrinterList()

            dialog.dismiss()

            drawerLayout.closeDrawer(
                Gravity.START
            )

            refresh()
        }

        dialog.show()

        configureDialogWindow(
            dialog
        )
    }

    override fun onDestroy() {
        scope.cancel()

        super.onDestroy()
    }

    companion object {
        private const val REQUEST_FILE = 1001
    }
}
