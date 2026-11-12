package com.pixelexperience.forall.controlcenter

/** O que o painel pode pedir à janela (abrir telas do sistema, fechar o painel). */
internal interface PanelActions {
    fun openInternet()
    fun openBluetooth()
    fun openVolume()
    fun openSettings()
    fun openAirplane()
    fun openAlarms()
    fun showPowerMenu()
    fun requestWriteSettings()
    fun collapse()
}
