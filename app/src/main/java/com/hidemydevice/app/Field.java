package com.hidemydevice.app;

import android.text.InputType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/** Every id the module can spoof. An empty stored value means "leave the real value alone". */
enum Field {
    IMEI(Category.DEVICE, "imei", "IMEI", "15 digits", "\\d{15}", InputType.TYPE_CLASS_NUMBER),
    HARDWARE_ID(Category.DEVICE, "serial", "Hardware ID (Serial)", "6-20 letters or digits", "[A-Za-z0-9]{6,20}", Field.TEXT),
    ANDROID_ID(Category.DEVICE, "android_id", "Android ID", "16 hex characters", "[0-9a-fA-F]{16}", Field.TEXT),
    ADVERTISING_ID(Category.DEVICE, "advertising_id", "Advertising ID", "UUID, e.g. 38400000-8cf0-11bd-b23e-10b96e40000d",
            "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", Field.TEXT),
    MEDIA_DRM(Category.DEVICE, "media_drm", "MediaDrm ID", "16-64 hex characters (even length)", "([0-9a-fA-F]{2}){8,32}", Field.TEXT),

    BRAND(Category.MODEL, "brand", "Device Brand", "Up to 32 characters", ".{1,32}", Field.TEXT),
    MANUFACTURER(Category.MODEL, "manufacturer", "Device Manufacturer", "Up to 32 characters", ".{1,32}", Field.TEXT),
    MODEL(Category.MODEL, "model", "Device Model", "Up to 32 characters", ".{1,32}", Field.TEXT),

    WIFI_MAC(Category.NETWORK, "wifi_mac", "MAC Address", "aa:bb:cc:dd:ee:ff", Field.MAC, Field.TEXT),
    BSSID(Category.NETWORK, "bssid", "MAC BSSID", "aa:bb:cc:dd:ee:ff", Field.MAC, Field.TEXT),
    SSID(Category.NETWORK, "ssid", "MAC SSID", "Wi-Fi network name, up to 32 characters", ".{1,32}", Field.TEXT),
    BT_MAC(Category.NETWORK, "bt_mac", "Bluetooth MAC", "AA:BB:CC:DD:EE:FF", Field.MAC, Field.TEXT),

    SIM_SERIAL(Category.SIM, "sim_serial", "SIM Serial (ICCID)", "18-20 digits", "\\d{18,20}", InputType.TYPE_CLASS_NUMBER),
    SUB_ID(Category.SIM, "sub_id", "SIM Subscriber ID (IMSI)", "14-15 digits", "\\d{14,15}", InputType.TYPE_CLASS_NUMBER),
    MOBILE_NO(Category.SIM, "mobile_no", "Mobile Number", "6-15 digits, optional leading +", "\\+?\\d{6,15}", InputType.TYPE_CLASS_PHONE),
    SIM_OPERATOR(Category.SIM, "sim_operator", "SIM Operator (MCC+MNC)", "5-6 digits", "\\d{5,6}", InputType.TYPE_CLASS_NUMBER),
    SIM_OPERATOR_NAME(Category.SIM, "sim_operator_name", "SIM Operator Name", "Up to 32 characters", ".{1,32}", Field.TEXT),
    SIM_COUNTRY(Category.SIM, "sim_country", "SIM Country", "2-letter ISO code, e.g. us", "[a-z]{2}", Field.TEXT),

    TIMEZONE(Category.LOCATION_GEO, Prefs.SPOOF_TIMEZONE, "Timezone", "A zone id, e.g. America/New_York", "[A-Za-z0-9_+/-]{3,40}", Field.TEXT),
    REGION(Category.LOCATION_GEO, Prefs.SPOOF_REGION, "Region / Country", "2-letter ISO code, e.g. US", "[A-Za-z]{2}", Field.TEXT),
    LATITUDE(Category.LOCATION_GEO, Prefs.SPOOF_LATITUDE, "GPS Latitude", "-90 to 90, e.g. 40.7128", "-?\\d{1,3}(\\.\\d{1,8})?", Field.COORDINATE),
    LONGITUDE(Category.LOCATION_GEO, Prefs.SPOOF_LONGITUDE, "GPS Longitude", "-180 to 180, e.g. -74.0060", "-?\\d{1,3}(\\.\\d{1,8})?", Field.COORDINATE),

