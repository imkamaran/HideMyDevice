package com.hidemydevice.app;

/** Location and non-field keys of the preference file shared between the UI and the hooks. */
final class Prefs {
    static final String PACKAGE = "com.hidemydevice.app";
    static final String NAME = "ids";

    /** boolean, default true: master switch for all spoofing. */
    static final String ENABLED = "enabled";
    /** boolean, default false: replace every set value with a fresh random one per app start. */
    static final String RANDOM_EACH_LAUNCH = "random_each_launch";

    private Prefs() {
    }
}
