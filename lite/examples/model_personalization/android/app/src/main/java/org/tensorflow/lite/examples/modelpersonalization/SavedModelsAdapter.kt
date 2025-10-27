package org.tensorflow.lite.examples.modelpersonalization

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.tensorflow.lite.examples.modelpersonalization.databinding.ItemSavedModelBinding

class SavedModelsAdapter(
    private val onLoad: (ModelManager.ModelInfo) -> Unit,
    private val onDelete: (ModelManager.ModelInfo) -> Unit
) : RecyclerView.Adapter<SavedModelsAdapter.ViewHolder>() {

    private val models = mutableListOf<ModelManager.ModelInfo>()

    fun submitList(items: List<ModelManager.ModelInfo>) {
        models.clear()
        models.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedModelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = models.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(models[position])
    }

    inner class ViewHolder(
        private val binding: ItemSavedModelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(modelInfo: ModelManager.ModelInfo) {
            binding.tvModelName.text = modelInfo.getDisplayName()
            binding.tvModelClasses.text = binding.root.context.getString(
                R.string.item_model_classes,
                modelInfo.classNames.joinToString(", ") { it.ifBlank { binding.root.context.getString(R.string.class_label_placeholder) } }
            )
            binding.tvModelInfo.text = binding.root.context.getString(
                R.string.item_model_meta,
                modelInfo.numSamples,
                modelInfo.getSizeInKB()
            )

            binding.btnLoadModel.setOnClickListener { onLoad(modelInfo) }
            binding.btnDeleteModel.setOnClickListener { onDelete(modelInfo) }
        }
    }
}
