package com.orailnoor.droiddesk.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.orailnoor.droiddesk.x11.X11InputController
import com.termux.x11.input.InputStub
import kotlin.math.abs

/**
 * Floating laptop-style mouse buttons for phones without a mouse.
 *
 * Left and Right press at the cursor and stay down while touched, so holding Left and moving a
 * finger on the trackpad drags, as on a laptop. Dragging the middle strip scrolls; tapping it
 * middle-clicks. The grip on the left moves the whole bar.
 */
@SuppressLint("ViewConstructor")
class MouseButtonsBar(
    context: Context,
    private val input: () -> X11InputController?,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val heldButtons = mutableSetOf<Int>()

    /** Drag handle; the activity attaches its move listener here. */
    val grip: View

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            setColor(Color.argb(200, 22, 26, 34))
            setStroke(dp(1), Color.argb(40, 255, 255, 255))
        }
        elevation = 10 * density
        setPadding(dp(4), dp(4), dp(6), dp(4))
        // Touches on the bar's own padding must not fall through and click the desktop.
        isClickable = true

        grip = label("⋮⋮", 16f).apply {
            setTextColor(Color.argb(150, 255, 255, 255))
            contentDescription = "Move mouse buttons"
        }
        addView(grip, LayoutParams(dp(26), dp(44)))
        addView(mouseButton("Left", InputStub.BUTTON_LEFT), keyParams(dp(76)))
        addView(scrollStrip(), keyParams(dp(44)))
        addView(mouseButton("Right", InputStub.BUTTON_RIGHT), keyParams(dp(76)))
    }

    /** Lets go of any button still held, e.g. when the bar is hidden mid-press. */
    fun releaseAll() {
        heldButtons.toList().forEach { input()?.sendMouseButton(it, false) }
        heldButtons.clear()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    private fun mouseButton(text: String, button: Int) = label(text, 14f).apply {
        background = keyBackground()
        contentDescription = "$text mouse button"
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    heldButtons += button
                    input()?.sendMouseButton(button, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    if (heldButtons.remove(button)) input()?.sendMouseButton(button, false)
                }
            }
            true
        }
    }

    private fun scrollStrip() = label("⇕", 18f).apply {
        background = keyBackground()
        contentDescription = "Scroll, or tap for middle click"
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var scrolling = false
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    downX = event.x; downY = event.y
                    lastX = event.x; lastY = event.y
                    scrolling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scrolling && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                        scrolling = true
                    }
                    if (scrolling) {
                        // Same direction as a two-finger swipe on the desktop.
                        input()?.scroll(lastX - event.x, lastY - event.y)
                        lastX = event.x; lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!scrolling) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        input()?.sendMouseButton(InputStub.BUTTON_MIDDLE, true)
                        input()?.sendMouseButton(InputStub.BUTTON_MIDDLE, false)
                    }
                }
                MotionEvent.ACTION_CANCEL -> view.isPressed = false
            }
            true
        }
    }

    private fun label(text: String, size: Float) = TextView(context).apply {
        this.text = text
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
    }

    private fun keyBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.argb(255, 38, 110, 200)))
        addState(intArrayOf(), rounded(Color.argb(255, 40, 50, 66)))
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setColor(color)
    }

    private fun keyParams(width: Int) = LayoutParams(width, dp(44)).apply {
        setMargins(dp(2), 0, dp(2), 0)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
