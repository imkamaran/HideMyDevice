package com.hidemydevice.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import androidx.annotation.StringRes;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String BACKUP_FILE_NAME = "hide-my-device.json";
    private static final String BACKUP_PROFILES = "profiles";

    /** A global on/off switch: the view in the layout, its preference key and its default. */
    private static final class Toggle {
        final int viewId;
        final String key;
        final boolean defaultValue;

        Toggle(int viewId, String key, boolean defaultValue) {
            this.viewId = viewId;
            this.key = key;
            this.defaultValue = defaultValue;
        }
    }

    /**
     * Every switch on the screen, in layout order. Binding, refreshing, exporting and importing all
     * walk this one list, so a new toggle only has to be added here and to the layout.
     */
    private static final Toggle[] TOGGLES = {
            new Toggle(R.id.enabled, Prefs.ENABLED, true),
            new Toggle(R.id.random_each_launch, Prefs.RANDOM_EACH_LAUNCH, false),
            new Toggle(R.id.hide_vpn, Prefs.HIDE_VPN, false),
            new Toggle(R.id.hide_dev_mode, Prefs.HIDE_DEV_MODE, false),
            new Toggle(R.id.spoof_play_store, Prefs.SPOOF_PLAY_STORE, false),
            new Toggle(R.id.spoof_lan_scan, Prefs.SPOOF_LAN_SCAN, false),
            new Toggle(R.id.spoof_wifi_scans, Prefs.SPOOF_WIFI_SCANS, false),
            new Toggle(R.id.realistic_sensors, Prefs.REALISTIC_SENSORS, false),
            new Toggle(R.id.proxy_enabled, Prefs.PROXY_ENABLED, false),
    };

    private final Map<Field, TextView> valueViews = new EnumMap<>(Field.class);
    private final MaterialSwitch[] switchViews = new MaterialSwitch[TOGGLES.length];

    private SharedPreferences prefs;
    private ProfileManager profileManager;
    private boolean worldReadable;

    private View fab;
    private TextView statusText;
    private ImageView statusIcon;
    private MaterialCardView statusCard;
    private TextView profileNameText;

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

        try {
            prefs = getSharedPreferences(Prefs.NAME, MODE_WORLD_READABLE);
            worldReadable = true;
        } catch (SecurityException e) {
            prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        }
        profileManager = new ProfileManager(prefs);

        statusCard = findViewById(R.id.status_card);
        statusText = findViewById(R.id.status_text);
        statusIcon = findViewById(R.id.status_icon);
        profileNameText = findViewById(R.id.profile_name);

        findViewById(R.id.btn_switch_profile).setOnClickListener(v -> showSwitchProfileDialog());
        findViewById(R.id.btn_profile_menu).setOnClickListener(v -> showProfileMenu());

        fab = findViewById(R.id.random_all);
        fab.setOnClickListener(v -> save(Randomizer.all()));

        for (int i = 0; i < TOGGLES.length; i++) {
            switchViews[i] = findViewById(TOGGLES[i].viewId);
            bindSwitch(switchViews[i], TOGGLES[i]);
        }

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
                startActivity(new Intent(this, AboutActivity.class));
            } else {
                return false;
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ profiles

    private void showSwitchProfileDialog() {
        List<Profile> profiles = profileManager.getProfiles();
        String[] names = new String[profiles.size()];
        int selected = 0;
        Profile active = profileManager.getActiveProfile();
        for (int i = 0; i < profiles.size(); i++) {
            names[i] = profiles.get(i).getName();
            if (active != null && profiles.get(i).getId().equals(active.getId())) {
                selected = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_profile)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    profileManager.setActiveProfile(profiles.get(which));
                    dialog.dismiss();
                    commit(null, getString(R.string.saved));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showProfileMenu() {
        CharSequence[] actions = {
                getString(R.string.create_profile),
                getString(R.string.rename_profile),
                getString(R.string.delete_profile),
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showNameDialog(R.string.create_profile, "", name -> {
                            profileManager.setActiveProfile(profileManager.createProfile(name));
                            commit(null, getString(R.string.saved));
                        });
                    } else if (which == 1) {
                        showRenameProfileDialog();
                    } else {
                        confirmDeleteProfile();
                    }
                })
                .show();
    }

    private void showRenameProfileDialog() {
        Profile active = profileManager.getActiveProfile();
        if (active == null) {
            return;
        }
        showNameDialog(R.string.rename_profile, active.getName(), name -> {
            active.setName(name);
            profileManager.saveProfiles();
            updateProfileName();
        });
    }

    private void confirmDeleteProfile() {
        Profile active = profileManager.getActiveProfile();
        if (active == null) {
            return;
        }
        if (profileManager.getProfiles().size() < 2) {
            show(getString(R.string.delete_profile_last));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_profile)
                .setMessage(getString(R.string.delete_profile_message, active.getName()))
                .setPositiveButton(R.string.delete_profile, (dialog, which) -> {
                    profileManager.deleteProfile(active);
                    commit(null, getString(R.string.saved));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void showNameDialog(@StringRes int title, String initial, NameCallback callback) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit, null);
        TextInputLayout layout = view.findViewById(R.id.input_layout);
        TextInputEditText input = view.findViewById(R.id.input);
        layout.setHint(getString(R.string.profile_name));
        layout.setHelperText(null);
        input.setText(initial);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = String.valueOf(input.getText()).trim();
                    callback.onName(name.isEmpty() ? getString(R.string.new_profile) : name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateProfileName() {
        Profile active = profileManager.getActiveProfile();
        profileNameText.setText(active != null ? active.getName() : "");
    }

    // ------------------------------------------------------------------ state

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
        statusIcon.setImageTintList(ColorStateList.valueOf(fg));
    }

    private void bindSwitch(MaterialSwitch view, Toggle toggle) {
        view.setChecked(prefs.getBoolean(toggle.key, toggle.defaultValue));
        view.setOnCheckedChangeListener((button, checked) -> {
            if (prefs.getBoolean(toggle.key, toggle.defaultValue) != checked) {
                commit(prefs.edit().putBoolean(toggle.key, checked), getString(R.string.saved));
            }
        });
    }

    private void refresh() {
        showStatus();
        updateProfileName();

        for (int i = 0; i < TOGGLES.length; i++) {
            switchViews[i].setChecked(prefs.getBoolean(TOGGLES[i].key, TOGGLES[i].defaultValue));
        }

        for (Map.Entry<Field, TextView> entry : valueViews.entrySet()) {
            String value = Prefs.string(prefs, entry.getKey().key);
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
        // One JSON write for the whole batch: the profile store used to be re-serialised and
        // committed once per field, which meant around 25 synchronous writes per "Random all".
        profileManager.updateActiveProfile(values);
        commit(editor, getString(R.string.saved));
    }

    /** @param editor pending changes to write, or null when the values are already stored. */
    private void commit(SharedPreferences.Editor editor, CharSequence message) {
        if (editor != null) {
            editor.commit();
        }
        if (!worldReadable) {
            makeReadableForClassicXposed();
        }
        refresh();
        show(message);
    }

    private void show(CharSequence message) {
        Snackbar.make(fab, message, Snackbar.LENGTH_SHORT).setAnchorView(fab).show();
    }

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

    // ------------------------------------------------------------------ fields

    private void randomize(Field field) {
        save(Randomizer.linked(field, currentOperator()));
    }

    private String currentOperator() {
        return Prefs.string(prefs, Field.SIM_OPERATOR.key);
    }

    private boolean copy(Field field) {
        String value = Prefs.string(prefs, field.key);
        if (value.isEmpty()) {
            return false;
        }
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(field.title, value));
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
        input.setText(Prefs.string(prefs, field.key));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(field.title)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.random, null)
                .create();

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
                    Map<Field, String> empty = new EnumMap<>(Field.class);
                    for (Field field : Field.values()) {
                        empty.put(field, "");
                    }
                    save(empty);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ backup

    private void exportTo(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            for (Toggle toggle : TOGGLES) {
                json.put(toggle.key, prefs.getBoolean(toggle.key, toggle.defaultValue));
            }
            for (Field field : Field.values()) {
                json.put(field.key, Prefs.string(prefs, field.key));
            }
            JSONArray profiles = new JSONArray();
            for (Profile profile : profileManager.getProfiles()) {
                profiles.put(profile.toJson());
            }
            json.put(BACKUP_PROFILES, profiles);
            json.put(Prefs.ACTIVE_PROFILE_ID, Prefs.string(prefs, Prefs.ACTIVE_PROFILE_ID));

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

            Map<Field, String> values = new EnumMap<>(Field.class);
            int skipped = 0;
            for (Field field : Field.values()) {
                if (!json.has(field.key)) {
                    continue;
                }
                String value = json.optString(field.key, "").trim();
                if (value.isEmpty() || field.isValid(value)) {
                    values.put(field, value);
                } else {
                    skipped++;
                }
            }

            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<Field, String> entry : values.entrySet()) {
                editor.putString(entry.getKey().key, entry.getValue());
            }
            for (Toggle toggle : TOGGLES) {
                if (json.has(toggle.key)) {
                    editor.putBoolean(toggle.key, json.optBoolean(toggle.key, toggle.defaultValue));
                }
            }
            boolean hasProfiles = json.has(BACKUP_PROFILES);
            if (hasProfiles) {
                editor.putString(Prefs.PROFILES_DATA, json.getJSONArray(BACKUP_PROFILES).toString());
                editor.putString(Prefs.ACTIVE_PROFILE_ID, json.optString(Prefs.ACTIVE_PROFILE_ID, ""));
            }
            editor.commit();

            if (hasProfiles) {
                // The backup replaced the profile store, so drop the stale in-memory copy before
                // anything can write it back out, then mirror the active profile into the flat keys
                // so the two cannot disagree.
                profileManager.reload();
                profileManager.setActiveProfile(profileManager.getActiveProfile());
            } else {
                // A backup from a build without profiles: fold its values into the active one.
                profileManager.updateActiveProfile(values);
            }
            commit(null, getString(R.string.imported, values.size(), skipped));
        } catch (Exception e) {
            show(getString(R.string.import_failed, e.getMessage()));
        }
    }
}
