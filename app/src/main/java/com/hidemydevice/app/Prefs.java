package com.hidemydevice.app;

import android.content.SharedPreferences;

/** Location and non-field keys of the preference file shared between the UI and the hooks. */
final class Prefs {
    /** The application id (not the Java package); must match applicationId in app/build.gradle. */
    static final String PACKAGE = "io.github.imkamaran.hidemydevice";
    static final String NAME = "ids";

    /** boolean, default true: master switch for all spoofing. */
    static final String ENABLED = "enabled";
    /** boolean, default false: replace every set value with a fresh random one per app start. */
    static final String RANDOM_EACH_LAUNCH = "random_each_launch";

    // Profiles
    static final String ACTIVE_PROFILE_ID = "active_profile_id";
    static final String PROFILES_DATA = "profiles_data";

    // Privacy & Spoofing Feature Switches
    static final String HIDE_VPN = "hide_vpn";
    static final String HIDE_DEV_MODE = "hide_dev_mode";
    static final String SPOOF_PLAY_STORE = "spoof_play_store";
    static final String SPOOF_LAN_SCAN = "spoof_lan_scan";
    static final String SPOOF_WIFI_SCANS = "spoof_wifi_scans";
    static final String REALISTIC_SENSORS = "realistic_sensors";

    // Configurable Values
    static final String HIDDEN_KEYBOARDS = "hidden_keyboards";
    static final String DEFAULT_KEYBOARD_ID = "default_keyboard_id";
    static final String SPOOF_TIMEZONE = "spoof_timezone";
    static final String SPOOF_REGION = "spoof_region";
    static final String SPOOF_LATITUDE = "spoof_latitude";
    static final String SPOOF_LONGITUDE = "spoof_longitude";
    static final String HIDDEN_APPS = "hidden_apps";
    static final String VIRTUAL_GMAIL_ACCOUNTS = "virtual_gmail_accounts";

    // Proxy Settings
    static final String PROXY_ENABLED = "proxy_enabled";
    static final String PROXY_HOST = "proxy_host";
    static final String PROXY_PORT = "proxy_port";
    static final String PROXY_TYPE = "proxy_type";

    /**
     * {@link SharedPreferences#getString} that tolerates a key an older build stored under another
     * type, instead of letting the {@link ClassCastException} escape into the hooked app.
     */
    static String string(SharedPreferences prefs, String key) {
        try {
            String value = prefs.getString(key, "");
            return value == null ? "" : value;
        } catch (ClassCastException e) {
            return "";
        }
    }

    private Prefs() {
    }
}
