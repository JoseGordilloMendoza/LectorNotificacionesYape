package com.example.lectoryape.tesis

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lectoryape.R
import com.example.lectoryape.repository.FakeTesisRepository
import com.google.android.material.button.MaterialButton

class TesisShiftActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesis_shift)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mi Jornada"

        renderShiftState()
        renderStallButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderShiftState() {
        val activeStall = TesisDemoPrefs.getActiveStall(this)
        val session = FakeTesisRepository.getWorkSession(activeStall)

        findViewById<TextView>(R.id.tvShiftMemberName).text = session.memberName
        findViewById<TextView>(R.id.tvShiftActiveStall).text = "Puesto activo: ${session.selectedStall}"
        findViewById<TextView>(R.id.tvShiftStarted).text = session.startedLabel
        findViewById<TextView>(R.id.tvShiftWindow).text = session.shiftLabel
    }

    private fun renderStallButtons() {
        val container = findViewById<LinearLayout>(R.id.layoutShiftButtons)
        container.removeAllViews()

        FakeTesisRepository.getStalls().forEach { stall ->
            val button = MaterialButton(this).apply {
                text = "${stall.name} | ${stall.helperName}"
                setOnClickListener {
                    TesisDemoPrefs.setActiveStall(this@TesisShiftActivity, stall.name)
                    renderShiftState()
                    Toast.makeText(
                        this@TesisShiftActivity,
                        "Demo: jornada cambiada a ${stall.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            container.addView(button)
        }
    }
}
