package com.example.klippercontrol.model

data class PrintHistoryEntry(
    val jobId: String,
    val filename: String,
    val status: String,
    val startTime: Long?,
    val endTime: Long?,
    val printDuration: Double,
    val totalDuration: Double
)
