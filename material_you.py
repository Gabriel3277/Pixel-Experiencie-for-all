#!/usr/bin/env python3
"""Etapa 1 - Material You (claro/escuro) + tons coerentes. Idempotente, com assert.
Rodar em ~/PixelExperienceForAll:  python3 material_you.py
"""
import os, sys

B = "lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen/"
if not os.path.isdir(B):
    sys.exit("Rode dentro de ~/PixelExperienceForAll")


def edit(rel, steps):
    p = B + rel
    s = open(p, encoding="utf-8").read()
    for name, old, new, done in steps:
        if done in s:
            print(f"  [ja aplicado] {name}")
            continue
        assert s.count(old) == 1, f"{rel}: '{name}' nao encontrado (arquivo mudou, mande o atual)"
        s = s.replace(old, new)
        print(f"  [ok] {name}")
    open(p, "w", encoding="utf-8").write(s)


# ---------------------------------------------------------------- LockTheme.kt
LIGHT = '''    if (!dark) {
        return lightColorScheme(
            primary = t(s, .40f), onPrimary = t(s, 1f),
            primaryContainer = t(s, .90f), onPrimaryContainer = t(s, .10f),
            secondary = t(v, .40f), onSecondary = t(v, 1f),
            secondaryContainer = t(v, .90f), onSecondaryContainer = t(v, .12f),
            tertiary = t(a, .40f, 60f), onTertiary = t(a, 1f, 60f),
            tertiaryContainer = t(a, .90f, 60f), onTertiaryContainer = t(a, .12f, 60f),
            background = t(n, .98f), onBackground = t(n, .10f),
            surface = t(n, .98f), onSurface = t(n, .10f),
            surfaceVariant = t(v, .90f), onSurfaceVariant = t(v, .30f),
            outline = t(v, .50f), outlineVariant = t(v, .80f),
            surfaceContainerLowest = t(n, 1f), surfaceContainerLow = t(n, .96f),
            surfaceContainer = t(n, .94f), surfaceContainerHigh = t(n, .92f),
            surfaceContainerHighest = t(n, .90f),
        )
    }
'''
print("LockTheme.kt")
edit("ui/theme/LockTheme.kt", [
    ("import lightColorScheme",
     "import androidx.compose.material3.darkColorScheme\n",
     "import androidx.compose.material3.darkColorScheme\nimport androidx.compose.material3.lightColorScheme\n",
     "import androidx.compose.material3.lightColorScheme"),
    ("assinatura dark",
     "fun lockColorScheme(seed: Color): ColorScheme {",
     "fun lockColorScheme(seed: Color, dark: Boolean = true): ColorScheme {",
     "fun lockColorScheme(seed: Color, dark: Boolean = true)"),
    ("bloco claro",
     "    return darkColorScheme(\n        primary = t(s, .80f)",
     LIGHT + "    return darkColorScheme(\n        primary = t(s, .80f)",
     "if (!dark) {"),
])

# ---------------------------------------------------------- LockScreenContent.kt
print("LockScreenContent.kt")
edit("ui/LockScreenContent.kt", [
    ("imports",
     "import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme\n",
     "import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme\n"
     "import androidx.compose.foundation.isSystemInDarkTheme\n"
     "import androidx.compose.ui.graphics.luminance\n",
     "import androidx.compose.foundation.isSystemInDarkTheme"),
    ("esquema claro/escuro",
     "    val scheme = remember(palette.accent) { lockColorScheme(palette.accent) }\n",
     "    val dark = isSystemInDarkTheme()\n"
     "    val scheme = remember(palette.accent, dark) { lockColorScheme(palette.accent, dark) }\n"
     "    // relogio fica como estava: sempre o esquema escuro (nao muda com o tema)\n"
     "    val clockScheme = remember(palette.accent) { lockColorScheme(palette.accent) }\n",
     "val clockScheme = remember"),
    ("relogio DYNAMIC 1",
     "ClockColor.DYNAMIC -> scheme.onPrimaryContainer",
     "ClockColor.DYNAMIC -> clockScheme.onPrimaryContainer",
     "ClockColor.DYNAMIC -> clockScheme.onPrimaryContainer"),
    ("relogio ACCENT 1",
     "ClockColor.ACCENT -> scheme.primary\n",
     "ClockColor.ACCENT -> clockScheme.primary\n",
     "ClockColor.ACCENT -> clockScheme.primary"),
    ("relogio DYNAMIC 2",
     "ClockColor.DYNAMIC -> scheme.onTertiaryContainer",
     "ClockColor.DYNAMIC -> clockScheme.onTertiaryContainer",
     "ClockColor.DYNAMIC -> clockScheme.onTertiaryContainer"),
    ("relogio ACCENT 2",
     "ClockColor.ACCENT -> scheme.tertiary\n",
     "ClockColor.ACCENT -> clockScheme.tertiary\n",
     "ClockColor.ACCENT -> clockScheme.tertiary"),
    ("aviso sem acesso (titulo)",
     '"Notificações desativadas", color = Color.White,',
     '"Notificações desativadas", color = scheme.onSurface,',
     '"Notificações desativadas", color = scheme.onSurface,'),
    ("aviso sem acesso (texto)",
     '"Toque para liberar o acesso às notificações", color = Color.White.copy(alpha = 0.78f),',
     '"Toque para liberar o acesso às notificações", color = scheme.onSurfaceVariant,',
     'liberar o acesso às notificações", color = scheme.onSurfaceVariant,'),
    ("atalhos tonais",
     "            tint = scheme.onPrimaryContainer, opacity = 1f, iconColor = scheme.primaryContainer,\n",
     "            // par tonal com contraste garantido: circulo claro + icone escuro (escuro) ou o inverso do esquema (claro)\n"
     "            tint = if (scheme.background.luminance() > 0.5f) scheme.primaryContainer else scheme.onPrimaryContainer,\n"
     "            opacity = 1f,\n"
     "            iconColor = if (scheme.background.luminance() > 0.5f) scheme.onPrimaryContainer else scheme.primaryContainer,\n",
     "iconColor = if (scheme.background.luminance() > 0.5f)"),
])

# ------------------------------------------------------------ NotificationsList.kt
print("NotificationsList.kt")
edit("ui/NotificationsList.kt", [
    ("texto do cartao derivado do tom",
     "    val onCard: Color = if (tint.luminance() > 0.5f) Color(0xFF1C1B1F) else Color(0xFFE6E1E5)\n",
     "    // texto = o proprio tom do cartao levado quase ao preto/branco (acompanha a paleta, contraste automatico)\n"
     "    val onCard: Color = if (tint.luminance() > 0.5f) lerp(tint, Color.Black, 0.88f) else lerp(tint, Color.White, 0.88f)\n",
     "val onCard: Color = if (tint.luminance() > 0.5f) lerp(tint, Color.Black, 0.88f)"),
])
print("Pronto. Commit e push para o GitHub Actions verificar.")
