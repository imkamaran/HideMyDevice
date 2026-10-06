package com.hidemydevice.app;

import android.text.InputType;

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
    SIM_COUNTRY(Category.SIM, "sim_country", "SIM Country", "2-letter ISO code, e.g. us", "[a-z]{2}", Field.TEXT);

    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    private static final String MAC = "([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}";

    final Category category;
    final String key;
    final String title;
    final String hint;
    final int inputType;
    private final Pattern pattern;

    Field(Category category, String key, String title, String hint, String regex, int inputType) {
        this.category = category;
        this.key = key;
        this.title = title;
        this.hint = hint;
        this.inputType = inputType;
        this.pattern = Pattern.compile(regex);
    }

    boolean isValid(String value) {
        return pattern.matcher(value).matches();
    }
}
