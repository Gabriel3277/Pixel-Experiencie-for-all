import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
B = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen"
MANIFEST = R + "/lockscreen/src/main/AndroidManifest.xml"


def patch(path, edits):
    s = open(path, encoding="utf-8").read()
    for marker, old, new in edits:
        if marker in s:
            continue
        assert s.count(old) == 1, f"{os.path.basename(path)}: trecho ausente ou duplicado: {old[:70]!r}"
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)


# ---------- 1) permissão do leitor (normal, concedida na instalação) ----------
patch(MANIFEST, [
    ("USE_FINGERPRINT",
     '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n',
     '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n'
     '    <uses-permission android:name="android.permission.USE_FINGERPRINT" />\n'),
])

# ---------- 2) FingerprintHint completo: posição da marcação + animações de leitura/erro ----------
HP = B + "/ui/components/FingerprintHint.kt"
assert "fun FingerprintHint" in open(HP, encoding="utf-8").read(), "FingerprintHint.kt não encontrado"
open(HP, "w", encoding="utf-8").write(r'''@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Altura (fração da tela) do leitor/botão de energia na lateral direita, conforme a marcação no print. */
private const val HINT_Y = 0.43f

/** MaterialShapes.Arrow nasce apontando para cima: 90° = aponta para a direita. */
private const val ARROW_ROTATION = 90f

/**
 * Indicador da posição do leitor lateral. Não consome toques. A pulsação contínua só roda com a Activity em RESUMED.
 * readTick: pulso quando o sistema informa leitura parcial; errorTick: balanço quando o sistema informa falha.
 */
@Composable
fun FingerprintHint(
    visible: Boolean, color: Color,
    readTick: Int = 0, errorTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    // toShape() é @Composable (já faz remember por dentro), então não pode ficar dentro de remember { }
    val shape = MaterialShapes.Arrow.toShape()

    val pulse = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(readTick) {
        if (readTick > 0) {
            pulse.animateTo(1.3f, tween(90))
            pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
        }
    }
    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            for (v in floatArrayOf(-6f, 6f, -4f, 4f, 0f)) shake.animateTo(v, tween(50))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = maxHeight * HINT_Y),
            enter = fadeIn(tween(600, delayMillis = 500)) + slideInHorizontally(tween(600, delayMillis = 500)) { it },
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.6f),
        ) {
            val dx = if (resumed) {
                rememberInfiniteTransition(label = "fp").animateFloat(
                    initialValue = 0f, targetValue = 4f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "fpDx",
                ).value
            } else 0f
            Box(
                Modifier.padding(end = 4.dp).offset(x = (dx + shake.value).dp).size(24.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                        rotationZ = ARROW_ROTATION
                    }
                    .background(color, shape)
            )
        }
    }
}
''')

# ---------- 3) LockSettings: fingerprintAssist ----------
patch(B + "/data/LockSettings.kt", [
    ("val fingerprintAssist", "    val showBatteryPercent: Boolean = true,\n",
     "    val showBatteryPercent: Boolean = true,\n    val fingerprintAssist: Boolean = true,\n"),
    ("K.fpAssist", '    val batPct = booleanPreferencesKey("show_battery_pct")\n',
     '    val batPct = booleanPreferencesKey("show_battery_pct")\n    val fpAssist = booleanPreferencesKey("fp_assist")\n'),
    ("this[K.fpAssist] ?:", "        showBatteryPercent = this[K.batPct] ?: d.showBatteryPercent,\n",
     "        showBatteryPercent = this[K.batPct] ?: d.showBatteryPercent,\n        fingerprintAssist = this[K.fpAssist] ?: d.fingerprintAssist,\n"),
    ("this[K.fpAssist] = ", "    this[K.batPct] = s.showBatteryPercent\n",
     "    this[K.batPct] = s.showBatteryPercent\n    this[K.fpAssist] = s.fingerprintAssist\n"),
])

# ---------- 4) LockScreenContent: repassa os eventos ao indicador ----------
patch(B + "/ui/LockScreenContent.kt", [
    ("fpErrorTick: Int = 0", "    unlockSignal: Boolean = false,\n",
     "    unlockSignal: Boolean = false,\n    fpReadTick: Int = 0,\n    fpErrorTick: Int = 0,\n"),
    ("readTick = fpReadTick",
     "FingerprintHint(visible = !unlocking, color = scheme.secondaryContainer)",
     "FingerprintHint(visible = !unlocking, color = scheme.secondaryContainer, readTick = fpReadTick, errorTick = fpErrorTick)"),
])

# ---------- 5) Activity: FingerprintManager (API oficial) ----------
AP = B + "/ui/LockScreenActivity.kt"
src = open(AP, encoding="utf-8").read()
if "startFingerprint" not in src:
    assert "override fun onPause" not in src, "Activity já tem onPause: ajuste manual"

