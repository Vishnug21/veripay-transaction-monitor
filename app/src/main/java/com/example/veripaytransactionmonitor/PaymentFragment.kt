package com.example.veripaytransactionmonitor

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

class PaymentFragment : Fragment() {

    private lateinit var etTransactionId: EditText
    private lateinit var etAmount: EditText
    private lateinit var spinnerPaymentMethod: Spinner
    private lateinit var btnVerify: Button
    private lateinit var tvResult: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "PaymentFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views (must match IDs in your XML)
        try {
            etTransactionId = view.findViewById(R.id.etTransactionId)
            etAmount = view.findViewById(R.id.etAmount)
            spinnerPaymentMethod = view.findViewById(R.id.spinnerPaymentMethod)
            btnVerify = view.findViewById(R.id.btnVerify)
            tvResult = view.findViewById(R.id.tvResult)
        } catch (e: Exception) {
            Log.e(TAG, "View binding failed: ${e.message}", e)
            Toast.makeText(requireContext(), "Layout binding error — check IDs", Toast.LENGTH_LONG).show()
            return
        }

        // Spinner setup
        val methods = listOf("Credit Card", "UPI", "Net Banking", "Cash", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, methods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPaymentMethod.adapter = adapter

        btnVerify.setOnClickListener {
            try {
                startVerification()
            } catch (e: Exception) {
                Log.e(TAG, "Error in verify click: ${e.message}", e)
                Toast.makeText(requireContext(), "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Allow Done on keyboard to trigger verification (amount input)
        etAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    startVerification()
                } catch (e: Exception) {
                    Log.e(TAG, "Error on IME action: ${e.message}", e)
                    Toast.makeText(requireContext(), "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                true
            } else false
        }
    }

    private fun startVerification() {
        val txnRaw = etTransactionId.text.toString().trim()
        val txnId = if (txnRaw.isBlank()) null else txnRaw.uppercase(Locale.getDefault())

        val amountInput = etAmount.text.toString().trim()
        val amountVal = amountInput.toDoubleOrNull()

        // If no txnId and no amount -> ask user
        if (txnId == null && amountVal == null) {
            showResult("⚠️ Please enter Transaction ID or Amount", false)
            return
        }

        tvResult.text = "Verifying..."
        btnVerify.isEnabled = false

        if (txnId != null) {
            verifyByTransactionId(txnId, amountVal) { msg, ok ->
                showResult(msg, ok)
                btnVerify.isEnabled = true
            }
        } else {
            // txnId is null -> verify by amount
            verifyByAmount(amountVal!!) { msg, ok ->
                showResult(msg, ok)
                btnVerify.isEnabled = true
            }
        }
    }

    private fun verifyByTransactionId(transactionId: String, expectedAmount: Double?, onResult: (String, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult("❌ Not signed in", false)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val storeId = userDoc.getString("storeId")
                Log.d(TAG, "verifyByTransactionId: storeId=$storeId for uid=${user.uid}")

                if (storeId.isNullOrEmpty()) {
                    onResult("⚠️ No store ID linked to this user", false)
                    return@addOnSuccessListener
                }

                db.collection("transactions")
                    .whereEqualTo("transactionId", transactionId)
                    .whereEqualTo("storeId", storeId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (!snap.isEmpty) {
                            handleDocument(snap.documents.first(), expectedAmount, onResult)
                        } else {
                            // try fallback capitalisation or alternative collection name if relevant
                            onResult("❌ Transaction not found for this Store ID", false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Query failed: ${e.message}", e)
                        onResult("⚠️ Query failed: ${e.message}", false)
                    }

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user doc: ${e.message}", e)
                onResult("⚠️ Failed to get user info: ${e.message}", false)
            }
    }

    private fun verifyByAmount(expectedAmount: Double, onResult: (String, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult("❌ Not signed in", false)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val storeId = userDoc.getString("storeId")
                Log.d(TAG, "verifyByAmount: storeId=$storeId for uid=${user.uid}")

                if (storeId.isNullOrEmpty()) {
                    onResult("⚠️ No store ID linked to this user", false)
                    return@addOnSuccessListener
                }

                db.collection("transactions")
                    .whereEqualTo("amount", expectedAmount)
                    .whereEqualTo("storeId", storeId)
                    .get()
                    .addOnSuccessListener { snap ->
                        when (snap.size()) {
                            0 -> onResult("❌ No transactions found for ₹${"%.2f".format(expectedAmount)}", false)
                            1 -> handleDocument(snap.documents.first(), expectedAmount, onResult)
                            else -> {
                                // multiple matches — show list picker
                                val docs = snap.documents
                                val items = docs.map { d ->
                                    val id = d.getString("transactionId") ?: d.id
                                    val ts = d.getTimestamp("timestamp")?.toDate()
                                    val amtAny = d.get("amount")
                                    val amtDisplay = when (amtAny) {
                                        is Number -> "%.2f".format(amtAny.toDouble())
                                        else -> amtAny?.toString() ?: "N/A"
                                    }
                                    "$id • ₹$amtDisplay • ${ts ?: ""}"
                                }.toTypedArray()

                                AlertDialog.Builder(requireContext())
                                    .setTitle("Multiple matches — pick transaction")
                                    .setItems(items) { _, which ->
                                        try {
                                            handleDocument(docs[which], expectedAmount, onResult)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error selecting document: ${e.message}", e)
                                            onResult("⚠️ Unexpected error selecting document", false)
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Query failed: ${e.message}", e)
                        onResult("⚠️ Query failed: ${e.message}", false)
                    }

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user doc: ${e.message}", e)
                onResult("⚠️ Failed to get user info: ${e.message}", false)
            }
    }

    private fun handleDocument(doc: DocumentSnapshot?, expectedAmount: Double?, onResult: (String, Boolean) -> Unit) {
        if (doc == null || !doc.exists()) {
            onResult("❌ Transaction not found for this Store ID", false)
            return
        }

        val status = doc.getString("status") ?: "UNKNOWN"
        val amount = doc.getDouble("amount") ?: run {
            val longAmt = doc.getLong("amount")
            longAmt?.toDouble() ?: run {
                val amtStr = doc.getString("amount")
                amtStr?.toDoubleOrNull() ?: 0.0
            }
        }
        val timestamp = doc.getTimestamp("timestamp")?.toDate()

        val errors = mutableListOf<String>()
        if (!status.equals("SUCCESS", ignoreCase = true)) errors.add("Status = $status")
        if (amount <= 0.0) errors.add("Invalid amount: $amount")
        if (expectedAmount != null && abs(amount - expectedAmount) > 0.01)
            errors.add("Amount mismatch: expected ₹${"%.2f".format(expectedAmount)}, got ₹${"%.2f".format(amount)}")

        if (errors.isEmpty()) {
            val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            val id = doc.getString("transactionId") ?: doc.id
            val msg = """
                ✅ Payment Verified
                ID: $id
                Amount: ${nf.format(amount)}
                Status: $status
                Date: $timestamp
            """.trimIndent()
            onResult(msg, true)
        } else {
            onResult("❌ Verification failed:\n${errors.joinToString("\n")}", false)
        }
    }

    private fun showResult(message: String, success: Boolean) {
        tvResult.text = message
        tvResult.setTextColor(
            if (success) ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            else ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
        )

        val dialogBuilder = AlertDialog.Builder(requireContext())
        dialogBuilder.setTitle(if (success) "✅ Payment Verified" else "❌ Verification Failed")
        dialogBuilder.setMessage(message)
        dialogBuilder.setCancelable(false)
        dialogBuilder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = dialogBuilder.create()
        if (success) {
            dialog.setOnDismissListener {
                etTransactionId.text.clear()
                etAmount.text.clear()
                tvResult.text = ""
            }
        }
        dialog.show()
    }
}
