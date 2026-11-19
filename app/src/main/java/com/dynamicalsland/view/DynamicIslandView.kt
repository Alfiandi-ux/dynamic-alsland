package com.dynamicalsland.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.dynamicalsland.model.IslandConfig
import com.dynamicalsland.model.IslandState
import com.dynamicalsland.util.dpToPx
import com.dynamicalsland.util.formatTimer
import com.dynamicalsland.util.hapticFeedback

class DynamicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var config = IslandConfig()
    private var currentState: IslandState = IslandState.Idle
    private var isExpanded = false
    private var isAnimating = false

    private val handler = Handler(Looper.getMainLooper())
    private var autoCollapseRunnable: Runnable? = null

    // Child views
    private val pillContainer: FrameLayout
    private val expandedContainer: FrameLayout

    // Pill content views
    private val pillIcon: ImageView
    private val pillText: TextView
    private val pillIndicator: View

    // Expanded content views
    private val expandedTitle: TextView
    private val expandedSubtitle: TextView
    private val expandedIcon: ImageView
    private val expandedAction: LinearLayout
    private val mediaProgressBar: ProgressBar

    // Callbacks
    var onMediaAction: ((String) -> Unit)? = null
    var onIslandTapped: (() -> Unit)? = null
    var onIslandDismissed: (() -> Unit)? = null

    private val gestureDetector: GestureDetector

    init {
        val bg = GradientDrawable().apply {
            setColor(config.backgroundColor)
            cornerRadius = context.dpToPx(config.cornerRadiusDp).toFloat()
        }
        background = bg
        elevation = context.dpToPx(8).toFloat()
        clipToOutline = true

        // ---- Pill container (compact state) ----
        pillContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
        }

        pillIcon = ImageView(context).apply {
            layoutParams = LayoutParams(context.dpToPx(20), context.dpToPx(20)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = context.dpToPx(12)
            }
            setColorFilter(Color.WHITE)
        }

        pillText = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
        }

        pillIndicator = View(context).apply {
            layoutParams = LayoutParams(context.dpToPx(8), context.dpToPx(8)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = context.dpToPx(12)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(config.accentColor)
            }
            visibility = View.GONE
        }

        pillContainer.addView(pillIcon)
        pillContainer.addView(pillText)
        pillContainer.addView(pillIndicator)

        // ---- Expanded container ----
        expandedContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
            setPadding(context.dpToPx(16), context.dpToPx(12), context.dpToPx(16), context.dpToPx(12))
        }

        expandedIcon = ImageView(context).apply {
            layoutParams = LayoutParams(context.dpToPx(40), context.dpToPx(40)).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setColorFilter(Color.WHITE)
        }

        expandedTitle = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = context.dpToPx(52)
                topMargin = context.dpToPx(2)
            }
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            isSingleLine = true
        }

        expandedSubtitle = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = context.dpToPx(52)
                topMargin = context.dpToPx(22)
            }
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            maxLines = 2
        }

        mediaProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dpToPx(3)).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = context.dpToPx(36)
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }

        expandedAction = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dpToPx(32)).apply {
                gravity = Gravity.BOTTOM
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        expandedContainer.addView(expandedIcon)
        expandedContainer.addView(expandedTitle)
        expandedContainer.addView(expandedSubtitle)
        expandedContainer.addView(mediaProgressBar)
        expandedContainer.addView(expandedAction)

        addView(pillContainer)
        addView(expandedContainer)

        // Gesture detector
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isAnimating) {
                    if (isExpanded) {
                        collapse()
                    } else {
                        onIslandTapped?.invoke()
                        expand()
                    }
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && (e1.y - e2.y) > 100 && Math.abs(velocityY) > 200) {
                    collapse()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isExpanded && !isAnimating) {
                    context.hapticFeedback(30)
                    expand()
                }
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun updateConfig(newConfig: IslandConfig) {
        config = newConfig
        val bg = background as? GradientDrawable
        bg?.setColor(config.backgroundColor)
        bg?.cornerRadius = context.dpToPx(config.cornerRadiusDp).toFloat()
    }

    fun setState(state: IslandState) {
        currentState = state
        when (state) {
            is IslandState.Idle -> collapse()
            is IslandState.Notification -> showNotification(state)
            is IslandState.MediaPlayback -> showMediaPlayback(state)
            is IslandState.Timer -> showTimer(state)
            is IslandState.Charging -> showCharging(state)
            is IslandState.PhoneCall -> showPhoneCall(state)
        }
    }

    private fun showNotification(notification: IslandState.Notification) {
        if (config.enableHaptic) context.hapticFeedback()
        pillText.text = ""
        pillIndicator.visibility = View.VISIBLE
        notification.icon?.let { pillIcon.setImageDrawable(it) }

        expandedTitle.text = notification.title
        expandedSubtitle.text = notification.text
        notification.icon?.let { expandedIcon.setImageDrawable(it) }
        mediaProgressBar.visibility = View.GONE
        expandedAction.removeAllViews()

        expand()
        scheduleAutoCollapse(config.notificationTimeoutMs)
    }

    private fun showMediaPlayback(media: IslandState.MediaPlayback) {
        pillText.text = "♫"
        pillIndicator.visibility = if (media.isPlaying) View.VISIBLE else View.GONE

        if (media.isPlaying) {
            ObjectAnimator.ofFloat(pillIndicator, "alpha", 1f, 0.3f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
            }.start()
        }

        expandedTitle.text = media.trackTitle
        expandedSubtitle.text = media.artist
        media.albumArt?.let { expandedIcon.setImageBitmap(it) }
            ?: expandedIcon.setImageResource(android.R.drawable.ic_media_play)

        mediaProgressBar.visibility = View.VISIBLE
        mediaProgressBar.progress = (media.progress * 100).toInt()

        expandedAction.removeAllViews()
        addMediaButton("⏮") { onMediaAction?.invoke("prev") }
        addMediaButton(if (media.isPlaying) "⏸" else "▶") { onMediaAction?.invoke("toggle") }
        addMediaButton("⏭") { onMediaAction?.invoke("next") }

        if (!isExpanded) compactExpand()
    }

    private fun showTimer(timer: IslandState.Timer) {
        val timeStr = timer.remainingMillis.formatTimer()
        pillText.text = timeStr
        pillIndicator.visibility = View.GONE

        expandedTitle.text = "Timer"
        expandedSubtitle.text = timeStr
        expandedIcon.setImageResource(android.R.drawable.ic_menu_recent_history)
        mediaProgressBar.visibility = View.VISIBLE
        mediaProgressBar.max = timer.totalMillis.toInt()
        mediaProgressBar.progress = (timer.totalMillis - timer.remainingMillis).toInt()

        expandedAction.removeAllViews()
        addMediaButton(if (timer.isRunning) "⏸" else "▶") { onMediaAction?.invoke("timer_toggle") }
        addMediaButton("✕") { onMediaAction?.invoke("timer_stop") }
    }

    private fun showCharging(charging: IslandState.Charging) {
        pillText.text = "⚡${charging.batteryLevel}%"
        pillIndicator.visibility = View.GONE

        expandedTitle.text = "Charging"
        expandedSubtitle.text = "${charging.batteryLevel}% battery"
        expandedIcon.setImageResource(android.R.drawable.ic_lock_idle_charging)
        mediaProgressBar.visibility = View.VISIBLE
        mediaProgressBar.max = 100
        mediaProgressBar.progress = charging.batteryLevel
        expandedAction.removeAllViews()

        compactExpand()
        scheduleAutoCollapse(3000)
    }

    private fun showPhoneCall(call: IslandState.PhoneCall) {
        if (config.enableHaptic) context.hapticFeedback()
        pillText.text = "📞"
        pillIndicator.visibility = View.VISIBLE
        (pillIndicator.background as? GradientDrawable)?.setColor(Color.parseColor("#4CAF50"))

        expandedTitle.text = call.callerName
        expandedSubtitle.text = if (call.isActive) "On call · ${call.duration.formatTimer()}" else "Incoming call..."
        expandedIcon.setImageResource(android.R.drawable.ic_menu_call)
        mediaProgressBar.visibility = View.GONE
        expandedAction.removeAllViews()

        expand()
    }

    private fun addMediaButton(label: String, onClick: () -> Unit) {
        val btn = TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(context.dpToPx(48), context.dpToPx(32)).apply {
                marginStart = context.dpToPx(8)
                marginEnd = context.dpToPx(8)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = context.dpToPx(16).toFloat()
            }
            setOnClickListener {
                if (config.enableHaptic) context.hapticFeedback(10)
                onClick()
            }
        }
        expandedAction.addView(btn)
    }

    // ==================== ANIMATIONS ====================

    fun expand() {
        if (isExpanded || isAnimating) return
        isAnimating = true

        val targetW = context.dpToPx(config.expandedWidthDp)
        val targetH = context.dpToPx(config.expandedHeightDp)

        val widthAnim = ValueAnimator.ofInt(width, targetW).apply {
            addUpdateListener { val p = layoutParams; p.width = it.animatedValue as Int; layoutParams = p }
        }
        val heightAnim = ValueAnimator.ofInt(height, targetH).apply {
            addUpdateListener { val p = layoutParams; p.height = it.animatedValue as Int; layoutParams = p }
        }

        AnimatorSet().apply {
            playTogether(widthAnim, heightAnim)
            duration = config.animationDurationMs
            interpolator = OvershootInterpolator(0.8f)
            doOnEnd {
                isExpanded = true
                isAnimating = false
                pillContainer.visibility = View.GONE
                expandedContainer.visibility = View.VISIBLE
                expandedContainer.alpha = 0f
                expandedContainer.animate().alpha(1f).setDuration(150).start()
            }
        }.start()

        if (config.enableHaptic) context.hapticFeedback()
    }

    fun compactExpand() {
        if (isAnimating) return
        isAnimating = true

        val targetW = context.dpToPx(config.expandedWidthDp)
        val targetH = context.dpToPx(config.compactExpandedHeightDp)

        val widthAnim = ValueAnimator.ofInt(width, targetW).apply {
            addUpdateListener { val p = layoutParams; p.width = it.animatedValue as Int; layoutParams = p }
        }
        val heightAnim = ValueAnimator.ofInt(height, targetH).apply {
            addUpdateListener { val p = layoutParams; p.height = it.animatedValue as Int; layoutParams = p }
        }

        AnimatorSet().apply {
            playTogether(widthAnim, heightAnim)
            duration = config.animationDurationMs
            interpolator = OvershootInterpolator(0.6f)
            doOnEnd { isExpanded = true; isAnimating = false }
        }.start()
    }

    fun collapse() {
        if (!isExpanded || isAnimating) return
        isAnimating = true
        cancelAutoCollapse()

        expandedContainer.animate().alpha(0f).setDuration(100).withEndAction {
            expandedContainer.visibility = View.GONE
            pillContainer.visibility = View.VISIBLE
        }.start()

        val targetW = context.dpToPx(config.pillWidthDp)
        val targetH = context.dpToPx(config.pillHeightDp)

        val widthAnim = ValueAnimator.ofInt(width, targetW).apply {
            addUpdateListener { val p = layoutParams; p.width = it.animatedValue as Int; layoutParams = p }
        }
        val heightAnim = ValueAnimator.ofInt(height, targetH).apply {
            addUpdateListener { val p = layoutParams; p.height = it.animatedValue as Int; layoutParams = p }
        }

        AnimatorSet().apply {
            playTogether(widthAnim, heightAnim)
            duration = config.animationDurationMs
            interpolator = DecelerateInterpolator()
            doOnEnd { isExpanded = false; isAnimating = false }
        }.start()
    }

    private fun scheduleAutoCollapse(delayMs: Long) {
        cancelAutoCollapse()
        autoCollapseRunnable = Runnable { collapse() }
        handler.postDelayed(autoCollapseRunnable!!, delayMs)
    }

    private fun cancelAutoCollapse() {
        autoCollapseRunnable?.let { handler.removeCallbacks(it) }
        autoCollapseRunnable = null
    }

    fun cleanup() {
        cancelAutoCollapse()
        handler.removeCallbacksAndMessages(null)
    }
}
