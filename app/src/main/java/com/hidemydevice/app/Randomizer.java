package com.hidemydevice.app;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Generates plausible, correctly formatted random values. */
final class Randomizer {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789";
    private static final String HEX = "0123456789abcdef";
    private static final String SERIAL_CHARS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    /**
     * MCC+MNC, operator name, country ISO, country calling code, zone id and the latitude and
     * longitude of a large city in that country - so a randomised SIM, timezone, region and GPS
     * position all describe the same place. A device whose carrier, clock and coordinates disagree
     * stands out more than one that never spoofed them at all.
     */
    private static final String[][] OPERATORS = {
            {"310260", "T-Mobile", "us", "1", "America/New_York", "40.7128", "-74.0060"},
            {"310410", "AT&T", "us", "1", "America/Chicago", "41.8781", "-87.6298"},
            {"311480", "Verizon", "us", "1", "America/Los_Angeles", "34.0522", "-118.2437"},
            {"302720", "Rogers", "ca", "1", "America/Toronto", "43.6532", "-79.3832"},
            {"23415", "Vodafone UK", "gb", "44", "Europe/London", "51.5074", "-0.1278"},
            {"23410", "O2 - UK", "gb", "44", "Europe/London", "53.4808", "-2.2426"},
            {"26201", "Telekom.de", "de", "49", "Europe/Berlin", "52.5200", "13.4050"},
            {"20801", "Orange F", "fr", "33", "Europe/Paris", "48.8566", "2.3522"},
            {"22210", "Vodafone IT", "it", "39", "Europe/Rome", "41.9028", "12.4964"},
            {"40445", "Airtel", "in", "91", "Asia/Kolkata", "19.0760", "72.8777"},
            {"50501", "Telstra", "au", "61", "Australia/Sydney", "-33.8688", "151.2093"},
    };

    /** Build.BRAND, Build.MANUFACTURER, Build.MODEL. */
    private static final String[][] DEVICES = {
            {"google", "Google", "Pixel 7"},
            {"google", "Google", "Pixel 6a"},
            {"google", "Google", "Pixel 8 Pro"},
            {"samsung", "samsung", "SM-S918B"},
            {"samsung", "samsung", "SM-A546B"},
            {"samsung", "samsung", "SM-G991B"},
            {"OnePlus", "OnePlus", "CPH2449"},
            {"Xiaomi", "Xiaomi", "2201123G"},
            {"Redmi", "Xiaomi", "23021RAAEG"},
            {"motorola", "motorola", "moto g84 5G"},
            {"Nokia", "HMD Global", "Nokia G42 5G"},
    };

    private static final String[] SSID_PREFIXES = {
            "TP-Link", "NETGEAR", "Linksys", "ASUS", "DIRECT", "HomeWiFi", "Tenda", "dlink"
    };

    private Randomizer() {
    }

    /**
     * A new value for {@code field}, together with the fields that are only believable when they
     * change with it (operator code/name/country, device brand/manufacturer/model, and the
     * timezone/region/position that belong to the operator's country).
     *
     * @param operatorCode the MCC+MNC currently in use, so SIM and location values stay consistent
     *                     with it; may be empty
     */
    static Map<Field, String> linked(Field field, String operatorCode) {
        Map<Field, String> values = new EnumMap<>(Field.class);
        switch (field) {
            case SIM_OPERATOR:
            case SIM_OPERATOR_NAME:
            case SIM_COUNTRY:
                putOperator(values, pick(OPERATORS));
                break;
            case BRAND:
            case MANUFACTURER:
            case MODEL:
                putDevice(values, pick(DEVICES));
                break;
            case TIMEZONE:
            case REGION:
            case LATITUDE:
            case LONGITUDE: {
                String[] op = find(operatorCode);
                putLocation(values, op != null ? op : pick(OPERATORS));
                break;
            }
            default:
                values.put(field, single(field, operatorCode));
        }
        return values;
    }

    /**
     * Random values for every randomisable field, with the SIM, timezone, region and GPS position
     * all belonging to the same operator. Configuration fields ({@link Field#randomizable} false)
     * are left untouched.
     */
    static Map<Field, String> all() {
        String[] op = pick(OPERATORS);
        Map<Field, String> values = new EnumMap<>(Field.class);
        putOperator(values, op);
        putDevice(values, pick(DEVICES));
        for (Field field : Field.values()) {
            if (field.randomizable && !values.containsKey(field)) {
                values.put(field, single(field, op[0]));
            }
        }
        return values;
    }