FP = '''    // ---- leitor de digital (API oficial FingerprintManager; o reconhecimento é do sistema) ----
    private val fpRead = mutableStateOf(0)
    private val fpError = mutableStateOf(0)
    private var fpCancel: CancellationSignal? = null

    @Suppress("DEPRECATION")
    private fun startFingerprint() {
        if (fpCancel != null || isFinishing) return
        if (!LockSettingsRepository.get(this).settings.value.fingerprintAssist) return
        val fm = runCatching { getSystemService(FingerprintManager::class.java) }.getOrNull() ?: return
        val usable = runCatching { fm.isHardwareDetected && fm.hasEnrolledFingerprints() }.getOrDefault(false)
        if (!usable) return
        val cancel = CancellationSignal()
        fpCancel = cancel
        runCatching {
            fm.authenticate(null, cancel, 0, object : FingerprintManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                    fpCancel = null
                    if (aod.value) applyAod(false)
                    // quem libera o aparelho é o keyguard do sistema: pedimos a liberação e animamos quando ele confirmar
                    unlock(onSuccess = { unlockSignal.value = true }, onFail = { fpError.value++ })
                }

                override fun onAuthenticationFailed() {
                    fpError.value++
                    window.decorView.performHapticFeedback(
                        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONTEXT_CLICK
                    )
                }

                override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) { fpRead.value++ }

                // bloqueio por tentativas, cancelamento por outro cliente etc.: para de ouvir, sem laço de reinício
                override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) { fpCancel = null }
            }, handler)
        }.onFailure { fpCancel = null }
    }

    private fun stopFingerprint() {
        fpCancel?.cancel()
        fpCancel = null
    }

    override fun onPause() {
        stopFingerprint()
        super.onPause()
    }

'''
patch(AP, [
    ("import android.hardware.fingerprint.FingerprintManager", "import android.os.SystemClock\n",
     "import android.os.SystemClock\nimport android.hardware.fingerprint.FingerprintManager\nimport android.os.CancellationSignal\nimport android.view.HapticFeedbackConstants\n"),
    ("private val fpRead", "    private var unlockPending = false\n", FP + "    private var unlockPending = false\n"),
    ("        startFingerprint()\n    }", "        LockScreenModule.ensureNotificationListener(this)\n    }\n",
     "        LockScreenModule.ensureNotificationListener(this)\n        startFingerprint()\n    }\n"),
    ("stopFingerprint()\n        val km", "        unlockPending = true\n        val km = getSystemService(KeyguardManager::class.java)\n",
     "        unlockPending = true\n        stopFingerprint() // o keyguard precisa do sensor para a própria confirmação\n        val km = getSystemService(KeyguardManager::class.java)\n"),
    ("onDismissCancelled() { unlockPending = false; startFingerprint()",
     "                override fun onDismissCancelled() { unlockPending = false; onFail() }\n                override fun onDismissError() { unlockPending = false; onFail() }\n",
     "                override fun onDismissCancelled() { unlockPending = false; startFingerprint(); onFail() }\n                override fun onDismissError() { unlockPending = false; startFingerprint(); onFail() }\n"),
    ("if (!started) { unlockPending = false; startFingerprint()",
     "        if (!started) { unlockPending = false; onFail() }\n",
     "        if (!started) { unlockPending = false; startFingerprint(); onFail() }\n"),
    ("fpReadTick = fpRead.value", "                    unlockSignal = unlockSignal.value,\n",
     "                    unlockSignal = unlockSignal.value,\n                    fpReadTick = fpRead.value,\n                    fpErrorTick = fpError.value,\n"),
])

# ---------- 6) Tela de configurações: página "Leitor de digital" ----------
SP = B + "/ui/settings/LockSettingsScreen.kt"
patch(SP, [
    ("icons.filled.Fingerprint", "import androidx.compose.material.icons.filled.Bedtime\n",
     "import androidx.compose.material.icons.filled.Bedtime\nimport androidx.compose.material.icons.filled.Fingerprint\n"),
    ('"Leitor de digital", if',
     '                            Cat(Icons.Filled.Bedtime, P_BLUE, "Always-On", if (s.aodEnabled) "Ativado" else "Desativado", "Always-On"),\n',
     '                            Cat(Icons.Filled.Bedtime, P_BLUE, "Always-On", if (s.aodEnabled) "Ativado" else "Desativado", "Always-On"),\n'
     '                            Cat(Icons.Filled.Fingerprint, P_GREEN, "Leitor de digital", if (s.fingerprintAssist) "Ativado" else "Desativado", "Leitor de digital"),\n'),
    ('"Leitor de digital" -> {', '                "Always-On" -> {\n',
     '                "Leitor de digital" -> {\n'
     '                Section("Leitor de digital") {\n'
     '                    SwitchRow("Reagir ao leitor lateral", s.fingerprintAssist) { v -> set { it.copy(fingerprintAssist = v) } }\n'
     '                    Text(\n'
     '                        "Ao tocar no leitor com a tela de bloqueio aberta, o app recebe o resultado do Android, anima e pede o desbloqueio. '
     'Quem confere a digital é o sistema; o app não guarda nem reconhece digitais. Em alguns aparelhos o Android ainda pede PIN ou um novo toque na tela de confirmação.",\n'
     '                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,\n'
     '                    )\n'
     '                }\n'
     '                }\n'
     '                "Always-On" -> {\n'),
])
print("OK")
