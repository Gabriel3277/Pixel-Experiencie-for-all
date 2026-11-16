#!/usr/bin/env python3
"""PIN feedback (escurecer ao aguardar, tremida+haptic no erro, fade no sucesso).
Uso (na raiz do repo): python3 pin_feedback.py
Idempotente: pode rodar de novo sem duplicar. Aborta sem gravar nada se algum assert falhar."""
import re, subprocess, sys

def find(name):
    out = subprocess.run(["find", "lockscreen/src", "-name", name], capture_output=True, text=True).stdout.split()
    assert len(out) == 1, f"{name}: esperado 1 arquivo, achei {out}"
    return out[0]

def edit(text, label, old, new, marker):
    if marker in text:
        print(f"= {label}: já aplicado")
        return text
    assert text.count(old) == 1, f"{label}: âncora não encontrada/ambígua ({text.count(old)})"
    print(f"+ {label}")
    return text.replace(old, new)

# ---------------- LockScreenContent.kt ----------------
pc = find("LockScreenContent.kt")
c = open(pc, encoding="utf-8").read()

c = edit(c, "imports spring",
  "import androidx.compose.animation.core.RepeatMode\n",
  "import androidx.compose.animation.core.RepeatMode\nimport androidx.compose.animation.core.Spring\nimport androidx.compose.animation.core.spring\n",
  "import androidx.compose.animation.core.Spring\n")

c = edit(c, "layerFx assinatura",
  "unlockDist: () -> Float): Modifier =\n    graphicsLayer {",
  "unlockDist: () -> Float, shakeX: () -> Float = { 0f }): Modifier =\n    graphicsLayer {",
  "shakeX: () -> Float = { 0f }")

c = edit(c, "layerFx translationX",
  "        translationY = (1f - e) * 36.dp.toPx() + d * parallax\n",
  "        translationY = (1f - e) * 36.dp.toPx() + d * parallax\n        translationX = shakeX().dp.toPx()\n",
  "translationX = shakeX()")

if "distFn, shakeFn)" in c:
    print("= layerFx chamadas: já aplicado")
else:
    lines = c.split("\n"); n = 0
    for i, l in enumerate(lines):
        if ".layerFx(" in l and "distFn)" in l and "private fun" not in l:
            lines[i] = l.replace("distFn)", "distFn, shakeFn)"); n += 1
    assert n == 7, f"layerFx chamadas: esperado 7, achei {n}"
    c = "\n".join(lines); print("+ layerFx chamadas (7)")

c = edit(c, "estado pinOpen/shake/scrim",
  "    var unlocking by remember { mutableStateOf(false) }\n",
  "    var unlocking by remember { mutableStateOf(false) }\n"
  "    // feedback do PIN nativo: escurece suavemente enquanto o sistema pede o PIN e treme de leve ao cancelar/erro\n"
  "    var pinOpen by remember { mutableStateOf(false) }\n"
  "    val shake = remember { Animatable(0f) }\n"
  "    val pinScrim = animateFloatAsState(\n"
  "        if (pinOpen) 0.32f else 0f,\n"
  "        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),\n"
  "        label = \"pinScrim\",\n"
  "    )\n",
  "var pinOpen by remember")

c = edit(c, "rejectFeedback",
  "    suspend fun settle(vy: Float) {\n",
  "    fun rejectFeedback() {\n"
  "        view.performHapticFeedback(\n"
  "            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT\n"
  "            else android.view.HapticFeedbackConstants.CONTEXT_CLICK\n"
  "        )\n"
  "        scope.launch {\n"
  "            val snap = spring<Float>(stiffness = Spring.StiffnessHigh)\n"
  "            shake.snapTo(0f)\n"
  "            shake.animateTo(-14f, snap)\n"
  "            shake.animateTo(10f, snap)\n"
  "            shake.animateTo(-5f, snap)\n"
  "            shake.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))\n"
  "        }\n"
  "    }\n\n"
  "    suspend fun settle(vy: Float) {\n",
  "fun rejectFeedback()")

c = edit(c, "settle -> pinOpen/reject",
  "            requestUnlock { ok ->\n                if (!ok) {\n                    unlocking = false\n                    scope.launch { drag.animateTo(0f, LockMotion.bouncy()) }\n                } else confirmHaptic()\n            }\n",
  "            pinOpen = true\n"
  "            requestUnlock { ok ->\n                if (!ok) {\n                    pinOpen = false\n                    unlocking = false\n                    rejectFeedback()\n"
  "                    scope.launch { drag.animateTo(0f, LockMotion.bouncy()) }\n                } else confirmHaptic()\n            }\n",
  "            pinOpen = true\n            requestUnlock")

c = edit(c, "shakeFn",
  "    val distFn = { unlockDist }\n",
  "    val distFn = { unlockDist }\n    val shakeFn = { shake.value }\n",
  "val shakeFn = ")

c = edit(c, "overlay scrim",
  "        // indicador visual do leitor lateral",
  "        // escurecimento suave enquanto o PIN do sistema está aberto (não consome toques)\n"
  "        if (!preview) Box(Modifier.fillMaxSize().graphicsLayer { alpha = pinScrim.value }.background(Color.Black))\n"
  "        // indicador visual do leitor lateral",
  "alpha = pinScrim.value")

# ---------------- LockScreenActivity.kt ----------------
pa = find("LockScreenActivity.kt")
a = open(pa, encoding="utf-8").read()

a = edit(a, "fade ao fechar",
  "overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)",
  "overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)",
  "OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out")

a = edit(a, "cb(true) no sucesso",
  "onRequestUnlock = { cb -> unlock(onFail = { cb(false) }) },",
  "onRequestUnlock = { cb -> unlock(onSuccess = { cb(true) }, onFail = { cb(false) }) },",
  "onSuccess = { cb(true) }")

# só grava depois de todos os asserts passarem
open(pc, "w", encoding="utf-8").write(c)
open(pa, "w", encoding="utf-8").write(a)
print("OK")
