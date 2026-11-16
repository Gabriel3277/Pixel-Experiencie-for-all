import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
B = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen"

HINT = r'''@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Altura aproximada (fração da tela) do leitor/botão de energia na lateral direita. Ajuste conforme o aparelho. */
private const val HINT_Y = 0.30f

/** Rotação da seta (MaterialShapes.Arrow nasce apontando para cima): 90° = aponta para a direita. */
private const val ARROW_ROTATION = 90f

/**
 * Indicador só visual da posição do leitor lateral. Não lê biometria nem toca na autenticação do Android.
 * Não consome toques; a pulsação só roda com a Activity em RESUMED (nada anima com a tela apagada).
 */
@Composable
fun FingerprintHint(visible: Boolean, color: Color, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val shape = remember { MaterialShapes.Arrow.toShape() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = maxHeight * HINT_Y),
            enter = fadeIn(tween(600, delayMillis = 500)) + slideInHorizontally(tween(600, delayMillis = 500)) { it },
            exit = fadeOut(tween(150)),
        ) {
            val dx = if (resumed) {
                rememberInfiniteTransition(label = "fp").animateFloat(
                    initialValue = 0f, targetValue = 4f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "fpDx",
                ).value
            } else 0f
            androidx.compose.foundation.layout.Box(
                Modifier.padding(end = 4.dp).offset(x = dx.dp).size(20.dp).rotate(ARROW_ROTATION).background(color, shape)
            )
        }
    }
}
'''
open(B + "/ui/components/FingerprintHint.kt", "w", encoding="utf-8").write(HINT)

p = B + "/ui/LockScreenContent.kt"
s = open(p, encoding="utf-8").read()
if "FingerprintHint(" not in s:
    a = "import com.pixelexperience.forall.lockscreen.ui.components.MediaPlayerCard\n"
    b = "                    ShortcutSlot(settings.rightShortcut, torchOn, scheme, settings, onShortcut)\n                }\n            }\n        }\n    }\n}\n"
    assert s.count(a) == 1, "import âncora"
    assert s.count(b) == 1, "fim do conteúdo não encontrado"
    s = s.replace(a, a + "import com.pixelexperience.forall.lockscreen.ui.components.FingerprintHint\n")
    s = s.replace(b, b.replace("        }\n    }\n}\n", "        }\n        // indicador visual do leitor lateral: some ao desbloquear (unlocking/unlockSignal) e não aparece no preview\n"
                                "        if (!preview) FingerprintHint(visible = !unlocking, color = scheme.secondaryContainer)\n    }\n}\n"))
    open(p, "w", encoding="utf-8").write(s)
print("OK")
