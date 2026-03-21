package com.example.languagereminder.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.languagereminder.R
import java.time.DayOfWeek

class FloatingWidgetView(
    context: Context,
    private val onSave: (Map<DayOfWeek, String>) -> Unit,
    private val onClose: () -> Unit
) {
    val rootView: View = LayoutInflater.from(context).inflate(R.layout.overlay_widget, null)

    private val collapsedContainer: LinearLayout = rootView.findViewById(R.id.collapsed_container)
    private val expandedContainer: LinearLayout = rootView.findViewById(R.id.expanded_container)
    private val todayText: TextView = rootView.findViewById(R.id.today_text)
    private val expandButton: ImageButton = rootView.findViewById(R.id.expand_button)
    private val saveButton: Button = rootView.findViewById(R.id.save_button)
    private val closeButton: Button = rootView.findViewById(R.id.close_button)

    private val editMap: Map<DayOfWeek, EditText> = mapOf(
        DayOfWeek.MONDAY to rootView.findViewById(R.id.edit_monday),
        DayOfWeek.TUESDAY to rootView.findViewById(R.id.edit_tuesday),
        DayOfWeek.WEDNESDAY to rootView.findViewById(R.id.edit_wednesday),
        DayOfWeek.THURSDAY to rootView.findViewById(R.id.edit_thursday),
        DayOfWeek.FRIDAY to rootView.findViewById(R.id.edit_friday),
        DayOfWeek.SATURDAY to rootView.findViewById(R.id.edit_saturday),
        DayOfWeek.SUNDAY to rootView.findViewById(R.id.edit_sunday)
    )

    private var isExpanded = false
    private val cornerRadiusPx = context.resources.displayMetrics.density * 18f

    init {
        updateWidgetShape(stuckToRight = false)
        expandButton.setOnClickListener {
            setExpanded(!isExpanded)
        }
        saveButton.setOnClickListener {
            val values = DayOfWeek.entries.associateWith { editMap[it]?.text?.toString().orEmpty() }
            onSave(values)
            setExpanded(false)
        }
        closeButton.setOnClickListener { onClose() }
        rootView.setOnLongClickListener {
            setExpanded(!isExpanded)
            true
        }
    }

    fun bindValues(values: Map<DayOfWeek, String>, today: DayOfWeek) {
        todayText.text = values[today].orEmpty()
        DayOfWeek.entries.forEach { day ->
            editMap[day]?.setText(values[day].orEmpty())
        }
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        expandedContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        expandButton.setImageResource(
            if (expanded) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float
        )
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
