# Entry point named in assets/xposed_init, instantiated by the Xposed framework
-keep class com.hidemydevice.app.Hook { *; }

# Hooked by name from Hook to report that the module is loaded
-keepclassmembers class com.hidemydevice.app.MainActivity {
    public static boolean isModuleActive();
}

# Provided by the framework at runtime (compileOnly)
-dontwarn de.robv.android.xposed.**
