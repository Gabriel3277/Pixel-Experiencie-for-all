#!/usr/bin/env python3
"""Quicksand Bold em toda a UI da lockscreen, exceto o relogio (LockClock.kt nao e tocado,
e lockFont() continua existindo para os estilos do relogio). Idempotente, com assert.
Rodar em ~/PixelExperienceForAll:  python3 fonte_quicksand.py
"""
import os, re, sys

B = "lockscreen/src/main/kotlin/com/pixelexperience/forall/lockscreen/"
if not os.path.isdir(B):
    sys.exit("Rode dentro de ~/PixelExperienceForAll")
IMP_OLD = "import com.pixelexperience.forall.lockscreen.ui.theme.lockFont\n"
IMP_NEW = "import com.pixelexperience.forall.lockscreen.ui.theme.UiFont\n"


def rd(rel):
    return open(B + rel, encoding="utf-8").read()


def wr(rel, s):
    open(B + rel, "w", encoding="utf-8").write(s)


# ---- LockTheme.kt: define UiFont e usa na tipografia
rel = "ui/theme/LockTheme.kt"
s = rd(rel)
if "val UiFont" in s:
    print("LockTheme.kt [ja aplicado]")
else:
    old1 = "val DateFont = FontFamily(Font(R.font.quicksand_bold, FontWeight.Bold))\n"
    old2 = "private fun TextStyle.g(w: Int) = copy(fontFamily = lockFont(w))"
    assert s.count(old1) == 1, "LockTheme: DateFont nao encontrado (arquivo mudou)"
    assert s.count(old2) == 1, "LockTheme: TextStyle.g nao encontrado (arquivo mudou)"
    s = s.replace(old1, old1 + "\n/** Fonte unica da UI da lockscreen (tudo, menos o relogio). */\nval UiFont: FontFamily = DateFont\n")
    s = s.replace(old2, '@Suppress("UNUSED_PARAMETER")\nprivate fun TextStyle.g(w: Int) = copy(fontFamily = UiFont)')
    wr(rel, s)
    print("LockTheme.kt [ok]")

# ---- troca lockFont(N) -> UiFont nos arquivos de UI (nunca no LockClock.kt)
for rel, n in (("ui/LockScreenContent.kt", 3), ("ui/components/MediaPlayerCard.kt", 5), ("ui/NotificationsList.kt", 15)):
    s = rd(rel)
    if IMP_NEW in s and "lockFont(" not in s:
        print(rel, "[ja aplicado]")
        continue
    found = re.findall(r"lockFont\(\d+\)", s)
    assert len(found) == n and s.count("lockFont(") == n, f"{rel}: esperava {n} usos de lockFont(N), achei {len(found)} (arquivo mudou, mande o atual)"
    assert s.count(IMP_OLD) == 1, f"{rel}: import de lockFont nao encontrado"
    s = re.sub(r"lockFont\(\d+\)", "UiFont", s)
    s = s.replace(IMP_OLD, IMP_NEW)
    wr(rel, s)
    print(rel, f"[ok] {n} trocas")

print("Pronto. LockClock.kt nao foi alterado. Commit e push para o GitHub Actions verificar.")
