package com.example.veripaytransactionmonitor

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnViewAll: Button
    private lateinit var tvLedgerBalance: TextView
    private lateinit var tvTodayTotal: TextView
    private lateinit var tvAllTimeTotal: TextView

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val adapter = HomeTransactionAdapter()

    data class TransactionModel(
        val transactionId: String = "",
        val amount: Double = 0.0,
        val status: String = "",
        val timestamp: Date? = null
    )

    // Diff for ListAdapter
    private val TRANSACTION_DIFF = object : DiffUtil.ItemCallback<TransactionModel>() {
        override fun areItemsTheSame(oldItem: TransactionModel, newItem: TransactionModel) =
            oldItem.transactionId == newItem.transactionId

        override fun areContentsTheSame(oldItem: TransactionModel, newItem: TransactionModel) =
            oldItem == newItem
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvLastThree)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        btnViewAll = view.findViewById(R.id.btnViewAll)
        tvLedgerBalance = view.findViewById(R.id.tvLedgerBalance)
        tvTodayTotal = view.findViewById(R.id.tvTodayTotal)
        tvAllTimeTotal = view.findViewById(R.id.tvAllTimeTotal)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnViewAll.setOnClickListener {
            // navigate to DashboardFragment (use action if you prefer)
            findNavController().navigate(R.id.dashboardFragment)
        }

        // Load last 3 transactions and totals (robust client-side totals)
        loadLastThree()
        loadTotals()
    }

    /**
     * Loads the latest 3 transactions for the current user's storeId (ordered by timestamp desc).
     */
    private fun loadLastThree() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "User not signed in")
            showEmpty(true)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val storeId = userDoc.getString("storeId")
                if (storeId.isNullOrEmpty()) {
                    Log.e(TAG, "No storeId for user ${user.uid}")
                    showEmpty(true)
                    return@addOnSuccessListener
                }

                db.collection("transactions")
                    .whereEqualTo("storeId", storeId)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(3)
                    .get()
                    .addOnSuccessListener { snap ->
                        val list = snap.documents.mapNotNull { doc ->
                            try {
                                val id = doc.getString("transactionId") ?: doc.getString("transactionid") ?: doc.id
                                val amount = parseAmount(doc.get("amount"))
                                val status = doc.getString("status") ?: "UNKNOWN"
                                val timestamp = parseTimestampToDate(doc.get("timestamp"))
                                val finalId = if (id.isBlank()) doc.id else id
                                TransactionModel(finalId, amount, status, timestamp)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing txn ${doc.id}: ${e.message}", e)
                                null
                            }
                        }

                        adapter.submitList(list)
                        showEmpty(list.isEmpty())
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to load transactions: ${e.message}", e)
                        Toast.makeText(requireContext(), "Failed to load transactions", Toast.LENGTH_SHORT).show()
                        showEmpty(true)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load user doc: ${e.message}", e)
                showEmpty(true)
            }
    }

    /**
     * Robust totals loader: fetch all transactions for storeId once and compute
     * both all-time total and today's total by comparing transaction local date
     * (year/month/day) to device-local today. Supports Timestamp, Long (sec/ms), Double, String.
     */
    private fun loadTotals() {
        val user = auth.currentUser
        if (user == null) {
            updateTotalsUI(0.0, 0.0)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val storeId = userDoc.getString("storeId")
                if (storeId.isNullOrEmpty()) {
                    updateTotalsUI(0.0, 0.0)
                    return@addOnSuccessListener
                }

                db.collection("transactions")
                    .whereEqualTo("storeId", storeId)
                    .get()
                    .addOnSuccessListener { snapAll ->
                        var allTotal = 0.0
                        var todayTotal = 0.0

                        // compute today's Y/M/D in local timezone
                        val todayCal = Calendar.getInstance()
                        val todayYear = todayCal.get(Calendar.YEAR)
                        val todayMonth = todayCal.get(Calendar.MONTH)
                        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

                        for (doc in snapAll.documents) {
                            try {
                                val amt = parseAmount(doc.get("amount"))
                                allTotal += amt

                                // determine txn date as Date? robustly
                                val txDate = parseTimestampToDate(doc.get("timestamp"))
                                if (txDate != null) {
                                    val txCal = Calendar.getInstance().apply { time = txDate }
                                    val y = txCal.get(Calendar.YEAR)
                                    val m = txCal.get(Calendar.MONTH)
                                    val d = txCal.get(Calendar.DAY_OF_MONTH)

                                    if (y == todayYear && m == todayMonth && d == todayDay) {
                                        todayTotal += amt
                                    }
                                } else {
                                    Log.w(TAG, "Transaction ${doc.id} has no parseable timestamp")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to process doc ${doc.id} for totals: ${e.message}")
                            }
                        }

                        updateTotalsUI(allTotal, todayTotal)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to load transactions for totals: ${e.message}", e)
                        updateTotalsUI(0.0, 0.0)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load user doc for totals: ${e.message}", e)
                updateTotalsUI(0.0, 0.0)
            }
    }

    private fun updateTotalsUI(allTimeTotal: Double, todayTotal: Double) {
        // ledger summary = all-time total
        tvLedgerBalance.text = "Ledger balance: ${formatCurrency(allTimeTotal)}"
        tvAllTimeTotal.text = "All-time: ${formatCurrency(allTimeTotal)}"
        tvTodayTotal.text = "Today: ${formatCurrency(todayTotal)}"
    }

    /**
     * Parse Firestore timestamp-like fields into java.util.Date (supports Timestamp, Date, Long, Double, String)
     */
    private fun parseTimestampToDate(raw: Any?): Date? {
        try {
            when (raw) {
                is Timestamp -> return raw.toDate()
                is Date -> return raw
                is Long -> {
                    // heuristic: if > 10^12 it's milliseconds, else seconds
                    return if (raw > 1000000000000L) Date(raw) else Date(raw * 1000L)
                }
                is Double -> {
                    val longVal = raw.toLong()
                    return if (longVal > 1000000000000L) Date(longVal) else Date(longVal * 1000L)
                }
                is String -> {
                    val s = raw.trim()
                    // try ISO OffsetDateTime first
                    try {
                        val inst = java.time.OffsetDateTime.parse(s)
                        return Date.from(inst.toInstant())
                    } catch (_: Exception) { /* ignore */ }

                    val formats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                        "yyyy-MM-dd HH:mm:ss",
                        "dd-MM-yyyy HH:mm",
                        "dd/MM/yyyy HH:mm",
                        "dd MMM yyyy HH:mm",
                        "dd MMM, yyyy hh:mm a"
                    )
                    for (fmt in formats) {
                        try {
                            val sdf = java.text.SimpleDateFormat(fmt, Locale.getDefault())
                            sdf.isLenient = true
                            return sdf.parse(s)
                        } catch (_: Exception) { /* try next */ }
                    }
                }
                else -> return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseTimestampToDate error: ${e.message}")
        }
        return null
    }

    private fun parseAmount(raw: Any?): Double {
        return try {
            when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun formatCurrency(amount: Double): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val formatted = nf.format(amount)
        return if (formatted.contains(".")) formatted.substringBefore(".") else formatted
    }

    private fun showEmpty(show: Boolean) {
        tvEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Adapter implementation
    private inner class HomeTransactionAdapter :
        ListAdapter<TransactionModel, HomeTransactionAdapter.VH>(TRANSACTION_DIFF) {

        private val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMsg: TextView = itemView.findViewById(R.id.tvMsg)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

            fun bind(t: TransactionModel) {
                tvMsg.text = t.status
                tvAmount.text = "₹${t.amount.toInt()}"
                tvTime.text = t.timestamp?.let { sdf.format(it) } ?: "—"
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction_small, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }
}