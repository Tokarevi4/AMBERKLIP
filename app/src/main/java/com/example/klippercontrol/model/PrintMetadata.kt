package com.example.klippercontrol.model

data class PrintMetadata(
    val slicer: String? = null,
    val slicerVersion: String? = null,
    val estimatedTime: Double? = null,
    val filamentTotal: Double? = null,
    val filamentWeightTotal: Double? = null,
    val layerHeight: Double? = null,
    val firstLayerHeight: Double? = null,
    val layerCount: Int? = null,
    val objectHeight: Double? = null,
    val nozzleDiameter: Double? = null,
    val printerVendor: String? = null,
    val printerModel: String? = null,
    val printerVariant: String? = null
)
