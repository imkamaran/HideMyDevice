package com.hidemydevice.app;

import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The saved profiles, one of which is mirrored into the flat preference keys the hooks read.
 *
 * <p>Writes go through {@link SharedPreferences.Editor#commit()} rather than {@code apply()} so the
 * XML file is on disk before the user is told to restart the target apps.
 */
final class ProfileManager {
    private static final String DEFAULT_ID = "default";

    private final SharedPreferences prefs;
    private final List<Profile> profiles = new ArrayList<>();
    private Profile active;

    ProfileManager(SharedPreferences prefs) {
        this.prefs = prefs;
        reload();
    }

    /** Re-reads the stored profiles, e.g. after an import has replaced them. */
    void reload() {
        repairNonStringKeys();
        profiles.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(Prefs.PROFILES_DATA, "[]"));
            for (int i = 0; i < array.length(); i++) {
                profiles.add(Profile.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        if (profiles.isEmpty()) {
            // First run, or an upgrade from a build without profiles: adopt the values already
            // stored in the flat keys, so switching away and back does not wipe them.
            profiles.add(Profile.of(DEFAULT_ID, "Default profile", prefs));
            saveProfiles();
        }
        active = byId(prefs.getString(Prefs.ACTIVE_PROFILE_ID, DEFAULT_ID));
        if (active == null) {
            active = profiles.get(0);
        }
    }

    /**
     * Clears any field key an older build wrote under a non-String type. {@code proxy_port} used to
     * be stored as an int, which made every later {@code getString} on it throw.
     */
    private void repairNonStringKeys() {
        SharedPreferences.Editor editor = null;
        for (Field field : Field.values()) {
            if (!prefs.contains(field.key)) {
                continue;
            }
            try {
                prefs.getString(field.key, "");
            } catch (ClassCastException e) {
                if (editor == null) {
                    editor = prefs.edit();
                }
                editor.remove(field.key);
            }
        }
        if (editor != null) {
            editor.commit();
        }
    }

    List<Profile> getProfiles() {
        return profiles;
    }

    Profile getActiveProfile() {
        return active;
    }

    private Profile byId(String id) {
        for (Profile profile : profiles) {
            if (profile.getId().equals(id)) {
                return profile;
            }
        }
        return null;
    }

    void saveProfiles() {
        try {
            JSONArray array = new JSONArray();
            for (Profile profile : profiles) {
                array.put(profile.toJson());
            }
            prefs.edit().putString(Prefs.PROFILES_DATA, array.toString()).commit();
        } catch (Exception ignored) {
        }
    }

    Profile createProfile(String name) {
        Profile profile = new Profile(name);
        profiles.add(profile);
        saveProfiles();
        return profile;
    }

    /** @return false when {@code profile} is the only one left, which must stay. */
    boolean deleteProfile(Profile profile) {
        if (profiles.size() < 2 || !profiles.remove(profile)) {
            return false;
        }
        saveProfiles();
        if (profile.equals(active)) {
            setActiveProfile(profiles.get(0));
        }
        return true;
    }

    void setActiveProfile(Profile profile) {
        if (profile == null) {
            return;
        }
        active = profile;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Prefs.ACTIVE_PROFILE_ID, profile.getId());
        for (Field field : Field.values()) {
            editor.putString(field.key, profile.getValue(field.key));
        }
        editor.commit();
    }

    /**
     * Records {@code values} in the active profile. The caller is expected to write the same values
     * to the flat keys in one editor of its own, so this only touches the profile store.
     */
    void updateActiveProfile(Map<Field, String> values) {
        if (active == null) {
            return;
        }
        for (Map.Entry<Field, String> entry : values.entrySet()) {
            active.setValue(entry.getKey().key, entry.getValue());
        }
        saveProfiles();
    }
}
