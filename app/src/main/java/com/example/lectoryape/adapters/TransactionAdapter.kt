package com.example.lectoryape.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.lectoryape.R
import com.example.lectoryape.models.YapeNotificationRaw

class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<YapeNotificationRaw>()

    fun setTransactions(newTransactions: List<YapeNotificationRaw>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.bind(transaction)
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvWalletType: TextView = itemView.findViewById(R.id.tvWalletType)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val ivWalletIcon: ImageView = itemView.findViewById(R.id.ivWalletIcon)

        fun bind(transaction: YapeNotificationRaw) {
            tvSenderName.text = transaction.name
            
            // Format time relative (e.g., "Hace 5 min")
            val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                transaction.timestamp,
                System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS,
                android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
            )
            tvTime.text = relativeTime
            
            tvWalletType.text = transaction.walletType
            tvAmount.text = String.format("+ S/ %.2f", transaction.amount)
            
            if (transaction.walletType.uppercase() == "PLIN") {
                tvWalletType.setTextColor(ContextCompat.getColor(itemView.context, R.color.kaja_coral))
                ivWalletIcon.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.kaja_coral)
            } else {
                tvWalletType.setTextColor(ContextCompat.getColor(itemView.context, R.color.yape_purple))
                ivWalletIcon.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.yape_purple)
            }
        }
    }
}
