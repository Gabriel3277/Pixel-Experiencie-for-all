package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.pixelexperience.forall.lockscreen.data.WallpaperFit

/** Preencher e Topo cortam (Topo ancora no alto, bom para retratos); Ajustar mostra a imagem inteira. */
fun WallpaperFit.contentScale(): ContentScale = if (this == WallpaperFit.FIT) ContentScale.Fit else ContentScale.Crop

fun WallpaperFit.alignment(): Alignment = if (this == WallpaperFit.TOP) Alignment.TopCenter else Alignment.Center

/** Fundo do modo Ajustar: a própria imagem ampliada e desfocada, para as bordas não ficarem pretas. */
@Composable
fun FitBackdrop(image: ImageBitmap, preview: Boolean) {
    Image(
        bitmap = image, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.2f
            scaleY = 1.2f
            renderEffect = if (!preview) BlurEffect(48f, 48f, TileMode.Clamp) else null
        },
    )
}
