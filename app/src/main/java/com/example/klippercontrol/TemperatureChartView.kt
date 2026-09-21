package com.example.klippercontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    data class ChartState(
        val points: List<Triple<Long, Double?, Double?>>,
        val currentHotend: Double?,
        val currentBed: Double?
    )

    private data class TemperaturePoint(
        val time: Long,
        val hotend: Double?,
        val bed: Double?
    )

    companion object {

        private const val HISTORY_DURATION =
            10 * 60 * 1000L

        private const val LEFT_MARGIN = 52f
        private const val RIGHT_MARGIN = 52f
        private const val TOP_MARGIN = 42f
        private const val BOTTOM_MARGIN = 42f

        private val HOTEND_COLOR =
            Color.rgb(220, 70, 70)

        private val BED_COLOR =
            Color.rgb(70, 120, 220)

        private val GRID_COLOR =
            Color.rgb(190, 190, 190)

        private val TEXT_COLOR =
            Color.rgb(100, 100, 100)
    }

    private val points =
        mutableListOf<TemperaturePoint>()

    private var currentHotend: Double? = null
    private var currentBed: Double? = null

    private val gridPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = GRID_COLOR
            alpha = 80
        }

    private val axisPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = GRID_COLOR
            alpha = 160
        }

    private val hotendPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = HOTEND_COLOR
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private val bedPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = BED_COLOR
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = TEXT_COLOR
            textSize = dp(12f)
        }

    private val currentValuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = dp(15f)
        }

    init {
        isFocusable = false

        setBackgroundColor(
            Color.TRANSPARENT
        )
    }

    fun saveState(): ChartState {
        return ChartState(
            points = points.map {
                Triple(
                    it.time,
                    it.hotend,
                    it.bed
                )
            },
            currentHotend = currentHotend,
            currentBed = currentBed
        )
    }

    fun restoreState(
        state: ChartState?
    ) {
        points.clear()

        if (state == null) {
            currentHotend = null
            currentBed = null
            invalidate()
            return
        }

        points.addAll(
            state.points.map {
                TemperaturePoint(
                    time = it.first,
                    hotend = it.second,
                    bed = it.third
                )
            }
        )

        currentHotend = state.currentHotend
        currentBed = state.currentBed

        removeOldPoints(
            System.currentTimeMillis()
        )

        invalidate()
    }

    fun addPoint(
        hotend: Double?,
        bed: Double?
    ) {
        val now =
            System.currentTimeMillis()

        currentHotend = hotend
        currentBed = bed

        points.add(
            TemperaturePoint(
                time = now,
                hotend = hotend,
                bed = bed
            )
        )

        removeOldPoints(now)

        invalidate()
    }

    fun clearHistory() {
        points.clear()

        currentHotend = null
        currentBed = null

        invalidate()
    }

    private fun removeOldPoints(
        now: Long
    ) {
        val minimumTime =
            now - HISTORY_DURATION

        while (
            points.isNotEmpty() &&
            points.first().time < minimumTime
        ) {
            points.removeAt(0)
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val chartLeft =
            LEFT_MARGIN

        val chartRight =
            width.toFloat() -
                    RIGHT_MARGIN

        val chartTop =
            TOP_MARGIN

        val chartBottom =
            height.toFloat() -
                    BOTTOM_MARGIN

        if (
            chartRight <= chartLeft ||
            chartBottom <= chartTop
        ) {
            return
        }

        drawGrid(
            canvas,
            chartLeft,
            chartTop,
            chartRight,
            chartBottom
        )

        if (points.isNotEmpty()) {

            val range =
                calculateTemperatureRange()

            drawAxisLabels(
                canvas,
                chartLeft,
                chartTop,
                chartBottom,
                range.first,
                range.second
            )

            drawTemperatureLine(
                canvas,
                chartLeft,
                chartTop,
                chartRight,
                chartBottom,
                range.first,
                range.second,
                true
            )

            drawTemperatureLine(
                canvas,
                chartLeft,
                chartTop,
                chartRight,
                chartBottom,
                range.first,
                range.second,
                false
            )
        }

        drawTimeLabels(
            canvas,
            chartLeft,
            chartRight,
            chartBottom
        )
    }

    private fun drawTimeLabels(
        canvas: Canvas,
        left: Float,
        right: Float,
        bottom: Float
    ) {
        textPaint.textAlign =
            Paint.Align.CENTER

        val now =
            System.currentTimeMillis()

        val startTime =
            now - HISTORY_DURATION

        val intervals =
            5

        for (i in 0..intervals) {

            val ratio =
                i.toFloat() /
                        intervals.toFloat()

            val time =
                startTime +
                        (
                            HISTORY_DURATION *
                                    ratio
                        ).toLong()

            val x =
                left +
                        ratio *
                                (
                                    right -
                                            left
                                )

            val calendar =
                Calendar.getInstance()

            calendar.timeInMillis =
                time

            val hour =
                calendar.get(
                    Calendar.HOUR_OF_DAY
                )

            val minute =
                calendar.get(
                    Calendar.MINUTE
                )

            val label =
                String.format(
                    Locale.US,
                    "%02d:%02d",
                    hour,
                    minute
                )

            canvas.drawText(
                label,
                x,
                bottom + dp(26f),
                textPaint
            )
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val rows =
            4

        for (i in 0..rows) {

            val ratio =
                i.toFloat() /
                        rows.toFloat()

            val y =
                bottom -
                        ratio *
                                (
                                    bottom -
                                            top
                                )

            canvas.drawLine(
                left,
                y,
                right,
                y,
                gridPaint
            )
        }

        canvas.drawLine(
            left,
            top,
            left,
            bottom,
            axisPaint
        )

        canvas.drawLine(
            left,
            bottom,
            right,
            bottom,
            axisPaint
        )
    }

    private fun calculateTemperatureRange():
            Pair<Double, Double> {

        var minimum =
            Double.POSITIVE_INFINITY

        var maximum =
            Double.NEGATIVE_INFINITY

        for (point in points) {

            point.hotend?.let {
                minimum =
                    min(
                        minimum,
                        it
                    )

                maximum =
                    max(
                        maximum,
                        it
                    )
            }

            point.bed?.let {
                minimum =
                    min(
                        minimum,
                        it
                    )

                maximum =
                    max(
                        maximum,
                        it
                    )
            }
        }

        if (
            minimum ==
                    Double.POSITIVE_INFINITY ||
            maximum ==
                    Double.NEGATIVE_INFINITY
        ) {
            return Pair(
                0.0,
                100.0
            )
        }

        if (
            minimum == maximum
        ) {
            minimum -= 10.0
            maximum += 10.0
        }

        val rawRange =
            maximum - minimum

        val padding =
            max(
                5.0,
                rawRange * 0.10
            )

        var lower =
            floor(
                max(
                    0.0,
                    minimum - padding
                ) / 10.0
            ) * 10.0

        var upper =
            ceil(
                (
                    maximum +
                            padding
                ) / 10.0
            ) * 10.0

        if (
            upper - lower < 20.0
        ) {

            val center =
                (
                    upper +
                            lower
                ) / 2.0

            lower =
                max(
                    0.0,
                    floor(
                        (
                            center -
                                    10.0
                        ) / 10.0
                    ) * 10.0
                )

            upper =
                ceil(
                    (
                        center +
                                10.0
                    ) / 10.0
                ) * 10.0
        }

        return Pair(
            lower,
            upper
        )
    }

    private fun drawAxisLabels(
        canvas: Canvas,
        left: Float,
        top: Float,
        bottom: Float,
        minimum: Double,
        maximum: Double
    ) {
        textPaint.textAlign =
            Paint.Align.RIGHT

        for (i in 0..4) {

            val ratio =
                i.toFloat() / 4f

            val temperature =
                minimum +
                        (
                            maximum -
                                    minimum
                        ) *
                        ratio

            val y =
                bottom -
                        ratio *
                                (
                                    bottom -
                                            top
                                ) +
                        textPaint.textSize / 3f

            canvas.drawText(
                String.format(
                    Locale.US,
                    "%.0f",
                    temperature
                ),
                left - dp(8f),
                y,
                textPaint
            )
        }
    }

    private fun drawTemperatureLine(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minimum: Double,
        maximum: Double,
        hotend: Boolean
    ) {
        if (
            points.isEmpty()
        ) {
            return
        }

        val now =
            System.currentTimeMillis()

        val startTime =
            now - HISTORY_DURATION

        val path =
            Path()

        var hasPoint =
            false

        for (point in points) {

            val temperature =
                if (hotend) {
                    point.hotend
                } else {
                    point.bed
                }

            if (
                temperature == null
            ) {
                continue
            }

            val timeRatio =
                (
                    point.time -
                            startTime
                ).toFloat() /
                        HISTORY_DURATION.toFloat()

            val x =
                left +
                        timeRatio.coerceIn(
                            0f,
                            1f
                        ) *
                        (
                            right -
                                    left
                        )

            val temperatureRatio =
                (
                    temperature -
                            minimum
                ) /
                        (
                            maximum -
                                    minimum
                        )

            val y =
                bottom -
                        temperatureRatio
                            .toFloat()
                            .coerceIn(
                                0f,
                                1f
                            ) *
                        (
                            bottom -
                                    top
                        )

            if (!hasPoint) {

                path.moveTo(
                    x,
                    y
                )

                hasPoint =
                    true

            } else {

                path.lineTo(
                    x,
                    y
                )
            }
        }

        if (!hasPoint) {
            return
        }

        val paint =
            if (hotend) {
                hotendPaint
            } else {
                bedPaint
            }

        canvas.drawPath(
            path,
            paint
        )
    }

    private fun formatTemperature(
        temperature: Double?
    ): String {

        return temperature?.let {

            String.format(
                Locale.US,
                "%.1f C",
                it
            )

        } ?: "--"
    }

    private fun dp(
        value: Float
    ): Float {

        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    fun clear() {
        clearHistory()
    }
}
