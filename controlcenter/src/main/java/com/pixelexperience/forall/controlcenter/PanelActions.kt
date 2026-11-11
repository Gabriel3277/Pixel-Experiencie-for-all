package com.pixelexperience.forall.controlcenter

/** O que o painel pode pedir à janela (abrir telas do sistema, fechar o painel). */
internal interface PanelActions {
    fun openInternet()
    fun openBluetooth()
    fun openVolume()
    fun requestWriteSettings()
    fun collapse()
}
