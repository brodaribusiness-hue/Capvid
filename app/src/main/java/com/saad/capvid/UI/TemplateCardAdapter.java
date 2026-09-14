package com.saad.capvid.ui.template;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.saad.capvid.R;
import com.saad.capvid.font.FontManager;
import com.saad.capvid.style.CaptionStyleDefinition;

import java.util.List;

public class TemplateCardAdapter extends RecyclerView.Adapter<TemplateCardAdapter.VH> {

    public interface OnTemplateSelected {
        void onSelected(CaptionStyleDefinition def);
    }

    private final List<CaptionStyleDefinition> items;
    private final OnTemplateSelected listener;
    private String selectedId;

    public TemplateCardAdapter(List<CaptionStyleDefinition> items, String initiallySelectedId, OnTemplateSelected listener) {
        this.items = items;
        this.selectedId = initiallySelectedId;
        this.listener = listener;
    }

    public void updateItems(List<CaptionStyleDefinition> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CaptionStyleDefinition def = items.get(position);

        holder.name.setText(def.displayName);
        holder.liteBadge.setVisibility(def.isLite ? View.VISIBLE : View.GONE);
        holder.modernBadge.setVisibility(def.isModern ? View.VISIBLE : View.GONE);

        int[] c = def.swatchColors;
        holder.swatch0.setBackgroundColor(c[0]);
        holder.swatch1.setBackgroundColor(c[1]);
        holder.swatch2.setBackgroundColor(c[2]);
        holder.swatch3.setBackgroundColor(c[3]);

        // FontManager is a static utility in this project — FontManager.get(context, assetFileName)
        Typeface tf = FontManager.get(holder.itemView.getContext(), def.fontAsset);
        holder.preview.setTypeface(tf);
        holder.preview.bind(def, "on the live television");

        boolean isSelected = def.id.equals(selectedId);
        holder.itemView.setBackgroundResource(isSelected
                ? R.drawable.bg_template_card_selected
                : R.drawable.bg_template_card);

        holder.itemView.setOnClickListener(v -> {
            String previousId = selectedId;
            selectedId = def.id;
            notifyItemChanged(indexOf(previousId));
            notifyItemChanged(holder.getBindingAdapterPosition());
            listener.onSelected(def);
        });
    }

    private int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MiniStylePreviewView preview;
        TextView name, liteBadge, modernBadge;
        View swatch0, swatch1, swatch2, swatch3;

        VH(View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.previewView);
            name = itemView.findViewById(R.id.styleName);
            liteBadge = itemView.findViewById(R.id.liteBadge);
            modernBadge = itemView.findViewById(R.id.modernBadge);
            swatch0 = itemView.findViewById(R.id.swatch0);
            swatch1 = itemView.findViewById(R.id.swatch1);
            swatch2 = itemView.findViewById(R.id.swatch2);
            swatch3 = itemView.findViewById(R.id.swatch3);
        }
    }
}
