package com.saad.capvid.ui.project;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.saad.capvid.R;
import com.saad.capvid.project.Project;

import java.util.List;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {

    public interface OnProjectClick {
        void onClick(Project project);
    }

    private final List<Project> items;
    private final OnProjectClick listener;

    public ProjectAdapter(List<Project> items, OnProjectClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Project p = items.get(position);
        holder.name.setText(p.name);
        String when = DateUtils.getRelativeTimeSpanString(p.lastEditedMs).toString();
        holder.meta.setText("Edited " + when + " \u00B7 " + p.words.size() + " words");
        holder.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, meta;
        VH(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.projectName);
            meta = itemView.findViewById(R.id.projectMeta);
        }
    }
}
