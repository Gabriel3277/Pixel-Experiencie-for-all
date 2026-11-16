package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens do painel (Material 3 Expressive): formas e tipografia num só lugar. */
internal object Pd {
    val Gap = 12.dp
    val SectionGap = 12.dp
    val CompactTileHeight = 56.dp
    val TileHeight = 64.dp
    val WideTileHeight = 76.dp
    val CardRadius = 36.dp
    val NotifOuter = 28.dp
    val NotifInner = 8.dp

    val Time = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
    val Title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val Label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
