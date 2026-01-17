package com.example.lectoryape.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lectoryape.R
import com.example.lectoryape.models.YapeDisplayItem

/**
 * Adapter para mostrar lista de yapeos en RecyclerView
 */
class YapeosAdapter : RecyclerView.Adapter<YapeosAdapter.YapeoViewHolder>() {
    
    private var yapeos = listOf<YapeDisplayItem>()
    
    class YapeoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMonto: TextView = itemView.findViewById(R.id.tvMonto)
        val tvTexto: TextView = itemView.findViewById(R.id.tvTexto)
        val tvFecha: TextView = itemView.findViewById(R.id.tvFecha)
        
        fun bind(yapeo: YapeDisplayItem) {
            tvMonto.text = yapeo.monto
            tvTexto.text = yapeo.texto
            tvFecha.text = yapeo.fecha
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): YapeoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_yapeo, parent, false)
        return YapeoViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: YapeoViewHolder, position: Int) {
        holder.bind(yapeos[position])
    }
    
    override fun getItemCount() = yapeos.size
    
    /**
     * Actualiza la lista de yapeos
     */
    fun updateYapeos(newYapeos: List<YapeDisplayItem>) {
        yapeos = newYapeos.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }
}
