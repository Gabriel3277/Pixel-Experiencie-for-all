package com.pixelexperience.forall.lockscreen.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Mostra o widget de relógio escolhido (AppWidgetHostView real, sem borda/cartão).
 * Se não houver widget escolhido (ou o app dele foi removido), mostra [fallback].
 *
 * interactive = false: toques não chegam ao widget (evita abrir apps pela lockscreen
 * e não atrapalha o gesto de deslizar).
 */
@Composable
fun HostedClockWidget(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    interactive: Boolean = false,
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { ClockWidgetController.get(context) }
    var widgetId by remember { mutableStateOf(ClockWidgetStore.widgetId(context)) }

    DisposableEffect(controller) {
        val unregister = ClockWidgetStore.register(context) {
            widgetId = ClockWidgetStore.widgetId(context)
        }
        controller.startListening()
        onDispose {
            controller.stopListening()
            unregister()
        }
    }

    val info = remember(widgetId) { controller.infoFor(widgetId) }

    if (info == null) {
        fallback()
        return
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = alignment) {
        val wDp = if (constraints.hasBoundedWidth) maxWidth.value.toInt() else 0
        val hDp = if (constraints.hasBoundedHeight) maxHeight.value.toInt() else 0

        key(widgetId) {
            AndroidView(
                factory = { ctx ->
                    WidgetFrame(ctx, interactive).also { frame ->
                        controller.createView(ctx, widgetId)?.let { view ->
                            frame.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ),
                            )
                        }
                    }
                },
                update = { frame ->
                    frame.interactive = interactive
                    val view = frame.getChildAt(0) as? AppWidgetHostView
                    if (view != null && wDp > 0 && hDp > 0) {
                        @Suppress("DEPRECATION")
                        view.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
                    }
                },
                modifier = Modifier.wrapContentSize(),
            )
        }
    }
}

private class WidgetFrame(context: Context, var interactive: Boolean) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !interactive
}
