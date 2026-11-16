import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
p = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen/ui/settings/LockSettingsScreen.kt"
s = open(p, encoding="utf-8").read()


def rep(marker, old, new):
    global s
    if marker in s:
        return
    assert s.count(old) == 1, f"trecho ausente ou duplicado: {old[:60]!r}"
    s = s.replace(old, new)


rep("icons.filled.Bedtime", "import androidx.compose.material.icons.filled.Apps\n",
    "import androidx.compose.material.icons.filled.Apps\nimport androidx.compose.material.icons.filled.Bedtime\n")

# imports
rep("import androidx.compose.ui.unit.Density",
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity\n"
    "import androidx.compose.ui.unit.Density\nimport androidx.compose.runtime.CompositionLocalProvider\n")

# mockup: escala pela densidade (medido e desenhado no tamanho real) em vez de graphicsLayer
rep("CompositionLocalProvider(LocalDensity provides",
    '''            Box(
                Modifier.requiredSize(vw, vh).align(Alignment.TopStart).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            ) {
                LockScreenContent(
                    settings = s, wallpaper = wallpaper, notifications = demo, torchOn = false,
                    preview = true, previewScale = scale,
                )
            }
''',
    '''            // escala a densidade em vez de usar graphicsLayer: o conteúdo é medido e desenhado no tamanho real
            CompositionLocalProvider(LocalDensity provides Density(dens.density * scale, dens.fontScale)) {
                Box(Modifier.requiredSize(vw, vh).align(Alignment.TopStart)) {
                    LockScreenContent(
                        settings = s, wallpaper = wallpaper, notifications = demo, torchOn = false,
                        preview = true, previewScale = 1f,
                    )
                }
            }
''')
rep("val dens = LocalDensity.current",
    "    val shape = RoundedCornerShape(26.dp)\n    BoxWithConstraints(",
    "    val shape = RoundedCornerShape(26.dp)\n    val dens = LocalDensity.current\n    BoxWithConstraints(")

# categoria + página Always-On
rep('"Always-On", if',
    '"Atalhos inferiores", s.leftShortcut.label + " · " + s.rightShortcut.label, "Atalhos inferiores"),\n',
    '"Atalhos inferiores", s.leftShortcut.label + " · " + s.rightShortcut.label, "Atalhos inferiores"),\n'
    '                            Cat(Icons.Filled.Bedtime, P_BLUE, "Always-On", if (s.aodEnabled) "Ativado" else "Desativado", "Always-On"),\n')
rep('"Always-On" -> {',
    '                "Atalhos inferiores" -> {\n',
    '                "Always-On" -> {\n'
    '                Section("Always-On") {\n'
    '                    SwitchRow("Always-On Display", s.aodEnabled) { v -> set { it.copy(aodEnabled = v) } }\n'
    '                    Text(\n'
    '                        "Entra após 17 s sem tocar na tela de bloqueio ou com toque duplo. Fundo preto, brilho mínimo e 20 s de duração; toque para voltar. Em telas LCD a luz de fundo continua acesa.",\n'
    '                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,\n'
    '                    )\n'
    '                }\n'
    '                }\n'
    '                "Atalhos inferiores" -> {\n')
open(p, "w", encoding="utf-8").write(s)
print("OK")
