package com.hidemydevice.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaDrm;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed entry point (see assets/xposed_init). Runs inside every app selected in the module scope.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "HideMyDevice: ";
    private static final String ADVERTISING_INFO_CLASS =
            "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info";
    private static final String PACKAGE_MANAGER_CLASS = "android.app.ApplicationPackageManager";
    private static final String PLAY_STORE = "com.android.vending";

    private static final AtomicBoolean installed = new AtomicBoolean();
    private static final Random SENSOR_RANDOM = new Random();

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
        if ("android".equals(lpparam.packageName)) {
            return;
        }
        if (!installed.compareAndSet(false, true)) {
            return;
        }

        XSharedPreferences prefs = new XSharedPreferences(Prefs.PACKAGE, Prefs.NAME);
        if (!prefs.getFile().canRead()) {
            XposedBridge.log(TAG + "cannot read " + prefs.getFile() + " in " + lpparam.packageName);
            return;
        }
        if (!prefs.getBoolean(Prefs.ENABLED, true)) {
            return;
        }

        Map<Field, String> values = load(prefs);

        // Every hook installer below swallows its own failures: one unsupported API on one Android
        // version must not stop the rest of the module from being applied.
        hookTelephony(values);
        hookBuild(values);
        hookNetwork(values);
        hookSettings(values);
        hookMediaDrm(values);
        hookAdvertisingId(values, lpparam.classLoader);

        hookVpnHiding(prefs);
        hookDeveloperMode(prefs);
        hookPlayStoreInstaller(prefs);
        hookKeyboards(values);
        hookLanScan(prefs);
        hookWifiScans(prefs);
        hookGeoLocationTimezone(values);
        hookAccounts(values);
        hookHiddenApps(values);
        hookSensors(prefs);
        hookProxy(prefs, values);
    }

    private static Map<Field, String> load(XSharedPreferences prefs) {
        Map<Field, String> fresh =
                prefs.getBoolean(Prefs.RANDOM_EACH_LAUNCH, false) ? Randomizer.all() : null;
        Map<Field, String> values = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            String value = Prefs.string(prefs, field.key);
            if (value.isEmpty()) {
                continue;
            }
            // Randomising per launch only applies to identifiers. A re-rolled proxy address or
            // hide list would just break the feature the user configured.
            String replacement = fresh != null && field.randomizable ? fresh.get(field) : null;
            values.put(field, replacement != null ? replacement : value);
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
        String defaultIme = v.get(Field.DEFAULT_KEYBOARD_ID);

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
                } else if (defaultIme != null && Settings.Secure.DEFAULT_INPUT_METHOD.equals(name)) {
                    param.setResult(defaultIme);
                }
            }
        };

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

    private static void hookVpnHiding(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.HIDE_VPN, false)) {
            return;
        }
        hookAll(ConnectivityManager.class, "getNetworkCapabilities", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                NetworkCapabilities nc = (NetworkCapabilities) param.getResult();
                if (nc == null) {
                    return;
                }
                try {
                    XposedHelpers.callMethod(nc, "removeTransportType", NetworkCapabilities.TRANSPORT_VPN);
                    XposedHelpers.callMethod(nc, "addCapability", NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                } catch (Throwable ignored) {
                }
            }
        });

        hookAll(NetworkInterface.class, "getNetworkInterfaces", new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (result == null) {
                    return;
                }
                List<NetworkInterface> kept = new ArrayList<>();
                for (NetworkInterface ni : Collections.list((Enumeration<NetworkInterface>) result)) {
                    if (!isTunnel(ni.getName())) {
                        kept.add(ni);
                    }
                }
                param.setResult(Collections.enumeration(kept));
            }
        });
    }

    private static boolean isTunnel(String interfaceName) {
        String name = interfaceName.toLowerCase(Locale.ROOT);
        return name.startsWith("tun") || name.startsWith("ppp")
                || name.startsWith("wg") || name.startsWith("tap");
    }

    private static void hookDeveloperMode(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.HIDE_DEV_MODE, false)) {
            return;
        }
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 2) {
                    return;
                }
                String name = String.valueOf(param.args[1]);
                if (Settings.Global.DEVELOPMENT_SETTINGS_ENABLED.equals(name)
                        || Settings.Global.ADB_ENABLED.equals(name)
                        || "adb_wifi_enabled".equals(name)) {
                    param.setResult(param.method.getName().startsWith("getInt") ? (Object) 0 : "0");
                }
            }
        };
        // The *ForUser variants are what the framework actually routes to on recent releases; the
        // plain ones remain for older ones and for apps that reflect into them directly.
        hookAll(Settings.Global.class, "getInt", hook);
        hookAll(Settings.Global.class, "getIntForUser", hook);
        hookAll(Settings.Global.class, "getString", hook);
        hookAll(Settings.Global.class, "getStringForUser", hook);
    }

    private static void hookPlayStoreInstaller(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.SPOOF_PLAY_STORE, false)) {
            return;
        }
        Class<?> pm = XposedHelpers.findClassIfExists(PACKAGE_MANAGER_CLASS, null);
        if (pm == null) {
            XposedBridge.log(TAG + "no " + PACKAGE_MANAGER_CLASS);
            return;
        }
        hookAll(pm, "getInstallerPackageName", XC_MethodReplacement.returnConstant(PLAY_STORE));

        // Deprecated since API 30. An app that asks getInstallSourceInfo() instead still sees the
        // real installer unless its fields are rewritten too, which makes the spoof trivial to spot.
        hookAll(pm, "getInstallSourceInfo", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object info = param.getResult();
                if (info == null) {
                    return;
                }
                for (String field : new String[]{
                        "mInitiatingPackageName", "mInstallingPackageName", "mOriginatingPackageName",
                        "mUpdateOwnerPackageName"}) {
                    try {
                        XposedHelpers.setObjectField(info, field, PLAY_STORE);
                    } catch (Throwable ignored) {
                        // Field names differ between releases; set the ones that exist.
                    }
                }
            }
        });
    }

    private static void hookKeyboards(Map<Field, String> v) {
        Set<String> hidden = csv(v.get(Field.HIDDEN_KEYBOARDS));
        if (hidden.isEmpty()) {
            return;
        }
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                List<InputMethodInfo> list = (List<InputMethodInfo>) param.getResult();
                if (list == null) {
                    return;
                }
                List<InputMethodInfo> kept = new ArrayList<>();
                for (InputMethodInfo info : list) {
                    if (!hidden.contains(info.getPackageName())) {
                        kept.add(info);
                    }
                }
                param.setResult(kept);
            }
        };
        hookAll(InputMethodManager.class, "getInputMethodList", hook);
        hookAll(InputMethodManager.class, "getEnabledInputMethodList", hook);
    }

    private static void hookLanScan(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.SPOOF_LAN_SCAN, false)) {
            return;
        }
        // Loopback is deliberately left alone: apps run their own local servers on it (WebView
        // proxies, media servers), and blocking those breaks the app rather than hiding anything.
        hookAll(InetAddress.class, "isReachable", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                InetAddress address = (InetAddress) param.thisObject;
                if (address != null && address.isSiteLocalAddress()) {
                    param.setResult(false);
                }
            }
        });

        hookAll(Socket.class, "connect", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof InetSocketAddress)) {
                    return;
                }
                InetAddress address = ((InetSocketAddress) param.args[0]).getAddress();
                if (address != null && address.isSiteLocalAddress()) {
                    param.setThrowable(new SocketTimeoutException("Connection timed out"));
                }
            }
        });
    }

    private static void hookWifiScans(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.SPOOF_WIFI_SCANS, false)) {
            return;
        }
        // An empty list is what the platform itself returns without location permission, so it is
        // the one plausible answer available; fabricating ScanResults would not survive inspection.
        hookAll(WifiManager.class, "getScanResults",
                XC_MethodReplacement.returnConstant(new ArrayList<ScanResult>()));
    }

    private static void hookGeoLocationTimezone(Map<Field, String> v) {
        String zoneId = v.get(Field.TIMEZONE);
        if (zoneId != null) {
            TimeZone zone = TimeZone.getTimeZone(zoneId);
            if (!zone.getID().equalsIgnoreCase(zoneId)) {
                // getTimeZone falls back to GMT for an unknown id rather than failing, which would
                // silently put the app in UTC.
                XposedBridge.log(TAG + "unknown timezone " + zoneId);
            } else {
                try {
                    TimeZone.setDefault(zone);
                    // getDefault() is specified to hand back a copy; sharing one instance lets an
                    // app that calls setRawOffset on it corrupt every other caller's zone.
                    hookAll(TimeZone.class, "getDefault", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(zone.clone());
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "hookTimezone: " + t);
                }
            }
        }

        hookRegion(v.get(Field.REGION));

        hookLocation(v.get(Field.LATITUDE), v.get(Field.LONGITUDE));
    }

    /**
     * Reports {@code region} as the country of {@link Locale#getDefault()}, keeping whatever
     * language the device is really set to: forcing the language as well would change the whole app
     * UI, which is both visible to the user and a louder signal than the country it hides.
     *
     * <p>This has to be a hook rather than a {@link Locale#setDefault} call. The framework applies
     * the app's own configuration in {@code ActivityThread.handleBindApplication}, which runs after
     * this one and resets the default, so a value set here would simply be overwritten.
     */
    private static void hookRegion(String region) {
        if (region == null) {
            return;
        }
        String country = region.toUpperCase(Locale.ROOT);
        // getDefault() is called from String.format, toLowerCase and most of the formatting
        // classes, so the callback has to stay cheap. It normally hands back the same instance
        // every time, so remembering the last one seen avoids rebuilding the spoofed locale.
        AtomicReference<Locale[]> memo = new AtomicReference<>();
        hookAll(Locale.class, "getDefault", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Locale real = (Locale) param.getResult();
                if (real == null || country.equals(real.getCountry())) {
                    return;
                }
                // The {real, spoofed} pair is published as one array so a caller on another
                // thread can never read one half against the other's language.
                Locale[] cached = memo.get();
                if (cached == null || cached[0] != real) {
                    cached = new Locale[]{
                            real, new Locale(real.getLanguage(), country, real.getVariant())};
                    memo.set(cached);
                }
                param.setResult(cached[1]);
            }
        });
    }

    private static void hookLocation(String latitude, String longitude) {
        if (latitude == null || longitude == null) {
            if (latitude != null || longitude != null) {
                XposedBridge.log(TAG + "latitude and longitude must both be set to spoof a position");
            }
            return;
        }
        final double lat;
        final double lng;
        try {
            lat = Double.parseDouble(latitude);
            lng = Double.parseDouble(longitude);
        } catch (NumberFormatException e) {
            XposedBridge.log(TAG + "bad coordinates " + latitude + "," + longitude);
            return;
        }

        hookAll(Location.class, "getLatitude", XC_MethodReplacement.returnConstant(lat));
        hookAll(Location.class, "getLongitude", XC_MethodReplacement.returnConstant(lng));

        hookAll(LocationManager.class, "getLastKnownLocation", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Location location = (Location) param.getResult();
                if (location == null) {
                    // A fix with no timestamp or accuracy is rejected as stale by most callers, so
                    // the fabricated one has to look like it was taken just now.
                    location = new Location(LocationManager.GPS_PROVIDER);
                    location.setAccuracy(10f);
                    location.setTime(System.currentTimeMillis());
                    location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                }
                location.setLatitude(lat);
                location.setLongitude(lng);
                param.setResult(location);
            }
        });
    }

    private static void hookAccounts(Map<Field, String> v) {
        Set<String> emails = csv(v.get(Field.VIRTUAL_GMAIL));
        if (emails.isEmpty()) {
            return;
        }
        final Account[] accounts;
        try {
            // new Account("", type) throws, so a stray comma in the list would otherwise take down
            // the hooked app rather than this hook.
            List<Account> list = new ArrayList<>();
            for (String email : emails) {
                list.add(new Account(email, "com.google"));
            }
            accounts = list.toArray(new Account[0]);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hookAccounts: " + t);
            return;
        }

        hookAll(AccountManager.class, "getAccounts", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(accounts.clone());
            }
        });

        hookAll(AccountManager.class, "getAccountsByType", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0 && "com.google".equals(param.args[0])) {
                    param.setResult(accounts.clone());
                }
            }
        });
    }

    private static void hookHiddenApps(Map<Field, String> v) {
        Set<String> hidden = csv(v.get(Field.HIDDEN_APPS));
        if (hidden.isEmpty()) {
            return;
        }
        Class<?> pm = XposedHelpers.findClassIfExists(PACKAGE_MANAGER_CLASS, null);
        if (pm == null) {
            XposedBridge.log(TAG + "no " + PACKAGE_MANAGER_CLASS);
            return;
        }

        hookAll(pm, "getInstalledPackages", new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                List<PackageInfo> list = (List<PackageInfo>) param.getResult();
                if (list == null) {
                    return;
                }
                List<PackageInfo> kept = new ArrayList<>();
                for (PackageInfo info : list) {
                    if (!hidden.contains(info.packageName)) {
                        kept.add(info);
                    }
                }
                param.setResult(kept);
            }
        });

        hookAll(pm, "getInstalledApplications", new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                List<ApplicationInfo> list = (List<ApplicationInfo>) param.getResult();
                if (list == null) {
                    return;
                }
                List<ApplicationInfo> kept = new ArrayList<>();
                for (ApplicationInfo info : list) {
                    if (!hidden.contains(info.packageName)) {
                        kept.add(info);
                    }
                }
                param.setResult(kept);
            }
        });
    }

    private static void hookSensors(XSharedPreferences prefs) {
        if (!prefs.getBoolean(Prefs.REALISTIC_SENSORS, false)) {
            return;
        }
        Class<?> queue = XposedHelpers.findClassIfExists(
                "android.hardware.SystemSensorManager$SensorEventQueue", null);
        if (queue == null) {
            XposedBridge.log(TAG + "no SensorEventQueue");
            return;
        }
        hookAll(queue, "dispatchSensorEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 2 || !(param.args[1] instanceof float[])) {
                    return;
                }
                float[] values = (float[]) param.args[1];
                for (int i = 0; i < values.length; i++) {
                    float value = values[i];
                    // Only perturb a reading that is already a varying measurement. Adding noise to
                    // an exact 0 or 1 would corrupt the step counter, proximity and trigger
                    // sensors, whose values are counts and flags rather than magnitudes.
                    if (value != 0f && value != 1f && !Float.isNaN(value) && !Float.isInfinite(value)) {
                        values[i] = value + (SENSOR_RANDOM.nextFloat() - 0.5f) * 0.02f;
                    }
                }
            }
        });
    }

    private static void hookProxy(XSharedPreferences prefs, Map<Field, String> v) {
        if (!prefs.getBoolean(Prefs.PROXY_ENABLED, false)) {
            return;
        }
        String host = v.get(Field.PROXY_HOST);
        if (host == null) {
            XposedBridge.log(TAG + "proxy enabled but no host set");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(v.getOrDefault(Field.PROXY_PORT, "8080"));
        } catch (NumberFormatException e) {
            XposedBridge.log(TAG + "bad proxy port: " + v.get(Field.PROXY_PORT));
            return;
        }

        Proxy.Type type = "SOCKS".equalsIgnoreCase(v.get(Field.PROXY_TYPE))
                ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        List<Proxy> proxy =
                Collections.singletonList(new Proxy(type, InetSocketAddress.createUnresolved(host, port)));

        try {
            ProxySelector selector = new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    // Sending the app's own loopback and LAN traffic through an external proxy
                    // breaks it, so only route what actually leaves the device.
                    return isLocal(uri) ? Collections.singletonList(Proxy.NO_PROXY) : proxy;
                }

                @Override
                public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) {
                    XposedBridge.log(TAG + "proxy connect failed for " + uri + ": " + failure);
                }
            };
            ProxySelector.setDefault(selector);
            hookAll(ProxySelector.class, "getDefault", XC_MethodReplacement.returnConstant(selector));
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hookProxy: " + t);
        }
    }

    private static boolean isLocal(URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return true;
        }
        if ("localhost".equalsIgnoreCase(host) || host.startsWith("127.") || "::1".equals(host)) {
            return true;
        }
        return host.startsWith("10.") || host.startsWith("192.168.") || host.endsWith(".local");
    }

    /** The trimmed, non-empty entries of a comma-separated preference value. */
    private static Set<String> csv(String value) {
        Set<String> entries = new LinkedHashSet<>();
        if (value == null) {
            return entries;
        }
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

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
