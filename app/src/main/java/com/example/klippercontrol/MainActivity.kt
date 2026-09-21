package com.example.klippercontrol

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.klippercontrol.ui.PrintTaskAdapter
import com.example.klippercontrol.model.HeaterLimits
import com.example.klippercontrol.model.Printer
import com.example.klippercontrol.model.PrinterStatus
import com.example.klippercontrol.model.PrintTask
import com.example.klippercontrol.model.PrintMetadata
import com.example.klippercontrol.model.PrintHistoryEntry
import com.example.klippercontrol.data.PrinterStorage
import com.example.klippercontrol.moonraker.MoonrakerClient
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.Date
import java.util.UUID

class MainActivity : Activity() {

    private enum class ConnectionUiState {
        CONNECTING,
        CONNECTED,
        ERROR,
        EMPTY
    }

    private enum class TaskUiState {
        CONNECTING,
        UNAVAILABLE,
        LOADING,
        EMPTY,
        ERROR,
        READY
    }

    private var selectedPrinter = 0
    private var selectedTab = TAB_METRICS

    private var connectionUiState =
        ConnectionUiState.CONNECTING


    override fun attachBaseContext(
        newBase: android.content.Context
    ) {
        super.attachBaseContext(
            LocaleHelper.apply(newBase)
        )
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private lateinit var printerStorage: PrinterStorage

    private val invalidFilenameCharacters = Regex("""[\\/:*?"<>|]""")

    private fun isValidGcodeName(name: String): Boolean {
        return name.isNotBlank() &&
                !name.contains(invalidFilenameCharacters) &&
                name != "." &&
                name != ".."
    }

    private val printers =
        mutableListOf<Printer>()

    private val clients =
        mutableMapOf<String, MoonrakerClient>()

    private data class PrintMetrics(
        val speed: Double? = null,
        val flow: Double? = null,
        val filamentMeters: Double = 0.0,
        val currentLayer: Int? = null,
        val totalLayer: Int? = null,
        val remainingTime: Long? = null,
        val slicerRemainingTime: Long? = null,
        val elapsedTime: Long = 0L,
        val estimatedFinishTime: Long? = null
    )

    private data class PrinterUiState(
        var connectionState: ConnectionUiState = ConnectionUiState.CONNECTING,
        var connected: Boolean = false,
        var connectionBlocked: Boolean = false,
        var status: PrinterStatus? = null,
        var hotendLimits: HeaterLimits? = null,
        var bedLimits: HeaterLimits? = null,
        var lastHotendTarget: Int? = null,
        var lastBedTarget: Int? = null,
        var temperatureChartState: TemperatureChartView.ChartState? = null,
        var currentPrintFilename: String? = null,
        var printMetadata: PrintMetadata? = null,
        var printMetrics: PrintMetrics? = null,
        var metadataLoadJob: kotlinx.coroutines.Job? = null,
        var recentPrintHistory: List<PrintHistoryEntry> = emptyList(),
        var recentPrintHistoryLoaded: Boolean = false,
        var recentHistoryLoadJob: kotlinx.coroutines.Job? = null
    )

    private val printerStates =
        mutableMapOf<String, PrinterUiState>()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var printerList: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var contentScroll: MaxHeightScrollView

    private lateinit var metricsContainer: LinearLayout
    private lateinit var tasksContainer: RecyclerView
    private lateinit var tasksCardContainer: FrameLayout
    private lateinit var printTaskAdapter: PrintTaskAdapter
    private lateinit var tabContent: SwipeTabLayout

    private lateinit var settingsContainer: LinearLayout
    private lateinit var bottomNavigation: LinearLayout

    private lateinit var settingsTitleText: TextView
    private lateinit var settingsLanguageLabel: TextView
    private lateinit var languageGroup: RadioGroup
    private lateinit var languageSystem: RadioButton
    private lateinit var languageRussian: RadioButton
    private lateinit var languageEnglish: RadioButton

    private lateinit var hotendCurrentTemperature: TextView
    private lateinit var bedCurrentTemperature: TextView

    private lateinit var hotendTargetInput: EditText
    private lateinit var bedTargetInput: EditText

    private lateinit var progressValue: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var printInfoCard: FrameLayout
    private lateinit var printHistoryContainer: LinearLayout
    private lateinit var printHistoryTitle: TextView
    private lateinit var printHistoryList: LinearLayout
    private lateinit var liveMetricsContainer: LinearLayout
    private lateinit var liveMetricsTitle: TextView
    private lateinit var liveMetricsList: LinearLayout

    private lateinit var temperatureChart: TemperatureChartView

    private lateinit var controlContainer: LinearLayout
    private lateinit var controlEmptyText: TextView

    private lateinit var controlTab: LinearLayout
    private lateinit var controlIcon: ImageView
    private lateinit var controlTabText: TextView

    private lateinit var disableMotorsButton: TextView

    private lateinit var xMinusButton: ImageButton
    private lateinit var xPlusButton: ImageButton
    private lateinit var yMinusButton: ImageButton
    private lateinit var yPlusButton: ImageButton
    private lateinit var xyHomeButton: ImageButton

    private lateinit var zMinusButton: ImageButton
    private lateinit var zPlusButton: ImageButton
    private lateinit var zHomeButton: ImageButton

    private lateinit var xStepSeekBar: SeekBar
    private lateinit var yStepSeekBar: SeekBar
    private lateinit var zStepSeekBar: SeekBar

    private lateinit var xStepValue: TextView
    private lateinit var yStepValue: TextView
    private lateinit var zStepValue: TextView

    private lateinit var xCoordinateInput: EditText
    private lateinit var yCoordinateInput: EditText
    private lateinit var zCoordinateInput: EditText

    private var controlCommandInProgress = false

    private lateinit var tasksEmptyText: TextView

    private val printTasks =
        mutableListOf<PrintTask>()

    private var tasksSortAscending = false

    private var tasksSortField = PrintTaskAdapter.SortField.MODIFIED_DATE

    private var hotendLimits: HeaterLimits? = null
    private var bedLimits: HeaterLimits? = null
    private var limitsPrinterId: String? = null

    private var lastHotendTarget: Int? = null
    private var lastBedTarget: Int? = null

    private var lastSubmittedHotendTarget: Int? = null
    private var lastSubmittedBedTarget: Int? = null

    private var keyboardVisible = false

    private var hotendSubmitRunnable: Runnable? = null
    private var bedSubmitRunnable: Runnable? = null

    private val keyboardHandler =
        Handler(Looper.getMainLooper())

    private var globalLayoutListener:
        ViewTreeObserver.OnGlobalLayoutListener? = null

    private var activeApiKeyEdit: EditText? = null

    private var refreshJob:
        kotlinx.coroutines.Job? = null

    private var refreshGeneration = 0L

    private var taskLoadJob:
        kotlinx.coroutines.Job? = null

    private var taskUiState =
        TaskUiState.UNAVAILABLE

    private var lastStatus: PrinterStatus? = null

    private var printerConnected =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        printerStorage =
            PrinterStorage(this)

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

        actionBar?.hide()

        setContentView(
            R.layout.activity_main
        )

        tabContent =
            findViewById(
                R.id.tabContent
            )

        drawerLayout =
            findViewById(R.id.drawerLayout)

        printerList =
            findViewById(R.id.printerList)

        content =
            findViewById(R.id.content)

        contentScroll =
            findViewById(R.id.contentScroll)

        metricsContainer =
            findViewById(R.id.metricsContainer)

        tasksContainer =
            findViewById(R.id.tasksContainer)

        tasksCardContainer =
            findViewById(R.id.tasksCardContainer)

        tasksEmptyText =
            findViewById(R.id.tasksEmptyText)

        settingsContainer =
            findViewById(
                R.id.settingsContainer
            )

        bottomNavigation =
            findViewById(
                R.id.bottomNavigation
            )

        settingsTitleText =
            findViewById(
                R.id.settingsTitleText
            )

        settingsLanguageLabel =
            findViewById(
                R.id.settingsLanguageLabel
            )

        languageGroup =
            findViewById(
                R.id.languageGroup
            )

        languageSystem =
            findViewById(
                R.id.languageSystem
            )

        languageRussian =
            findViewById(
                R.id.languageRussian
            )

        languageEnglish =
            findViewById(
                R.id.languageEnglish
            )

        printTaskAdapter =
            PrintTaskAdapter(
                onTaskClick = { task ->
                    showTaskActionsDialog(task)
                },
                onSortClick = { field ->
                    changeTaskSort(field)
                }
            )

        tasksContainer.layoutManager =
            LinearLayoutManager(this)

        tasksContainer.adapter =
            printTaskAdapter

        tasksContainer.setHasFixedSize(true)

        configureLanguageSettings()

        contentScroll.maxHeightPx =
            (
                resources.displayMetrics.density *
                    144f
                ).toInt()

        hotendCurrentTemperature =
            findViewById(
                R.id.hotendCurrentTemperature
            )

        bedCurrentTemperature =
            findViewById(
                R.id.bedCurrentTemperature
            )

        hotendTargetInput =
            findViewById(
                R.id.hotendTargetInput
            )

        bedTargetInput =
            findViewById(
                R.id.bedTargetInput
            )

        progressValue =
            findViewById(
                R.id.progressValue
            )

        progressBar =
            findViewById(
                R.id.progressBar
            )

        temperatureChart =
            findViewById(
                R.id.temperatureChart
            )

        printInfoCard =
            findViewById(
                R.id.printInfoCard
            )

        printHistoryContainer =
            findViewById(
                R.id.printHistoryContainer
            )

        printHistoryTitle =
            findViewById(
                R.id.printHistoryTitle
            )

        printHistoryList =
            findViewById(
                R.id.printHistoryList
            )

        liveMetricsContainer =
            findViewById(
                R.id.liveMetricsContainer
            )

        liveMetricsTitle =
            findViewById(
                R.id.liveMetricsTitle
            )

        liveMetricsList =
            findViewById(
                R.id.liveMetricsList
            )

        controlContainer =
            findViewById(
                R.id.controlContainer
            )

        controlEmptyText =
            findViewById(
                R.id.controlEmptyText
            )

        controlTab =
            findViewById(
                R.id.controlTab
            )

        controlIcon =
            findViewById(
                R.id.controlIcon
            )

        controlTabText =
            findViewById(
                R.id.controlTabText
            )

        disableMotorsButton =
            findViewById(
                R.id.disableMotorsButton
            )

        xMinusButton =
            findViewById(R.id.xMinusButton)

        xPlusButton =
            findViewById(R.id.xPlusButton)

        yMinusButton =
            findViewById(R.id.yMinusButton)

        yPlusButton =
            findViewById(R.id.yPlusButton)

        xyHomeButton =
            findViewById(R.id.xyHomeButton)

        zMinusButton =
            findViewById(R.id.zMinusButton)

        zPlusButton =
            findViewById(R.id.zPlusButton)

        zHomeButton =
            findViewById(R.id.zHomeButton)

        xStepSeekBar =
            findViewById(R.id.xStepSeekBar)

        yStepSeekBar =
            findViewById(R.id.yStepSeekBar)

        zStepSeekBar =
            findViewById(R.id.zStepSeekBar)

        xStepValue =
            findViewById(R.id.xStepValue)

        yStepValue =
            findViewById(R.id.yStepValue)

        zStepValue =
            findViewById(R.id.zStepValue)

        xCoordinateInput =
            findViewById(R.id.xCoordinateInput)

        yCoordinateInput =
            findViewById(R.id.yCoordinateInput)

        zCoordinateInput =
            findViewById(R.id.zCoordinateInput)

        findViewById<ImageButton>(
            R.id.menuButton
        ).setOnClickListener {
            drawerLayout.openDrawer(
                Gravity.START
            )
        }

        findViewById<View>(
            R.id.settingsTab
        ).setOnClickListener {
            switchToTab(TAB_SETTINGS)
        }

        findViewById<Button>(
            R.id.addPrinterButton
        ).setOnClickListener {
            drawerLayout.closeDrawer(
                Gravity.START
            )

            showPrinterManager()
        }

        findViewById<View>(
            R.id.metricsTab
        ).setOnClickListener {
            switchToTab(TAB_METRICS)
        }

        findViewById<View>(
            R.id.controlTab
        ).setOnClickListener {
            switchToTab(TAB_CONTROL)
        }

        findViewById<View>(
            R.id.tasksTab
        ).setOnClickListener {
            switchToTab(TAB_TASKS)
        }

        findViewById<View>(
            R.id.uploadGcodeButton
        ).setOnClickListener {
            openFilePicker()
        }

        tabContent.onSwipeListener =
            object : SwipeTabLayout.OnSwipeListener {

                override fun onSwipeLeft() {

                    when (selectedTab) {

                        TAB_METRICS ->
                            switchToTab(
                                TAB_CONTROL
                            )

                        TAB_CONTROL ->
                            switchToTab(
                                TAB_TASKS
                            )


                        TAB_TASKS ->
                            switchToTab(
                                TAB_SETTINGS
                            )
                    }
                }

                override fun onSwipeRight() {

                    when (selectedTab) {

                        TAB_SETTINGS ->
                            switchToTab(
                                TAB_TASKS
                            )

                        TAB_TASKS ->
                            switchToTab(
                                TAB_CONTROL
                            )

                        TAB_CONTROL ->
                            switchToTab(
                                TAB_METRICS
                            )
                    }
                }
            }

        updateBottomNavigation()

        configureTemperatureInputs()

        hotendTargetInput.clearFocus()
        bedTargetInput.clearFocus()

        hotendTargetInput.rootView.isFocusableInTouchMode = true
        hotendTargetInput.rootView.requestFocus()

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        configureKeyboardListener()
        configureControlInputs()

        buildPrinterList()
        refresh()
    }

