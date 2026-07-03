package com.example.kajaapp.tesis

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kajaapp.R
import com.example.kajaapp.repository.FakeTesisRepository

class TesisOwnerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesis_owner)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Panel del Dueno"

        val business = FakeTesisRepository.getBusiness()
        findViewById<TextView>(R.id.tvOwnerBusinessName).text = business.name
        findViewById<TextView>(R.id.tvOwnerHeader).text =
            "${business.ownerName} controla ${business.activeHelpers} ayudantes y ${business.openAlerts} alertas abiertas."

        val summaryContainer = findViewById<LinearLayout>(R.id.layoutOwnerSummary)
        summaryContainer.removeAllViews()
        FakeTesisRepository.getOwnerSummaryLines().forEach { line ->
            summaryContainer.addView(buildLine(line))
        }

        val stallsContainer = findViewById<LinearLayout>(R.id.layoutOwnerStalls)
        stallsContainer.removeAllViews()
        FakeTesisRepository.getStalls().forEach { stall ->
            stallsContainer.addView(buildLine("${stall.name}: ${stall.totalLabel} | ${stall.helperName} | ${stall.statusLabel}"))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildLine(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(getColor(R.color.kaja_teal_dark))
            setPadding(0, 0, 0, 20)
        }
    }
}
