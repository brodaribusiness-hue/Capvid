package com.saad.capvid.ui.template;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.saad.capvid.R;

import java.util.List;

/**
 * Horizontal chip list of available font assets for the Fonts tab.
 * Displayed name is the asset filename with the extension stripped and
 * separators replaced with spaces — good enough until a proper display-name
 * map is added alongside the next batch of uploaded font files.
 */
public class FontListAdapter extends RecyclerView.Adapter<FontListAdapter.VH> {

    public interface OnFontSelected {
        void onSelected(String fontAssetFileName);
    }

    private final List<String> fontAssets;
    private final OnFontSelected listener;
    private String selectedAsset;

    public FontListAdapter(List<String> fontAssets, String initiallySelected, OnFontSelected listener) {
        this.fontAssets = fontAssets;
        this.selectedAsset = initiallySelected;
        this.listener = listener;
    }

    public void setSelected(String asset) {
        this.selectedAsset = asset;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_font_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String asset = fontAssets.get(position);
        String display = asset.replaceAll("\\.(ttf|otf)$", "").replaceAll("[-_]", " ");
        holder.label.setText(display);

        boolean selected = asset.equals(selectedAsset);
        holder.label.setSelected(selected);
        holder.label.setTextColor(selected ? 0xFFFFFFFF : 0xFFB0B0B5);

        holder.itemView.setOnClickListener(v -> {
            selectedAsset = asset;
            notifyDataSetChanged();
            listener.onSelected(asset);
        });
    }

    @Override
    public int getItemCount() {
        return fontAssets.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView label;
        VH(View itemView) {
            super(itemView);
            label = (TextView) itemView;
        }
    }
}
