package com.hidemydevice.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String BACKUP_FILE_NAME = "hide-my-device.json";

    private final Map<Field, TextView> valueViews = new EnumMap<>(Field.class);
    private SharedPreferences prefs;
    private boolean worldReadable;
    private View fab;
    private TextView statusText;
    private ImageView statusIcon;
    private MaterialCardView statusCard;
    private MaterialSwitch enabledSwitch;
    private MaterialSwitch randomEachLaunchSwitch;

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::exportTo);
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importFrom);

    /** Replaced by {@link Hook} to return true when the framework has loaded the module. */
    public static boolean isModuleActive() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // LSPosed permits MODE_WORLD_READABLE for enabled modules and moves the file to a place
        // the hooked apps can read. Without it Android throws a SecurityException.
        try {
            prefs = getSharedPreferences(Prefs.NAME, MODE_WORLD_READABLE);
            worldReadable = true;
        } catch (SecurityException e) {
            prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        }

        statusCard = findViewById(R.id.status_card);
        statusText = findViewById(R.id.status_text);
        statusIcon = findViewById(R.id.status_icon);

        fab = findViewById(R.id.random_all);
        fab.setOnClickListener(v -> save(Randomizer.all()));

        enabledSwitch = findViewById(R.id.enabled);
        randomEachLaunchSwitch = findViewById(R.id.random_each_launch);
        bindSwitch(enabledSwitch, Prefs.ENABLED, true);
        bindSwitch(randomEachLaunchSwitch, Prefs.RANDOM_EACH_LAUNCH, false);

        ViewGroup list = findViewById(R.id.list);
        LayoutInflater inflater = getLayoutInflater();
        Category lastCategory = null;
        for (Field field : Field.values()) {
            if (field.category != lastCategory) {
                lastCategory = field.category;
                View header = inflater.inflate(R.layout.item_header, list, false);
                ((TextView) header.findViewById(R.id.header_title)).setText(field.category.titleRes);
                ((ImageView) header.findViewById(R.id.header_icon)).setImageResource(field.category.iconRes);
                list.addView(header);
            }
            View item = inflater.inflate(R.layout.item_field, list, false);
            ((TextView) item.findViewById(R.id.title)).setText(field.title);
            valueViews.put(field, item.findViewById(R.id.value));
            item.findViewById(R.id.edit).setOnClickListener(v -> edit(field));
            item.findViewById(R.id.random).setOnClickListener(v -> randomize(field));
            item.setOnClickListener(v -> edit(field));
            item.setOnLongClickListener(v -> copy(field));
            list.addView(item);
        }
        refresh();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.clear_all) {
                confirmClear();
            } else if (id == R.id.export_values) {
                exportLauncher.launch(BACKUP_FILE_NAME);
            } else if (id == R.id.import_values) {
                importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
            } else if (id == R.id.about) {
                startActivity(new android.content.Intent(this, AboutActivity.class));
            } else {
                return false;
            }
            return true;
        });
    }

    private void showStatus() {
        boolean active = worldReadable || isModuleActive();
        statusText.setText(active ? R.string.status_active : R.string.status_inactive);
        statusIcon.setImageResource(active ? R.drawable.ic_shield_on : R.drawable.ic_shield_off);
        int bg = MaterialColors.getColor(statusCard, active
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer);
        int fg = MaterialColors.getColor(statusCard, active
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer);
        statusCard.setCardBackgroundColor(bg);
        statusText.setTextColor(fg);
        statusIcon.setImageTintList(android.content.res.ColorStateList.valueOf(fg));
    }

    private void bindSwitch(MaterialSwitch view, String key, boolean defaultValue) {
        view.setChecked(prefs.getBoolean(key, defaultValue));
        view.setOnCheckedChangeListener((button, checked) -> {
            // also fires when refresh() syncs the switch to an imported value
            if (prefs.getBoolean(key, defaultValue) != checked) {
                commit(prefs.edit().putBoolean(key, checked), getString(R.string.saved));
            }
        });
    }

    private void refresh() {
        showStatus();
        enabledSwitch.setChecked(prefs.getBoolean(Prefs.ENABLED, true));
        randomEachLaunchSwitch.setChecked(prefs.getBoolean(Prefs.RANDOM_EACH_LAUNCH, false));
        for (Map.Entry<Field, TextView> entry : valueViews.entrySet()) {
            String value = prefs.getString(entry.getKey().key, "");
            TextView view = entry.getValue();
            if (value.isEmpty()) {
                view.setText(R.string.not_set);
                view.setAlpha(0.6f);
            } else {
                view.setText(value);
                view.setAlpha(1f);
            }
        }
    }

    private void save(Map<Field, String> values) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<Field, String> entry : values.entrySet()) {
            editor.putString(entry.getKey().key, entry.getValue());
        }
        commit(editor, getString(R.string.saved));
    }

    private void commit(SharedPreferences.Editor editor, CharSequence message) {
        // commit(): the file must be on disk before a target app is started
        editor.commit();
        if (!worldReadable) {
            makeReadableForClassicXposed();
        }
        refresh();
        show(message);
    }

    private void show(CharSequence message) {
        Snackbar.make(fab, message, Snackbar.LENGTH_SHORT).setAnchorView(fab).show();
    }

    /** Best effort for frameworks without LSPosed's preference redirection. */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void makeReadableForClassicXposed() {
        File dataDir = new File(getApplicationInfo().dataDir);
        File prefsDir = new File(dataDir, "shared_prefs");
        File file = new File(prefsDir, Prefs.NAME + ".xml");
        dataDir.setExecutable(true, false);
        prefsDir.setExecutable(true, false);
        prefsDir.setReadable(true, false);
        file.setReadable(true, false);
    }

    private void randomize(Field field) {
        save(Randomizer.linked(field, currentOperator()));
    }

    private String currentOperator() {
        return prefs.getString(Field.SIM_OPERATOR.key, "");
    }

    private boolean copy(Field field) {
        String value = prefs.getString(field.key, "");
        if (value.isEmpty()) {
            return false;
        }
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(field.title, value));
        // Android 13+ shows its own clipboard confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            show(getString(R.string.copied, field.title));
        }
        return true;
    }

    private void edit(Field field) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit, null);
        TextInputLayout layout = view.findViewById(R.id.input_layout);
        TextInputEditText input = view.findViewById(R.id.input);
        layout.setHelperText(field.hint);
        input.setInputType(field.inputType);
        input.setText(prefs.getString(field.key, ""));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(field.title)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.random, null)
                .create();
        // Listeners are attached after show() so these buttons do not auto-dismiss the dialog
        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                input.setText(Randomizer.linked(field, currentOperator()).get(field));
                layout.setError(null);
            });
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = String.valueOf(input.getText()).trim();
                if (!value.isEmpty() && !field.isValid(value)) {
                    layout.setError(getString(R.string.invalid_value, field.hint));
                    return;
                }
                save(Collections.singletonMap(field, value));
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_all)
                .setMessage(R.string.clear_all_message)
                .setPositiveButton(R.string.clear_all, (d, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (Field field : Field.values()) {
                        editor.remove(field.key);
                    }
                    commit(editor, getString(R.string.saved));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportTo(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put(Prefs.ENABLED, prefs.getBoolean(Prefs.ENABLED, true));
            json.put(Prefs.RANDOM_EACH_LAUNCH, prefs.getBoolean(Prefs.RANDOM_EACH_LAUNCH, false));
            for (Field field : Field.values()) {
                json.put(field.key, prefs.getString(field.key, ""));
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            show(getString(R.string.exported));
        } catch (Exception e) {
            show(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void importFrom(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    bytes.write(buffer, 0, n);
                }
            }
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));

            SharedPreferences.Editor editor = prefs.edit();
            int imported = 0;
            int skipped = 0;
            for (Field field : Field.values()) {
                if (!json.has(field.key)) {
                    continue;
                }
                String value = json.optString(field.key, "").trim();
                if (value.isEmpty() || field.isValid(value)) {
                    editor.putString(field.key, value);
                    imported++;
                } else {
                    skipped++;
                }
            }
            if (json.has(Prefs.ENABLED)) {
                editor.putBoolean(Prefs.ENABLED, json.optBoolean(Prefs.ENABLED, true));
            }
            if (json.has(Prefs.RANDOM_EACH_LAUNCH)) {
                editor.putBoolean(Prefs.RANDOM_EACH_LAUNCH, json.optBoolean(Prefs.RANDOM_EACH_LAUNCH, false));
            }
            commit(editor, getString(R.string.imported, imported, skipped));
        } catch (Exception e) {
            show(getString(R.string.import_failed, e.getMessage()));
        }
    }
}
