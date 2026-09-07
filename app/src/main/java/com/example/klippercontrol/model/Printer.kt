package com.example.klippercontrol.model

data class Printer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val apiKey: String? = null
) {
    val httpsUrl: String
        get() = "https://$host:$port"

    val httpUrl: String
        get() = "http://$host:$port"

    val displayAddress: String
        get() = "$host:$port"
}

data class PrinterStatus(
    val klippyState: String = "unknown",
    val printState: String = "standby",
    val filename: String? = null,
    val progress: Double = 0.0,
    val hotendTemperature: Double? = null,
    val hotendTarget: Double? = null,
    val bedTemperature: Double? = null,
    val bedTarget: Double? = null,
    val message: String? = null
)
