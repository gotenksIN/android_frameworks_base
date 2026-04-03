/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shared.clocks.view

import android.util.AttributeSet
import android.widget.ImageView
import com.android.systemui.customization.R
import com.android.systemui.customization.clocks.FontTextStyle
import com.android.systemui.customization.clocks.view.IDigitalClockTextView
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.shared.clocks.FlexClockContext
import com.android.systemui.shared.clocks.controller.FlexClockFaceController.Companion.SMALL_CLOCK_MAX_WIDTH

class BitmapClockView(
    clockCtx: FlexClockContext,
    private val isLargeClock: Boolean,
    attrs: AttributeSet?,
) : ImageView(clockCtx.context, attrs), IDigitalClockTextView {
    override var dozeFraction: Float = 0f
        set(value) {
            field = value
            alpha = 1f - (value * 0.5f)
        }

    override var text: String = ""
        set(value) {
            field = value
            updateDigitImage(value)

            requestLayout()
            invalidate()
        }

    private fun updateDigitImage(digitPath: String) {
        setImageResource(R.drawable.eight)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val ratio = measuredHeight / measuredWidth

        if (!isLargeClock) {
            val finalWidth = minOf(measuredWidth.toFloat(), SMALL_CLOCK_MAX_WIDTH).toInt()
            val finalHeight = finalWidth * ratio

            setMeasuredDimension(finalWidth, finalHeight)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            val rect = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            val size = VPointF(width.toFloat(), height.toFloat())
            maxSize = size

            onViewBoundsChanged?.invoke(rect)
            onViewMaxSizeChanged?.invoke(size)
        }
    }

    fun applyBitmapStyles(style: FontTextStyle) {
        this.scaleX = style.fontSizeScale
        this.scaleY = style.fontSizeScale
    }

    override var maxSize = VPointF(-1f, -1f)
    override var onViewBoundsChanged: ((VRectF) -> Unit)? = null
    override var onViewMaxSizeChanged: ((VPointF) -> Unit)? = null

    override fun refreshText() {
        invalidate()
    }

    override fun animateCharge() {}
}
