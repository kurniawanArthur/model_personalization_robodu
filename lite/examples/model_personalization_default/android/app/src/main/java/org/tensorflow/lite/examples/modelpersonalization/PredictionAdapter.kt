package org.tensorflow.lite.examples.modelpersonalization

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.tensorflow.lite.examples.modelpersonalization.databinding.ItemPredictionBinding
import org.tensorflow.lite.support.label.Category

class PredictionAdapter : ListAdapter<PredictionItem, PredictionAdapter.PredictionViewHolder>(PredictionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictionViewHolder {
        val binding = ItemPredictionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PredictionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PredictionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PredictionViewHolder(
        private val binding: ItemPredictionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PredictionItem) {
            binding.apply {
                tvClassName.text = item.className
                tvConfidence.text = String.format("%.0f%%", item.confidence * 100)

                // Show winner icon for top prediction
                if (item.isTopPrediction) {
                    tvWinnerIcon.visibility = View.VISIBLE
                    cardPrediction.setCardBackgroundColor(0xFFFFE0B2.toInt()) // Light orange
                    cardPrediction.cardElevation = 8f
                } else {
                    tvWinnerIcon.visibility = View.GONE
                    cardPrediction.setCardBackgroundColor(0xFFFFFFFF.toInt()) // White
                    cardPrediction.cardElevation = 4f
                }

                // Animate confidence bar
                val targetWidth = (item.confidence * 100).toInt()
                animateConfidenceBar(viewConfidenceBar, targetWidth)
            }
        }

        private fun animateConfidenceBar(view: View, targetWidth: Int) {
            val animator = ValueAnimator.ofInt(0, targetWidth)
            animator.duration = 500
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                val params = view.layoutParams
                params.width = (animation.animatedValue as Int)
                view.layoutParams = params
            }
            animator.start()
        }
    }

    private class PredictionDiffCallback : DiffUtil.ItemCallback<PredictionItem>() {
        override fun areItemsTheSame(oldItem: PredictionItem, newItem: PredictionItem): Boolean {
            return oldItem.className == newItem.className
        }

        override fun areContentsTheSame(oldItem: PredictionItem, newItem: PredictionItem): Boolean {
            return oldItem == newItem
        }
    }
}
