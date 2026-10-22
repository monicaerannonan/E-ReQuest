package com.example.request

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class TransactionProgressUiModel(
    val transactionId: String,
    val transactionName: String,
    val officeName: String,
    val completedCount: Int,
    val totalCount: Int,
    val percentage: Int,
    val isComplete: Boolean
)

class ProgressAdapter(
    private val onContinueClick: (TransactionProgressUiModel) -> Unit
) : ListAdapter<TransactionProgressUiModel, ProgressAdapter.ProgressViewHolder>(DiffCallback) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).transactionId.hashCode().toLong()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProgressViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_progress_transaction, parent, false)
        return ProgressViewHolder(
            itemView = itemView,
            onActionClick = onContinueClick,
            getItemAt = ::getItem
        )
    }

    override fun onBindViewHolder(
        holder: ProgressViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    class ProgressViewHolder(
        itemView: View,
        private val onActionClick: (TransactionProgressUiModel) -> Unit,
        private val getItemAt: (Int) -> TransactionProgressUiModel
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvProgressTransactionName: TextView =
            itemView.findViewById(R.id.tvProgressTransactionName)
        private val tvProgressOfficeName: TextView =
            itemView.findViewById(R.id.tvProgressOfficeName)
        private val pbTransactionProgress: ProgressBar =
            itemView.findViewById(R.id.pbTransactionProgress)
        private val tvProgressMetrics: TextView =
            itemView.findViewById(R.id.tvProgressMetrics)
        private val tvProgressStatus: TextView =
            itemView.findViewById(R.id.tvProgressStatus)
        private val btnContinueChecklist: Button =
            itemView.findViewById(R.id.btnContinueChecklist)

        init {
            val clickListener = View.OnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onActionClick(getItemAt(pos))
                }
            }
            itemView.setOnClickListener(clickListener)
            btnContinueChecklist.setOnClickListener(clickListener)
        }

        fun bind(item: TransactionProgressUiModel) {
            val context = itemView.context

            tvProgressTransactionName.text = item.transactionName
            tvProgressOfficeName.text = item.officeName.ifBlank { "School Service" }

            pbTransactionProgress.progress = item.percentage
            tvProgressMetrics.text =
                "${item.completedCount} of ${item.totalCount} requirements completed • ${item.percentage}%"

            if (item.isComplete) {
                tvProgressStatus.text = "✓ Requirements Complete"
                tvProgressStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.success)
                )
                btnContinueChecklist.text = "View Checklist"
            } else {
                tvProgressStatus.text = "In Progress"
                tvProgressStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.primary)
                )
                btnContinueChecklist.text = "Continue Checklist"
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TransactionProgressUiModel>() {

        override fun areItemsTheSame(
            oldItem: TransactionProgressUiModel,
            newItem: TransactionProgressUiModel
        ): Boolean {
            return oldItem.transactionId == newItem.transactionId
        }

        override fun areContentsTheSame(
            oldItem: TransactionProgressUiModel,
            newItem: TransactionProgressUiModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}
