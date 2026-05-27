package com.example.lectoryape.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.lectoryape.R
import com.example.lectoryape.models.TesisTransactionUiModel

class TesisTransactionAdapter : RecyclerView.Adapter<TesisTransactionAdapter.TesisTransactionViewHolder>() {

    private val transactions = mutableListOf<TesisTransactionUiModel>()

    fun setTransactions(newTransactions: List<TesisTransactionUiModel>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TesisTransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tesis_transaction, parent, false)
        return TesisTransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TesisTransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TesisTransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val ivWalletIcon: ImageView = itemView.findViewById(R.id.ivWalletIcon)

        fun bind(transaction: TesisTransactionUiModel) {
            val context = itemView.context
            tvSenderName.text = transaction.senderName
            tvMeta.text = "${transaction.hourLabel} | ${transaction.walletType} | ${transaction.puesto} | ${transaction.helperName}"
            tvAmount.text = String.format("+ S/ %.2f", transaction.amount)
            tvDescription.text = transaction.description ?: "Sin descripcion. Aqui el ayudante podria agregar el motivo de venta."

            when (transaction.walletType.uppercase()) {
                "PLIN" -> {
                    ivWalletIcon.backgroundTintList = ContextCompat.getColorStateList(context, R.color.kaja_coral)
                }
                else -> {
                    ivWalletIcon.backgroundTintList = ContextCompat.getColorStateList(context, R.color.yape_purple)
                }
            }

            when (transaction.status) {
                "Confirmado" -> {
                    tvStatus.text = "CONFIRMADO"
                    tvStatus.setBackgroundResource(R.drawable.bg_pill_status_success)
                }
                "Observado" -> {
                    tvStatus.text = "OBSERVADO"
                    tvStatus.setBackgroundResource(R.drawable.bg_pill_status_alert)
                }
                else -> {
                    tvStatus.text = "POR REVISAR"
                    tvStatus.setBackgroundResource(R.drawable.bg_pill_status_pending)
                }
            }
        }
    }
}
