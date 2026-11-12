package com.pixelexperience.forall.controlcenter

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lê as notificações só para mostrá-las no nosso painel. O usuário libera o acesso uma
 * vez, em Configurações > Acesso às notificações (sem root, sem adb).
 */
class PanelNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.onConnected(this)
    }

    override fun onListenerDisconnected() {
        NotificationStore.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.upsert(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove(sbn.key)
    }
}
