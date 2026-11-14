package com.pixelexperience.forall.lockscreen.widget

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Editor de tamanho do relógio de widget (como o launcher): arraste as bolinhas das bordas.
 * O widget é redimensionado de verdade (o widget recebe o novo tamanho), sem esticar imagem.
 */
class ClockWidgetResizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = ClockWidgetController.get(this)
        val info = controller.selectedInfo()
        if (info == null) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        val savedW = ClockWidgetStore.widthDp(this)
        val savedH = ClockWidgetStore.heightDp(this)
        val startW = if (savedW > 0) savedW.toFloat() else (info.minWidth / density).coerceAtLeast(80f)
        val startH = if (savedH > 0) savedH.toFloat() else (info.minHeight / density).coerceAtLeast(80f)

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                val cfg = LocalConfiguration.current
                val maxW = (cfg.screenWidthDp - 48).toFloat()
                val maxH = cfg.screenHeightDp * 0.6f
                var w by remember { mutableFloatStateOf(startW.coerceIn(80f, maxW)) }
                var h by remember { mutableFloatStateOf(startH.coerceIn(80f, maxH)) }
                val accent = MaterialTheme.colorScheme.primary

                Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
                    Box(Modifier.align(Alignment.Center).size(w.dp, h.dp)) {
                        HostedClockWidget(
                            modifier = Modifier.fillMaxSize(),
                            alignment = Alignment.Center,
                            previewSize = DpSize(w.dp, h.dp),
                        )
                        Box(
                            Modifier.fillMaxSize()
                                .border(2.dp, accent, RoundedCornerShape(16.dp)),
                        )
                        Handle(Modifier.align(Alignment.CenterEnd).offset(x = 14.dp), accent) { dx, _ ->
                            w = (w + dx * 2).coerceIn(80f, maxW)
                        }
                        Handle(Modifier.align(Alignment.CenterStart).offset(x = (-14).dp), accent) { dx, _ ->
                            w = (w - dx * 2).coerceIn(80f, maxW)
                        }
                        Handle(Modifier.align(Alignment.BottomCenter).offset(y = 14.dp), accent) { _, dy ->
                            h = (h + dy * 2).coerceIn(80f, maxH)
                        }
                        Handle(Modifier.align(Alignment.TopCenter).offset(y = (-14).dp), accent) { _, dy ->
                            h = (h - dy * 2).coerceIn(80f, maxH)
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            ClockWidgetStore.setSize(this@ClockWidgetResizeActivity, 0, 0)
                            finish()
                        }) { Text("Automático") }
                        Button(onClick = {
                            ClockWidgetStore.setSize(this@ClockWidgetResizeActivity, w.roundToInt(), h.roundToInt())
                            finish()
                        }) { Text("Salvar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Handle(modifier: Modifier, color: Color, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current.density
    val cb by rememberUpdatedState(onDrag)
    Box(
        modifier
            .size(36.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    cb(drag.x / density, drag.y / density)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(color, CircleShape))
    }
}
