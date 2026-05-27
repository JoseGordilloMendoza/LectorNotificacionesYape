package com.example.lectoryape.tesis

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lectoryape.R
import com.example.lectoryape.adapters.TesisTransactionAdapter
import com.example.lectoryape.repository.FakeTesisRepository

class TesisQueueActivity : AppCompatActivity() {

    private var featuredState: String = "SIN_RECLAMAR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesis_queue)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bandeja del Ayudante"

        val activeStall = TesisDemoPrefs.getActiveStall(this)
        findViewById<TextView>(R.id.tvQueueContext).text =
            "Trabajando en $activeStall. Aqui el ayudante veria pagos sin reclamar y los suyos."

        setupFeaturedPaymentCard(activeStall)

        bindRecycler(
            recyclerId = R.id.rvClaimableTransactions,
            items = FakeTesisRepository.getClaimableTransactions()
        )
        bindRecycler(
            recyclerId = R.id.rvMyTransactions,
            items = FakeTesisRepository.getMyClaimedTransactions()
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupFeaturedPaymentCard(activeStall: String) {
        val transaction = FakeTesisRepository.getFeaturedClaimableTransaction() ?: return
        val currentHelper = FakeTesisRepository.getCurrentHelper().fullName

        val badge = findViewById<TextView>(R.id.tvIncomingBadge)
        val amount = findViewById<TextView>(R.id.tvIncomingAmount)
        val sender = findViewById<TextView>(R.id.tvIncomingSender)
        val meta = findViewById<TextView>(R.id.tvIncomingMeta)
        val assignment = findViewById<TextView>(R.id.tvIncomingAssignment)
        val description = findViewById<TextView>(R.id.tvIncomingDescription)

        amount.text = String.format("S/ %.2f", transaction.amount)
        sender.text = transaction.senderName
        meta.text = "${transaction.hourLabel} | ${transaction.walletType} | Ref. ${transaction.id.uppercase()}"

        fun renderCardState() {
            when (featuredState) {
                "RECLAMADO" -> {
                    badge.text = "RECLAMADO"
                    badge.setBackgroundResource(R.drawable.bg_pill_status_pending)
                    assignment.text = "Reclamado por $currentHelper en $activeStall."
                    description.text = "Descripcion: venta rapida pendiente de completar."
                }
                "CONFIRMADO" -> {
                    badge.text = "CONFIRMADO"
                    badge.setBackgroundResource(R.drawable.bg_pill_status_success)
                    assignment.text = "Confirmado por $currentHelper en $activeStall."
                    description.text = "Descripcion: pago validado y asociado a una venta del puesto."
                }
                "OBSERVADO" -> {
                    badge.text = "OBSERVADO"
                    badge.setBackgroundResource(R.drawable.bg_pill_status_alert)
                    assignment.text = "Observado por $currentHelper. El dueno deberia revisarlo."
                    description.text = "Descripcion: posible pago sin contexto o con duda."
                }
                else -> {
                    badge.text = "SIN RECLAMAR"
                    badge.setBackgroundResource(R.drawable.bg_pill_status_pending)
                    assignment.text = "Aun no reclamado. Se asignara al ayudante y puesto activos."
                    description.text = "Descripcion: pendiente"
                }
            }
        }

        renderCardState()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIncomingClaim).setOnClickListener {
            featuredState = "RECLAMADO"
            renderCardState()
            Toast.makeText(this, "Demo: pago reclamado por el ayudante actual", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIncomingConfirm).setOnClickListener {
            featuredState = "CONFIRMADO"
            renderCardState()
            Toast.makeText(this, "Demo: pago confirmado para el puesto activo", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIncomingObserve).setOnClickListener {
            featuredState = "OBSERVADO"
            renderCardState()
            Toast.makeText(this, "Demo: pago marcado como observado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindRecycler(recyclerId: Int, items: List<com.example.lectoryape.models.TesisTransactionUiModel>) {
        val recycler = findViewById<RecyclerView>(recyclerId)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = TesisTransactionAdapter().apply {
            setTransactions(items)
        }
    }
}
