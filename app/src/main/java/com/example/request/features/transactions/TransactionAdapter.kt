package com.example.request.features.transactions

import com.example.request.R
import com.example.request.data.local.*
import com.example.request.data.sync.*
import com.example.request.features.checklist.*
import com.example.request.features.home.*
import com.example.request.features.notifications.*
import com.example.request.features.progress.*
import com.example.request.features.services.*
import com.example.request.features.splash.*
import com.example.request.features.transactions.*
import com.example.request.ui.custom.*

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TransactionAdapter(
    private val onTransactionClick: (TransactionEntity) -> Unit
) : ListAdapter<TransactionEntity, TransactionAdapter.TransactionViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(
            itemView = itemView,
            onClick = onTransactionClick,
            getItemAt = ::getItem
        )
    }

    override fun onBindViewHolder(
        holder: TransactionViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class TransactionViewHolder(
        itemView: View,
        private val onClick: (TransactionEntity) -> Unit,
        private val getItemAt: (Int) -> TransactionEntity
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTransactionName: TextView = itemView.findViewById(R.id.tvTransactionName)
        private val tvTransactionDescription: TextView = itemView.findViewById(R.id.tvTransactionDescription)

        init {
            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(getItemAt(pos))
                }
            }
        }

        fun bind(transaction: TransactionEntity) {
            tvTransactionName.text = transaction.name
            tvTransactionDescription.text = transaction.description
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {

        override fun areItemsTheSame(
            oldItem: TransactionEntity,
            newItem: TransactionEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: TransactionEntity,
            newItem: TransactionEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
