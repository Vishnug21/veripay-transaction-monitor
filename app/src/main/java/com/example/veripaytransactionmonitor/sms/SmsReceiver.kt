package com.example.veripaytransactionmonitor.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.veripaytransactionmonitor.util.FirestoreMatcher
import com.example.veripaytransactionmonitor.util.PaymentParser

private const val TAG = "SmsReceiver"
private const val STORE_ID = "vishnug270" // change if needed

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) return

            val bundle: Bundle? = intent.extras
            if (bundle == null) {
                Log.d(TAG, "no bundle in intent")
                return
            }

            val pdusObj = bundle["pdus"] as? Array<*>
            val format = bundle.getString("format")

            if (pdusObj == null) {
                Log.d(TAG, "no pdus")
                return
            }

            val sb = StringBuilder()
            for (aPdusObj in pdusObj) {
                val pdu = aPdusObj as? ByteArray ?: continue
                val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu)
                }
                sb.append(sms.messageBody)
            }

            val message = sb.toString().trim()
            val receiveTimeMs = System.currentTimeMillis()

            Log.d(TAG, "Received SMS: $message")

            val parsed: PaymentParser.ParsedPayment = PaymentParser.parse(message)
            Log.d(TAG, "Parsed payment from SMS: amountPaisa=${parsed.amountPaisa} reference=${parsed.reference} providerTxId=${parsed.providerTxId}")

            if (parsed.amountPaisa == null && parsed.reference.isNullOrBlank() && parsed.providerTxId.isNullOrBlank()) {
                Log.d(TAG, "No meaningful payment data parsed — ignoring.")
                return
            }

            FirestoreMatcher.matchAndMark(
                parsed = parsed,
                storeId = STORE_ID,
                smsReceiveTimeMs = receiveTimeMs,
                onComplete = { success: Boolean, messageStr: String? ->
                    if (success) {
                        Log.i(TAG, "matchAndMark success: $messageStr")
                        val updateIntent = Intent("com.example.veripaytransactionmonitor.TRANSACTION_UPDATED")
                        updateIntent.putExtra("result", messageStr)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
                    } else {
                        Log.w(TAG, "matchAndMark failed: $messageStr")
                        Toast.makeText(context, "Payment match failed: $messageStr", Toast.LENGTH_SHORT).show()
                        val failIntent = Intent("com.example.veripaytransactionmonitor.TRANSACTION_UPDATE_FAILED")
                        failIntent.putExtra("reason", messageStr)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(failIntent)
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "onReceive exception: ${e.message}", e)
        }
    }
}
