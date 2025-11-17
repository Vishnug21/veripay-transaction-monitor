package com.example.veripaytransactionmonitor.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.veripaytransactionmonitor.sms.SmsReceiver // reuse parsing logic if desired

class PayNotificationListener : NotificationListenerService() {
    private val TAG = "PayNotifListener"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val notif = sbn.notification
            val extras = notif.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            Log.d(TAG, "Notification posted: title=$title text=$text")

            // Optionally reuse SMS parsing logic: call parsePaymentSms from SmsReceiver by making it accessible
            // For now just log; you can create a shared parser util to detect and handle payments
            // Example (if you extract parser into a shared util):
            // val parsed = PaymentParser.parse(text)
            // if (parsed != null) { update Firestore same as SmsReceiver }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
}
