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
    val filePosition: Long? = null,
    val fileSize: Long? = null,

    val hotendTemperature: Double? = null,
    val hotendTarget: Double? = null,

    val bedTemperature: Double? = null,
    val bedTarget: Double? = null,

    val speed: Double? = null,
    val liveVelocity: Double? = null,
    val liveExtruderVelocity: Double? = null,

    val filamentUsed: Double = 0.0,

    val currentLayer: Int? = null,
    val totalLayer: Int? = null,
    val currentZ: Double? = null,

    val printDuration: Double = 0.0,
    val totalDuration: Double = 0.0,

    val message: String? = null,

    val xPosition: Double? = null,
    val yPosition: Double? = null,
    val zPosition: Double? = null,

    val xMinimum: Double? = null,
    val xMaximum: Double? = null,

    val yMinimum: Double? = null,
    val yMaximum: Double? = null,

    val zMinimum: Double? = null,
    val zMaximum: Double? = null,

    val homedAxes: String = "",

    val motorsEnabled: Boolean? = null
)
