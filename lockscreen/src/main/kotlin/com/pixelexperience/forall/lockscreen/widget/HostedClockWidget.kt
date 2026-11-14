package com.pixelexperience.forall.lockscreen.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Mostra o widget de relógio escolhido. Tamanho: o salvo no editor (ClockWidgetResizeActivity)
 * ou automático. previewSize só é usado pelo editor.
 */
@Composable
fun HostedClockWidget(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter,
    interactive: Boolean = false,
    previewSize: DpSize? = null,
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val controller = remember { ClockWidgetController.get(context) }
    var widgetId by remember { mutableStateOf(ClockWidgetStore.widgetId(context)) }
    var savedW by remember { mutableStateOf(ClockWidgetStore.widthDp(context)) }
    var savedH by remember { mutableStateOf(ClockWidgetStore.heightDp(context)) }

    DisposableEffect(controller) {
        val unregister = ClockWidgetStore.register(context) {
            widgetId = ClockWidgetStore.widgetId(context)
            savedW = ClockWidgetStore.widthDp(context)
            savedH = ClockWidgetStore.heightDp(context)
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

    val fixed: DpSize? = previewSize ?: if (savedW > 0 && savedH > 0) DpSize(savedW.dp, savedH.dp) else null

    BoxWithConstraints(modifier = modifier, contentAlignment = alignment) {
        val wDp = fixed?.width?.value?.toInt() ?: if (constraints.hasBoundedWidth) maxWidth.value.toInt() else 0
        val hDp = fixed?.height?.value?.toInt() ?: if (constraints.hasBoundedHeight) maxHeight.value.toInt() else 0

        key(widgetId) {
            AndroidView(
                factory = { ctx ->
                    WidgetFrame(ctx, interactive).also { frame ->
                        controller.createView(ctx, widgetId)?.let { view ->
                            frame.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
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
                modifier = if (fixed != null) Modifier.size(fixed.width, fixed.height) else Modifier.wrapContentSize(),
            )
        }
    }
}

private class WidgetFrame(context: Context, var interactive: Boolean) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !interactive
}
