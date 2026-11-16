import os, sys

R = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/PixelExperienceForAll")
p = R + "/lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen/ui/components/FingerprintHint.kt"
s = open(p, encoding="utf-8").read()
old = "    val shape = remember { MaterialShapes.Arrow.toShape() }\n"
new = "    // toShape() é @Composable (já faz remember por dentro), então não pode ficar dentro de remember { }\n    val shape = MaterialShapes.Arrow.toShape()\n"
if new not in s:
    assert s.count(old) == 1, "linha do shape não encontrada"
    s = s.replace(old, new)
    # remember deixou de ser usado: remove o import para não gerar aviso
    if s.count("remember") == s.count("import androidx.compose.runtime.remember"):
        s = s.replace("import androidx.compose.runtime.remember\n", "")
    open(p, "w", encoding="utf-8").write(s)
print("OK")
