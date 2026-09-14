package com.saad.capvid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.saad.capvid.ui.project.ProjectAdapter;
import com.saad.capvid.project.Project;
import com.saad.capvid.project.ProjectManager;

import java.util.List;

/**
 * Reached from the home screen's "Project" button. Tapping a saved project
 * reopens PreviewActivity with its videoUri + a projectId extra so the
 * editor restores trim/scale/style/words instead of starting fresh.
 */
public class ProjectListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        findViewById(R.id.btnExitProjects).setOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.projectList);
        View emptyState = findViewById(R.id.emptyState);
        list.setLayoutManager(new LinearLayoutManager(this));

        List<Project> projects = new ProjectManager(this).loadAll();
        emptyState.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);

        ProjectAdapter adapter = new ProjectAdapter(projects, this::openProject);
        list.setAdapter(adapter);
    }

    private void openProject(Project project) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("videoUri", Uri.parse(project.videoUri));
        intent.putExtra("projectId", project.id);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        finish();
    }
}
