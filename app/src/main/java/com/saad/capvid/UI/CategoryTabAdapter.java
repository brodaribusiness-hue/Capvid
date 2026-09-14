package com.saad.capvid.ui.template;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.saad.capvid.R;
import com.saad.capvid.style.CaptionStyleCategory;

import java.util.List;

public class CategoryTabAdapter extends RecyclerView.Adapter<CategoryTabAdapter.VH> {

    public interface OnCategorySelected {
        void onSelected(CaptionStyleCategory category);
    }

    private final List<CaptionStyleCategory> categories;
    private final OnCategorySelected listener;
    private int selectedPosition = 1; // default to "Featured" (index 1, after Custom)

    public CategoryTabAdapter(List<CaptionStyleCategory> categories, OnCategorySelected listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CaptionStyleCategory category = categories.get(position);
        holder.label.setText(category.getLabel());
        boolean isSelected = position == selectedPosition;
        holder.label.setSelected(isSelected);
        holder.label.setTextColor(isSelected ? 0xFFFFFFFF : 0xFFB0B0B5);
        holder.itemView.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onSelected(category);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView label;
        VH(View itemView) {
            super(itemView);
            label = (TextView) itemView; // item_category_chip.xml root IS the TextView
        }
    }
}
