package com.example.kajaapp.tesis

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.kajaapp.R
import com.example.kajaapp.repository.FakeTesisRepository

class TesisTeamActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesis_team)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Equipo e Invitaciones"

        bindMembers()
        bindInvitations()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindMembers() {
        val container = findViewById<LinearLayout>(R.id.layoutTeamMembers)
        container.removeAllViews()

        FakeTesisRepository.getMembers().forEach { member ->
            val stallLabel = member.defaultStall ?: "Sin puesto fijo"
            container.addView(buildLine("${member.fullName} | ${member.role} | $stallLabel | ${member.status}"))
        }
    }

    private fun bindInvitations() {
        val container = findViewById<LinearLayout>(R.id.layoutTeamInvitations)
        container.removeAllViews()

        FakeTesisRepository.getInvitations().forEach { invitation ->
            val stallLabel = invitation.stallName ?: "Puesto libre"
            container.addView(buildLine("${invitation.code} | ${invitation.targetRole} | $stallLabel | ${invitation.expiresLabel} | ${invitation.status}"))
        }
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
