package com.example.veripaytransactionmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Data model for one transaction
data class Transaction(
    val transactionId: String = "",
    val amount: Double = 0.0,
    val status: String = "",
    val timestamp: Date? = null
)

// RecyclerView Adapter
class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onItemClick: ((Transaction) -> Unit)? = null  // optional click listener
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTxnId: TextView = itemView.findViewById(R.id.tvTxnId)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val txn = transactions[position]

        val nf = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        holder.tvTxnId.text = "Transaction ID: ${txn.transactionId}"
        holder.tvAmount.text = "Amount: ${nf.format(txn.amount)}"
        holder.tvStatus.text = "Status: ${txn.status}"
        holder.tvDate.text = "Date: ${txn.timestamp?.let { sdf.format(it) } ?: "--"}"

        // Optional: change color based on status
        when (txn.status.uppercase(Locale.ROOT)) {
            "SUCCESS" -> holder.tvStatus.setTextColor(0xFF388E3C.toInt())  // green
            "FAILED" -> holder.tvStatus.setTextColor(0xFFD32F2F.toInt())   // red
            else -> holder.tvStatus.setTextColor(0xFF1976D2.toInt())       // blue
        }

        // Optional: handle item click
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(txn)
        }
    }

    override fun getItemCount(): Int = transactions.size
}
