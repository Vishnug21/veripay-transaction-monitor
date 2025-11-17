package com.example.veripaytransactionmonitor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.veripaytransactionmonitor.databinding.FragmentSettingsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show logged-in email if available
        binding.tvEmail.text = auth.currentUser?.email ?: "—"

        // Existing permission buttons (hooks to MainActivity)
        binding.btnEnableSmsPerm.setOnClickListener {
            (activity as? MainActivity)?.checkAndPromptSmsPermission()
        }
        binding.btnOpenNotifAccess.setOnClickListener {
            (activity as? MainActivity)?.openNotificationAccessSettings()
        }

        // Logout: sign out, clear example prefs, and navigate to loginFragment clearing backstack
        binding.btnLogout.setOnClickListener {
            auth.signOut()

            // clear saved credentials if any (example keys)
            try {
                val prefs = requireContext().getSharedPreferences("veripay_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("saved_email").remove("saved_password").remove("remember_me").apply()
            } catch (_: Exception) { /* ignore */ }

            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true) // clear entire back stack
                .build()

            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }

        // DEBUG: Seed test transactions button (writes/overwrites TXN001..TXN015)
        binding.btnSeedTestData.setOnClickListener {
            seedTestTransactions()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Seeds 15 test transactions into Firestore.
     * Overwrites documents if they already exist (document IDs TXN001..TXN015).
     * Two transactions (TXN005, TXN011) are created with status "PENDING".
     */
    private fun seedTestTransactions() {
        val storeId = "vishnug270"
        val batch = db.batch()
        val now = Calendar.getInstance()

        // ids TXN001 .. TXN015
        val pendingTxnIds = setOf("TXN005", "TXN011")
        val baseAmounts = listOf(75, 120, 230, 350, 500, 820, 999, 1200, 1500, 2700, 3500, 5000, 60, 410, 880)

        for (i in 0 until 15) {
            val txnNumber = i + 1
            val txnId = "TXN%03d".format(txnNumber)
            val refId = "REF%03d".format(txnNumber)

            // Spread dates across a 15-day window (older to newer)
            val cal = Calendar.getInstance().apply {
                time = now.time
                add(Calendar.DAY_OF_YEAR, -(14 - i)) // 14 days ago .. today
                set(Calendar.HOUR_OF_DAY, 8 + (i % 10))   // 8..17
                set(Calendar.MINUTE, (5 + i * 3) % 60)
                set(Calendar.SECOND, (10 + i) % 60)
                set(Calendar.MILLISECOND, 0)
            }
            val ts = Timestamp(cal.time)

            val amount = if (i < baseAmounts.size) baseAmounts[i] else 100 + i * 100
            val status = if (txnId in pendingTxnIds) "PENDING" else "SUCCESS"

            val docRef = db.collection("transactions").document(txnId)
            val data = hashMapOf(
                "transactionId" to txnId,
                "amount" to amount,
                "matchedAmount" to amount,
                "matchedAmountPaisa" to amount * 100,
                "currency" to "INR",
                "status" to status,
                "reference" to refId,
                "storeId" to storeId,
                "verificationSource" to "auto_sms",
                "matchedBy" to "SMS_AUTO",
                "timestamp" to ts,
                "createdAt" to ts,
                "matchedAt" to ts,
                "payerNumber" to listOf("+919812345678", "+919876543210", "+919900112233")[i % 3],
                "paymentMethod" to if (i % 3 == 0) "UPI" else if (i % 3 == 1) "CARD" else "NEFT",
                "notes" to "Auto-generated test txn #$txnId"
            )

            batch.set(docRef, data)
        }

        // Commit batch
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Seeded 15 test transactions (2 pending)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to seed test transactions: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
