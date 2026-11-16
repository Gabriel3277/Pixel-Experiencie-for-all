package com.pixelexperience.forall.statusbar

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Dono de ciclo de vida/estado salvo para o Compose numa janela criada por serviço. */
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
