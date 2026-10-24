package br.com.auron.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Android binds this service only after the owner grants notification access in Settings. */
class AuronNotificationListenerService : NotificationListenerService() {
    private val controller by lazy { AutomationController.get(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        controller.listenerConnected(this)
    }

    override fun onListenerDisconnected() {
        controller.listenerDisconnected(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let(controller::posted)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(controller::removed)
    }

    override fun onDestroy() {
        controller.listenerDisconnected(this)
        super.onDestroy()
    }
}
