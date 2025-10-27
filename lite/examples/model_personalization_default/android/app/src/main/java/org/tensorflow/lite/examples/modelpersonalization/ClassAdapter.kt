package org.tensorflow.lite.examples.modelpersonalization

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.tensorflow.lite.examples.modelpersonalization.databinding.ItemClassBinding

class ClassAdapter(
    private val onClassSelected: (TrainingClass) -> Unit,
    private val onEditClass: (TrainingClass) -> Unit,
    private val onDeleteClass: (TrainingClass) -> Unit
) : ListAdapter<TrainingClass, ClassAdapter.ClassViewHolder>(ClassDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ItemClassBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClassViewHolder(
        private val binding: ItemClassBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(trainingClass: TrainingClass) {
            binding.apply {
                rbSelected.isChecked = trainingClass.isSelected
                tvClassName.text = trainingClass.name
                tvSampleCount.text = itemView.context.getString(
                    R.string.sample_count_format,
                    trainingClass.sampleCount
                )

                // Select class on item click or radio button click
                root.setOnClickListener {
                    onClassSelected(trainingClass)
                }
                rbSelected.setOnClickListener {
                    onClassSelected(trainingClass)
                }

                // Edit class name
                btnEdit.setOnClickListener {
                    onEditClass(trainingClass)
                }

                // Delete class
                btnDelete.setOnClickListener {
                    onDeleteClass(trainingClass)
                }
            }
        }
    }

    private class ClassDiffCallback : DiffUtil.ItemCallback<TrainingClass>() {
        override fun areItemsTheSame(oldItem: TrainingClass, newItem: TrainingClass): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrainingClass, newItem: TrainingClass): Boolean {
            return oldItem == newItem
        }
    }
}
