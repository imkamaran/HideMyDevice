package com.hidemydevice.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;

public class AboutActivity extends AppCompatActivity {
    static final String LINKEDIN_URL = "https://www.linkedin.com/in/kamaran/";
    static final String GITHUB_URL = "https://github.com/imkamaran/";
    static final String SOURCE_URL = "https://github.com/imkamaran/HideMyDevice";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.version)).setText(getString(R.string.about_version, versionName()));

        findViewById(R.id.linkedin).setOnClickListener(v -> open(LINKEDIN_URL));
        findViewById(R.id.github).setOnClickListener(v -> open(GITHUB_URL));
        findViewById(R.id.source).setOnClickListener(v -> open(SOURCE_URL));
        findViewById(R.id.issues).setOnClickListener(v -> open(SOURCE_URL + "/issues"));
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(findViewById(R.id.toolbar), R.string.no_browser, Snackbar.LENGTH_SHORT).show();
        }
    }
}
