package com.bro.brostore;

import android.graphics.drawable.Drawable;

public class AppModel {
    public Drawable icon;
    public String name;
    public String packageName;
    public String version;
    public UpdateInfo updateInfo; // neu

    public AppModel(Drawable icon, String name, String packageName, String version) {
        this.icon = icon;
        this.name = name;
        this.packageName = packageName;
        this.version = version;
    }
}