    private fun logUserAction(
        action: String
    ) {

        val printer =
            printers.getOrNull(
                selectedPrinter
            )

        android.util.Log.i(
            "AMBERKLIP_ACTION",
            "action=$action " +
                    "printerId=${printer?.id ?: "none"} " +
                    "printerName=${printer?.name ?: "none"}"
        )
    }

    private fun configureLanguageSettings() {

        updateSettingsSelection()

        languageGroup.setOnCheckedChangeListener {
                _, checkedId ->

            val language =
                when (checkedId) {

                    R.id.languageRussian ->
                        LocaleHelper.LANGUAGE_RUSSIAN

                    R.id.languageEnglish ->
                        LocaleHelper.LANGUAGE_ENGLISH

                    R.id.languageSystem ->
                        LocaleHelper.LANGUAGE_SYSTEM

                    else ->
                        return@setOnCheckedChangeListener
                }

            LocaleHelper.setLanguage(
                this,
                language
            )

            LocaleHelper.applyToResources(
                this
            )

            updateLocalizedUi()
        }
    }

    private fun updateSettingsSelection() {

        languageGroup.setOnCheckedChangeListener(
            null
        )

        when (
            LocaleHelper.getLanguage(this)
        ) {

            LocaleHelper.LANGUAGE_RUSSIAN ->
                languageRussian.isChecked = true

            LocaleHelper.LANGUAGE_ENGLISH ->
                languageEnglish.isChecked = true

            else ->
                languageSystem.isChecked = true
        }

        languageGroup.setOnCheckedChangeListener {
                _, checkedId ->

            val language =
                when (checkedId) {

                    R.id.languageRussian ->
                        LocaleHelper.LANGUAGE_RUSSIAN

                    R.id.languageEnglish ->
                        LocaleHelper.LANGUAGE_ENGLISH

                    R.id.languageSystem ->
                        LocaleHelper.LANGUAGE_SYSTEM

                    else ->
                        return@setOnCheckedChangeListener
                }

            LocaleHelper.setLanguage(
                this,
                language
            )

            LocaleHelper.applyToResources(
                this
            )

            updateLocalizedUi()
        }
    }

    private fun configureTemperatureInputs() {

        configureTemperatureInput(
            hotendTargetInput
        ) {
            scheduleHotendTemperatureSubmit()
        }

        configureTemperatureInput(
            bedTargetInput
        ) {
            scheduleBedTemperatureSubmit()
        }
    }

    private fun configureTemperatureInput(
        input: EditText,
        onDone: () -> Unit
    ) {

        input.inputType =
            InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_SIGNED

        input.imeOptions =
            EditorInfo.IME_ACTION_DONE

        input.setOnEditorActionListener {
                _,
                actionId,
                _ ->

            if (
                actionId ==
                EditorInfo.IME_ACTION_DONE
            ) {
                input.clearFocus()

                onDone()

                true
            } else {
                false
            }
        }

        input.setOnFocusChangeListener {
                _,
                hasFocus ->

            if (hasFocus) {
                input.selectAll()
            }
        }
    }

    private fun configureControlInputs() {

        configureStepSeekBar(
            xStepSeekBar,
            xStepValue
        )

        configureStepSeekBar(
            yStepSeekBar,
            yStepValue
        )

        configureStepSeekBar(
            zStepSeekBar,
            zStepValue
        )

        xMinusButton.setOnClickListener {
            moveAxisByStep('X', -xStepSeekBar.progress - 1)
        }

        xPlusButton.setOnClickListener {
            moveAxisByStep('X', xStepSeekBar.progress + 1)
        }

        yMinusButton.setOnClickListener {
            moveAxisByStep('Y', -yStepSeekBar.progress - 1)
        }

        yPlusButton.setOnClickListener {
            moveAxisByStep('Y', yStepSeekBar.progress + 1)
        }

        zMinusButton.setOnClickListener {
            moveAxisByStep('Z', -zStepSeekBar.progress - 1)
        }

        zPlusButton.setOnClickListener {
            moveAxisByStep('Z', zStepSeekBar.progress + 1)
        }

        xyHomeButton.setOnClickListener {
            homeControlXY()
        }

        zHomeButton.setOnClickListener {
            homeControlZ()
        }

        disableMotorsButton.setOnClickListener {
            disableControlMotors()
        }

        configureCoordinateInput(
            xCoordinateInput,
            'X'
        )

        configureCoordinateInput(
            yCoordinateInput,
            'Y'
        )

        configureCoordinateInput(
            zCoordinateInput,
            'Z'
        )

        updateControlControls(
            currentPrinterStatus()
        )
    }

