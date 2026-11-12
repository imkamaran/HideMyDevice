package com.hidemydevice.app;

/** Groups the fields into the sections shown in the UI. */
enum Category {
    DEVICE(R.string.cat_device, R.drawable.ic_device),
    MODEL(R.string.cat_model, R.drawable.ic_model),
    NETWORK(R.string.cat_network, R.drawable.ic_network),
    SIM(R.string.cat_sim, R.drawable.ic_sim),
    LOCATION_GEO(R.string.cat_location_geo, R.drawable.ic_network),
    KEYBOARD_APP(R.string.cat_keyboard_app, R.drawable.ic_code),
    SYSTEM_PRIVACY(R.string.cat_system_privacy, R.drawable.ic_shield_on),
    PROXIES_ACCOUNTS(R.string.cat_proxies_accounts, R.drawable.ic_person);

    final int titleRes;
    final int iconRes;

    Category(int titleRes, int iconRes) {
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }
}
