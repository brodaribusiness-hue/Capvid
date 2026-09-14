package com.saad.capvid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Home screen: 2 buttons only, per spec — "Project" (saved sessions) and
 * "Create" (opens the Caption / Record Video choice on CreateActivity).
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialButton btnProject = findViewById(R.id.btnProject);
        MaterialButton btnCreate = findViewById(R.id.btnCreate);

        btnProject.setOnClickListener(v -> startActivity(new Intent(this, ProjectListActivity.class)));
        btnCreate.setOnClickListener(v -> startActivity(new Intent(this, CreateActivity.class)));
    }
}