    private static void putOperator(Map<Field, String> values, String[] op) {
        values.put(Field.SIM_OPERATOR, op[0]);
        values.put(Field.SIM_OPERATOR_NAME, op[1]);
        values.put(Field.SIM_COUNTRY, op[2]);
        // A new carrier moves the device to a new country, so the clock and position follow it.
        // The reverse is not true: picking a timezone leaves the SIM alone.
        putLocation(values, op);
    }

    private static void putLocation(Map<Field, String> values, String[] op) {
        values.put(Field.TIMEZONE, op[4]);
        values.put(Field.REGION, op[2].toUpperCase(Locale.ROOT));
        // Jitter the city centre by up to ~5km so repeated randomising is not a fixed landmark.
        values.put(Field.LATITUDE, jitter(op[5]));
        values.put(Field.LONGITUDE, jitter(op[6]));
    }

    private static String jitter(String degrees) {
        double value = Double.parseDouble(degrees) + (RANDOM.nextDouble() - 0.5) * 0.1;
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void putDevice(Map<Field, String> values, String[] device) {
        values.put(Field.BRAND, device[0]);
        values.put(Field.MANUFACTURER, device[1]);
        values.put(Field.MODEL, device[2]);
    }

    private static String single(Field field, String operatorCode) {
        switch (field) {
            case IMEI:
                return withLuhn("35" + chars(DIGITS, 12));
            case HARDWARE_ID:
                return chars(SERIAL_CHARS, 12);
            case WIFI_MAC:
            case BSSID:
                return mac();
            case BT_MAC:
                return mac().toUpperCase(Locale.ROOT);
            case SSID:
                return pick(SSID_PREFIXES) + "_" + chars(HEX, 4).toUpperCase(Locale.ROOT);
            case ANDROID_ID:
                return chars(HEX, 16);
            case ADVERTISING_ID:
                return UUID.randomUUID().toString();
            case SIM_SERIAL:
                return withLuhn("89" + chars(DIGITS, 16));
            case SUB_ID: {
                String code = Field.SIM_OPERATOR.isValid(operatorCode) ? operatorCode : pick(OPERATORS)[0];
                return code + chars(DIGITS, 15 - code.length());
            }
            case MOBILE_NO: {
                String[] op = find(operatorCode);
                if (op == null) {
                    op = pick(OPERATORS);
                }
                return "+" + op[3] + (2 + RANDOM.nextInt(8)) + chars(DIGITS, 9);
            }
            case MEDIA_DRM:
                return chars(HEX, 64);
            case TIMEZONE:
            case REGION:
            case LATITUDE:
            case LONGITUDE: {
                Map<Field, String> location = new EnumMap<>(Field.class);
                String[] op = find(operatorCode);
                putLocation(location, op != null ? op : pick(OPERATORS));
                return location.get(field);
            }
            case DEFAULT_KEYBOARD_ID:
                return "com.google.android.inputmethod.latin/.LatinIME";
            case HIDDEN_KEYBOARDS:
                return "com.baidu.input,com.sohu.inputmethod.sogou";
            case HIDDEN_APPS:
                return "com.topjohnwu.magisk,org.meowcat.edxp.manager";
            case VIRTUAL_GMAIL:
                return "user" + chars(DIGITS, 6) + "@gmail.com";
            case PROXY_HOST:
                return "192.168.1." + (10 + RANDOM.nextInt(200));
            case PROXY_PORT:
                return "8080";
            case PROXY_TYPE:
                return "HTTP";
            default:
                throw new IllegalArgumentException(field.name());
        }
    }

    private static <T> T pick(T[] items) {
        return items[RANDOM.nextInt(items.length)];
    }

    private static String[] find(String operatorCode) {
        for (String[] op : OPERATORS) {
            if (op[0].equals(operatorCode)) {
                return op;
            }
        }
        return null;
    }

    private static String chars(String alphabet, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String mac() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        // unicast, globally administered - looks like a real vendor address
        bytes[0] &= (byte) 0xFC;
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String withLuhn(String body) {
        int sum = 0;
        boolean dbl = true;
        for (int i = body.length() - 1; i >= 0; i--) {
            int d = body.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return body + ((10 - sum % 10) % 10);
    }
}
