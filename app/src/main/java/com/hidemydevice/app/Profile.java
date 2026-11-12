package com.hidemydevice.app;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * One named set of spoofed values. Only the active profile is mirrored into the flat preference
 * keys that {@link Hook} reads; the rest sit in {@link Prefs#PROFILES_DATA} as JSON.
 *
 * <p>Everything a profile holds is keyed by {@link Field#key}. The feature switches (enabled,
 * random each launch, the hiding toggles, proxy on/off) are deliberately global instead: they
 * describe how the module behaves, not which device it pretends to be.
 */
final class Profile {
    private final String id;
    private String name;
    private final Map<String, String> values = new HashMap<>();

    Profile(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    Profile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** A profile holding whatever is currently stored in the flat keys. */
    static Profile of(String id, String name, SharedPreferences prefs) {
        Profile profile = new Profile(id, name);
        for (Field field : Field.values()) {
            profile.setValue(field.key, Prefs.string(prefs, field.key));
        }
        return profile;
    }

    static Profile fromJson(JSONObject json) throws JSONException {
        Profile profile = new Profile(
                json.optString("id", UUID.randomUUID().toString()),
                json.optString("name", "Profile"));
        JSONObject stored = json.optJSONObject("values");
        if (stored != null) {
            for (Iterator<String> keys = stored.keys(); keys.hasNext(); ) {
                String key = keys.next();
                profile.setValue(key, stored.optString(key, ""));
            }
        }
        return profile;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("values", new JSONObject(values));
        return json;
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getValue(String key) {
        String value = values.get(key);
        return value != null ? value : "";
    }

    void setValue(String key, String value) {
        if (value == null || value.isEmpty()) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }
}
