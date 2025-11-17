package com.example.veripaytransactionmonitor

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var rvTransactions: RecyclerView
    private lateinit var auth: FirebaseAuth
    private lateinit var toolbar: Toolbar
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Realtime listener handle and adapter state
    private var listenerRegistration: ListenerRegistration? = null
    private val txnsList = mutableListOf<Transaction>()
    private var txnAdapter: TransactionAdapter? = null

    // remembers last applied sort (optional, for UI/logic continuity)
    private var currentSort: SortOption = SortOption.NONE

    companion object {
        private const val TAG = "DASHBOARD"
    }

    private enum class SortOption {
        NONE, NEWEST_FIRST, OLDEST_FIRST, AMT_HIGH_TO_LOW, AMT_LOW_TO_HIGH
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        rvTransactions = view.findViewById(R.id.rvTransactions)
        toolbar = view.findViewById(R.id.toolbarDashboard)

        // set up toolbar menu (menu resource must include action_sort and action_settings)
        toolbar.inflateMenu(R.menu.dashboard_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                R.id.action_settings -> {
                    // navigate to SettingsFragment; make sure id exists in your nav graph
                    findNavController().navigate(R.id.settingsFragment)
                    true
                }
                else -> false
            }
        }

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        txnAdapter = TransactionAdapter(txnsList) { txn ->
            Toast.makeText(requireContext(), "Clicked: ${txn.transactionId}", Toast.LENGTH_SHORT).show()
        }
        rvTransactions.adapter = txnAdapter

        loadTransactions()
    }

    private fun loadTransactions() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "User not signed in")
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val storeId = userDoc.getString("storeId")
                Log.d(TAG, "Loaded user ${user.email}, storeId=$storeId")

                if (storeId.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "No store ID found for user", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "User doc missing storeId field")
                    return@addOnSuccessListener
                }

                // Remove previous listener
                listenerRegistration?.remove()

                listenerRegistration = db.collection("transactions")
                    .whereEqualTo("storeId", storeId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                            Toast.makeText(requireContext(), "Failed to load transactions: ${error.message}", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }

                        if (snapshot == null) {
                            Log.w(TAG, "Snapshot is null")
                            return@addSnapshotListener
                        }

                        Log.d(TAG, "Realtime update: ${snapshot.size()} docs")

                        val newList = mutableListOf<Transaction>()
                        snapshot.documents.forEach { doc ->
                            try {
                                Log.d(TAG, "DOC RAW: id=${doc.id} data=${doc.data}")

                                val id = doc.getString("transactionId")
                                    ?: doc.getString("transactionid")
                                    ?: doc.id

                                val amount = parseAmountField(doc.get("amount"))
                                Log.d(TAG, "Parsed doc ${doc.id}: transactionId=$id amount=$amount status=${doc.getString("status")}")

                                val status = doc.getString("status") ?: "UNKNOWN"
                                val time = doc.getTimestamp("timestamp")?.toDate()
                                val finalId = if (id.isBlank()) doc.id else id

                                newList.add(Transaction(finalId, amount, status, time))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing doc ${doc.id}: ${e.message}", e)
                            }
                        }

                        txnsList.clear()
                        txnsList.addAll(newList)

                        // Apply current sort (if user has chosen one)
                        applySortOption(currentSort, notify = false)

                        txnAdapter?.notifyDataSetChanged()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get user info: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to get user info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Robust parser for amount field (Number, Double, or String containing digits/currency)
    private fun parseAmountField(raw: Any?): Double {
        return try {
            when (raw) {
                is Number -> raw.toDouble()
                is String -> {
                    val cleaned = raw.replace("[^0-9.]".toRegex(), "")
                    cleaned.toDoubleOrNull() ?: run {
                        Log.w(TAG, "parseAmountField: can't parse amount string '$raw'")
                        0.0
                    }
                }
                null -> {
                    Log.w(TAG, "parseAmountField: amount is null")
                    0.0
                }
                else -> {
                    Log.w(TAG, "parseAmountField: unknown amount type ${raw.javaClass.name}")
                    0.0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAmountField exception: ${e.message}", e)
            0.0
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "Newest first",
            "Oldest first",
            "Amount — High to Low",
            "Amount — Low to High",
            "Clear sort (Firestore default)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Sort transactions")
            .setItems(options) { _, which ->
                currentSort = when (which) {
                    0 -> SortOption.NEWEST_FIRST
                    1 -> SortOption.OLDEST_FIRST
                    2 -> SortOption.AMT_HIGH_TO_LOW
                    3 -> SortOption.AMT_LOW_TO_HIGH
                    else -> SortOption.NONE
                }
                applySortOption(currentSort, notify = true)
            }
            .show()
    }

    private fun applySortOption(option: SortOption, notify: Boolean) {
        when (option) {
            SortOption.NONE -> txnsList.sortWith(compareByDescending<Transaction> { it.timestamp ?: Date(0) })
            SortOption.NEWEST_FIRST -> txnsList.sortWith(compareByDescending<Transaction> { it.timestamp ?: Date(0) })
            SortOption.OLDEST_FIRST -> txnsList.sortWith(compareBy<Transaction> { it.timestamp ?: Date(Long.MAX_VALUE) })
            SortOption.AMT_HIGH_TO_LOW -> txnsList.sortWith(compareByDescending<Transaction> { it.amount })
            SortOption.AMT_LOW_TO_HIGH -> txnsList.sortWith(compareBy<Transaction> { it.amount })
        }
        if (notify) {
            txnAdapter?.notifyDataSetChanged()
            rvTransactions.scrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
