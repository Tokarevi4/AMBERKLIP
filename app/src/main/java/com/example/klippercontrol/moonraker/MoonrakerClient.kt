package com.example.klippercontrol.moonraker

import com.example.klippercontrol.model.Printer
import com.example.klippercontrol.model.PrinterStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MoonrakerClient(
    private val printer: Printer
) {

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
        path: String,
        requestFactory: (Request.Builder) -> Request.Builder
    ): String {

        try {
            val request = requestFactory(
                buildRequest(path, true)
            ).build()

            httpsClient.newCall(request).execute().use { response ->

                val body =
                    response.body()?.string() ?: ""

                if (!response.isSuccessful) {
                    error(
                        "HTTPS HTTP ${response.code()}: $body"
                    )
                }

                return body
            }

        } catch (e: Exception) {

            try {
                val request = requestFactory(
                    buildRequest(path, false)
                ).build()

                httpClient.newCall(request).execute().use { response ->

                    val body =
                        response.body()?.string() ?: ""

                    if (!response.isSuccessful) {
                        error(
                            "HTTP ${response.code()}: $body"
                        )
                    }

                    return body
                }

            } catch (httpException: Exception) {

                error(
                    "HTTPS failed: ${e.message ?: "unknown error"}\n" +
                    "HTTP failed: ${httpException.message ?: "unknown error"}"
                )
            }
        }
    }

    fun getStatus(): PrinterStatus {

        val json = """
            {
                "objects": {
                    "webhooks": null,
                    "print_stats": null,
                    "extruder": ["temperature", "target"],
                    "heater_bed": ["temperature", "target"]
                }
            }
        """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse(
                "application/json; charset=utf-8"
            ),
            json
        )

        val responseBody = executeWithFallback(
            "/printer/objects/query"
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

        val webhooks =
            status["webhooks"]?.jsonObject

        val print =
            status["print_stats"]?.jsonObject

        val extruder =
            status["extruder"]?.jsonObject

        val bed =
            status["heater_bed"]?.jsonObject

        return PrinterStatus(
            klippyState =
                webhooks
                    ?.get("state")
                    ?.jsonPrimitive
                    ?.content
                    ?: "unknown",

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
                print
                    ?.get("progress")
                    ?.jsonPrimitive
                    ?.doubleOrNull
                    ?: 0.0,

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

            message =
                webhooks
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
        )
    }

    fun startPrint(filename: String) {

        val encodedFilename =
            URLEncoder.encode(
                filename,
                "UTF-8"
            )

        executeWithFallback(
            "/printer/print/start?filename=$encodedFilename"
        ) { builder ->
            builder.post(
                RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
        }
    }

    fun pausePrint() {
        post("/printer/print/pause")
    }

    fun resumePrint() {
        post("/printer/print/resume")
    }

    fun cancelPrint() {
        post("/printer/print/cancel")
    }

    fun emergencyStop() {
        post("/printer/emergency_stop")
    }

    fun uploadGcode(
        file: File,
        startAfterUpload: Boolean = false
    ) {

        val multipart = MultipartBody.Builder()
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
            "/server/files/upload"
        ) { builder ->
            builder.post(multipart)
        }
    }

    private fun post(path: String) {

        executeWithFallback(path) { builder ->
            builder.post(
                RequestBody.create(
                    null,
                    ByteArray(0)
                )
            )
        }
    }
}
