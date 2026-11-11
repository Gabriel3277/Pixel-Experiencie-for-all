package com.pixelexperience.forall

import android.app.Application
import com.pixelexperience.forall.core.PixelCore

class PixelApplication : Application() {
    /** Criado só quando alguém precisa dele; nada pesado roda no start. */
    val core: PixelCore by lazy { PixelCore(this) }
}
