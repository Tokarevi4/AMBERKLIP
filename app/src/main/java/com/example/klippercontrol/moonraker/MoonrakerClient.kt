package com.example.klippercontrol.moonraker

import android.util.Log
import android.net.Uri
import com.example.klippercontrol.model.HeaterLimits
import com.example.klippercontrol.model.Printer
import com.example.klippercontrol.model.PrinterStatus
import com.example.klippercontrol.model.PrintTask
import com.example.klippercontrol.model.PrintMetadata
import com.example.klippercontrol.model.PrintHistoryEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okio.Buffer

class MoonrakerClient(
    private val printer: Printer
) {

    private class MoonrakerHttpException(
        val code: Int,
        val responseBody: String
    ) : Exception(
        "HTTP $code: $responseBody"
    )

    private var preferredHttps: Boolean? = null

    private val httpsClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(
        path: String,
        useHttps: Boolean
    ): Request.Builder {

        val baseUrl = if (useHttps) {
            printer.httpsUrl
        } else {
            printer.httpUrl
        }

        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)

        printer.apiKey
            ?.takeIf { it.isNotBlank() }
            ?.let { apiKey ->
                builder.header(
                    "X-Api-Key",
                    apiKey
                )
            }

        return builder
    }

    private fun executeWithFallback(
        operation: String,
        path: String,
        requestFactory: (Request.Builder) -> Request.Builder
    ): String {

        Log.d(
            "AMBERKLIP_MOONRAKER",
            "START operation=$operation path=$path"
        )

        val preferred =
            preferredHttps

        if (preferred != null) {

            try {

                val client =
                    if (preferred) {
                        httpsClient
                    } else {
                        httpClient
                    }

                val request =
                    requestFactory(
                        buildRequest(
                            path,
                            preferred
                        )
                    ).build()

                return executeLogged(
                    operation = operation,
                    client = client,
                    request = request
                )

            } catch (e: IOException) {

                Log.w(
                    "AMBERKLIP_MOONRAKER",
                    "TRANSPORT_ERROR operation=$operation " +
                            "protocol=${if (preferred) "HTTPS" else "HTTP"} " +
                            "type=${e::class.simpleName} " +
                            "message=${e.message}"
                )

                preferredHttps =
                    null
            }
        }

        try {

            val request =
                requestFactory(
                    buildRequest(
                        path,
                        true
                    )
                ).build()

            val response =
                executeLogged(
                    operation = operation,
                    client = httpsClient,
                    request = request
                )

            preferredHttps =
                true

            return response

        } catch (httpsException: IOException) {

            Log.w(
                "AMBERKLIP_MOONRAKER",
                "HTTPS_TRANSPORT_ERROR " +
                        "operation=$operation " +
                        "type=${httpsException::class.simpleName} " +
                        "message=${httpsException.message}"
            )

            try {

                val request =
                    requestFactory(
                        buildRequest(
                            path,
                            false
                        )
                    ).build()

                val response =
                    executeLogged(
                        operation = operation,
                        client = httpClient,
                        request = request
                    )

                preferredHttps =
                    false

                return response

            } catch (httpException: IOException) {

                Log.e(
                    "AMBERKLIP_MOONRAKER",
                    "HTTP_TRANSPORT_ERROR " +
                            "operation=$operation " +
                            "type=${httpException::class.simpleName} " +
                            "message=${httpException.message}"
                )

                throw IOException(
                    "HTTPS failed: " +
                            "${httpsException.message ?: "unknown error"}\n" +
                            "HTTP failed: " +
                            "${httpException.message ?: "unknown error"}",
                    httpException
                )
            }
        }
    }

    private fun executeLogged(
        operation: String,
        client: OkHttpClient,
        request: Request
    ): String {

        val requestId =
            java.util.UUID.randomUUID()
                .toString()
                .take(8)

        Log.d(
            "AMBERKLIP_MOONRAKER",
            "REQUEST id=$requestId " +
                    "operation=$operation " +
                    "method=${request.method()} " +
                    "url=${request.url()}"
        )

        request.body()?.let { body ->

            val contentType =
                body.contentType()

            val isMultipart =
                contentType?.type() == "multipart"

            if (isMultipart) {

                Log.d(
                    "AMBERKLIP_MOONRAKER",
                    "REQUEST_BODY id=$requestId " +
                            "operation=$operation " +
                            "type=multipart " +
                            "body=<omitted>"
                )

            } else {

                try {

                    val buffer =
                        Buffer()

                    body.writeTo(buffer)

                    val bodyText =
                        buffer.readUtf8()

                    val displayedBody =
                        if (bodyText.length > 4000) {
                            bodyText.take(4000) +
                                    "... [truncated]"
                        } else {
                            bodyText
                        }

                    Log.d(
                        "AMBERKLIP_MOONRAKER",
                        "REQUEST_BODY id=$requestId " +
                                "operation=$operation " +
                                "body=$displayedBody"
                    )

                } catch (e: Exception) {

                    Log.w(
                        "AMBERKLIP_MOONRAKER",
                        "REQUEST_BODY_ERROR id=$requestId " +
                                "operation=$operation " +
                                "type=${e::class.simpleName} " +
                                "message=${e.message}"
                    )
                }
            }
        }

        return try {

            client.newCall(
                request
            ).execute()
                .use { response ->

                    val body =
                        response.body()?.string()
                            ?: ""

                    val displayedBody =
                        if (body.length > 8000) {
                            body.take(8000) +
                                    "... [truncated]"
                        } else {
                            body
                        }

                    Log.d(
                        "AMBERKLIP_MOONRAKER",
                        "RESPONSE id=$requestId " +
                                "operation=$operation " +
                                "code=${response.code()} " +
                                "message=${response.message()}"
                    )

                    Log.d(
                        "AMBERKLIP_MOONRAKER",
                        "RESPONSE_BODY id=$requestId " +
                                "operation=$operation " +
                                "body=$displayedBody"
                    )

                    if (!response.isSuccessful) {

                        Log.e(
                            "AMBERKLIP_MOONRAKER",
                            "HTTP_ERROR id=$requestId " +
                                    "operation=$operation " +
                                    "code=${response.code()}"
                        )

                        throw MoonrakerHttpException(
                            code =
                                response.code(),
                            responseBody =
                                body
                        )
                    }

                    Log.d(
                        "AMBERKLIP_MOONRAKER",
                        "SUCCESS id=$requestId " +
                                "operation=$operation"
                    )

                    body
                }

        } catch (e: MoonrakerHttpException) {

            throw e

        } catch (e: IOException) {

            Log.e(
                "AMBERKLIP_MOONRAKER",
                "TRANSPORT_ERROR id=$requestId " +
                        "operation=$operation " +
                        "type=${e::class.simpleName} " +
                        "message=${e.message}"
            )

            throw e
        }
    }

    private fun getKlippyState(): String {

        val responseBody =
            executeWithFallback(
                operation = "GET_SERVER_INFO",
                path = "/server/info"
            ) { builder ->
                builder.get()
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val result =
            root["result"]
                ?.jsonObject
                ?: error(
                    "Invalid Moonraker server.info response: $responseBody"
                )

        val connected =
            result["klippy_connected"]
                ?.jsonPrimitive
                ?.content
                ?.toBoolean()
                ?: false

        val state =
            result["klippy_state"]
                ?.jsonPrimitive
                ?.content
                ?: "disconnected"

        val effectiveState =
            if (connected) {
                state
            } else {
                "disconnected"
            }

        Log.d(
            "AMBERKLIP_KLIPPY",
            "connected=$connected state=$effectiveState"
        )

        return effectiveState
    }

    fun getStatus(): PrinterStatus {

        val klippyState =
            getKlippyState()

        if (
            !klippyState.equals(
                "ready",
                ignoreCase = true
            )
        ) {

            Log.d(
                "AMBERKLIP_KLIPPY",
                "Klipper is not ready: $klippyState"
            )

            return PrinterStatus(
                klippyState =
                    klippyState
            )
        }

        val json = """
            {
                "objects": {
                    "webhooks": null,
                    "print_stats": null,
                    "virtual_sdcard": [
                        "progress",
                        "file_position",
                        "file_size",
                        "is_active",
                        "file_path"
                    ],
                    "gcode_move": [
                        "speed",
                        "gcode_position"
                    ],
                    "motion_report": [
                        "live_velocity",
                        "live_extruder_velocity",
                        "live_position"
                    ],
                    "toolhead": [
                        "position",
                        "homed_axes",
                        "axis_minimum",
                        "axis_maximum"
                    ],
                    "extruder": [
                        "temperature",
                        "target"
                    ],
                    "heater_bed": [
                        "temperature",
                        "target"
                    ]
                }
            }
        """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse(
                "application/json; charset=utf-8"
            ),
            json
        )

        val responseBody =
            executeWithFallback(
                operation = "GET_STATUS",
                path = "/printer/objects/query"
            ) { builder ->
                builder.post(body)
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val status =
            root["result"]
                ?.jsonObject
                ?.get("status")
                ?.jsonObject
                ?: error(
                    "Invalid Moonraker response: $responseBody"
                )

        val webhooks = status["webhooks"]?.jsonObject

        val print = status["print_stats"]?.jsonObject

        val extruder = status["extruder"]?.jsonObject

        val bed = status["heater_bed"]?.jsonObject

        val virtualSd = status["virtual_sdcard"]?.jsonObject

        val gcodeMove = status["gcode_move"]?.jsonObject

        val toolhead = status["toolhead"]?.jsonObject

        val toolheadPosition =
            toolhead
                ?.get("position")
                ?.jsonArray

        val axisMinimum =
            toolhead
                ?.get("axis_minimum")
                ?.jsonArray

        val axisMaximum =
            toolhead
                ?.get("axis_maximum")
                ?.jsonArray

        val homedAxes =
            toolhead
                ?.get("homed_axes")
                ?.jsonPrimitive
                ?.content
                ?: ""

        val xPosition =
            toolheadPosition
                ?.getOrNull(0)
                ?.jsonPrimitive
                ?.doubleOrNull

        val yPosition =
            toolheadPosition
                ?.getOrNull(1)
                ?.jsonPrimitive
                ?.doubleOrNull

        val zPosition =
            toolheadPosition
                ?.getOrNull(2)
                ?.jsonPrimitive
                ?.doubleOrNull

        val xMinimum =
            axisMinimum
                ?.getOrNull(0)
                ?.jsonPrimitive
                ?.doubleOrNull

        val yMinimum =
            axisMinimum
                ?.getOrNull(1)
                ?.jsonPrimitive
                ?.doubleOrNull

        val zMinimum =
            axisMinimum
                ?.getOrNull(2)
                ?.jsonPrimitive
                ?.doubleOrNull

        val xMaximum =
            axisMaximum
                ?.getOrNull(0)
                ?.jsonPrimitive
                ?.doubleOrNull

        val yMaximum =
            axisMaximum
                ?.getOrNull(1)
                ?.jsonPrimitive
                ?.doubleOrNull

        val zMaximum =
            axisMaximum
                ?.getOrNull(2)
                ?.jsonPrimitive
                ?.doubleOrNull

        val gcodePosition = gcodeMove
            ?.get("gcode_position")
            ?.jsonArray

        val currentZ = gcodePosition
            ?.getOrNull(2)
            ?.jsonPrimitive
            ?.doubleOrNull

        val filePosition =
            virtualSd
                ?.get("file_position")
                ?.jsonPrimitive
                ?.content
                ?.toLongOrNull()

        val fileSize =
            virtualSd
                ?.get("file_size")
                ?.jsonPrimitive
                ?.content
                ?.toLongOrNull()

        val motionReport = status["motion_report"]?.jsonObject

        android.util.Log.d(
            "AMBERKLIP_POSITION",
            "virtual_sd=$virtualSd"
        )

        android.util.Log.d(
            "AMBERKLIP_POSITION",
            "gcode_move=$gcodeMove"
        )

        android.util.Log.d(
            "AMBERKLIP_POSITION",
            "motion_report=$motionReport"
        )

        val printInfo = print?.get("info")?.jsonObject

        android.util.Log.d(
            "AMBERKLIP_LAYERS",
            "print_stats.info=$printInfo"
        )

        return PrinterStatus(

            klippyState = klippyState,

            printState =
                print
                    ?.get("state")
                    ?.jsonPrimitive
                    ?.content
                    ?: "standby",

            filename =
                print
                    ?.get("filename")
                    ?.jsonPrimitive
                    ?.contentOrNull,

            progress =
                virtualSd
                    ?.get("progress")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: print
                        ?.get("progress")
                        ?.jsonPrimitive
                        ?.doubleOrNull
                    ?: 0.0,

            filePosition =
                filePosition,

            fileSize =
                fileSize,

            hotendTemperature =
                extruder
                    ?.get("temperature")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            hotendTarget =
                extruder
                    ?.get("target")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            bedTemperature =
                bed
                    ?.get("temperature")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            bedTarget =
                bed
                    ?.get("target")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            speed =
                gcodeMove
                    ?.get("speed")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            liveVelocity =
                motionReport
                    ?.get("live_velocity")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            liveExtruderVelocity =
                motionReport
                    ?.get("live_extruder_velocity")
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            filamentUsed =
                print
                    ?.get("filament_used")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0,

            currentLayer =
                printInfo
                    ?.get("current_layer")
                    ?.jsonPrimitive
                    ?.content
                    ?.toIntOrNull(),

            currentZ =
                currentZ,

            totalLayer =
                printInfo
                    ?.get("total_layer")
                    ?.jsonPrimitive
                    ?.content
                    ?.toIntOrNull(),

            printDuration =
                print
                    ?.get("print_duration")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0,

            totalDuration =
                print
                    ?.get("total_duration")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0,

            message =
                webhooks
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull,

            xPosition =
                xPosition,

            yPosition =
                yPosition,

            zPosition =
                zPosition,

            xMinimum =
                xMinimum,

            xMaximum =
                xMaximum,

            yMinimum =
                yMinimum,

            yMaximum =
                yMaximum,

            zMinimum =
                zMinimum,

            zMaximum =
                zMaximum,

            homedAxes =
                homedAxes,

            motorsEnabled =
                homedAxes.isNotEmpty()
        )
    }

    fun getHeaterLimits(): Pair<HeaterLimits, HeaterLimits> {

        val json = """
            {
                "objects": {
                    "configfile": ["config"]
                }
            }
        """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse(
                "application/json; charset=utf-8"
            ),
            json
        )

       val  responseBody =
            executeWithFallback(
                operation = "GET_HEATER_LIMITS",
                path = "/printer/objects/query"
            ) { builder ->
                builder.post(body)
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val status =
            root["result"]
                ?.jsonObject
                ?.get("status")
                ?.jsonObject
                ?: error(
                    "Invalid Moonraker response: $responseBody"
                )

        val configfile =
            status["configfile"]
                ?.jsonObject
                ?: error(
                    "Moonraker did not return configfile"
                )

        val config =
            configfile["config"]
                ?.jsonObject
                ?: error(
                    "Moonraker did not return printer config"
                )

        val extruder =
            config["extruder"]
                ?.jsonObject
                ?: error(
                    "Extruder configuration not found"
                )

        val heaterBed =
            config["heater_bed"]
                ?.jsonObject
                ?: error(
                    "Heater bed configuration not found"
                )

        val extruderMin =
            extruder["min_temp"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?: error(
                    "Extruder min_temp not found"
                )

        val extruderMax =
            extruder["max_temp"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?: error(
                    "Extruder max_temp not found"
                )

        val bedMin =
            heaterBed["min_temp"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?: error(
                    "Heater bed min_temp not found"
                )

        val bedMax =
            heaterBed["max_temp"]
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull()
                ?: error(
                    "Heater bed max_temp not found"
                )

        val extruderLimits =
            HeaterLimits(
                min = extruderMin.toInt(),
                max = extruderMax.toInt()
            )

        val bedLimits =
            HeaterLimits(
                min = bedMin.toInt(),
                max = bedMax.toInt()
            )

        return Pair(
            extruderLimits,
            bedLimits
        )
    }

    fun setHotendTemperature(
        temperature: Int
    ) {
        executeGcode(
            operation = "SET_HOTEND_TEMPERATURE",
            script =
                "SET_HEATER_TEMPERATURE " +
                "HEATER=extruder " +
                "TARGET=$temperature"
            )
    }

    fun setBedTemperature(
        temperature: Int
    ) {
        executeGcode(
            operation = "SET_BED_TEMPERATURE",
            script =
                "SET_HEATER_TEMPERATURE " +
                "HEATER=heater_bed " +
                "TARGET=$temperature"
        )
    }

    private fun executeGcode(
        operation: String,
        script: String
    ) {

        val json =
            """
            {
                "script": "${
                    script.replace(
                        "\"",
                        "\\\""
                    )
                }"
            }
            """.trimIndent()

        val body =
            RequestBody.create(
                MediaType.parse(
                    "application/json; charset=utf-8"
                ),
                json
            )

        val responseBody =
            executeWithFallback(
                operation = operation,
                path = "/printer/gcode/script"
            ) { builder ->
                builder.post(body)
            }

        if (
            responseBody.isBlank()
        ) {
            error(
                "Empty Moonraker response"
            )
        }
    }

    fun startPrint(
        filename: String
    ) {

        val encodedFilename =
            URLEncoder.encode(
                filename,
                "UTF-8"
            )

        executeWithFallback(
            operation = "START_PRINT",
            path = "/printer/print/start?filename=$encodedFilename"
        ) { builder ->
            builder.post(
                RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
        }
    }

    fun enqueuePrint(
        filename: String
    ) {

        val json =
            """
            {
                "filenames": [
                    "${escapeJson(filename)}"
                ],
                "reset": false
            }
            """.trimIndent()

        val body =
            RequestBody.create(
                MediaType.parse(
                    "application/json; charset=utf-8"
                ),
                json
            )

        executeWithFallback(
            operation = "ENQUEUE_PRINT",
            path = "/server/job_queue/job"
        ) { builder ->
            builder.post(body)
        }
    }

    fun pausePrint() {
        post(
            operation = "PAUSE_PRINT",
            path = "/printer/print/pause"
        )
    }

    fun resumePrint() {
        post(
            operation = "RESUME_PRINT",
            path = "/printer/print/resume"
        )
    }

    fun cancelPrint() {
        post(
            operation = "CANCEL_PRINT",
            path = "/printer/print/cancel"
        )
    }

    fun emergencyStop() {
        post(
            operation = "EMERGENCY_STOP",
            path = "/printer/emergency_stop"
        )
    }

    fun getFileMetadata(
        filename: String
    ): PrintMetadata {

        val encodedFilename =
            URLEncoder.encode(
                filename,
                "UTF-8"
            )

        val responseBody =
            executeWithFallback(
                operation = "GET_FILE_METADATA",
                path = "/server/files/metadata?filename=$encodedFilename"
            ) { builder ->
                builder.get()
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val result =
            root["result"]
                ?.jsonObject
                ?: error(
                    "Moonraker did not return file metadata"
                )

        android.util.Log.d(
            "AMBERKLIP_METADATA_RAW",
            "filename=$filename result=$result"
        )

        return PrintMetadata(

            slicer =
                result["slicer"]
                    ?.jsonPrimitive
                    ?.contentOrNull,

            slicerVersion =
                result["slicer_version"]
                    ?.jsonPrimitive
                    ?.contentOrNull,

            estimatedTime =
                result["estimated_time"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            filamentTotal =
                result["filament_total"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            filamentWeightTotal =
                result["filament_weight_total"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            layerHeight =
                result["layer_height"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            layerCount =
                result["layer_count"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toIntOrNull(),

            objectHeight =
                result["object_height"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            nozzleDiameter =
                result["nozzle_diameter"]
                    ?.jsonPrimitive
                    ?.doubleOrNull,

            printerVendor =
                result["printer_vendor"]
                    ?.jsonPrimitive
                    ?.contentOrNull,

            printerModel =
                result["printer_model"]
                    ?.jsonPrimitive
                    ?.contentOrNull,

            printerVariant =
                result["printer_variant"]
                    ?.jsonPrimitive
                    ?.contentOrNull
        )
    }

    fun getGcodeFiles(): List<PrintTask> {

        val responseBody =
            executeWithFallback(
                operation = "GET_GCODE_FILES",
                path = "/server/files/list?root=gcodes"
            ) { builder ->
                builder.get()
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val files =
            root["result"]
                ?.jsonArray
                ?: error(
                    "Moonraker did not return G-code files"
                )

        val tasks =
            mutableListOf<PrintTask>()

        for (file in files) {

            val fileObject =
                file.jsonObject

            val path =
                fileObject["path"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val modified =
                fileObject["modified"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: continue

            val size =
                fileObject["size"]
                ?.jsonPrimitive
                ?.content
                ?.toLongOrNull()
                ?: continue

            tasks.add(
                PrintTask(
                    id = "file:$path",
                    name = path,
                    printDate = null,
                    modifiedDate =
                        (modified * 1000.0)
                            .toLong(),
                    fileSize = size
                )
            )
        }

        return tasks
    }

    fun getRecentPrintHistory(): List<PrintHistoryEntry> {

        val responseBody =
            executeWithFallback(
                operation = "GET_RECENT_PRINT_HISTORY",
                path = "/server/history/list?limit=20&order=desc"
            ) { builder ->
                builder.get()
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val jobs =
            root["result"]
                ?.jsonObject
                ?.get("jobs")
                ?.jsonArray
                ?: error(
                    "Moonraker did not return print history"
                )

        val history =
            mutableListOf<PrintHistoryEntry>()

        for (job in jobs) {

            val jobObject =
                job.jsonObject

            val jobId =
                jobObject["job_id"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val filename =
                jobObject["filename"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val status =
                jobObject["status"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val startTime =
                jobObject["start_time"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?.let {
                        (it * 1000.0).toLong()
                    }

            val endTime =
                jobObject["end_time"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?.let {
                        (it * 1000.0).toLong()
                    }

            val printDuration =
                jobObject["print_duration"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0

            val totalDuration =
                jobObject["total_duration"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0

            history.add(
                PrintHistoryEntry(
                    jobId = jobId,
                    filename = filename,
                    status = status,
                    startTime = startTime,
                    endTime = endTime,
                    printDuration = printDuration,
                    totalDuration = totalDuration
                )
            )
        }

        return history
            .filter { entry ->
                !entry.status.equals(
                    "in_progress",
                    ignoreCase = true
                )
            }
            .take(3)
    }

    fun getPrintHistory(): List<PrintTask> {

        val responseBody =
            executeWithFallback(
                operation = "GET_PRINT_HISTORY",
                path = "/server/history/list?limit=100&order=desc"
            ) { builder ->
                builder.get()
            }

        val root =
            Json.parseToJsonElement(
                responseBody
            ).jsonObject

        val jobs =
            root["result"]
                ?.jsonObject
                ?.get("jobs")
                ?.jsonArray
                ?: error(
                    "Moonraker did not return print history"
                )

        val tasks =
            mutableListOf<PrintTask>()

        for (job in jobs) {

            val jobObject =
                job.jsonObject

            val id =
                jobObject["job_id"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val name =
                jobObject["filename"]
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            val printDate =
                jobObject["start_time"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: continue

            val metadata =
                jobObject["metadata"]
                    ?.jsonObject
                    ?: continue

            val modifiedDate =
                metadata["modified"]
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: continue

            val fileSize =
                metadata["size"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toLongOrNull()
                    ?: continue

            tasks.add(
                PrintTask(
                    id = id,
                    name = name,
                    printDate =
                        (printDate * 1000.0)
                            .toLong(),
                    modifiedDate =
                        (modifiedDate * 1000.0)
                            .toLong(),
                    fileSize = fileSize
                )
            )
        }

        return tasks
    }

    fun refreshGcodeMetadata(
        filename: String
    ) {

        val encodedFilename =
            URLEncoder.encode(
                filename,
                "UTF-8"
            )

        executeWithFallback(
            operation = "REFRESH_GCODE_METADATA",
            path = "/server/files/metascan?filename=$encodedFilename"
        ) { builder ->
            builder.post(
                RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
        }
    }

    fun renameGcode(source: String, destination: String) {
        val json = """
            {
                "source": "${escapeJson(withGcodesRoot(source))}",
                "dest": "${escapeJson(withGcodesRoot(destination))}"
            }
            """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            json
        )

        executeWithFallback(
            operation = "RENAME_GCODE",
            path = "/server/files/move"
        ) { builder ->
            builder.post(body)
        }
    }

    fun copyGcode(source: String): String {
        val files = getGcodeFiles()
        val destination = createCopyName(
            source,
            files.map { it.name }
        )

        val json = """
            {
                "source": "${escapeJson(withGcodesRoot(source))}",
                "dest": "${escapeJson(withGcodesRoot(destination))}"
            }
            """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            json
        )

        executeWithFallback(
            operation = "COPY_GCODE",
            path = "/server/files/copy"
        ) { builder ->
            builder.post(body)
        }

        return destination
    }

    fun deleteGcode(filename: String) {
        val encodedFilename = Uri.encode(filename, "/")

        executeWithFallback(
            operation = "DELETE_GCODE",
            path = "/server/files/gcodes/$encodedFilename"
        ) { builder ->
            builder.delete()
        }
    }

    private fun createCopyName(
        source: String,
        existingNames: List<String>
    ): String {

        val lastSlash = source.lastIndexOf('/')

        val directory =
            if (lastSlash >= 0) {
                source.substring(0, lastSlash + 1)
            } else {
                ""
            }

        val fileName =
            source.substring(lastSlash + 1)

        val dotIndex = fileName.lastIndexOf('.')

        val extension =
            if (dotIndex > 0) {
                fileName.substring(dotIndex)
            } else {
                ""
            }

        val baseName =
            if (dotIndex > 0) {
                fileName.substring(0, dotIndex)
            } else {
                fileName
            }

        /*
         * Если файл уже является копией:
         *
         * model.gcode       -> model (1).gcode
         * model (1).gcode   -> model (2).gcode
         * model (2).gcode   -> model (3).gcode
         */
        val copyPattern = Regex("""^(.*) \((\d+)\)$""")

        val match = copyPattern.matchEntire(baseName)

        val originalBaseName =
            match?.groupValues?.get(1) ?: baseName

        var copyNumber =
            match?.groupValues
                ?.get(2)
                ?.toIntOrNull()
                ?.plus(1)
                ?: 1

        while (true) {

            val candidate =
                directory +
                    originalBaseName +
                    " ($copyNumber)" +
                    extension

            if (candidate !in existingNames) {
                return candidate
            }

            copyNumber++
        }
    }

    private fun withGcodesRoot(path: String): String {
        return if (path.startsWith("gcodes/")) {
            path
        } else {
            "gcodes/$path"
        }
    }

    private fun escapeJson(
        value: String
    ): String {

        return value
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\"",
                "\\\""
            )
            .replace(
                "\n",
                "\\n"
            )
            .replace(
                "\r",
                "\\r"
            )
            .replace(
                "\t",
                "\\t"
            )
    }

    fun uploadGcode(
        file: File,
        startAfterUpload: Boolean = false
    ) {

        val multipart =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    RequestBody.create(
                        MediaType.parse(
                            "application/octet-stream"
                        ),
                        file
                    )
                )
                .addFormDataPart(
                    "root",
                    "gcodes"
                )
                .addFormDataPart(
                    "print",
                    startAfterUpload.toString()
                )
                .build()

        executeWithFallback(
            operation = "UPLOAD_GCODE",
            path = "/server/files/upload"
        ) { builder ->
            builder.post(multipart)
        }
    }

    fun moveAxis(
        axis: Char,
        coordinate: Double,
        feedrate: Int
    ) {

        val value =
            String.format(
                java.util.Locale.US,
                "%.3f",
                coordinate
            )
                .trimEnd('0')
                .trimEnd('.')

        executeGcode(
            operation = "MOVE_AXIS_$axis",
            script =
                "G1 $axis$value F$feedrate"
        )
    }

    fun homeXY() {
        executeGcode(
            operation = "HOME_XY",
            script = "G28 X Y"
        )
    }

    fun homeZ() {
        executeGcode(
            operation = "HOME_Z",
            script = "G28 Z"
        )
    }

    fun disableMotors() {
        executeGcode(
            operation = "DISABLE_MOTORS",
            script = "M84"
        )
    }

    private fun post(
        operation: String,
        path: String
    ) {

        executeWithFallback(
            operation = operation,
            path = path
        ) { builder ->
            builder.post(
                RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
        }
    }
}
