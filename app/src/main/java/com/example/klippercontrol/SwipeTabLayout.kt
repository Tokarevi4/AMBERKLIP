package com.example.klippercontrol

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface OnSwipeListener {
        fun onSwipeLeft()
        fun onSwipeRight()
    }

    var onSwipeListener: OnSwipeListener? = null

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f

    private var isHorizontalSwipe = false

    override fun onInterceptTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isHorizontalSwipe = false

                /*
                 * Не перехватываем ACTION_DOWN.
                 * Дочерний RecyclerView/ScrollView должен
                 * получить начало жеста.
                 */
                return false
            }

            MotionEvent.ACTION_MOVE -> {

                val dx =
                    event.x - downX

                val dy =
                    event.y - downY

                /*
                 * Пока палец не ушёл дальше touchSlop,
                 * направление ещё не определяем.
                 */
                if (
                    !isHorizontalSwipe &&
                    abs(dx) > touchSlop &&
                    abs(dx) > abs(dy)
                ) {
                    isHorizontalSwipe = true

                    /*
                     * Теперь забираем жест у дочернего элемента.
                     * Android автоматически отправит ему ACTION_CANCEL.
                     */
                    return true
                }

                /*
                 * Если движение вертикальное,
                 * отдаём его дочернему ScrollView/RecyclerView.
                 */
                if (
                    abs(dy) > touchSlop &&
                    abs(dy) > abs(dx)
                ) {
                    isHorizontalSwipe = false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isHorizontalSwipe = false
            }
        }

        return false
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isHorizontalSwipe = false

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                val dx =
                    event.x - downX

                val dy =
                    event.y - downY

                if (
                    !isHorizontalSwipe &&
                    abs(dx) > touchSlop &&
                    abs(dx) > abs(dy)
                ) {
                    isHorizontalSwipe = true
                }

                return true
            }

            MotionEvent.ACTION_UP -> {

                val dx =
                    event.x - downX

                val dy =
                    event.y - downY

                if (
                    isHorizontalSwipe &&
                    abs(dx) > touchSlop &&
                    abs(dx) > abs(dy)
                ) {

                    if (dx < 0f) {
                        onSwipeListener?.onSwipeLeft()
                    } else {
                        onSwipeListener?.onSwipeRight()
                    }
                }

                isHorizontalSwipe = false

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isHorizontalSwipe = false
                return true
            }
        }

        return true
    }
}
