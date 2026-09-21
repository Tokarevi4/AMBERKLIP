package com.example.klippercontrol.model

data class PrintTask(
    val id: String,
    val name: String,
    val printDate: Long?,
    val modifiedDate: Long,
    val fileSize: Long
)
