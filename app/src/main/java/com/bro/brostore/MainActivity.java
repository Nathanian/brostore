package com.bro.brostore;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<AppModel> appList = new ArrayList<>();
    private RecyclerView recyclerView;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recyclerView);

        loadBroApps();

        adapter = new AppAdapter(appList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadBroApps() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);

        for (PackageInfo pkg : packages) {
            String pkgName = pkg.packageName;

            if (pkgName.startsWith("com.bro")) {
                String version = pkg.versionName;
                Drawable icon = pkg.applicationInfo.loadIcon(pm);
                String label = pkg.applicationInfo.loadLabel(pm).toString();

                // Neues AppModel-Objekt erstellen
                AppModel app = new AppModel(icon, label, pkgName, version);

                // Zuerst ohne UpdateInfo zur Liste hinzufügen
                appList.add(app);

                // Dann im Hintergrund die Update-Info laden
                new Thread(() -> {
                    UpdateInfo info = fetchUpdateInfo(pkgName);
                    if (info != null && !info.version.equals(version)) {
                        app.updateInfo = info;
                        Log.d("BroStore", "UPDATE für " + label + ": " + version + " → " + info.version);

                        // UI aktualisieren
                        runOnUiThread(() -> adapter.notifyDataSetChanged());
                    }
                }).start();
            }
        }
    }



    public UpdateInfo fetchUpdateInfo(String pkgName) {
        try {
            // Beispiel: https://nathanian.github.io/brostore/updates/com.bro.brofinder.json
            String baseUrl = "https://nathanian.github.io/brostore/updates/";
            URL url = new URL(baseUrl + pkgName + ".json");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                InputStream input = conn.getInputStream();
                InputStreamReader reader = new InputStreamReader(input);
                return new Gson().fromJson(reader, UpdateInfo.class);
            } else {
                System.err.println("Update JSON not found for: " + pkgName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    public void backupApk(Context context, String packageName, String version) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            File sourceApk = new File(appInfo.sourceDir);

            File backupDir = new File(Environment.getExternalStorageDirectory(),
                    "BroStore/backups/" + packageName);
            if (!backupDir.exists()) backupDir.mkdirs();

            File backupFile = new File(backupDir, version + ".apk");

            try (InputStream in = new FileInputStream(sourceApk);
                 OutputStream out = new FileOutputStream(backupFile)) {

                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            Log.d("BroStore", "APK gesichert unter: " + backupFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("BroStore", "Backup fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
