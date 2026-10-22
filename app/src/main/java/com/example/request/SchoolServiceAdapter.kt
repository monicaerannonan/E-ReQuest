package com.example.request

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SchoolServiceAdapter(
    private val onServiceClick: (ServiceEntity) -> Unit
) : ListAdapter<ServiceEntity, SchoolServiceAdapter.ServiceViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ServiceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_school_service, parent, false)
        return ServiceViewHolder(
            itemView = itemView,
            onClick = onServiceClick,
            getItemAt = ::getItem
        )
    }

    override fun onBindViewHolder(
        holder: ServiceViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class ServiceViewHolder(
        itemView: View,
        private val onClick: (ServiceEntity) -> Unit,
        private val getItemAt: (Int) -> ServiceEntity
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvServiceIcon: TextView = itemView.findViewById(R.id.tvServiceIcon)
        private val tvServiceName: TextView = itemView.findViewById(R.id.tvServiceName)
        private val tvServiceDescription: TextView = itemView.findViewById(R.id.tvServiceDescription)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(getItemAt(pos))
                }
            }
        }

        fun bind(service: ServiceEntity) {
            tvServiceIcon.text = service.name
                .trim()
                .firstOrNull()
                ?.uppercase()
                ?: "S"
            tvServiceName.text = service.name
            tvServiceDescription.text = service.description
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ServiceEntity>() {

        override fun areItemsTheSame(
            oldItem: ServiceEntity,
            newItem: ServiceEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ServiceEntity,
            newItem: ServiceEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
