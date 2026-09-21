package com.example.klippercontrol

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class MaxHeightScrollView : ScrollView {

    var maxHeightPx: Int = Int.MAX_VALUE

    constructor(context: Context) : super(context)

    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )

        if (measuredHeight > maxHeightPx) {
            setMeasuredDimension(
                measuredWidth,
                maxHeightPx
            )
        }
    }
}