    private fun configureStepSeekBar(
        seekBar: SeekBar,
        valueView: TextView
    ) {

        fun updateValue() {
            val value =
                seekBar.progress + 1

            valueView.text =
                getString(
                    R.string.step_value,
                    value
                )
        }

        seekBar.max = 99
        seekBar.progress = 9

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    updateValue()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar
                ) {
                }
            }
        )

        updateValue()
    }

    private fun configureCoordinateInput(
        input: EditText,
        axis: Char
    ) {

        input.setOnEditorActionListener { _, actionId, _ ->

            if (
                actionId ==
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {

                submitAbsoluteCoordinate(
                    axis,
                    input
                )

                true

            } else {

                false
            }
        }
    }

    private fun configureKeyboardListener() {

        val root =
            findViewById<View>(
                android.R.id.content
            )

        globalLayoutListener =
            ViewTreeObserver.OnGlobalLayoutListener {

                val rect =
                    android.graphics.Rect()

                root.getWindowVisibleDisplayFrame(
                    rect
                )

                val height =
                    root.rootView.height

                val visibleHeight =
                    rect.bottom - rect.top

                val heightDifference =
                    height - visibleHeight

                val visible =
                    heightDifference >
                    height * 0.15

                if (
                    keyboardVisible &&
                    !visible
                ) {
                    onKeyboardHidden()
                }

                keyboardVisible = visible
            }

        root.viewTreeObserver
            .addOnGlobalLayoutListener(
                globalLayoutListener
            )
    }

    private fun onKeyboardHidden() {

        val hotendText =
            hotendTargetInput.text
                .toString()
                .trim()

        if (
            hotendTargetInput.hasFocus() &&
            hotendText.isNotEmpty()
        ) {
            scheduleHotendTemperatureSubmit()
        }

        val bedText =
            bedTargetInput.text
                .toString()
                .trim()

        if (
            bedTargetInput.hasFocus() &&
            bedText.isNotEmpty()
        ) {
            scheduleBedTemperatureSubmit()
        }

        hotendTargetInput.clearFocus()
        bedTargetInput.clearFocus()
    }

    private fun scheduleHotendTemperatureSubmit() {

        hotendSubmitRunnable?.let {
            keyboardHandler.removeCallbacks(it)
        }

        val runnable =
            Runnable {
                submitHotendTemperature()
            }

        hotendSubmitRunnable =
            runnable

        keyboardHandler.postDelayed(
            runnable,
            2000L
        )
    }

    private fun currentPrinterStatus(): PrinterStatus? {

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return null

        return printerStates[printer.id]
            ?.status
    }

    private fun updateControlControls(
        status: PrinterStatus?
    ) {

        if (status == null) {

            setControlMoveEnabled(
                xMinusButton,
                false
            )

            setControlMoveEnabled(
                xPlusButton,
                false
            )

            setControlMoveEnabled(
                yMinusButton,
                false
            )

            setControlMoveEnabled(
                yPlusButton,
                false
            )

            setControlMoveEnabled(
                zMinusButton,
                false
            )

            setControlMoveEnabled(
                zPlusButton,
                false
            )

            xStepSeekBar.isEnabled = false
            xCoordinateInput.isEnabled = false

            yStepSeekBar.isEnabled = false
            yCoordinateInput.isEnabled = false

            zStepSeekBar.isEnabled = false
            zCoordinateInput.isEnabled = false

            setHomeButtonEnabled(
                xyHomeButton,
                false
            )

            setHomeButtonEnabled(
                zHomeButton,
                false
            )

            return
        }

        val motorsEnabled =
            status.motorsEnabled == true

        val xHomed =
            status.homedAxes.contains(
                'x',
                ignoreCase = true
            )

        val yHomed =
            status.homedAxes.contains(
                'y',
                ignoreCase = true
            )

        val zHomed =
            status.homedAxes.contains(
                'z',
                ignoreCase = true
            )

        val xEnabled =
            motorsEnabled && xHomed

        val yEnabled =
            motorsEnabled && yHomed

        val zEnabled =
            motorsEnabled && zHomed

        setControlMoveEnabled(
            xMinusButton,
            xEnabled
        )

        setControlMoveEnabled(
            xPlusButton,
            xEnabled
        )

        setControlMoveEnabled(
            yMinusButton,
            yEnabled
        )

        setControlMoveEnabled(
            yPlusButton,
            yEnabled
        )

        setControlMoveEnabled(
            zMinusButton,
            zEnabled
        )

        setControlMoveEnabled(
            zPlusButton,
            zEnabled
        )

        xStepSeekBar.isEnabled = xEnabled
        xCoordinateInput.isEnabled = xEnabled

        yStepSeekBar.isEnabled = yEnabled
        yCoordinateInput.isEnabled = yEnabled

        zStepSeekBar.isEnabled = zEnabled
        zCoordinateInput.isEnabled = zEnabled

        setHomeButtonEnabled(
            xyHomeButton,
            !(xHomed && yHomed)
        )

        setHomeButtonEnabled(
            zHomeButton,
            !zHomed
        )

        android.util.Log.d(
            "AMBERKLIP_CONTROL",
            "homedAxes=${status.homedAxes} " +
                    "xHomed=$xHomed " +
                    "yHomed=$yHomed " +
                    "zHomed=$zHomed " +
                    "xyHomeEnabled=${xyHomeButton.isEnabled} " +
                    "zHomeEnabled=${zHomeButton.isEnabled}"
        )

        if (!xCoordinateInput.hasFocus()) {
            status.xPosition?.let {
                xCoordinateInput.setText(
                    formatCoordinate(it)
                )
            }
        }

        if (!yCoordinateInput.hasFocus()) {
            status.yPosition?.let {
                yCoordinateInput.setText(
                    formatCoordinate(it)
                )
            }
        }

        if (!zCoordinateInput.hasFocus()) {
            status.zPosition?.let {
                zCoordinateInput.setText(
                    formatCoordinate(it)
                )
            }
        }
    }

    private fun setControlMoveEnabled(
        button: ImageButton,
        enabled: Boolean
    ) {

        button.isEnabled = enabled

        button.background =
            resources.getDrawable(
                if (enabled) {
                    R.drawable.bg_management_pad_button
                } else {
                    R.drawable.bg_management_pad_button_disabled
                }
            )

        button.setColorFilter(
            resources.getColor(
                if (enabled) {
                    R.color.background
                } else {
                    R.color.background
                }
            )
        )
    }

    private fun scheduleBedTemperatureSubmit() {

        bedSubmitRunnable?.let {
            keyboardHandler.removeCallbacks(it)
        }

        val runnable =
            Runnable {
                submitBedTemperature()
            }

        bedSubmitRunnable =
            runnable

        keyboardHandler.postDelayed(
            runnable,
            2000L
        )
    }

    private fun submitHotendTemperature() {

        val value =
            hotendTargetInput.text
                .toString()
                .trim()
                .toIntOrNull()
                ?: return

        val limits =
            hotendLimits
                ?: return

        android.util.Log.d(
            "AMBERKLIP_TEMP",
            "HOTEND input=$value limits=${limits.min}-${limits.max}"
        )

        if (
            value < limits.min ||
            value > limits.max
        ) {
            android.util.Log.d(
                "AMBERKLIP_TEMP",
                "HOTEND rejected: value=$value outside ${limits.min}-${limits.max}"
            )

            Toast.makeText(
                this,
                getString(
                    R.string.temperature_invalid_range,
                    limits.min,
                    limits.max
                ),
                Toast.LENGTH_SHORT
            ).show()

            restoreHotendTarget()

            return
        }

        if (
            lastSubmittedHotendTarget ==
            value
        ) {
            android.util.Log.d(
                "AMBERKLIP_TEMP",
                "HOTEND skipped: value=$value already submitted"
            )

            return
        }

        lastSubmittedHotendTarget =
            value

        lastHotendTarget =
            value

        val printer =
            printers[selectedPrinter]

        val state =
            getPrinterState(printer)

        state.lastHotendTarget =
            value

        android.util.Log.d(
            "AMBERKLIP_TEMP",
            "HOTEND submitting target=$value"
        )

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {

                    android.util.Log.d(
                        "AMBERKLIP_TEMP",
                        "HOTEND sending target=$value"
                    )

                    getClient(
                        printers[selectedPrinter]
                    ).setHotendTemperature(
                        value
                    )

                    printerStorage.saveTargets(
                        printerId = printer.id,
                        hotendTarget = value,
                        bedTarget = state.lastBedTarget
                    )

                    android.util.Log.d(
                        "AMBERKLIP_TEMP",
                        "HOTEND command completed target=$value"
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.temperature_set,
                        value
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                android.util.Log.d(
                    "AMBERKLIP_TEMP",
                    "HOTEND refresh after target=$value"
                )

                refresh()

            } catch (e: Exception) {

                lastSubmittedHotendTarget =
                    null

                android.util.Log.d(
                    "AMBERKLIP_TEMP",
                    "HOTEND command failed: ${e.message ?: "unknown error"}"
                )

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.command_failed
                        ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun submitBedTemperature() {

        val value =
            bedTargetInput.text
                .toString()
                .trim()
                .toIntOrNull()
                ?: return

        val limits =
            bedLimits
                ?: return

        android.util.Log.d(
            "AMBERKLIP_TEMP",
            "BED input=$value limits=${limits.min}-${limits.max}"
        )

        if (
            value < limits.min ||
            value > limits.max
        ) {
            android.util.Log.d(
                "AMBERKLIP_TEMP",
                "BED rejected: value=$value outside ${limits.min}-${limits.max}"
            )

            Toast.makeText(
                this,
                getString(
                    R.string.temperature_invalid_range,
                    limits.min,
                    limits.max
                ),
                Toast.LENGTH_SHORT
            ).show()

            restoreBedTarget()

            return
        }

        if (
            lastSubmittedBedTarget ==
            value
        ) {
            android.util.Log.d(
                "AMBERKLIP_TEMP",
                "BED skipped: value=$value already submitted"
            )

            return
        }

        lastSubmittedBedTarget =
            value

        lastBedTarget =
            value

        val printer =
            printers[selectedPrinter]

        val state =
            getPrinterState(printer)

        state.lastBedTarget =
            value

        android.util.Log.d(
            "AMBERKLIP_TEMP",
            "BED submitting target=$value"
        )

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {

                    android.util.Log.d(
                        "AMBERKLIP_TEMP",
                        "BED sending target=$value"
                    )

                    getClient(
                        printers[selectedPrinter]
                    ).setBedTemperature(
                        value
                    )

                    printerStorage.saveTargets(
                        printerId = printer.id,
                        hotendTarget = state.lastHotendTarget,
                        bedTarget = value
                    )

                    android.util.Log.d(
                        "AMBERKLIP_TEMP",
                        "BED command completed target=$value"
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.temperature_set,
                        value
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                android.util.Log.d(
                    "AMBERKLIP_TEMP",
                    "BED refresh after target=$value"
                )

                refresh()

            } catch (e: Exception) {

                lastSubmittedBedTarget =
                    null

                android.util.Log.d(
                    "AMBERKLIP_TEMP",
                    "BED command failed: ${e.message ?: "unknown error"}"
                )

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.command_failed
                        ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun restoreHotendTarget() {

        val target =
            lastHotendTarget
                ?: return

        hotendTargetInput.setText(
            target.toString()
        )

        hotendTargetInput.setSelection(
            hotendTargetInput.text.length
        )
    }

    private fun restoreBedTarget() {

        val target =
            lastBedTarget
                ?: return

        bedTargetInput.setText(
            target.toString()
        )

        bedTargetInput.setSelection(
            bedTargetInput.text.length
        )
    }

    private fun updateBottomNavigation() {

        val metricsIcon =
            findViewById<ImageView>(
                R.id.metricsIcon
            )

        val metricsText =
            findViewById<TextView>(
                R.id.metricsTabText
            )

        val controlIcon =
            findViewById<ImageView>(
                R.id.controlIcon
            )

        val controlText =
            findViewById<TextView>(
                R.id.controlTabText
            )

        val tasksIcon =
            findViewById<ImageView>(
                R.id.tasksIcon
            )

        val tasksText =
            findViewById<TextView>(
                R.id.tasksTabText
            )

        val settingsIcon =
            findViewById<ImageView>(
                R.id.settingsIcon
            )

        val settingsText =
            findViewById<TextView>(
                R.id.settingsTabText
            )

        val metricsActive =
            selectedTab == TAB_METRICS

        val controlActive =
            selectedTab == TAB_CONTROL

        val tasksActive =
            selectedTab == TAB_TASKS

        val settingsActive =
            selectedTab == TAB_SETTINGS

        metricsIcon.setColorFilter(
            resources.getColor(
                if (metricsActive) {
                    R.color.primary
                } else {
                    R.color.text_muted
                }
            )
        )

        controlIcon.setColorFilter(
            resources.getColor(
                if (controlActive) {
                    R.color.primary
                } else {
                    R.color.text_muted
                }
            )
        )

        controlText.setTextColor(
            resources.getColor(
                if (controlActive) {
                    R.color.text_primary
                } else {
                    R.color.text_secondary
                }
            )
        )

        metricsText.setTextColor(
            resources.getColor(
                if (metricsActive) {
                    R.color.primary
                } else {
                    R.color.text_secondary
                }
            )
        )

        tasksIcon.setColorFilter(
            resources.getColor(
                if (tasksActive) {
                    R.color.primary
                } else {
                    R.color.text_muted
                }
            )
        )

        tasksText.setTextColor(
            resources.getColor(
                if (tasksActive) {
                    R.color.primary
                } else {
                    R.color.text_secondary
                }
            )
        )

        settingsIcon.setColorFilter(
            resources.getColor(
                if (settingsActive) {
                    R.color.primary
                } else {
                    R.color.text_muted
                }
            )
        )

        settingsText.setTextColor(
            resources.getColor(
                if (settingsActive) {
                    R.color.primary
                } else {
                    R.color.text_secondary
                }
            )
        )
    }

    private fun switchToTab(
        tab: Int
    ) {

        if (selectedTab == tab) {
            return
        }

        selectedTab =
            tab

        updateBottomNavigation()

        when (tab) {

            TAB_METRICS ->
                showMetrics()

            TAB_CONTROL ->
                showControl()

            TAB_TASKS ->
                showTasks()

            TAB_SETTINGS ->
                showSettings()
        }
    }

    private fun calculatePrintMetrics(
        status: PrinterStatus,
        metadata: PrintMetadata?
    ): PrintMetrics {

        val effectiveProgress =
            when {
                status.printState.equals(
                    "complete",
                    ignoreCase = true
                ) ->
                    1.0

                status.filePosition != null &&
                status.fileSize != null &&
                status.fileSize > 0L ->
                    (
                        status.filePosition.toDouble() /
                            status.fileSize.toDouble()
                    ).coerceIn(
                        0.0,
                        0.99
                    )

                else ->
                    status.progress.coerceIn(
                        0.0,
                        1.0
                    )
            }

        val elapsedTime =
            status.printDuration
                .coerceAtLeast(0.0)
                .toLong()

        val remainingTime =
            if (
                effectiveProgress > 0.0 &&
                effectiveProgress < 1.0 &&
                elapsedTime > 0L
            ) {
                (
                    elapsedTime.toDouble() *
                        (1.0 - effectiveProgress) /
                        effectiveProgress
                ).toLong()
            } else {
                null
            }

        val slicerRemainingTime =
            metadata?.estimatedTime
                ?.takeIf { it > 0.0 }
                ?.let { estimatedTime ->
                    if (effectiveProgress >= 1.0) {
                        0L
                    } else {
                        (
                            estimatedTime *
                                (1.0 - effectiveProgress)
                        ).toLong()
                    }
                }

        val estimatedFinishTime =
            remainingTime?.let {
                System.currentTimeMillis() +
                    it * 1000L
            }

        val filamentMeters =
            status.filamentUsed
                .coerceAtLeast(0.0) /
                1000.0

        val speed = status.liveVelocity

        val filamentDiameter = 1.75

        val filamentArea =
            Math.PI *
                filamentDiameter *
                filamentDiameter /
                4.0

        val flow =
            status.liveExtruderVelocity
                ?.let {
                    kotlin.math.abs(it) *
                        filamentArea
                }

        val calculatedCurrentLayer =
            if (
                status.currentZ != null &&
                metadata?.layerHeight != null &&
                metadata.layerHeight > 0.0
            ) {

                val firstLayerHeight =
                    metadata.firstLayerHeight
                        ?.takeIf { it > 0.0 }
                        ?: metadata.layerHeight

                val layer =
                    kotlin.math.round(
                        (
                            status.currentZ -
                                firstLayerHeight
                        ) /
                            metadata.layerHeight
                    ).toInt() + 1

                layer.coerceAtLeast(1)

            } else {
                null
            }

        return PrintMetrics(
            speed = speed,
            flow = flow,
            filamentMeters = filamentMeters,
            currentLayer = calculatedCurrentLayer,
            totalLayer = metadata?.layerCount,
            remainingTime = remainingTime,
            slicerRemainingTime = slicerRemainingTime,
            elapsedTime = elapsedTime,
            estimatedFinishTime = estimatedFinishTime
        )
    }

    private fun formatDuration(
        seconds: Long?
    ): String {

        if (seconds == null || seconds < 0L) {
            return "--:--:--"
        }

        val hours =
            seconds / 3600L

        val minutes =
            (seconds % 3600L) / 60L

        val remainingSeconds =
            seconds % 60L

        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            remainingSeconds
        )
    }

    private fun formatFinishTime(
        timestamp: Long?
    ): String {

        if (timestamp == null) {
            return "--:--"
        }

        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun formatSpeed(
        speed: Double?
    ): String {

        if (
            speed == null ||
            !speed.isFinite()
        ) {
            return "--"
        }

        return String.format(
            Locale.getDefault(),
            "%.1f mm/s",
            speed
        )
    }

    private fun formatFlow(
        flow: Double?
    ): String {

        if (
            flow == null ||
            !flow.isFinite()
        ) {
            return "--"
        }

        return String.format(
            Locale.getDefault(),
            "%.2f mm³/s",
            flow
        )
    }

    private fun formatFilament(
        meters: Double
    ): String {

        return String.format(
            Locale.getDefault(),
            "%.2f m",
            meters
        )
    }

    private fun formatLayer(
        current: Int?,
        total: Int?
    ): String {

        if (
            current == null ||
            total == null
        ) {
            return "-- / --"
        }

        return "$current / $total"
    }

    private fun formatCoordinate(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%.2f",
            value
        )
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun showMetrics() {

        metricsContainer.visibility = View.VISIBLE
        controlContainer.visibility = View.GONE
        controlEmptyText.visibility = View.GONE
        tasksCardContainer.visibility = View.GONE
        tasksEmptyText.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        bottomNavigation.visibility = View.VISIBLE
        renderCurrentLanguageState()

        if (
            printers.isEmpty() ||
            selectedPrinter < 0 ||
            selectedPrinter >= printers.size
        ) {
            resetPrintInfoCard()
            return
        }

        val printer = printers[selectedPrinter]
        val state = getPrinterState(printer)
        val status = state.status

        if (
            !state.connected ||
            status == null
        ) {
            resetPrintInfoCard()
            return
        }

        if (
            isPrintActive(status)
        ) {

            state.printMetrics?.let {
                renderLivePrintMetrics(
                    it
                )
            } ?: run {
                state.printMetrics = calculatePrintMetrics(status, state.printMetadata
                    )
                state.printMetrics?.let {
                    renderLivePrintMetrics(
                        it
                    )
                }
            }

        } else if (
            state.recentPrintHistoryLoaded
        ) {
            renderPrintHistory(state.recentPrintHistory)
        } else {
            resetPrintInfoCard()
            loadRecentPrintHistory(printer, forceReload = false
            )
        }
    }

    private fun renderControlState() {

        controlContainer.visibility =
            View.GONE

        controlEmptyText.visibility =
            View.VISIBLE

        if (printers.isEmpty()) {

            controlEmptyText.text =
                getString(
                    R.string.tasks_connection_required
                )

            return
        }

        val printer =
            printers.getOrNull(
                selectedPrinter
            )

        if (printer == null) {

            controlEmptyText.text =
                getString(
                    R.string.tasks_connection_required
                )

            return
        }

        val state =
            getPrinterState(printer)

        when (state.connectionState) {

            ConnectionUiState.CONNECTING -> {

                controlEmptyText.text =
                    getString(
                        R.string.tasks_connecting_printer
                    )
            }

            ConnectionUiState.CONNECTED -> {

                if (state.status != null) {

                    controlEmptyText.visibility =
                        View.GONE

                    controlContainer.visibility =
                        View.VISIBLE

                    updateControlControls(
                        state.status
                    )

                } else {

                    controlEmptyText.text =
                        getString(
                            R.string.tasks_connecting_printer
                        )
                }
            }

            ConnectionUiState.ERROR -> {

                controlEmptyText.text =
                    getString(
                        R.string.tasks_connection_required
                    )
            }

            ConnectionUiState.EMPTY -> {

                controlEmptyText.text =
                    getString(
                        R.string.tasks_connection_required
                    )
            }
        }
    }

    private fun showControl() {

        metricsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        tasksCardContainer.visibility = View.GONE
        tasksEmptyText.visibility = View.GONE
        bottomNavigation.visibility = View.VISIBLE
        renderControlState()
    }

    private fun showSettings() {

        metricsContainer.visibility = View.GONE
        controlContainer.visibility = View.GONE
        controlEmptyText.visibility = View.GONE
        tasksCardContainer.visibility = View.GONE
        tasksEmptyText.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        bottomNavigation.visibility = View.VISIBLE
        updateSettingsSelection()
    }

    private fun showTasks() {
        metricsContainer.visibility = View.GONE
        controlContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        controlEmptyText.visibility = View.GONE
        tasksCardContainer.visibility = View.VISIBLE
        tasksEmptyText.visibility = View.GONE
        bottomNavigation.visibility = View.VISIBLE
        renderTasks()
    }

    private fun setTaskActionEnabled(
        action: LinearLayout,
        enabled: Boolean
    ) {

        action.isEnabled =
            enabled

        val isDelete =
            action.id ==
                R.id.taskActionDelete

        val iconColor =
            resources.getColor(
                when {
                    !enabled ->
                        R.color.text_muted

                    isDelete ->
                        R.color.danger

                    else ->
                        R.color.primary
                }
            )

        val textColor =
            resources.getColor(
                when {
                    !enabled ->
                        R.color.text_muted

                    isDelete ->
                        R.color.danger

                    else ->
                        R.color.text_primary
                }
            )

        for (
            index in 0 until action.childCount
        ) {

            when (
                val child =
                    action.getChildAt(index)
            ) {

                is ImageView ->
                    child.setColorFilter(
                        iconColor
                    )

                is TextView ->
                    child.setTextColor(
                        textColor
                    )
            }
        }
    }

    private fun renderTasks() {

        if (selectedTab != TAB_TASKS) {
            tasksCardContainer.visibility = View.GONE
            tasksContainer.visibility =  View.GONE
            tasksEmptyText.visibility = View.GONE
            return
        }

        if (
            connectionUiState == ConnectionUiState.CONNECTING
        ) {
            tasksCardContainer.visibility = View.GONE
            tasksContainer.visibility = View.GONE
            tasksEmptyText.visibility = View.VISIBLE
            tasksEmptyText.text = getString(R.string.tasks_connecting_printer)
            printTaskAdapter.submitList(emptyList())
            printTaskAdapter.setSortState(tasksSortField, tasksSortAscending)
            return
        }

        if (
            connectionUiState == ConnectionUiState.ERROR
        ) {
            tasksCardContainer.visibility = View.GONE
            tasksContainer.visibility = View.GONE
            tasksEmptyText.visibility = View.VISIBLE
            tasksEmptyText.text = getString(R.string.tasks_connection_required)
            printTaskAdapter.submitList(emptyList())
            printTaskAdapter.setSortState(tasksSortField, tasksSortAscending)
            return
        }

        if (printTasks.isEmpty()) {

            tasksCardContainer.visibility =
                View.GONE

            tasksContainer.visibility =
                View.GONE

            tasksEmptyText.visibility =
                View.VISIBLE

            tasksEmptyText.text =
                when (taskUiState) {

                    TaskUiState.CONNECTING ->
                        getString(
                            R.string.tasks_connecting_printer
                        )

                    TaskUiState.UNAVAILABLE ->
                        getString(
                            R.string.tasks_connection_required
                        )

                    TaskUiState.LOADING ->
                        getString(
                            R.string.tasks_loading
                        )

                    TaskUiState.EMPTY ->
                        getString(
                            R.string.tasks_empty
                        )

                    TaskUiState.ERROR ->
                        getString(
                            R.string.tasks_load_error
                        )

                    TaskUiState.READY ->
                        ""
                }

            printTaskAdapter.submitList(
                emptyList()
            )

            printTaskAdapter.setSortState(
                tasksSortField,
                tasksSortAscending
            )

            return
        }

        tasksEmptyText.visibility =
            View.GONE

        tasksCardContainer.visibility =
            View.VISIBLE

        tasksContainer.visibility =
            View.VISIBLE

        val sortedTasks =
            when (tasksSortField) {

                PrintTaskAdapter.SortField.NAME -> {

                    if (tasksSortAscending) {

                        printTasks.sortedBy {
                            it.name.lowercase(
                                Locale.getDefault()
                            )
                        }

                    } else {

                        printTasks.sortedByDescending {
                            it.name.lowercase(
                                Locale.getDefault()
                            )
                        }
                    }
                }

                PrintTaskAdapter.SortField.PRINT_DATE -> {

                    if (tasksSortAscending) {

                        printTasks.sortedBy {
                            it.printDate
                                ?: Long.MIN_VALUE
                        }

                    } else {

                        printTasks.sortedByDescending {
                            it.printDate
                                ?: Long.MIN_VALUE
                        }
                    }
                }

                PrintTaskAdapter.SortField.MODIFIED_DATE -> {

                    if (tasksSortAscending) {

                        printTasks.sortedBy {
                            it.modifiedDate
                        }

                    } else {

                        printTasks.sortedByDescending {
                            it.modifiedDate
                        }
                    }
                }

                PrintTaskAdapter.SortField.FILE_SIZE -> {

                    if (tasksSortAscending) {

                        printTasks.sortedBy {
                            it.fileSize
                        }

                    } else {

                        printTasks.sortedByDescending {
                            it.fileSize
                        }
                    }
                }
            }

        printTaskAdapter.submitList(
            sortedTasks
        )

        printTaskAdapter.setSortState(
            tasksSortField,
            tasksSortAscending
        )
    }

    private fun showTaskActionsDialog(
        task: PrintTask
    ) {

        val view =
            layoutInflater.inflate(
                R.layout.dialog_task_actions,
                null
            )

        val dialog =
            Dialog(
                this,
                R.style.TaskActionsDialogTheme
            )

        dialog.setContentView(view)

        view.findViewById<TextView>(
            R.id.taskDialogTitle
        ).text = task.name

        val printActive =
        currentPrinterStatus()
                ?.let {
                    isPrintActive(it)
                }
                ?: false

        val printAction =
            view.findViewById<LinearLayout>(
                R.id.taskActionPrint
            )

        val preheatAction =
            view.findViewById<LinearLayout>(
                R.id.taskActionPreheat
            )

        val renameAction =
            view.findViewById<LinearLayout>(
                R.id.taskActionRename
            )

        val deleteAction =
            view.findViewById<LinearLayout>(
                R.id.taskActionDelete
            )

        setTaskActionEnabled(
            printAction,
            !printActive
        )

        setTaskActionEnabled(
            preheatAction,
            !printActive
        )

        setTaskActionEnabled(
            renameAction,
            !printActive
        )

        setTaskActionEnabled(
            deleteAction,
            !printActive
        )

        view.findViewById<View>(
            R.id.taskActionPrint
        ).setOnClickListener {

            dialog.dismiss()

            printTask(
                task
            )
        }

        view.findViewById<View>(
            R.id.taskActionPreheat
        ).setOnClickListener {

            dialog.dismiss()

            preheatForTask(
                task
            )
        }

        view.findViewById<View>(
            R.id.taskActionRefreshMetadata
        ).setOnClickListener {

            dialog.dismiss()

            refreshTaskMetadata(
                task
            )
        }

        view.findViewById<View>(
            R.id.taskActionRename
        ).setOnClickListener {

            dialog.dismiss()

            showRenameTaskDialog(
                task
            )
        }

        view.findViewById<View>(
            R.id.taskActionCopy
        ).setOnClickListener {

            dialog.dismiss()

            copyTask(
                task
            )
        }

        view.findViewById<View>(
            R.id.taskActionDelete
        ).setOnClickListener {

            dialog.dismiss()

            showDeleteTaskDialog(
                task
            )
        }

        view.findViewById<TextView>(
            R.id.taskActionCancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.window?.setLayout(
            dpToPx(340),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun printTask(
        task: PrintTask
    ) {

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        val status =
            currentPrinterStatus()

        if (
            status != null &&
            isPrintActive(status)
        ) {

            return
        }

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    getClient(
                        printer
                    ).startPrint(
                        task.name
                    )
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_print_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun queueTask(
        task: PrintTask
    ) {

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    getClient(
                        printer
                    ).enqueuePrint(
                        task.name
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.task_added_to_queue
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_queue_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun preheatForTask(
        task: PrintTask
    ) {

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        val state =
            getPrinterState(
                printer
            )

        val hotendTarget =
            state.lastHotendTarget
                ?: lastHotendTarget

        val bedTarget =
            state.lastBedTarget
                ?: lastBedTarget

        if (
            hotendTarget == null &&
            bedTarget == null
        ) {

            Toast.makeText(
                this,
                getString(
                    R.string.preheat_targets_not_set
                ),
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {

                    val client =
                        getClient(
                            printer
                        )

                    hotendTarget?.let {
                        client.setHotendTemperature(
                            it
                        )
                    }

                    bedTarget?.let {
                        client.setBedTemperature(
                            it
                        )
                    }
                }

                refresh()

                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.preheat_started
                    ),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.preheat_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshTaskMetadata(
        task: PrintTask
    ) {

        if (printers.isEmpty()) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    client.refreshGcodeMetadata(
                        task.name
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    R.string.task_metadata_refreshed,
                    Toast.LENGTH_SHORT
                ).show()

                loadPrintHistory()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_metadata_refresh_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRenameTaskDialog(task: PrintTask) {

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_rename_task,
            null
        )

        val taskName = dialogView.findViewById<EditText>(
            R.id.task_name
        )

        val taskExtension = dialogView.findViewById<TextView>(
            R.id.task_extension
        )

        val buttonCancel = dialogView.findViewById<TextView>(
            R.id.button_cancel
        )

        val buttonRename = dialogView.findViewById<TextView>(
            R.id.button_rename
        )

        val originalName = task.name.substringAfterLast('/')

        val dotIndex = originalName.lastIndexOf('.')

        val extension =
            if (dotIndex > 0) {
                originalName.substring(dotIndex)
            } else {
                ""
            }

        val nameWithoutExtension =
            if (dotIndex > 0) {
                originalName.substring(0, dotIndex)
            } else {
                originalName
            }

        taskName.setText(nameWithoutExtension)
        taskName.setSelection(taskName.text.length)

        taskExtension.text = extension

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        buttonRename.setOnClickListener {

            val newName = taskName.text
                .toString()
                .trim()

            if (!isValidGcodeName(newName)) {

                Toast.makeText(
                    this,
                    R.string.invalid_filename_characters,
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (newName == nameWithoutExtension) {
                dialog.dismiss()
                return@setOnClickListener
            }

            val newFilename = newName + extension

            if (printers.isEmpty()) {
                return@setOnClickListener
            }

            val printer = printers[selectedPrinter]
            val client = getClient(printer)

            scope.launch {

                try {

                    withContext(Dispatchers.IO) {
                        client.renameGcode(
                            task.name,
                            newFilename
                        )
                    }

                    dialog.dismiss()

                    Toast.makeText(
                        this@MainActivity,
                        R.string.task_renamed,
                        Toast.LENGTH_SHORT
                    ).show()

                    refresh()
                    loadPrintHistory()

                } catch (e: Exception) {

                    Toast.makeText(
                        this@MainActivity,
                        e.message
                            ?: getString(R.string.task_rename_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun renameTask(
        task: PrintTask,
        newName: String
    ) {

        if (printers.isEmpty()) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    client.renameGcode(
                        task.name,
                        newName
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    R.string.task_renamed,
                    Toast.LENGTH_SHORT
                ).show()

                loadPrintHistory()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_rename_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun copyTask(
        task: PrintTask
    ) {

        if (printers.isEmpty()) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            getClient(printer)

        scope.launch {

            try {

                val newName =
                    withContext(
                        Dispatchers.IO
                    ) {
                        client.copyGcode(
                            task.name
                        )
                    }

                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.task_copied,
                        newName
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                loadPrintHistory()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_copy_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDeleteTaskDialog(task: PrintTask) {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_delete_task,
            null
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val message = dialogView.findViewById<TextView>(
            R.id.deleteMessage
        )

        val cancelButton = dialogView.findViewById<TextView>(
            R.id.cancelButton
        )

        val actionButton = dialogView.findViewById<TextView>(
            R.id.actionButton
        )

        message.text = getString(
            R.string.delete_task_confirmation,
            task.name
        )

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        actionButton.setOnClickListener {
            dialog.dismiss()
            deleteTask(task)
        }

        dialog.show()

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.window?.setLayout(
            (340 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun deleteTask(
        task: PrintTask
    ) {

        if (printers.isEmpty()) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(
                    Dispatchers.IO
                ) {
                    client.deleteGcode(
                        task.name
                    )
                }

                Toast.makeText(
                    this@MainActivity,
                    R.string.task_deleted,
                    Toast.LENGTH_SHORT
                ).show()

                loadPrintHistory()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.task_delete_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun changeTaskSort(
        field: PrintTaskAdapter.SortField
    ) {

        if (tasksSortField == field) {

            tasksSortAscending =
                !tasksSortAscending

        } else {

            tasksSortField =
                field

            tasksSortAscending =
                field ==
                    PrintTaskAdapter.SortField.NAME
        }

        renderTasks()

        printTaskAdapter.setSortState(
            tasksSortField,
            tasksSortAscending
        )
    }

    private fun loadPrintHistory() {

        if (
            printers.isEmpty() ||
            !printerConnected ||
            lastStatus == null
        ) {
            taskLoadJob?.cancel()
            taskLoadJob = null

            printTasks.clear()

            if (connectionUiState == ConnectionUiState.CONNECTING) {
                taskUiState =
                    TaskUiState.CONNECTING
            } else {
                taskUiState =
                    TaskUiState.UNAVAILABLE
            }

            renderTasks()

            return
        }

        taskLoadJob?.cancel()

        val printer =
            printers[selectedPrinter]

        val printerId =
            printer.id

        val client =
            getClient(printer)

        printTasks.clear()

        taskUiState =
            TaskUiState.LOADING

        renderTasks()

        taskLoadJob =
            scope.launch {

                try {

                    val history =
                        withContext(
                            Dispatchers.IO
                        ) {
                            client.getPrintHistory()
                        }

                    val gcodeFiles =
                        withContext(
                            Dispatchers.IO
                        ) {
                            client.getGcodeFiles()
                        }

                    if (
                        !coroutineContext.isActive ||
                        !printerConnected ||
                        printers.isEmpty() ||
                        selectedPrinter >= printers.size ||
                        printers[selectedPrinter].id != printerId
                    ) {
                        return@launch
                    }

                    val combined =
                        mutableListOf<PrintTask>()

                    combined.addAll(
                        history
                    )

                    val historyNames =
                        history
                            .map {
                                it.name
                            }
                            .toSet()

                    gcodeFiles.forEach { file ->

                        if (
                            file.name !in historyNames
                        ) {
                            combined.add(
                                file
                            )
                        }
                    }

                    printTasks.clear()

                    printTasks.addAll(
                        combined
                    )

                    taskUiState =
                        if (combined.isEmpty()) {
                            TaskUiState.EMPTY
                        } else {
                            TaskUiState.READY
                        }

                    renderTasks()

                } catch (e: Exception) {

                    if (!coroutineContext.isActive) {
                        return@launch
                    }

                    if (
                        !printerConnected ||
                        printers.isEmpty() ||
                        selectedPrinter >= printers.size ||
                        printers[selectedPrinter].id != printerId
                    ) {
                        return@launch
                    }

                    printTasks.clear()

                    taskUiState =
                        TaskUiState.ERROR

                    renderTasks()

                    android.util.Log.d(
                        "AMBERKLIP_TASKS",
                        "history loading failed: ${e.message}"
                    )
                }
            }
    }

    private fun formatTaskDate(
        timestamp: Long?
    ): String {

        if (timestamp == null) {
            return "---"
        }

        val formatter =
            SimpleDateFormat(
                "dd.MM.yyyy HH:mm",
                Locale.getDefault()
            )

        return formatter.format(
            Date(timestamp)
        )
    }

    private fun formatTaskSize(
        size: Long
    ): String {

        if (size < 1024L) {
            return "$size B"
        }

        if (size < 1024L * 1024L) {
            return "%.1f KB".format(
                Locale.getDefault(),
                size / 1024.0
            )
        }

        if (size < 1024L * 1024L * 1024L) {
            return "%.1f MB".format(
                Locale.getDefault(),
                size / (1024.0 * 1024.0)
            )
        }

        return "%.1f GB".format(
            Locale.getDefault(),
            size / (1024.0 * 1024.0 * 1024.0)
        )
    }

    private fun buildPrinterList() {
        printerList.removeAllViews()

        if (printers.isEmpty()) {
            return
        }

        printers.forEachIndexed { index, printer ->

            val selected =
                index == selectedPrinter

            val row =
                LinearLayout(this)

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

                if (selectedPrinter == index) {
                    drawerLayout.closeDrawer(
                        Gravity.START
                    )
                    return@setOnClickListener
                }

                refreshGeneration++

                refreshJob?.cancel()
                refreshJob = null

                taskLoadJob?.cancel()
                taskLoadJob = null

                if (
                    selectedPrinter >= 0 &&
                    selectedPrinter < printers.size
                ) {
                    val currentPrinter =
                        printers[selectedPrinter]

                    val currentState =
                        getPrinterState(
                            currentPrinter
                        )

                    currentState.temperatureChartState =
                        temperatureChart.saveState()
                }

                resetPrintInfoCard()

                selectedPrinter = index

                restorePrinterState()

                val newPrinter =
                    printers[selectedPrinter]

                val newState =
                    getPrinterState(
                        newPrinter
                    )

                /*
                 * Старый runtime-status больше не должен
                 * отображаться для нового принтера.
                 */
                newState.status = null
                newState.connected = false
                newState.connectionState =
                    ConnectionUiState.CONNECTING
                newState.connectionBlocked = false

                lastStatus = null
                printerConnected = false

                connectionUiState =
                    ConnectionUiState.CONNECTING

                buildPrinterList()

                if (selectedTab == TAB_CONTROL) {
                    renderControlState()
                }

                drawerLayout.closeDrawer(
                    Gravity.START
                )

                refresh()

                if (selectedTab == TAB_TASKS) {
                    loadPrintHistory()
                }
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
                getString(
                    R.string.edit_printer
                )

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
                getString(
                    R.string.delete_printer
                )

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

    private fun resetPrintInfoCard() {

        printInfoCard.visibility =
            View.GONE

        printHistoryContainer.visibility =
            View.GONE

        liveMetricsContainer.visibility =
            View.GONE
    }

    private fun restorePrinterState() {

        if (
            printers.isEmpty() ||
            selectedPrinter < 0 ||
            selectedPrinter >= printers.size
        ) {
            return
        }

        val printer = printers[selectedPrinter]

        val state = getPrinterState(printer)

        val savedTargets = printerStorage.loadTargets(printer.id)

        if (state.lastHotendTarget == null) {
            state.lastHotendTarget =
                savedTargets.first
        }

        if (state.lastBedTarget == null) {
            state.lastBedTarget =
                savedTargets.second
        }

        connectionUiState =
            state.connectionState

        printerConnected =
            state.connected

        lastStatus =
            state.status

        resetPrintInfoCard()

        hotendLimits =
            state.hotendLimits

        bedLimits =
            state.bedLimits

        limitsPrinterId =
            if (
                state.hotendLimits != null &&
                state.bedLimits != null
            ) {
                printer.id
            } else {
                null
            }

        lastHotendTarget =
            state.lastHotendTarget

        lastBedTarget =
            state.lastBedTarget

        temperatureChart.restoreState(
            state.temperatureChartState
        )

        if (state.status != null) {
            updateTemperatureControls(state.status!!)
        } else {
            hotendCurrentTemperature.text = "--.-°"
            bedCurrentTemperature.text = "--.-°"
            progressValue.text = "0%"
            progressBar.progress = 0
            hotendTargetInput.setText(state.lastHotendTarget?.toString() ?: "")
            bedTargetInput.setText(state.lastBedTarget?.toString() ?: "")
        }

        if (
            connectionUiState ==
            ConnectionUiState.CONNECTED
        ) {
            setTemperatureInputsEnabled(
                true
            )
        } else {
            setTemperatureInputsEnabled(
                false
            )
        }

        if (
            selectedTab ==
            TAB_METRICS
        ) {
            val status =
                state.status

            if (
                state.connected &&
                status != null
            ) {
                if (
                    isPrintActive(status)
                ) {
                    state.printMetrics?.let {
                        renderLivePrintMetrics(it)
                    }
                } else if (
                    state.recentPrintHistoryLoaded
                ) {
                    renderPrintHistory(
                        state.recentPrintHistory
                    )
                }
            }
        }

        if (
            selectedTab ==
            TAB_METRICS
        ) {
            renderCurrentLanguageState()
        }
    }

    private fun resetTemperatureState() {

        taskLoadJob?.cancel()
        taskLoadJob = null

        printTasks.clear()

        taskUiState =
            TaskUiState.UNAVAILABLE

        connectionUiState =
            ConnectionUiState.CONNECTING

        printerConnected =
            false

        lastStatus =
            null

        hotendLimits =
            null

        bedLimits =
            null

        limitsPrinterId =
            null

        lastHotendTarget =
            null

        lastBedTarget =
            null

        lastSubmittedHotendTarget =
            null

        lastSubmittedBedTarget =
            null

        hotendTargetInput.text.clear()
        bedTargetInput.text.clear()

        hotendCurrentTemperature.text =
            "--.-°"

        bedCurrentTemperature.text =
            "--.-°"

        progressValue.text =
            "0%"

        progressBar.progress =
            0

        temperatureChart.clearHistory()

        if (selectedTab == TAB_TASKS) {
            renderTasks()
        }
    }

    private fun invalidatePrinterState(
        printerId: String
    ) {

        printerStates.remove(
            printerId
        )

        clients.remove(
            printerId
        )
    }

    private fun renderConnecting() {

        content.removeAllViews()

        addText(
            getString(R.string.connection_in_progress),
            18
        )
    }

    private fun refresh() {

        if (printers.isEmpty()) {
            return
        }

        if (
            selectedPrinter < 0 ||
            selectedPrinter >= printers.size
        ) {
            return
        }

        val generation =
            refreshGeneration

        val printer =
            printers[selectedPrinter]

        val printerId =
            printer.id

        val state =
            getPrinterState(printer)

        if (state.connectionBlocked) {

            restorePrinterState()
            renderSelectedTabState()

            return
        }

        val client =
            getClient(printer)

        /*
         * Показываем CONNECTING только тогда,
         * когда у текущего принтера действительно
         * нет актуального статуса.
         *
         * При обычном refresh работающего принтера
         * старые координаты/температуры не исчезают.
         */
        if (state.status == null) {

            state.connectionState =
                ConnectionUiState.CONNECTING

            state.connected =
                false

            connectionUiState =
                ConnectionUiState.CONNECTING

            printerConnected =
                false

            lastStatus =
                null

            renderSelectedTabState()
        }

        scope.launch {

            try {

                val status =
                    withContext(
                        Dispatchers.IO
                    ) {
                        client.getStatus()
                    }

                /*
                 * Проверяем, что ответ относится
                 * именно к текущему выбранному принтеру.
                 */
                if (
                    !isActive ||
                    generation != refreshGeneration ||
                    printers.isEmpty() ||
                    selectedPrinter < 0 ||
                    selectedPrinter >= printers.size ||
                    printers[selectedPrinter].id != printerId
                ) {
                    return@launch
                }

                val wasConnected =
                    state.connected

                processStatus(
                    status
                )

                if (
                    selectedTab ==
                    TAB_CONTROL
                ) {
                    renderControlState()
                }

                /*
                 * История печати не является частью
                 * проверки подключения принтера.
                 * Её ошибка не должна переводить
                 * подключённый принтер в ERROR.
                 */
                if (!wasConnected) {

                    try {

                        loadPrintHistory()

                    } catch (e: Exception) {

                        android.util.Log.d(
                            "AMBERKLIP_HISTORY",
                            "initial history loading failed: " +
                                    "${e.message}"
                        )
                    }
                }

                /*
                 * Пределы нагревателей также являются
                 * дополнительными данными.
                 * Ошибка здесь не означает потерю
                 * соединения с Moonraker.
                 */
                try {

                    loadHeaterLimits(
                        printer
                    )

                } catch (e: Exception) {

                    setTemperatureInputsEnabled(
                        false
                    )

                    android.util.Log.d(
                        "AMBERKLIP_TEMP",
                        "heater limits loading failed: " +
                                "${e.message}"
                    )
                }

                if (
                    !isActive ||
                    generation != refreshGeneration ||
                    printers.isEmpty() ||
                    selectedPrinter < 0 ||
                    selectedPrinter >= printers.size ||
                    printers[selectedPrinter].id != printerId
                ) {
                    return@launch
                }

                startRefreshLoop(
                    printerId =
                        printerId,
                    client =
                        client,
                    generation =
                        generation
                )

            } catch (e: CancellationException) {

                return@launch

            } catch (e: Exception) {

                if (
                    !isActive ||
                    generation != refreshGeneration ||
                    printers.isEmpty() ||
                    selectedPrinter < 0 ||
                    selectedPrinter >= printers.size ||
                    printers[selectedPrinter].id != printerId
                ) {
                    return@launch
                }

                /*
                 * Ошибка именно первоначального
                 * подключения: актуального статуса
                 * у принтера нет.
                 */
                state.status =
                    null

                state.connected =
                    false

                state.connectionState =
                    ConnectionUiState.ERROR

                state.connectionBlocked =
                    true

                printerConnected =
                    false

                lastStatus =
                    null

                connectionUiState =
                    ConnectionUiState.ERROR

                taskLoadJob?.cancel()
                taskLoadJob = null

                printTasks.clear()

                taskUiState =
                    TaskUiState.UNAVAILABLE

                setTemperatureInputsEnabled(
                    false
                )

                updateControlControls(
                    null
                )

                android.util.Log.d(
                    "AMBERKLIP_REFRESH",
                    "initial connection failed: " +
                            "${e.message}"
                )

                renderSelectedTabState()
            }
        }
    }

    private fun getClient(
        printer: Printer
    ): MoonrakerClient {

        return clients.getOrPut(printer.id) {
            MoonrakerClient(printer)
        }
    }

    private fun getPrinterState(
        printer: Printer
    ): PrinterUiState {

        return printerStates.getOrPut(
            printer.id
        ) {
            PrinterUiState()
        }
    }

    private fun isAxisHomed(
        status: PrinterStatus?,
        axis: Char
    ): Boolean {

        val homedAxes =
            status?.homedAxes
                ?: return false

        return homedAxes.contains(
            axis,
            ignoreCase = true
        )
    }

    private fun isXYHomed(
        status: PrinterStatus?
    ): Boolean {

        return isAxisHomed(
            status,
            'X'
        ) &&
        isAxisHomed(
            status,
            'Y'
        )
    }

    private fun isZHomed(
        status: PrinterStatus?
    ): Boolean {

        return isAxisHomed(
            status,
            'Z'
        )
    }

    private fun moveAxisByStep(
        axis: Char,
        step: Int
    ) {

        logUserAction(
            "MOVE_${axis}_${if (step > 0) "PLUS" else "MINUS"} " +
                    "step=$step"
        )

        if (controlCommandInProgress) {
            return
        }

        val status =
            currentPrinterStatus()
                ?: return

        val current =
            when (axis) {
                'X' -> status.xPosition
                'Y' -> status.yPosition
                'Z' -> status.zPosition
                else -> null
            }
                ?: return

        val minimum =
            when (axis) {
                'X' -> status.xMinimum
                'Y' -> status.yMinimum
                'Z' -> status.zMinimum
                else -> null
            }
                ?: return

        val maximum =
            when (axis) {
                'X' -> status.xMaximum
                'Y' -> status.yMaximum
                'Z' -> status.zMaximum
                else -> null
            }
                ?: return

        val target =
            (current + step.toDouble())
                .coerceIn(
                    minimum,
                    maximum
                )

        if (target == current) {
            return
        }

        moveAxisTo(
            axis,
            target
        )
    }

    private fun moveAxisTo(
        axis: Char,
        target: Double
    ) {

        logUserAction(
            "ABSOLUTE_$axis coordinate=$target"
        )

        if (controlCommandInProgress) {
            return
        }

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        val status =
            printerStates[printer.id]
                ?.status
                ?: return

        val homed =
            status.homedAxes.contains(
                axis,
                ignoreCase = true
            )

        if (
            status.motorsEnabled != true ||
            !homed
        ) {
            Toast.makeText(
                this,
                R.string.axis_not_homed,
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        controlCommandInProgress = true

        val client =
            getClient(printer)

        val feedrate =
            if (axis == 'Z') {
                600
            } else {
                3000
            }

        scope.launch {

            try {

                withContext(Dispatchers.IO) {

                    client.moveAxis(
                        axis,
                        target,
                        feedrate
                    )
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.axis_move_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                controlCommandInProgress = false
            }
        }
    }

    private fun submitAbsoluteCoordinate(
        axis: Char,
        input: EditText
    ) {

        val value =
            input.text
                .toString()
                .trim()
                .replace(',', '.')
                .toFloatOrNull()
                ?.toDouble()

        if (value == null) {

            Toast.makeText(
                this,
                R.string.invalid_coordinate,
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val status =
            currentPrinterStatus()
                ?: return

        val minimum =
            when (axis) {
                'X' -> status.xMinimum
                'Y' -> status.yMinimum
                'Z' -> status.zMinimum
                else -> null
            }

        val maximum =
            when (axis) {
                'X' -> status.xMaximum
                'Y' -> status.yMaximum
                'Z' -> status.zMaximum
                else -> null
            }

        if (
            minimum == null ||
            maximum == null
        ) {
            return
        }

        if (
            value < minimum ||
            value > maximum
        ) {

            Toast.makeText(
                this,
                getString(
                    R.string.coordinate_out_of_range,
                    axis.toString(),
                    formatCoordinate(minimum),
                    formatCoordinate(maximum)
                ),
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        moveAxisTo(
            axis,
            value
        )

        input.clearFocus()
    }

    private fun homeControlXY() {

        logUserAction(
            "HOME_XY"
        )

        if (controlCommandInProgress) {
            return
        }

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        val status =
            printerStates[printer.id]
                ?.status
                ?: return

        val xHomed =
            status.homedAxes.contains(
                'x',
                ignoreCase = true
            )

        val yHomed =
            status.homedAxes.contains(
                'y',
                ignoreCase = true
            )

        if (xHomed && yHomed) {
            logUserAction(
                "HOME_XY_BLOCKED_ALREADY_HOMED axes=${status.homedAxes}"
            )
            return
        }

        controlCommandInProgress = true

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(Dispatchers.IO) {
                    client.homeXY()
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.axis_move_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                controlCommandInProgress = false
            }
        }
    }

    private fun homeControlZ() {

        logUserAction(
            "HOME_Z"
        )

        if (controlCommandInProgress) {
            return
        }

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        val status =
            printerStates[printer.id]
                ?.status
                ?: return

        val zHomed =
            status.homedAxes.contains(
                'z',
                ignoreCase = true
            )

        if (zHomed) {
            logUserAction(
                "HOME_Z_BLOCKED_ALREADY_HOMED axes=${status.homedAxes}"
            )
            return
        }

        controlCommandInProgress = true

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(Dispatchers.IO) {
                    client.homeZ()
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.axis_move_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                controlCommandInProgress = false
            }
        }
    }

    private fun disableControlMotors() {

        logUserAction(
            "DISABLE_MOTORS"
        )

        if (controlCommandInProgress) {
            return
        }

        val printer =
            printers.getOrNull(
                selectedPrinter
            )
                ?: return

        controlCommandInProgress = true

        val client =
            getClient(printer)

        scope.launch {

            try {

                withContext(Dispatchers.IO) {
                    client.disableMotors()
                }

                refresh()

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.motors_disable_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()

            } finally {

                controlCommandInProgress = false
            }
        }
    }

    private fun startRefreshLoop(
        printerId: String,
        client: MoonrakerClient,
        generation: Long
    ) {

        refreshJob?.cancel()

        if (printers.isEmpty()) {
            return
        }

        if (
            selectedPrinter < 0 ||
            selectedPrinter >= printers.size
        ) {
            return
        }

        if (
            printers[selectedPrinter].id != printerId
        ) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val state =
            getPrinterState(printer)

        if (state.connectionBlocked) {
            refreshJob = null
            return
        }

        refreshJob =
            scope.launch {

                try {

                    while (isActive) {

                        android.util.Log.d(
                            "AMBERKLIP_REFRESH",
                            "LOOP START printerId=$printerId " +
                                    "selectedPrinter=$selectedPrinter"
                        )

                        delay(1000)

                        if (!isActive) {
                            break
                        }

                        if (
                            generation !=
                            refreshGeneration
                        ) {
                            android.util.Log.d(
                                "AMBERKLIP_REFRESH",
                                "LOOP BREAK: generation changed"
                            )
                            break
                        }

                        if (
                            printers.isEmpty() ||
                            selectedPrinter < 0 ||
                            selectedPrinter >= printers.size ||
                            printers[selectedPrinter].id != printerId
                        ) {
                            android.util.Log.d(
                                "AMBERKLIP_REFRESH",
                                "LOOP BREAK: printer changed"
                            )
                            break
                        }

                        val requestStart =
                            System.currentTimeMillis()

                        android.util.Log.d(
                            "AMBERKLIP_REFRESH",
                            "REQUEST printerId=$printerId"
                        )

                        /*
                         * Внутри этого try находится только запрос
                         * текущего статуса принтера.
                         *
                         * Поэтому ошибка сети означает именно
                         * потерю соединения, а не ошибку какой-либо
                         * дополнительной операции.
                         */
                        val status =
                            try {

                                withContext(
                                    Dispatchers.IO
                                ) {
                                    client.getStatus()
                                }

                            } catch (e: CancellationException) {

                                throw e

                            } catch (e: Exception) {

                                android.util.Log.d(
                                    "AMBERKLIP_REFRESH",
                                    "STATUS REQUEST FAILED: " +
                                            "${e.message}"
                                )

                                state.status =
                                    null

                                state.connected =
                                    false

                                state.connectionState =
                                    ConnectionUiState.ERROR

                                state.connectionBlocked =
                                    false

                                printerConnected =
                                    false

                                lastStatus =
                                    null

                                connectionUiState =
                                    ConnectionUiState.ERROR

                                taskLoadJob?.cancel()
                                taskLoadJob = null

                                printTasks.clear()

                                taskUiState =
                                    TaskUiState.UNAVAILABLE

                                setTemperatureInputsEnabled(
                                    false
                                )

                                updateControlControls(
                                    null
                                )

                                refreshJob =
                                    null

                                renderSelectedTabState()

                                return@launch
                            }

                        val requestEnd =
                            System.currentTimeMillis()

                        /*
                         * Пока выполнялся запрос, пользователь мог
                         * переключить принтер или запустить новый refresh.
                         */
                        if (
                            !isActive ||
                            generation != refreshGeneration ||
                            printers.isEmpty() ||
                            selectedPrinter < 0 ||
                            selectedPrinter >= printers.size ||
                            printers[selectedPrinter].id != printerId
                        ) {
                            break
                        }

                        android.util.Log.d(
                            "AMBERKLIP_REFRESH",
                            "RESPONSE printerId=$printerId " +
                                    "printState=${status.printState} " +
                                    "filename=${status.filename}"
                        )

                        val wasConnected =
                            state.connected

                        val renderStart =
                            System.currentTimeMillis()

                        /*
                         * processStatus() является единственной
                         * точкой обновления состояния подключённого
                         * принтера и его интерфейса.
                         */
                        processStatus(
                            status
                        )

                        val renderEnd =
                            System.currentTimeMillis()

                        /*
                         * История нужна только при переходе
                         * из отключённого состояния в подключённое.
                         *
                         * Ошибка её загрузки НЕ должна считаться
                         * ошибкой соединения с принтером.
                         */
                        if (!wasConnected) {

                            try {

                                loadPrintHistory()

                            } catch (e: Exception) {

                                android.util.Log.d(
                                    "AMBERKLIP_HISTORY",
                                    "history loading failed: " +
                                            "${e.message}"
                                )
                            }
                        }

                        android.util.Log.d(
                            "AMBERKLIP_REFRESH",
                            "request=${requestEnd - requestStart}ms " +
                                    "render=${renderEnd - renderStart}ms"
                        )
                    }

                } catch (e: CancellationException) {

                    android.util.Log.d(
                        "AMBERKLIP_REFRESH",
                        "LOOP CANCELLED printerId=$printerId"
                    )

                } catch (e: Exception) {

                    /*
                     * Сюда не должны попадать обычные ошибки
                     * сетевого запроса: они обрабатываются выше.
                     *
                     * Если сюда попала ошибка, это уже ошибка
                     * самой логики обновления/UI.
                     */
                    android.util.Log.e(
                        "AMBERKLIP_REFRESH",
                        "LOOP FAILED: ${e.message}",
                        e
                    )
                }
            }
    }

    private suspend fun loadHeaterLimits(
        printer: Printer
    ) {

        if (
            limitsPrinterId ==
            printer.id &&
            hotendLimits != null &&
            bedLimits != null
        ) {
            return
        }

        val limits =
            withContext(
                Dispatchers.IO
            ) {
                getClient(
                    printer
                ).getHeaterLimits()
            }

        hotendLimits =
            limits.first

        bedLimits =
            limits.second

        limitsPrinterId =
            printer.id

        setTemperatureInputsEnabled(
            true
        )
    }

    private fun setHomeButtonEnabled(
        button: ImageButton,
        enabled: Boolean
    ) {

        button.isEnabled =
            enabled

        button.background =
            resources.getDrawable(
                if (enabled) {
                    R.drawable.bg_management_pad_button
                } else {
                    R.drawable.bg_management_pad_button_disabled
                }
            )
    }

    private fun setTemperatureInputsEnabled(
        enabled: Boolean
    ) {

        hotendTargetInput.isEnabled =
            enabled

        bedTargetInput.isEnabled =
            enabled
    }

    private fun loadPrintMetadata(
        printer: Printer,
        filename: String
    ) {

        val state =
            getPrinterState(printer)

        state.metadataLoadJob?.cancel()

        val printerId =
            printer.id

        state.metadataLoadJob =
            scope.launch {

                try {

                    val metadata =
                        withContext(
                            Dispatchers.IO
                        ) {
                            getClient(
                                printer
                            ).getFileMetadata(
                                filename
                            )
                        }

                    if (
                        !coroutineContext.isActive ||
                        printers.isEmpty() ||
                        selectedPrinter >= printers.size ||
                        printers[selectedPrinter].id != printerId
                    ) {
                        return@launch
                    }

                    val currentState =
                        getPrinterState(printer)

                    if (
                        currentState.currentPrintFilename !=
                        filename
                    ) {
                        return@launch
                    }

                    android.util.Log.d(
                        "AMBERKLIP_METADATA",
                        "filename=$filename metadata=$metadata"
                    )

                    currentState.printMetadata =
                        metadata

                    currentState.status?.let {
                        currentState.printMetrics =
                            calculatePrintMetrics(
                                it,
                                metadata
                            )
                    }

                    if (
                        selectedTab ==
                        TAB_METRICS
                    ) {
                        renderCurrentLanguageState()
                    }

                } catch (e: Exception) {

                    if (
                        !coroutineContext.isActive
                    ) {
                        return@launch
                    }

                    android.util.Log.d(
                        "AMBERKLIP_METADATA",
                        "metadata loading failed for " +
                                "$filename: " +
                                "${e.message}"
                    )
                }
            }
    }

    private fun loadRecentPrintHistory(
        printer: Printer,
        forceReload: Boolean = false
    ) {

        val state =
            getPrinterState(printer)

        if (
            state.recentPrintHistoryLoaded &&
            !forceReload
        ) {
            return
        }

        state.recentHistoryLoadJob?.cancel()

        val printerId =
            printer.id

        state.recentHistoryLoadJob =
            scope.launch {

                try {

                    val history =
                        withContext(
                            Dispatchers.IO
                        ) {
                            getClient(
                                printer
                            ).getRecentPrintHistory()
                        }

                    if (
                        !coroutineContext.isActive ||
                        printers.isEmpty() ||
                        selectedPrinter >= printers.size ||
                        printers[selectedPrinter].id != printerId
                    ) {
                        return@launch
                    }

                    val currentState =
                        getPrinterState(printer)

                    currentState.recentPrintHistory =
                        history

                    currentState.recentPrintHistoryLoaded =
                        true

                    if (
                        selectedTab ==
                        TAB_METRICS &&
                        currentState.status != null &&
                        !isPrintActive(
                            currentState.status!!
                        )
                    ) {
                        renderPrintHistory(
                            history
                        )
                    }

                } catch (e: Exception) {

                    if (!coroutineContext.isActive) {
                        return@launch
                    }

                    android.util.Log.d(
                        "AMBERKLIP_HISTORY",
                        "recent history loading failed: " +
                                "${e.message}"
                    )
                }
            }
    }

    private fun isPrintActive(
        status: PrinterStatus
    ): Boolean {

        return status.printState.equals(
            "printing",
            ignoreCase = true
        ) ||
        status.printState.equals(
            "paused",
            ignoreCase = true
        )
    }

    private fun processStatus(
        status: PrinterStatus
    ) {

        if (
            printers.isEmpty() ||
            selectedPrinter < 0 ||
            selectedPrinter >= printers.size
        ) {
            return
        }

        val printer =
            printers[selectedPrinter]

        val state =
            getPrinterState(printer)

        val previousPrintState =
            state.status?.printState

        state.connectionState =
            ConnectionUiState.CONNECTED

        state.connected =
            true

        state.status =
            status

        val filenameChanged =
            state.currentPrintFilename !=
                status.filename

        if (filenameChanged) {

            state.currentPrintFilename =
                status.filename

            state.printMetadata =
                null

            state.printMetrics =
                calculatePrintMetrics(
                    status,
                    null
                )

            if (
                !status.filename.isNullOrBlank()
            ) {
                loadPrintMetadata(
                    printer,
                    status.filename
                )
            }

        } else {

            state.printMetrics =
                calculatePrintMetrics(
                    status,
                    state.printMetadata
                )
        }

        val reportedHotendTarget =
            status.hotendTarget?.toInt()

        val reportedBedTarget =
            status.bedTarget?.toInt()

        if (
            lastSubmittedHotendTarget != null
        ) {

            if (
                reportedHotendTarget ==
                lastSubmittedHotendTarget
            ) {
                lastSubmittedHotendTarget =
                    null
            }

        } else {

            state.lastHotendTarget =
                reportedHotendTarget
        }

        if (
            lastSubmittedBedTarget != null
        ) {

            if (
                reportedBedTarget ==
                lastSubmittedBedTarget
            ) {
                lastSubmittedBedTarget =
                    null
            }

        } else {

            state.lastBedTarget =
                reportedBedTarget
        }

        printerStorage.saveTargets(
            printerId = printer.id,
            hotendTarget =
                state.lastHotendTarget,
            bedTarget =
                state.lastBedTarget
        )

        connectionUiState =
            ConnectionUiState.CONNECTED

        printerConnected =
            true

        lastStatus =
            status

        android.util.Log.d(
            "AMBERKLIP_TEMP",
            "STATUS hotend=" +
                    "${status.hotendTemperature}°C / " +
                    "${status.hotendTarget}°C " +
                    "bed=" +
                    "${status.bedTemperature}°C / " +
                    "${status.bedTarget}°C"
        )

        val displayProgress =
            when {
                status.printState.equals(
                    "complete",
                    ignoreCase = true
                ) ->
                    100

                status.filePosition != null &&
                status.fileSize != null &&
                status.fileSize > 0L ->
                    (
                        status.filePosition
                            .toDouble() /
                            status.fileSize.toDouble() *
                            100.0
                    )
                        .toInt()
                        .coerceIn(0, 99)

                else ->
                    (
                        status.progress * 100.0
                    )
                        .toInt()
                        .coerceIn(0, 100)
            }

        progressValue.text =
            "$displayProgress%"

        progressBar.progress =
            displayProgress

        hotendCurrentTemperature.text =
            status.hotendTemperature?.let {
                String.format(
                    Locale.getDefault(),
                    "%.1f°",
                    it
                )
            } ?: "--°"

        bedCurrentTemperature.text =
            status.bedTemperature?.let {
                String.format(
                    Locale.getDefault(),
                    "%.1f°",
                    it
                )
            } ?: "--°"

        temperatureChart.addPoint(
            status.hotendTemperature,
            status.bedTemperature
        )

        state.temperatureChartState =
            temperatureChart.saveState()

        updateTemperatureControls(
            status
        )

        updateControlControls(
            status
        )

        if (
            selectedTab ==
            TAB_CONTROL
        ) {
            renderControlState()
        }

        if (
            selectedTab ==
            TAB_METRICS
        ) {

            renderMetrics(
                status
            )

            val printIsActive =
                isPrintActive(
                    status
                )

            if (printIsActive) {

                state.printMetrics?.let {
                    renderLivePrintMetrics(
                        it
                    )
                }

            } else {

                if (
                    previousPrintState != null &&
                    (
                        previousPrintState.equals(
                            "printing",
                            ignoreCase = true
                        ) ||
                        previousPrintState.equals(
                            "paused",
                            ignoreCase = true
                        )
                    )
                ) {

                    state.recentPrintHistoryLoaded =
                        false
                }

                loadRecentPrintHistory(
                    printer,
                    forceReload =
                        !state.recentPrintHistoryLoaded
                )

                renderPrintHistory(
                    state.recentPrintHistory
                )
            }
        }
    }

    private fun getLocalizedKlipperState(
        state: String
    ): String {

        return when (
            state.lowercase(Locale.US)
        ) {
            "ready" ->
                getString(
                    R.string.klipper_state_ready
                )

            "shutdown" ->
                getString(
                    R.string.klipper_state_shutdown
                )

            "error" ->
                getString(
                    R.string.klipper_state_error
                )

            "startup" ->
                getString(
                    R.string.klipper_state_startup
                )

            else ->
                getString(
                    R.string.klipper_state_unknown
                )
        }
    }

    private fun getLocalizedPrintState(
        state: String
    ): String {

        return when (
            state.lowercase(Locale.US)
        ) {
            "standby" ->
                getString(
                    R.string.print_state_standby
                )

            "printing" ->
                getString(
                    R.string.print_state_printing
                )

            "paused" ->
                getString(
                    R.string.print_state_paused
                )

            "complete" ->
                getString(
                    R.string.print_state_complete
                )

            "cancelled" ->
                getString(
                    R.string.print_state_cancelled
                )

            "error" ->
                getString(
                    R.string.print_state_error
                )

            else ->
                getString(
                    R.string.print_state_unknown
                )
        }
    }

    private fun addPrintMetricRow(
        label: String,
        value: String
    ) {

        val row =
            LinearLayout(this)

        row.orientation =
            LinearLayout.HORIZONTAL

        row.gravity =
            Gravity.CENTER_VERTICAL

        row.setPadding(
            16,
            8,
            16,
            8
        )

        val labelView =
            TextView(this)

        labelView.text =
            label

        labelView.textSize =
            16f

        labelView.setTextColor(
            resources.getColor(
                R.color.text_secondary
            )
        )

        labelView.layoutParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        val valueView =
            TextView(this)

        valueView.text =
            value

        valueView.textSize =
            16f

        valueView.setTextColor(
            resources.getColor(
                R.color.text_primary
            )
        )

        valueView.gravity =
            Gravity.RIGHT

        row.addView(
            labelView
        )

        row.addView(
            valueView
        )

        liveMetricsList.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun renderMetrics(
        status: PrinterStatus
    ) {

        content.removeAllViews()

        addText(
            getString(
                R.string.klipper_status,
                getLocalizedKlipperState(
                    status.klippyState
                )
            ),
            18
        )

        addText(
            getString(
                R.string.print_status,
                getLocalizedPrintState(
                    status.printState
                )
            ),
            16
        )

        addText(
            getString(
                R.string.file_status,
                status.filename ?: "--"
            ),
            15
        )
    }

    private fun updateTemperatureControls(
        status: PrinterStatus
    ) {

        status.hotendTemperature?.let {

            hotendCurrentTemperature.text =
                String.format(
                    Locale.getDefault(),
                    "%.1f°",
                    it
                )
        }

        status.bedTemperature?.let {

            bedCurrentTemperature.text =
                String.format(
                    Locale.getDefault(),
                    "%.1f°",
                    it
                )
        }

        status.hotendTarget?.let {

            val target =
                it.toInt()

            lastHotendTarget =
                target

            if (
                !hotendTargetInput.hasFocus()
            ) {
                hotendTargetInput.setText(
                    target.toString()
                )
            }
        }

        status.bedTarget?.let {

            val target =
                it.toInt()

            lastBedTarget =
                target

            if (
                !bedTargetInput.hasFocus()
            ) {
                bedTargetInput.setText(
                    target.toString()
                )
            }
        }
    }

    private fun renderError() {

        content.removeAllViews()

        addText(
            getString(R.string.connection_error),
            18
        )
    }

    private fun addText(
        text: String,
        size: Int
    ) {

        val view = TextView(this)

        view.text = text
        view.textSize = size.toFloat()

        view.setTextColor(
            resources.getColor(R.color.text_primary)
        )

        view.setPadding(
            16,
            10,
            16,
            10
        )

        view.gravity = Gravity.CENTER_VERTICAL

        content.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
                        ?: getString(
                            R.string.command_failed
                        ),
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

                type = "*/*"

                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "text/plain",
                        "application/octet-stream"
                    )
                )

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
            }

        startActivityForResult(
            intent,
            REQUEST_FILE
        )
    }

    private fun renderSelectedTabState() {

        when (selectedTab) {

            TAB_METRICS -> {
                renderCurrentLanguageState()
            }

            TAB_CONTROL -> {
                renderControlState()
            }

            TAB_TASKS -> {
                renderTasks()
            }

            TAB_SETTINGS -> {
                updateSettingsSelection()
            }
        }
    }

    private fun renderCurrentLanguageState() {

        if (printers.isEmpty()) {

            connectionUiState =
                ConnectionUiState.EMPTY

            content.removeAllViews()

            addText(
                getString(
                    R.string.no_printers_configured
                ),
                18
            )

            setTemperatureInputsEnabled(
                false
            )

            return
        }

        when (
            connectionUiState
        ) {

            ConnectionUiState.CONNECTING ->
                renderConnecting()

            ConnectionUiState.CONNECTED -> {

                lastStatus?.let {
                    renderMetrics(it)
                } ?: renderConnecting()
            }

            ConnectionUiState.ERROR ->
                renderError()

            ConnectionUiState.EMPTY -> {

                content.removeAllViews()

                addText(
                    getString(
                        R.string.no_printers_configured
                    ),
                    18
                )

                setTemperatureInputsEnabled(
                    false
                )
            }
        }
    }

    private fun updateLocalizedUi() {

        val context =
            this

        findViewById<ImageButton>(
            R.id.menuButton
        ).contentDescription =
            context.getString(
                R.string.menu
            )

        findViewById<TextView>(
            R.id.titleText
        ).text =
            context.getString(
                R.string.app_name
            )

        findViewById<TextView>(
            R.id.hotendTemperatureLabel
        ).text =
            context.getString(
                R.string.hotend_temperature_label
            )

        findViewById<TextView>(
            R.id.bedTemperatureLabel
        ).text =
            context.getString(
                R.string.bed_temperature_label
            )

        findViewById<TextView>(
            R.id.progressLabel
        ).text =
            context.getString(
                R.string.progress_label
            )

        findViewById<TextView>(
            R.id.drawerPrintersTitle
        ).text =
            context.getString(
                R.string.printers
            )

        findViewById<Button>(
            R.id.addPrinterButton
        ).text =
            context.getString(
                R.string.add_printer
            )

        findViewById<TextView>(
            R.id.metricsTabText
        ).text =
            context.getString(
                R.string.metrics
            )

        findViewById<TextView>(
            R.id.tasksTabText
        ).text =
            context.getString(
                R.string.tasks
            )

        findViewById<TextView>(
            R.id.settingsTabText
        ).text =
            context.getString(
                R.string.settings
            )

        findViewById<TextView>(
            R.id.controlTitle
        ).text =
            context.getString(
                R.string.control
            )

        findViewById<TextView>(
            R.id.controlTabText
        ).text =
            context.getString(
                R.string.control
            )

        findViewById<TextView>(
            R.id.disableMotorsButton
        ).text =
            context.getString(
                R.string.disable_motors
            )

        xStepValue.text =
            context.getString(
                R.string.step_value,
                xStepSeekBar.progress + 1
            )

        yStepValue.text =
            context.getString(
                R.string.step_value,
                yStepSeekBar.progress + 1
            )

        zStepValue.text =
            context.getString(
                R.string.step_value,
                zStepSeekBar.progress + 1
            )

        findViewById<ImageView>(
            R.id.metricsIcon
        ).contentDescription =
            context.getString(
                R.string.metrics
            )

        findViewById<ImageView>(
            R.id.tasksIcon
        ).contentDescription =
            context.getString(
                R.string.tasks
            )

        findViewById<ImageView>(
            R.id.settingsIcon
        ).contentDescription =
            context.getString(
                R.string.settings
            )

        findViewById<ImageButton>(
            R.id.xMinusButton
        ).contentDescription =
            context.getString(
                R.string.move_x_minus
            )

        findViewById<ImageButton>(
            R.id.xPlusButton
        ).contentDescription =
            context.getString(
                R.string.move_x_plus
            )

        findViewById<ImageButton>(
            R.id.yMinusButton
        ).contentDescription =
            context.getString(
                R.string.move_y_minus
            )

        findViewById<ImageButton>(
            R.id.yPlusButton
        ).contentDescription =
            context.getString(
                R.string.move_y_plus
            )

        findViewById<ImageButton>(
            R.id.zMinusButton
        ).contentDescription =
            context.getString(
                R.string.move_z_minus
            )

        findViewById<ImageButton>(
            R.id.zPlusButton
        ).contentDescription =
            context.getString(
                R.string.move_z_plus
            )

        findViewById<ImageButton>(
            R.id.xyHomeButton
        ).contentDescription =
            context.getString(
                R.string.home_xy
            )

        findViewById<ImageButton>(
            R.id.zHomeButton
        ).contentDescription =
            context.getString(
                R.string.home_z
            )

        findViewById<ImageView>(
            R.id.controlIcon
        ).contentDescription =
            context.getString(
                R.string.control
            )

        printHistoryTitle.text =
            context.getString(
                R.string.print_history
            )

        liveMetricsTitle.text =
            context.getString(
                R.string.current_print_metrics
            )

        settingsTitleText.text =
            context.getString(
                R.string.settings
            )

        settingsLanguageLabel.text =
            context.getString(
                R.string.application_language
            )

        languageSystem.text =
            context.getString(
                R.string.language_system
            )

        languageRussian.text =
            context.getString(
                R.string.language_russian
            )

        languageEnglish.text =
            context.getString(
                R.string.language_english
            )

        buildPrinterList()

        updateBottomNavigation()

        printTaskAdapter.refreshHeaderLanguage()

        renderSelectedTabState()
    }

    private fun formatHistoryDate(
        timestamp: Long?
    ): String {

        if (timestamp == null) {
            return "--"
        }

        return SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun renderLivePrintMetrics(
        metrics: PrintMetrics
    ) {

        printInfoCard.visibility =
            View.VISIBLE

        printHistoryContainer.visibility =
            View.GONE

        liveMetricsContainer.visibility =
            View.VISIBLE

        liveMetricsList.removeAllViews()

        addPrintMetricRow(
            getString(
                R.string.metric_speed
            ),
            formatSpeed(
                metrics.speed
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_flow
            ),
            formatFlow(
                metrics.flow
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_filament
            ),
            formatFilament(
                metrics.filamentMeters
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_layer
            ),
            formatLayer(
                metrics.currentLayer,
                metrics.totalLayer
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_remaining
            ),
            formatDuration(
                metrics.remainingTime
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_slicer_remaining
            ),
            formatDuration(
                metrics.slicerRemainingTime
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_elapsed
            ),
            formatDuration(
                metrics.elapsedTime
            )
        )

        addPrintMetricRow(
            getString(
                R.string.metric_finish
            ),
            formatFinishTime(
                metrics.estimatedFinishTime
            )
        )
    }

    private fun renderPrintHistory(
        history: List<PrintHistoryEntry>
    ) {

        if (history.isEmpty()) {
            printInfoCard.visibility =
                View.GONE

            return
        }

        printInfoCard.visibility =
            View.VISIBLE

        liveMetricsContainer.visibility =
            View.GONE

        printHistoryContainer.visibility =
            View.VISIBLE

        printHistoryList.removeAllViews()

        history
            .filter { entry ->
                !entry.status.equals(
                    "in progress",
                    ignoreCase = true
                )
            }
            .take(3)
            .forEach { entry ->

                val row =
                    LinearLayout(this)

                row.orientation =
                    LinearLayout.HORIZONTAL

                row.gravity =
                    Gravity.CENTER_VERTICAL

                row.setPadding(
                    16,
                    8,
                    16,
                    8
                )

                val icon = ImageView(this)

                val normalizedStatus =
                    entry.status
                        .lowercase(
                            Locale.US
                        )

                val iconRes =
                    when (normalizedStatus) {
                        "completed" -> R.drawable.ic_history_completed
                        "cancelled" -> R.drawable.ic_history_cancelled
                        "error", "klippy_shutdown" -> R.drawable.ic_history_error
                        else -> R.drawable.ic_history_unknown
                    }

                icon.setImageResource(iconRes)

                icon.layoutParams =
                    LinearLayout.LayoutParams(
                        dpToPx(32),
                        dpToPx(32)
                    ).apply {
                        rightMargin =
                            dpToPx(12)
                    }

                row.addView(
                    icon
                )

                val info =
                    LinearLayout(this)

                info.orientation =
                    LinearLayout.VERTICAL

                info.layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                val filename =
                    TextView(this)

                filename.text =
                    entry.filename

                filename.textSize =
                    16f

                filename.setTextColor(
                    resources.getColor(
                        R.color.text_primary
                    )
                )

                filename.maxLines =
                    1

                filename.ellipsize =
                    android.text.TextUtils.TruncateAt.MIDDLE

                info.addView(
                    filename
                )

                val statusTextRes =
                    when (normalizedStatus) {

                        "completed" ->
                            R.string.print_state_complete

                        "cancelled" ->
                            R.string.print_state_cancelled

                        "error", "klippy_shutdown" ->
                            R.string.print_state_error

                        else ->
                            R.string.print_state_unknown
                    }

                val details =
                    TextView(this)

                details.text =
                    getString(
                        statusTextRes
                    ) +
                    "  " +
                    formatDuration(
                        entry.totalDuration.toLong()
                    ) +
                    "  " +
                    formatHistoryDate(
                        entry.endTime
                            ?: entry.startTime
                    )

                details.textSize =
                    13f

                details.setTextColor(
                    resources.getColor(
                        R.color.text_secondary
                    )
                )

                info.addView(
                    details
                )

                row.addView(
                    info
                )

                printHistoryList.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
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

        val scanResult =
            IntentIntegrator.parseActivityResult(
                requestCode,
                resultCode,
                data
            )

        if (
            scanResult != null
        ) {

            val contents =
                scanResult.contents

            if (
                contents != null &&
                activeApiKeyEdit != null
            ) {

                activeApiKeyEdit?.setText(
                    contents
                )

                Toast.makeText(
                    this,
                    getString(
                        R.string.qr_api_key_scanned
                    ),
                    Toast.LENGTH_SHORT
                ).show()

            } else if (
                contents == null
            ) {

                Toast.makeText(
                    this,
                    getString(
                        R.string.qr_scan_cancelled
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }

            return
        }

        if (
            requestCode !=
            REQUEST_FILE ||
            resultCode !=
            RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data
                ?: return

        uploadUri(uri)
    }

    private fun startQrScanner(
        editText: EditText
    ) {

        activeApiKeyEdit =
            editText

        val integrator =
            IntentIntegrator(this)

        integrator.setDesiredBarcodeFormats(
            IntentIntegrator.QR_CODE
        )

        integrator.setPrompt(
            getString(
                R.string.scan_qr_prompt
            )
        )

        integrator.setBeepEnabled(
            true
        )

        integrator.setOrientationLocked(
            true
        )

        integrator.initiateScan()
    }

    private fun uploadUri(
        uri: Uri
    ) {

        if (printers.isEmpty()) {
            return
        }

        val name =
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: ""

        val extension =
            name
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase()

        val allowedExtensions =
            setOf(
                "gcode",
                "gco",
                "g"
            )

        if (
            extension !in allowedExtensions
        ) {

            Toast.makeText(
                this,
                getString(
                    R.string.invalid_gcode_file
                ),
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val printer =
            printers[selectedPrinter]

        val client =
            getClient(printer)

        scope.launch {

            var file: File? = null

            try {

                file =
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
                    getString(
                        R.string.uploaded_file,
                        file.name
                    ),
                    Toast.LENGTH_LONG
                ).show()

                file.delete()

                if (
                    selectedTab ==
                    TAB_TASKS
                ) {

                    loadPrintHistory()

                } else {

                    refresh()
                }

            } catch (e: Exception) {

                file?.delete()

                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: getString(
                            R.string.upload_failed
                        ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun copyUriToCache(
        uri: Uri
    ): File =
        withContext(Dispatchers.IO) {

            val name =
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->

                    if (cursor.moveToFirst()) {
                        val index =
                            cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                            )

                        if (index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "upload.gcode"

            val file =
                File(
                    cacheDir,
                    name
                )

            contentResolver.openInputStream(
                uri
            ).use { input ->

                requireNotNull(input) {
                    getString(
                        R.string.cannot_open_selected_file
                    )
                }

                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file
        }

    private fun installSafeLongPressCopy(
        editText: EditText,
        fieldName: String,
        copiedMessageRes: Int,
        pastedMessageRes: Int
    ) {
        editText.setOnLongClickListener {

            try {
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
                        getString(copiedMessageRes),
                        Toast.LENGTH_SHORT
                    ).show()

                } else if (
                    clipboard.hasPrimaryClip()
                ) {

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
                            getString(pastedMessageRes),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                // Буфер обмена не должен приводить
                // к падению приложения.
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
            getString(R.string.printer_name),
            R.string.printer_name_copied,
            R.string.printer_name_pasted
        )

        installSafeLongPressCopy(
            hostEdit,
            getString(R.string.moonraker_address),
            R.string.moonraker_address_copied,
            R.string.moonraker_address_pasted
        )

        installSafeLongPressCopy(
            apiKeyEdit,
            getString(R.string.api_key),
            R.string.api_key_copied,
            R.string.api_key_pasted
        )

        configurePortField(
            portEdit
        )
    }

    private fun configurePortField(
        portEdit: EditText
    ) {
        portEdit.filters = arrayOf(
            android.text.InputFilter.LengthFilter(5)
        )

        portEdit.setOnLongClickListener {

            try {
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
                        getString(
                            R.string.port_copied
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                } else if (
                    clipboard.hasPrimaryClip()
                ) {

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
                                getString(
                                    R.string.port_pasted
                                ),
                                Toast.LENGTH_SHORT
                            ).show()

                        } else {
                            Toast.makeText(
                                this,
                                getString(
                                    R.string.port_invalid
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

            } catch (e: Exception) {
                // Буфер обмена не должен приводить
                // к падению приложения.
            }

            true
        }
    }

    private fun showDeletePrinterDialog(
        index: Int
    ) {
        if (
            index < 0 ||
            index >= printers.size
        ) {
            return
        }

        val printer =
            printers[index]

        val printerState =
            getPrinterState(printer)

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
            getString(
                R.string.delete_printer
            )

        deleteMessage.text =
            getString(
                R.string.delete_printer_confirmation
            ) +
            "\n\n" +
            getString(
                R.string.delete_printer_warning
            )

        deleteMessage.visibility =
            View.VISIBLE

        printerForm.visibility =
            View.GONE

        actionButton.text =
            getString(
                R.string.delete_printer
            )

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        actionButton.setOnClickListener {

            printerState.metadataLoadJob?.cancel()
            printerState.metadataLoadJob = null

            printerState.recentHistoryLoadJob?.cancel()
            printerState.recentHistoryLoadJob = null

            clients.remove(
                printer.id
            )

            printerStates.remove(
                printer.id
            )

            printers.removeAt(index)

            printerStorage.save(
                printers
            )

            if (printers.isEmpty()) {

                selectedPrinter = 0

                refreshJob?.cancel()
                refreshJob = null

                resetTemperatureState()

                buildPrinterList()

                content.removeAllViews()

                addText(
                    getString(
                        R.string.no_printers_configured
                    ),
                    18
                )

                setTemperatureInputsEnabled(
                    false
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

                resetTemperatureState()

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
            getString(
                R.string.edit_printer
            )

        actionButton.text =
            getString(
                R.string.save
            )

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
            startQrScanner(
                apiKeyEdit
            )
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
                    getString(
                        R.string.port_invalid
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val normalizedApiKey =
                apiKey.ifBlank {
                    null
                }

            val connectionChanged =
                printer.host != host ||
                printer.port != port ||
                printer.apiKey != normalizedApiKey

            val nameChanged =
                printer.name != name

            if (
                !connectionChanged &&
                !nameChanged
            ) {
                dialog.dismiss()
                return@setOnClickListener
            }

            val updatedPrinter =
                Printer(
                    id = printer.id,
                    name = name,
                    host = host,
                    port = port,
                    apiKey = normalizedApiKey
                )

            printers[index] =
                updatedPrinter

            printerStorage.save(
                printers
            )

            if (connectionChanged) {
                val state = getPrinterState(printer)

                state.metadataLoadJob?.cancel()
                state.metadataLoadJob = null

                state.recentHistoryLoadJob?.cancel()
                state.recentHistoryLoadJob = null
                state.recentPrintHistory = emptyList()
                state.recentPrintHistoryLoaded = false
                state.currentPrintFilename = null
                state.printMetadata = null
                state.printMetrics = null

                state.connectionBlocked = false
                state.connectionState = ConnectionUiState.CONNECTING
                state.connected = false
                state.status = null
                state.hotendLimits = null
                state.bedLimits = null
                state.lastHotendTarget = null
                state.lastBedTarget = null

                clients.remove(printer.id)

                if (selectedPrinter == index) {
                    refreshJob?.cancel()
                    refreshJob = null
                    resetTemperatureState()
                }
            }

            buildPrinterList()

            dialog.dismiss()

            if (connectionChanged) {
                refresh()
            }
        }

        dialog.setOnDismissListener {
            activeApiKeyEdit = null
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

        val displayMetrics =
            resources.displayMetrics

        val screenWidth =
            displayMetrics.widthPixels

        val screenHeight =
            displayMetrics.heightPixels

        val baseDimension =
            minOf(
                screenWidth,
                screenHeight
            )

        val width =
            (baseDimension * 0.90f).toInt()

        window.setLayout(
            width,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
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
            getString(
                R.string.add_printer
            )

        actionButton.text =
            getString(
                R.string.add_printer
            )

        configureEditFields(
            nameEdit,
            hostEdit,
            portEdit,
            apiKeyEdit
        )

        scanQrButton.setOnClickListener {
            startQrScanner(
                apiKeyEdit
            )
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
                    getString(
                        R.string.port_invalid
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            printers.add(
                Printer(
                    id =
                        UUID.randomUUID().toString(),
                    name =
                        name,
                    host =
                        host,
                    port =
                        port,
                    apiKey =
                        apiKey.ifBlank {
                            null
                        }
                )
            )

            printerStorage.save(
                printers
            )

            selectedPrinter =
                printers.lastIndex

            resetTemperatureState()

            buildPrinterList()

            dialog.dismiss()

            drawerLayout.closeDrawer(
                Gravity.START
            )

            refresh()
        }

        dialog.setOnDismissListener {
            activeApiKeyEdit = null
        }

        dialog.show()

        configureDialogWindow(
            dialog
        )
    }

    override fun onDestroy() {

        hotendSubmitRunnable?.let {
            keyboardHandler.removeCallbacks(
                it
            )
        }

        bedSubmitRunnable?.let {
            keyboardHandler.removeCallbacks(
                it
            )
        }

        globalLayoutListener?.let {
            findViewById<View>(
                android.R.id.content
            )
                .viewTreeObserver
                .removeOnGlobalLayoutListener(
                    it
                )
        }

        refreshJob?.cancel()

        scope.cancel()

        super.onDestroy()
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp * resources.displayMetrics.density
        ).toInt()
    }

    companion object {

        private const val REQUEST_FILE = 1001

        const val TAB_METRICS = 0
        const val TAB_CONTROL = 1
        const val TAB_TASKS = 2
        const val TAB_SETTINGS = 3
    }
}
