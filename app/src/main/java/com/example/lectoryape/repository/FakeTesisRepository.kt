package com.example.lectoryape.repository

import com.example.lectoryape.models.TesisBusinessUiModel
import com.example.lectoryape.models.TesisInvitationUiModel
import com.example.lectoryape.models.TesisMemberUiModel
import com.example.lectoryape.models.TesisStallUiModel
import com.example.lectoryape.models.TesisTransactionUiModel
import com.example.lectoryape.models.TesisWorkSessionUiModel

object FakeTesisRepository {

    private val business = TesisBusinessUiModel(
        name = "Bodega Santa Rosa",
        ownerName = "Rosa Huaman",
        walletLabel = "Yape del dueno: 987 654 321",
        activeHelpers = 3,
        openAlerts = 2
    )

    private val members = listOf(
        TesisMemberUiModel("owner-1", "Rosa Huaman", "Owner", null, "Activa"),
        TesisMemberUiModel("helper-1", "Lucia Ramos", "Helper", "Puesto 1", "En turno"),
        TesisMemberUiModel("helper-2", "Marco Salas", "Helper", "Puesto 2", "En turno"),
        TesisMemberUiModel("helper-3", "Rosa Quispe", "Helper", "Puesto 3", "En turno")
    )

    private val invitations = listOf(
        TesisInvitationUiModel("SANTA-AYUDA-01", "Helper", "Puesto 1", "Expira hoy 18:00", "Activa"),
        TesisInvitationUiModel("SANTA-AYUDA-02", "Helper", "Puesto 2", "Expira manana 12:00", "Pendiente"),
        TesisInvitationUiModel("SANTA-SUP-03", "Helper", null, "Expira en 2 dias", "Pendiente")
    )

    private val stalls = listOf(
        TesisStallUiModel("stall-1", "Puesto 1", "Lucia", "S/ 37.00", "Atendiendo"),
        TesisStallUiModel("stall-2", "Puesto 2", "Marco", "S/ 21.50", "Atendiendo"),
        TesisStallUiModel("stall-3", "Puesto 3", "Rosa", "S/ 18.00", "Atendiendo")
    )

    private val transactions = listOf(
        TesisTransactionUiModel(
            id = "tesis-001",
            puesto = "Puesto 1",
            senderName = "Ana Perez",
            amount = 12.00,
            walletType = "YAPE",
            hourLabel = "07:42",
            status = "Confirmado",
            description = "Desayuno y cafe",
            helperName = "Lucia"
        ),
        TesisTransactionUiModel(
            id = "tesis-002",
            puesto = "Puesto 2",
            senderName = "Luis Rojas",
            amount = 7.50,
            walletType = "PLIN",
            hourLabel = "08:05",
            status = "Por revisar",
            description = "Gaseosa y snack",
            helperName = "Marco"
        ),
        TesisTransactionUiModel(
            id = "tesis-003",
            puesto = "Puesto 1",
            senderName = "Carla Soto",
            amount = 25.00,
            walletType = "YAPE",
            hourLabel = "08:51",
            status = "Observado",
            description = null,
            helperName = "Lucia"
        ),
        TesisTransactionUiModel(
            id = "tesis-004",
            puesto = "Puesto 3",
            senderName = "Miguel Diaz",
            amount = 18.00,
            walletType = "YAPE",
            hourLabel = "09:12",
            status = "Confirmado",
            description = "Pedido WhatsApp",
            helperName = "Rosa"
        ),
        TesisTransactionUiModel(
            id = "tesis-005",
            puesto = "Puesto 2",
            senderName = "Julia Torres",
            amount = 14.00,
            walletType = "PLIN",
            hourLabel = "09:40",
            status = "Por revisar",
            description = "Menu ejecutivo",
            helperName = "Marco"
        )
    )

    fun getBusinessName(): String = "Bodega Santa Rosa"

    fun getOwnerWallet(): String = business.walletLabel

    fun getBusiness(): TesisBusinessUiModel = business

    fun getOwnerMember(): TesisMemberUiModel = members.first()

    fun getCurrentHelper(): TesisMemberUiModel = members[2]

    fun getMembers(): List<TesisMemberUiModel> = members

    fun getInvitations(): List<TesisInvitationUiModel> = invitations

    fun getStalls(): List<TesisStallUiModel> = stalls

    fun getDefaultActiveStallName(): String = stalls[1].name

    fun getWorkSession(activeStallName: String): TesisWorkSessionUiModel {
        return TesisWorkSessionUiModel(
            memberName = getCurrentHelper().fullName,
            selectedStall = activeStallName,
            startedLabel = "Iniciaste jornada a las 07:30",
            shiftLabel = "Turno manana - demo"
        )
    }

    fun getTransactions(): List<TesisTransactionUiModel> = transactions

    fun getRecentTransactions(limit: Int = 3): List<TesisTransactionUiModel> = transactions.takeLast(limit).reversed()

    fun getClaimableTransactions(): List<TesisTransactionUiModel> {
        return transactions.filter { it.status == "Por revisar" || it.status == "Observado" }
    }

    fun getFeaturedClaimableTransaction(): TesisTransactionUiModel? {
        return getClaimableTransactions().firstOrNull()
    }

    fun getMyClaimedTransactions(): List<TesisTransactionUiModel> {
        val helper = getCurrentHelper().fullName.substringBefore(" ")
        return transactions.filter { it.helperName == helper }
    }

    fun getTotalAmount(): Double = transactions.sumOf { it.amount }

    fun getTotalCount(): Int = transactions.size

    fun getObservedCount(): Int = transactions.count { it.status == "Observado" }

    fun getPendingCount(): Int = transactions.count { it.status == "Por revisar" }

    fun getLastPaymentLabel(): String = transactions.maxByOrNull { it.hourLabel }?.let {
        "Ultimo pago ${it.hourLabel}"
    } ?: "Sin pagos"

    fun getOwnerSummaryLines(): List<String> {
        return listOf(
            "Cobros sin reclamar: 2",
            "Pagos observados: ${getObservedCount()}",
            "Ayudantes activos: ${business.activeHelpers}",
            "Invitaciones pendientes: ${invitations.count { it.status == "Pendiente" }}"
        )
    }

    fun getTotalsByPuesto(): List<Pair<String, Double>> {
        return transactions
            .groupBy { it.puesto }
            .map { entry -> entry.key to entry.value.sumOf { it.amount } }
            .sortedBy { it.first }
    }
}
