package com.example.veripaytransactionmonitor.util

import android.util.Log
import java.util.Locale

object PaymentParser {

    private const val TAG = "PaymentParser"

    data class ParsedPayment(
        val amountPaisa: Long = 0L,           // normalized to paisa (e.g., ₹1700 -> 170000)
        val providerTxId: String?,
        val reference: String?,
        val raw: String,
        val receivedAtMillis: Long = System.currentTimeMillis()
    )

    // Amount regex: matches "INR 1,700.00", "₹1700", "Rs. 1,700", "credited with INR 1700" etc.
    private val amtRe = Regex(
        """(?i)(?:INR|₹|Rs\.?)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Provider / transaction id (many bank labels)
    private val txnIdRe = Regex(
        """(?i)\b(?:Txn(?:\s|\.|id|ID|ication)?(?:\s|:)?|Transaction\s+ID[:\s]*|Provider\s*Txn\s*ID[:\s]*|Txnid[:\s]*|TxnID[:\s]*|TxID[:\s]*|TXN[:\s]*)\s*([A-Za-z0-9\-_/]+)\b""",
        RegexOption.IGNORE_CASE
    )

    // Reference labels
    private val refRe = Regex(
        """(?i)\b(?:UPI\s*Ref(?:erence)?|Ref(?:\.?| No|No)?|Reference[:\s]*|Ref No[:\s]*)[:\s]*([A-Za-z0-9\-_/]+)\b""",
        RegexOption.IGNORE_CASE
    )

    // Safe fallback token: must contain at least one digit and be reasonably long to avoid words like "credited"
    private val fallbackTokenRe = Regex("""\b([A-Za-z0-9\-_/]*\d+[A-Za-z0-9\-_/]*)\b""", RegexOption.IGNORE_CASE)

    private val denylist = setOf("credited", "received", "payment", "success", "via", "upi")

    /**
     * Parse SMS/notification text and return ParsedPayment.
     * amountPaisa is always a Long (0L if not found/parseable).
     */
    fun parse(text: String): ParsedPayment {
        val raw = text.trim()
        val cleaned = raw.replace('\u00A0', ' ')

        // 1) amount
        val amtMatch = amtRe.find(cleaned)
        val amountPaisa = amtMatch?.groups?.get(1)?.value?.let { amtStr ->
            val cleanedAmt = amtStr.replace(",", "").trim()
            parseAmountToPaisa(cleanedAmt)
        } ?: 0L

        // 2) providerTxId (preferred)
        val providerTxId = txnIdRe.find(cleaned)?.groups?.get(1)?.value?.trim()

        // 3) reference (explicit)
        val reference = refRe.find(cleaned)?.groups?.get(1)?.value?.trim()

        // 4) safe fallback if nothing explicit found
        var fallbackId: String? = null
        if (providerTxId.isNullOrBlank() && reference.isNullOrBlank()) {
            val fb = fallbackTokenRe.find(cleaned)?.groups?.get(1)?.value?.trim()
            if (!fb.isNullOrBlank()) {
                val lower = fb.lowercase(Locale.getDefault())
                if (fb.any { it.isDigit() } && fb.length >= 4 && !denylist.contains(lower)) {
                    fallbackId = fb
                }
            }
        }

        val finalProviderTxId = providerTxId ?: fallbackId
        val finalReference = reference ?: (if (providerTxId == null) fallbackId else reference)

        val parsed = ParsedPayment(
            amountPaisa = amountPaisa,
            providerTxId = finalProviderTxId,
            reference = finalReference,
            raw = raw
        )

        Log.d(TAG, "parse -> amountPaisa=${parsed.amountPaisa} reference=${parsed.reference} providerTxId=${parsed.providerTxId}")
        return parsed
    }

    // Robust conversion using BigDecimal. Always returns a Long paisa value (0L on error).
    private fun parseAmountToPaisa(amountStr: String): Long {
        val s = amountStr.replace(",", "").trim()
        if (s.isEmpty()) return 0L

        return try {
            val bd = java.math.BigDecimal(s)
            val paisaBd = bd.multiply(java.math.BigDecimal(100))
            paisaBd.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            Log.w(TAG, "parseAmountToPaisa error for '$amountStr': ${e.message}")
            0L
        }
    }
}
