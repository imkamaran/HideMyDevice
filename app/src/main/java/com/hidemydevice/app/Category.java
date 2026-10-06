package com.hidemydevice.app;

/** Groups the fields into the sections shown in the UI. */
enum Category {
    DEVICE(R.string.cat_device, R.drawable.ic_device),
    MODEL(R.string.cat_model, R.drawable.ic_model),
    NETWORK(R.string.cat_network, R.drawable.ic_network),
    SIM(R.string.cat_sim, R.drawable.ic_sim);

    final int titleRes;
    final int iconRes;

    Category(int titleRes, int iconRes) {
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }
}
