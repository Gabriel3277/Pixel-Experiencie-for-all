import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
B = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen"


def patch(path, edits):
    s = open(path, encoding="utf-8").read()
    for marker, old, new in edits:
        if marker in s:
            continue
        assert s.count(old) == 1, f"{path}: trecho ausente ou duplicado: {old[:60]!r}"
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)


# Service: SCREEN_OFF não ativa mais o AOD
patch(B + "/service/LockScreenService.kt", [
    ("showLock(false)",
     "                // o SCREEN_OFF que encerra um AOD não abre outro (senão a tela nunca apagaria)\n"
     "                val aod = LockSettingsRepository.get(context).settings.value.aodEnabled && !AodState.screenIsAod\n"
     "                showLock(aod)\n",
     "                showLock(false) // o AOD agora só entra por 17 s sem toque ou toque duplo (LockScreenActivity)\n"),
])

NEW = '''    private val idle = Runnable {
        val pm = getSystemService(PowerManager::class.java)
        if (!aod.value && pm.isInteractive && LockSettingsRepository.get(this).settings.value.aodEnabled) applyAod(true)
    }
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = armIdle()
    }
    private var modeAt = 0L
    private val doubleTap by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // ignora o 2º toque de quem acabou de sair do AOD
                if (!aod.value && SystemClock.uptimeMillis() - modeAt > 800 &&
                    LockSettingsRepository.get(this@LockScreenActivity).settings.value.aodEnabled
                ) applyAod(true)
                return false
            }
        })
    }

    private fun armIdle() {
        handler.removeCallbacks(idle)
        if (!aod.value) handler.postDelayed(idle, IDLE_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        doubleTap.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) armIdle()
        return super.dispatchTouchEvent(ev)
    }

'''
patch(B + "/ui/LockScreenActivity.kt", [
    ("import android.view.GestureDetector",
     "import android.os.Looper\n",
     "import android.os.Looper\nimport android.os.PowerManager\nimport android.os.SystemClock\nimport android.view.GestureDetector\nimport android.view.MotionEvent\n"),
    ("private val idle = Runnable",
     "    private val endAod = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }\n",
     "    private val endAod = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }\n" + NEW),
    ("handler.removeCallbacks(idle)\n        modeAt",
     "        handler.removeCallbacks(endAod)\n        aod.value = on\n",
     "        handler.removeCallbacks(endAod)\n        handler.removeCallbacks(idle)\n        modeAt = SystemClock.uptimeMillis()\n        aod.value = on\n"),
    ("            setTurnScreenOn(false)\n            armIdle()",
     "            setTurnScreenOn(false)\n        }\n    }\n",
     "            setTurnScreenOn(false)\n            armIdle()\n        }\n    }\n"),
    ("IDLE_MS = 17_000L",
     "        private const val AOD_WINDOW_MS = 20_000L\n",
     "        private const val AOD_WINDOW_MS = 20_000L\n        private const val IDLE_MS = 17_000L\n"),
    ("registerReceiver(\n            this, screenOn",
     "        val repo = LockSettingsRepository.get(this)\n",
     "        ContextCompat.registerReceiver(\n            this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED\n        )\n        val repo = LockSettingsRepository.get(this)\n"),
    ("unregisterReceiver(screenOn)",
     "        handler.removeCallbacks(endAod)\n        runCatching { unregisterReceiver(userPresent) }\n",
     "        handler.removeCallbacks(endAod)\n        handler.removeCallbacks(idle)\n        runCatching { unregisterReceiver(screenOn) }\n        runCatching { unregisterReceiver(userPresent) }\n"),
])
print("OK")
