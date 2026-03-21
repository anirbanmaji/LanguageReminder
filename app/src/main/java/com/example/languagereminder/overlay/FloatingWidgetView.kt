package com.example.languagereminder.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.languagereminder.R
import java.time.DayOfWeek

class FloatingWidgetView(
    context: Context
) {
    val rootView: View = LayoutInflater.from(context).inflate(R.layout.overlay_widget, null)

    private val collapsedContainer: LinearLayout = rootView.findViewById(R.id.collapsed_container)
    private val todayText: TextView = rootView.findViewById(R.id.today_text)
    private val cornerRadiusPx = context.resources.displayMetrics.density * 18f

    init {
        updateWidgetShape(stuckToRight = false)
    }

    fun bindValues(values: Map<DayOfWeek, String>, today: DayOfWeek) {
        todayText.text = values[today].orEmpty()
    }

    fun installDragBehavior(params: WindowManager.LayoutParams, windowManager: WindowManager) {
        collapsedContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(rootView, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        snapToNearestEdge(params, windowManager)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapToNearestEdge(
        params: WindowManager.LayoutParams,
        windowManager: WindowManager
    ) {
        val screenWidth = rootView.resources.displayMetrics.widthPixels
        val widgetWidth = if (rootView.width > 0) rootView.width else rootView.measuredWidth
        val midpoint = screenWidth / 2
        val stickToRight = params.x + (widgetWidth / 2) >= midpoint
        params.x = if (stickToRight) {
            (screenWidth - widgetWidth).coerceAtLeast(0)
        } else {
            0
        }
        windowManager.updateViewLayout(rootView, params)
        updateWidgetShape(stuckToRight = stickToRight)
    }

    private fun updateWidgetShape(stuckToRight: Boolean) {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#CC1E1E1E"))
            cornerRadii = if (stuckToRight) {
                // Right edge is attached: round left side.
                floatArrayOf(
                    cornerRadiusPx, cornerRadiusPx,
                    0f, 0f,
                    0f, 0f,
                    cornerRadiusPx, cornerRadiusPx
                )
            } else {
                // Left edge is attached: round right side.
                floatArrayOf(
                    0f, 0f,
                    cornerRadiusPx, cornerRadiusPx,
                    cornerRadiusPx, cornerRadiusPx,
                    0f, 0f
                )
            }
        }
        rootView.background = bg
    }
}
