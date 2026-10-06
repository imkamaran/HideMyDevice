package com.hidemydevice.app;

import android.bluetooth.BluetoothAdapter;
import android.media.MediaDrm;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.NetworkInterface;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed entry point (see assets/xposed_init). Runs inside every app selected in the module scope.
 *
 * <p>The settings are read once, when the app process starts, and a hook is installed only for
 * the fields that have a value - unset fields and a disabled module cost nothing at runtime.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "HideMyDevice: ";
    private static final String ADVERTISING_INFO_CLASS =
            "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info";

    private static final AtomicBoolean installed = new AtomicBoolean();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (Prefs.PACKAGE.equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(MainActivity.class.getName(), lpparam.classLoader,
                        "isModuleActive", XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log(TAG + t);
            }
            return;
        }
        // Never spoof inside system_server; the hooks belong in the apps that read the ids.
        if ("android".equals(lpparam.packageName)) {
            return;
        }
        // A process can host several packages; the framework classes only need hooking once.
        if (!installed.compareAndSet(false, true)) {
            return;
        }

        XSharedPreferences prefs = new XSharedPreferences(Prefs.PACKAGE, Prefs.NAME);
        if (!prefs.getFile().canRead()) {
            XposedBridge.log(TAG + "cannot read " + prefs.getFile() + " in " + lpparam.packageName
                    + " - open the Hide My Device app once and save a value");
            return;
        }
        if (!prefs.getBoolean(Prefs.ENABLED, true)) {
            return;
        }
        Map<Field, String> values = load(prefs);
        if (values.isEmpty()) {
            return;
        }

        hookTelephony(values);
        hookBuild(values);
        hookNetwork(values);
        hookSettings(values);
        hookMediaDrm(values);
        hookAdvertisingId(values, lpparam.classLoader);
    }

    /** @return the fields to spoof in this process; unset fields are absent */
    private static Map<Field, String> load(XSharedPreferences prefs) {
        Map<Field, String> fresh =
                prefs.getBoolean(Prefs.RANDOM_EACH_LAUNCH, false) ? Randomizer.all() : null;
        Map<Field, String> values = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            String value = prefs.getString(field.key, "");
            if (value != null && !value.isEmpty()) {
                values.put(field, fresh != null ? fresh.get(field) : value);
            }
        }
        return values;
    }

    private static void hookTelephony(Map<Field, String> v) {
        Class<?> tm = TelephonyManager.class;
        constant(tm, "getDeviceId", v.get(Field.IMEI));
        constant(tm, "getImei", v.get(Field.IMEI));

        constant(tm, "getSimSerialNumber", v.get(Field.SIM_SERIAL));
        constant(SubscriptionInfo.class, "getIccId", v.get(Field.SIM_SERIAL));

        constant(tm, "getSubscriberId", v.get(Field.SUB_ID));

        constant(tm, "getLine1Number", v.get(Field.MOBILE_NO));
        constant(SubscriptionInfo.class, "getNumber", v.get(Field.MOBILE_NO));
        constant(SubscriptionManager.class, "getPhoneNumber", v.get(Field.MOBILE_NO));

        constant(tm, "getSimOperator", v.get(Field.SIM_OPERATOR));
        constant(tm, "getSimOperatorNumeric", v.get(Field.SIM_OPERATOR));
        constant(tm, "getNetworkOperator", v.get(Field.SIM_OPERATOR));
        constant(tm, "getNetworkOperatorForPhone", v.get(Field.SIM_OPERATOR));

        constant(tm, "getSimOperatorName", v.get(Field.SIM_OPERATOR_NAME));
        constant(tm, "getNetworkOperatorName", v.get(Field.SIM_OPERATOR_NAME));
        constant(tm, "getSimCarrierIdName", v.get(Field.SIM_OPERATOR_NAME));
        constant(tm, "getSimSpecificCarrierIdName", v.get(Field.SIM_OPERATOR_NAME));
        constant(SubscriptionInfo.class, "getCarrierName", v.get(Field.SIM_OPERATOR_NAME));
        constant(SubscriptionInfo.class, "getDisplayName", v.get(Field.SIM_OPERATOR_NAME));

        constant(SubscriptionInfo.class, "getCountryIso", v.get(Field.SIM_COUNTRY));
        constant(tm, "getSimCountryIso", v.get(Field.SIM_COUNTRY));
        constant(tm, "getSimCountryIsoForPhone", v.get(Field.SIM_COUNTRY));
        constant(tm, "getNetworkCountryIso", v.get(Field.SIM_COUNTRY));
        constant(tm, "getNetworkCountryIsoForPhone", v.get(Field.SIM_COUNTRY));
    }

    private static void hookBuild(Map<Field, String> v) {
        setBuildField("BRAND", v.get(Field.BRAND));
        setBuildField("MANUFACTURER", v.get(Field.MANUFACTURER));
        setBuildField("MODEL", v.get(Field.MODEL));

        String serial = v.get(Field.HARDWARE_ID);
        if (serial == null) {
            return;
        }
        setBuildField("SERIAL", serial);
        constant(Build.class, "getSerial", serial);
        try {
            Class<?> systemProperties = XposedHelpers.findClass("android.os.SystemProperties", null);
            hookAll(systemProperties, "get", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args.length > 0 ? param.args[0] : null;
                    if ("ro.serialno".equals(key) || "ro.boot.serialno".equals(key)) {
                        param.setResult(serial);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + t);
        }
    }

    private static void setBuildField(String name, String value) {
        if (value == null) {
            return;
        }
        try {
            XposedHelpers.setStaticObjectField(Build.class, name, value);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Build." + name + ": " + t);
        }
    }

    private static void hookNetwork(Map<Field, String> v) {
        constant(WifiInfo.class, "getMacAddress", v.get(Field.WIFI_MAC));
        constant(WifiInfo.class, "getBSSID", v.get(Field.BSSID));
        String ssid = v.get(Field.SSID);
        // WifiInfo reports UTF-8 network names wrapped in double quotes
        constant(WifiInfo.class, "getSSID", ssid == null ? null : "\"" + ssid + "\"");
        constant(BluetoothAdapter.class, "getAddress", v.get(Field.BT_MAC));

        byte[] wifiMac = macBytes(v.get(Field.WIFI_MAC));
        if (wifiMac != null) {
            hookAll(NetworkInterface.class, "getHardwareAddress", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if ("wlan0".equals(((NetworkInterface) param.thisObject).getName())) {
                        param.setResult(wifiMac.clone());
                    }
                }
            });
        }
    }

    private static void hookSettings(Map<Field, String> v) {
        String androidId = v.get(Field.ANDROID_ID);
        String btMac = v.get(Field.BT_MAC);
        if (androidId == null && btMac == null) {
            return;
        }
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 2) {
                    return;
                }
                Object name = param.args[1];
                if (androidId != null && Settings.Secure.ANDROID_ID.equals(name)) {
                    param.setResult(androidId);
                } else if (btMac != null && "bluetooth_address".equals(name)) {
                    param.setResult(btMac);
                }
            }
        };
        // getString() delegates to getStringForUser(); hook only one of them
        if (hookAll(Settings.Secure.class, "getStringForUser", hook) == 0) {
            hookAll(Settings.Secure.class, "getString", hook);
        }
    }

    private static void hookMediaDrm(Map<Field, String> v) {
        byte[] id = hexBytes(v.get(Field.MEDIA_DRM));
        if (id == null) {
            return;
        }
        hookAll(MediaDrm.class, "getPropertyByteArray", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0 && MediaDrm.PROPERTY_DEVICE_UNIQUE_ID.equals(param.args[0])) {
                    param.setResult(id.clone());
                }
            }
        });
    }

    /** The advertising id is read through a Google library bundled inside each app. */
    private static void hookAdvertisingId(Map<Field, String> v, ClassLoader appClassLoader) {
        String id = v.get(Field.ADVERTISING_ID);
        if (id == null) {
            return;
        }
        Class<?> info = XposedHelpers.findClassIfExists(ADVERTISING_INFO_CLASS, appClassLoader);
        if (info != null) {
            constant(info, "getId", id);
        }
    }

    /**
     * Makes every String/CharSequence-returning overload of {@code name} return {@code value};
     * null = no hook.
     */
    private static void constant(Class<?> cls, String name, String value) {
        if (value == null) {
            return;
        }
        try {
            XC_MethodHook hook = XC_MethodReplacement.returnConstant(value);
            for (Method method : cls.getDeclaredMethods()) {
                Class<?> type = method.getReturnType();
                if (method.getName().equals(name) && (type == String.class || type == CharSequence.class)
                        && !Modifier.isAbstract(method.getModifiers())) {
                    XposedBridge.hookMethod(method, hook);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + cls.getName() + "." + name + ": " + t);
        }
    }

    /** @return the number of overloads hooked; a missing method is simply not hooked */
    private static int hookAll(Class<?> cls, String name, XC_MethodHook hook) {
        try {
            return XposedBridge.hookAllMethods(cls, name, hook).size();
        } catch (Throwable t) {
            XposedBridge.log(TAG + cls.getName() + "." + name + ": " + t);
            return 0;
        }
    }

    private static byte[] macBytes(String mac) {
        return mac == null ? null : hexBytes(mac.replace(":", ""));
    }

    private static byte[] hexBytes(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            bytes[i] = (byte) (hi << 4 | lo);
        }
        return bytes;
    }
}
