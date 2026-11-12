package com.pixelexperience.forall.controlcenter

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pixelexperience.forall.controlcenter.ui.ControlCenterPanel
import com.pixelexperience.forall.controlcenter.ui.PANEL_CONTENT_DP
import com.pixelexperience.forall.themes.PixelTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Janela do painel.
 *
 * Ideia (estudada no PanOSP, reescrita do zero):
 * 1. Uma janela transparente e pequena fica numa faixa do lado direito da tela
 *    (tipo TYPE_ACCESSIBILITY_OVERLAY: não precisa da permissão "sobrepor a outros apps").
 * 2. No primeiro toque ela cresce até a tela toda, fecha o painel do sistema e
 *    segue o dedo na MESMA janela, para o gesto não se perder.
 * 3. Ao soltar, o painel abre ou fecha conforme a posição e a velocidade.
 */
internal class ShadeWindow(
    private val service: AccessibilityService,
    private val showVolume: () -> Unit = {},
) : PanelActions {

    private enum class Phase { IDLE, OPENING, OPEN, CLOSING, SETTLING }

    private val windowManager: WindowManager = service.getSystemService(WindowManager::class.java)
    private val keyguard: KeyguardManager? = service.getSystemService(KeyguardManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val flingPx = 700f * density

    private val owner = OverlayOwner()
    private val panelState = PanelState(service)

    private var root: ShadeRoot? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: ComposeView? = null
    private var anim: ValueAnimator? = null
    private var tracker: VelocityTracker? = null
    private var receiverRegistered = false

    private var phase = Phase.IDLE
    private var progress = 0f
    private var statusBarPx = 0
    private var panelHeightPx = 0
    private var zoneW = 0
    private var zoneH = 0
    private var zoneTop = 0
    private var startY = 0f
    private var startX = 0f
    private var moved = false
    private var downOnScrim = false
    private var lastDismissAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTouchable()
        }
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    fun start() {
        if (root != null) return

        computeGeometry()

        val view = ShadeRoot(service)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val lp = WindowManager.LayoutParams(
            zoneW,
            zoneH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = zoneTop
            title = "PixelControlCenterShade"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            windowManager.addView(view, lp)
            root = view
            params = lp
            owner.resume()
            registerScreenReceiver()
            updateTouchable()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível criar a janela do painel", e)
        }
    }

    fun stop() {
        anim?.cancel()
        anim = null
        releaseTracker()
        unregisterScreenReceiver()
        root?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        root = null
        params = null
        panel = null
        phase = Phase.IDLE
        owner.destroy()
    }

    /** Chamado pelo serviço quando alguma janela muda. */
    fun onWindowStateChanged(packageName: String?) {
        updateTouchable()
        if (phase == Phase.IDLE) return
        when {
            packageName == null || packageName == service.packageName -> Unit
            packageName == SYSTEM_UI -> dismissSystemShade()
            phase == Phase.OPEN -> collapse() // o usuário foi para outro app / Início
        }
    }

    /** A tela girou ou mudou de tamanho: recalcula a faixa e o painel. */
    fun onConfigurationChanged() {
        computeGeometry()
        if (phase == Phase.IDLE) applyGeometry(expanded = false)
    }

    // ------------------------------------------------------------------
    // Toque
    // ------------------------------------------------------------------

    private inner class ShadeRoot(context: Context) : FrameLayout(context) {
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 136, 136, 136)
        }
        private val handleRect = RectF()

        init {
            setWillNotDraw(false)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = interceptTouch(ev)

        override fun onTouchEvent(ev: MotionEvent): Boolean = handleTouch(ev)

        /** Marquinha cinza que mostra onde deslizar (só quando o painel está fechado). */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (phase != Phase.IDLE) return
            if (keyguard?.isKeyguardLocked == true) return
            val w = 4f * density
            val h = 56f * density
            val right = width - 10f * density
            val top = (height - h) / 2f
            handleRect.set(right - w, top, right, top + h)
            canvas.drawRoundRect(handleRect, w / 2f, w / 2f, handlePaint)
        }
    }

    private fun interceptTouch(ev: MotionEvent): Boolean {
        if (phase != Phase.OPEN) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                moved = false
                downOnScrim = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = startY - ev.rawY
                if (dy > touchSlop * 2 && dy > abs(startX - ev.rawX)) {
                    beginClose(ev)
                    return true
                }
            }
        }
        return false
    }

    private fun handleTouch(ev: MotionEvent): Boolean = when (phase) {
        Phase.IDLE -> touchIdle(ev)
        Phase.OPENING -> touchDrag(ev, opening = true)
        Phase.OPEN -> touchOpen(ev)
        Phase.CLOSING -> touchDrag(ev, opening = false)
        Phase.SETTLING -> true
    }

    private fun touchIdle(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) return false
        if (keyguard?.isKeyguardLocked == true) return false

        startY = ev.rawY
        startX = ev.rawX
        moved = false
        phase = Phase.OPENING
        tracker = VelocityTracker.obtain().also { it.addMovement(ev) }

        applyGeometry(expanded = true)
        dismissSystemShade()
        attachPanel()
        applyProgress(0f)
        return true
    }

    private fun touchDrag(ev: MotionEvent, opening: Boolean): Boolean {
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - startY
                if (abs(dy) > touchSlop) moved = true
                val p = if (opening) dy * DRAG_GAIN / panelHeightPx else 1f + dy * DRAG_GAIN / panelHeightPx
                applyProgress(p.coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP -> {
                val vy = releaseVelocity()
                val threshold = if (opening) 0.3f else 0.7f
                when {
                    opening && !moved -> settle(false) // toque simples na barra
                    vy > flingPx -> settle(true)
                    vy < -flingPx -> settle(false)
                    else -> settle(progress > threshold)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseTracker()
                settle(progress > (if (opening) 0.3f else 0.7f))
            }
        }
        return true
    }

    private fun touchOpen(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                moved = false
                downOnScrim = ev.y > panelHeightPx
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawY - startY) > touchSlop) moved = true
                if (startY - ev.rawY > touchSlop * 2) beginClose(ev)
            }
            MotionEvent.ACTION_UP -> {
                if (downOnScrim && !moved) settle(false) // toque fora do painel
            }
        }
        return true
    }

    private fun beginClose(ev: MotionEvent) {
        phase = Phase.CLOSING
        releaseTracker()
        tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    private fun releaseVelocity(): Float {
        val t = tracker ?: return 0f
        t.computeCurrentVelocity(1000)
        val v = t.yVelocity
        releaseTracker()
        return v
    }

    private fun releaseTracker() {
        tracker?.recycle()
        tracker = null
    }

    // ------------------------------------------------------------------
    // Painel
    // ------------------------------------------------------------------

    private fun attachPanel() {
        val host = root ?: return
        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PixelTheme(dynamicColor = true) {
                    val topInset = with(LocalDensity.current) { statusBarPx.toDp() }
                    ControlCenterPanel(
                        state = panelState,
                        topInset = topInset,
                        actions = this@ShadeWindow,
                    )
                }
            }
        }
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                panelHeightPx,
                Gravity.TOP,
            ),
        )
        panel = view
    }

    private fun applyProgress(p: Float) {
        progress = p
        panel?.translationY = -panelHeightPx * (1f - p)
        root?.setBackgroundColor(Color.argb((90f * p).toInt(), 0, 0, 0))
    }

    private fun settle(open: Boolean) {
        releaseTracker()
        if (panel == null) {
            detach()
            return
        }
        anim?.cancel()
        phase = Phase.SETTLING
        anim = ValueAnimator.ofFloat(progress, if (open) 1f else 0f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (canceled) return
                    anim = null
                    if (open) phase = Phase.OPEN else detach()
                }
            })
            start()
        }
    }

    private fun detach() {
        anim?.cancel()
        anim = null
        releaseTracker()
        root?.removeAllViews()
        root?.setBackgroundColor(Color.TRANSPARENT)
        panel = null
        progress = 0f
        phase = Phase.IDLE
        applyGeometry(expanded = false)
        root?.invalidate()
    }

    private fun computeGeometry() {
        val dm = service.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        statusBarPx = readStatusBarHeight()
        panelHeightPx = statusBarPx + (PANEL_CONTENT_DP * density).roundToInt()
        zoneW = max((min(w, h) * ZONE_WIDTH_FRACTION).roundToInt(), (56 * density).roundToInt())
        zoneH = (h * ZONE_HEIGHT_FRACTION).roundToInt()
        zoneTop = (h * ZONE_TOP_FRACTION).roundToInt()
    }

    /** expanded = tela toda (painel aberto/arrastando); senão, só a faixa do gesto. */
    private fun applyGeometry(expanded: Boolean) {
        val lp = params ?: return
        val view = root ?: return
        if (expanded) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT
            lp.height = WindowManager.LayoutParams.MATCH_PARENT
            lp.gravity = Gravity.TOP
            lp.x = 0
            lp.y = 0
        } else {
            lp.width = zoneW
            lp.height = zoneH
            lp.gravity = Gravity.TOP or Gravity.END
            lp.x = 0
            lp.y = zoneTop
        }
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ajustar a janela", e)
        }
    }

    // ------------------------------------------------------------------
    // Tela bloqueada: a janela deixa o toque passar
    // ------------------------------------------------------------------

    private fun updateTouchable() {
        val lp = params ?: return
        val view = root ?: return
        val locked = keyguard?.isKeyguardLocked == true
        val notTouchable = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (locked == notTouchable) return

        lp.flags = if (locked) {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) {
        }
        if (locked && phase != Phase.IDLE) detach()
        view.invalidate()
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            service,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        try {
            service.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    // ------------------------------------------------------------------
    // Painel do sistema
    // ------------------------------------------------------------------

    private fun dismissSystemShade() {
        val now = SystemClock.uptimeMillis()
        if (now - lastDismissAt < 200L) return
        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)) {
            lastDismissAt = now
        }
    }

    private fun readStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val h = if (id > 0) service.resources.getDimensionPixelSize(id) else 0
        return if (h > 0) h else (24 * density).roundToInt()
    }

    // ------------------------------------------------------------------
    // PanelActions
    // ------------------------------------------------------------------

    override fun openInternet() = launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))

    override fun openBluetooth() = launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    /** Fecha a central e abre o nosso painel de volume (já expandido). */
    override fun openVolume() {
        collapse()
        root?.postDelayed({ showVolume() }, 240L)
    }

    override fun openSettings() = launch(Intent(Settings.ACTION_SETTINGS))

    override fun openAirplane() = launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    override fun openAlarms() = launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    override fun showPowerMenu() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        collapse()
    }

    override fun requestWriteSettings() = launch(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${service.packageName}")),
    )

    override fun collapse() {
        if (phase == Phase.IDLE) return
        settle(false)
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir ${intent.action}", e)
        }
        collapse()
    }

    private companion object {
        const val TAG = "PixelControlCenter"
        const val SYSTEM_UI = "com.android.systemui"

        // Faixa do gesto (marcada pelo usuário no print): lado direito, altura do meio-alto.
        const val ZONE_WIDTH_FRACTION = 0.18f   // da menor dimensão da tela
        const val ZONE_TOP_FRACTION = 0.105f    // da altura da tela
        const val ZONE_HEIGHT_FRACTION = 0.235f // da altura da tela

        // Quanto o painel acompanha o dedo (>1 = abre com arrasto mais curto).
        const val DRAG_GAIN = 1.5f
    }
}
