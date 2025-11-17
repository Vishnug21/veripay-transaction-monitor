package com.example.veripaytransactionmonitor.util

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

object FirestoreMatcher {
    private const val TAG = "FirestoreMatcher"

    fun matchAndMark(
        parsed: PaymentParser.ParsedPayment,
        storeId: String? = null,
        smsReceiveTimeMs: Long = parsed.receivedAtMillis,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        val db = FirebaseFirestore.getInstance()
        val earliest = smsReceiveTimeMs - TimeUnit.MINUTES.toMillis(5)
        val latest = smsReceiveTimeMs + TimeUnit.MINUTES.toMillis(30)

        var baseQuery = db.collection("transactions")
            .whereEqualTo("status", "PENDING")
        if (!storeId.isNullOrBlank()) baseQuery = baseQuery.whereEqualTo("storeId", storeId)

        baseQuery.get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Log.w(TAG, "No pending transactions to compare against.")
                    saveUnmatched(parsed, db)
                    onComplete(false, "no-pending-docs")
                    return@addOnSuccessListener
                }

                fun docAmountToPaisa(doc: com.google.firebase.firestore.DocumentSnapshot): Long? {
                    val amtAny = doc.get("amount") ?: return null
                    return when (amtAny) {
                        is Long -> amtAny * 100L
                        is Int -> amtAny.toLong() * 100L
                        is Double -> kotlin.math.round(amtAny * 100.0).toLong()
                        is Float -> kotlin.math.round(amtAny * 100f).toLong()
                        else -> null
                    }
                }

                fun docCreatedAtMs(doc: com.google.firebase.firestore.DocumentSnapshot): Long? {
                    val created = doc.get("createdAt") ?: return null
                    return when (created) {
                        is Timestamp -> created.toDate().time
                        is com.google.firebase.Timestamp -> created.toDate().time
                        is java.util.Date -> created.time
                        else -> null
                    }
                }

                var candidate: com.google.firebase.firestore.DocumentSnapshot? = null
                val parsedProv = parsed.providerTxId?.trim()
                if (!parsedProv.isNullOrBlank()) {
                    candidate = snap.documents.find { doc ->
                        val docProv = doc.getString("providerTxId")
                        !docProv.isNullOrBlank() && docProv.equals(parsedProv, ignoreCase = true)
                    }
                    if (candidate != null) Log.i(TAG, "Matched by providerTxId -> ${candidate.id}")
                }

                if (candidate == null) {
                    val parsedRef = parsed.reference?.trim()
                    if (!parsedRef.isNullOrBlank()) {
                        candidate = snap.documents.find { doc ->
                            val docRef = doc.getString("reference")
                            !docRef.isNullOrBlank() && docRef.equals(parsedRef, ignoreCase = true)
                        }
                        if (candidate != null) Log.i(TAG, "Matched by reference -> ${candidate.id}")
                    }
                }

                if (candidate == null && parsed.amountPaisa != null) {
                    val parsedAmt = parsed.amountPaisa
                    candidate = snap.documents.find { doc ->
                        val docAmtP = docAmountToPaisa(doc) ?: return@find false
                        val createdMs = docCreatedAtMs(doc) ?: return@find false
                        val amtMatch = docAmtP == parsedAmt
                        val timeMatch = createdMs in earliest..latest
                        amtMatch && timeMatch
                    }
                    if (candidate != null) Log.i(TAG, "Matched by amount+time -> ${candidate.id}")
                }

                if (candidate == null) {
                    Log.w(TAG, "No candidate found for parsed=$parsed")
                    saveUnmatched(parsed, db)
                    onComplete(false, "no-candidate-found")
                    return@addOnSuccessListener
                }

                val docRef = candidate.reference
                db.runTransaction { tx ->
                    val snapshot = tx.get(docRef)
                    val currentStatus = snapshot.getString("status") ?: ""
                    if (!currentStatus.equals("PENDING", ignoreCase = true)) {
                        throw Exception("status-not-pending:${snapshot.id}:$currentStatus")
                    }

                    val updates = mutableMapOf<String, Any>(
                        "status" to "SUCCESS",
                        "matchedBy" to "SMS_AUTO",
                        "matchedAt" to Timestamp.now(),
                        "verificationSource" to "auto_sms"
                    )
                    parsed.providerTxId?.let { updates["providerTxId"] = it }
                    parsed.reference?.let { updates["reference"] = it }
                    parsed.amountPaisa?.let {
                        updates["matchedAmountPaisa"] = it
                        updates["matchedAmount"] = it.toDouble() / 100.0
                    }

                    tx.update(docRef, updates)
                    null
                }.addOnSuccessListener {
                    Log.i(TAG, "Transaction ${candidate.id} marked SUCCESS")
                    onComplete(true, "marked-success:${candidate.id}")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to mark transaction: ${e.message}", e)
                    onComplete(false, e.message)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Pending query failed: ${e.message}", e)
                saveUnmatched(parsed, db)
                onComplete(false, e.message)
            }
    }

    private fun saveUnmatched(parsed: PaymentParser.ParsedPayment, db: FirebaseFirestore) {
        try {
            val payload = hashMapOf<String, Any?>(
                "amountPaisa" to parsed.amountPaisa,
                "amount" to parsed.amountPaisa?.let { it.toDouble() / 100.0 },
                "raw" to parsed.raw,
                "reference" to parsed.reference,
                "providerTxId" to parsed.providerTxId,
                "receivedAt" to FieldValue.serverTimestamp()
            )
            db.collection("unmatchedPayments").add(payload)
                .addOnSuccessListener { docRef -> Log.i(TAG, "Saved unmatched payment ${docRef.id}") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to save unmatched: ${e.message}", e) }
        } catch (e: Exception) {
            Log.e(TAG, "saveUnmatched exception: ${e.message}", e)
        }
    }
}