    // Everything below is configuration rather than an identifier: "Random all" and
    // "new values every launch" leave it alone, so a proxy or a hide list keeps working.
    DEFAULT_KEYBOARD_ID(Category.KEYBOARD_APP, Prefs.DEFAULT_KEYBOARD_ID, "Virtual Default Keyboard ID",
            "e.g. com.google.android.inputmethod.latin/.LatinIME", ".{3,100}", Field.TEXT, Field.CONFIG),
    HIDDEN_KEYBOARDS(Category.KEYBOARD_APP, Prefs.HIDDEN_KEYBOARDS, "Hide Suspicious Keyboards",
            "Comma-separated package names", ".*", Field.TEXT, Field.CONFIG),

    HIDDEN_APPS(Category.SYSTEM_PRIVACY, Prefs.HIDDEN_APPS, "Hide Installed Apps",
            "Comma-separated package names", ".*", Field.TEXT, Field.CONFIG),

    VIRTUAL_GMAIL(Category.PROXIES_ACCOUNTS, Prefs.VIRTUAL_GMAIL_ACCOUNTS, "Virtual Gmail Accounts",
            "Comma-separated email addresses", ".*", Field.TEXT, Field.CONFIG),
    PROXY_HOST(Category.PROXIES_ACCOUNTS, Prefs.PROXY_HOST, "Proxy Host / IP",
            "e.g. 192.168.1.100", "[A-Za-z0-9.:_-]{3,100}", Field.TEXT, Field.CONFIG),
    PROXY_PORT(Category.PROXIES_ACCOUNTS, Prefs.PROXY_PORT, "Proxy Port",
            "1-65535", "\\d{1,5}", InputType.TYPE_CLASS_NUMBER, Field.CONFIG),
    PROXY_TYPE(Category.PROXIES_ACCOUNTS, Prefs.PROXY_TYPE, "Proxy Type",
            "HTTP or SOCKS", "(?i)(http|socks)", Field.TEXT, Field.CONFIG);

    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    private static final int COORDINATE = InputType.TYPE_CLASS_NUMBER
            | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final String MAC = "([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}";
    private static final boolean CONFIG = false;

    final Category category;
    final String key;
    final String title;
    final String hint;
    final int inputType;
    /** Whether bulk randomising ("Random all", "new values every launch") may touch this field. */
    final boolean randomizable;
    private final Pattern pattern;

    Field(Category category, String key, String title, String hint, String regex, int inputType) {
        this(category, key, title, hint, regex, inputType, true);
    }

    Field(Category category, String key, String title, String hint, String regex, int inputType,
            boolean randomizable) {
        this.category = category;
        this.key = key;
        this.title = title;
        this.hint = hint;
        this.inputType = inputType;
        this.randomizable = randomizable;
        this.pattern = Pattern.compile(regex);
    }

    boolean isValid(String value) {
        if (!pattern.matcher(value).matches()) {
            return false;
        }
        switch (this) {
            // A regex cannot tell "America/New_York" from "Atlantis/Nowhere", and an unknown zone
            // id silently resolves to GMT, so check it against the ones the platform knows.
            case TIMEZONE:
                return Zones.IDS.contains(value);
            case LATITUDE:
                return inRange(value, 90);
            case LONGITUDE:
                return inRange(value, 180);
            case PROXY_PORT:
                int port = Integer.parseInt(value);
                return port >= 1 && port <= 65535;
            default:
                return true;
        }
    }

    private static boolean inRange(String value, double limit) {
        try {
            double degrees = Double.parseDouble(value);
            return !Double.isNaN(degrees) && Math.abs(degrees) <= limit;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Built on first use - {@link TimeZone#getAvailableIDs()} allocates several hundred strings. */
    private static final class Zones {
        static final Set<String> IDS = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
    }
}
