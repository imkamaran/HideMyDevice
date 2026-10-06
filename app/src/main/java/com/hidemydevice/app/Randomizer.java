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

    /** MCC+MNC, operator name, country ISO, country calling code. */
    private static final String[][] OPERATORS = {
            {"310260", "T-Mobile", "us", "1"},
            {"310410", "AT&T", "us", "1"},
            {"311480", "Verizon", "us", "1"},
            {"302720", "Rogers", "ca", "1"},
            {"23415", "Vodafone UK", "gb", "44"},
            {"23410", "O2 - UK", "gb", "44"},
            {"26201", "Telekom.de", "de", "49"},
            {"20801", "Orange F", "fr", "33"},
            {"22210", "Vodafone IT", "it", "39"},
            {"40445", "Airtel", "in", "91"},
            {"50501", "Telstra", "au", "61"},
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
     * change with it (operator code/name/country, device brand/manufacturer/model).
     *
     * @param operatorCode the MCC+MNC currently in use, so SIM values stay consistent with it;
     *                     may be empty
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
            default:
                values.put(field, single(field, operatorCode));
        }
        return values;
    }

    /** Random values for every field, with all SIM values belonging to the same operator. */
    static Map<Field, String> all() {
        String[] op = pick(OPERATORS);
        Map<Field, String> values = new EnumMap<>(Field.class);
        putOperator(values, op);
        putDevice(values, pick(DEVICES));
        for (Field field : Field.values()) {
            if (!values.containsKey(field)) {
                values.put(field, single(field, op[0]));
            }
        }
        return values;
    }

    private static void putOperator(Map<Field, String> values, String[] op) {
        values.put(Field.SIM_OPERATOR, op[0]);
        values.put(Field.SIM_OPERATOR_NAME, op[1]);
        values.put(Field.SIM_COUNTRY, op[2]);
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
