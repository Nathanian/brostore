package com.bro.brostore;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
        checkStoragePermission();

        adapter = new AppAdapter(appList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    private void loadBroApps() {
        PackageManager pm = getPackageManager();
        appList.clear(); // Liste leeren vor dem Neubefüllen
        List<PackageInfo> packages = pm.getInstalledPackages(0);

        for (PackageInfo pkg : packages) {
            String pkgName = pkg.packageName;

            if (pkgName.startsWith("com.bro")) {
                String version = pkg.versionName;
                Drawable icon = pkg.applicationInfo.loadIcon(pm);
                String label = pkg.applicationInfo.loadLabel(pm).toString();

                AppModel app = new AppModel(icon, label, pkgName, version);
                appList.add(app); // Erstmal ohne UpdateInfo anzeigen

                // UpdateInfo im Hintergrund laden
                new Thread(() -> {
                    UpdateInfo info = fetchUpdateInfo(pkgName);
                    if (info != null && !info.version.equals(version)) {
                        app.updateInfo = info;
                        Log.d("BroStore", "UPDATE für " + label + ": " + version + " → " + info.version);

                        // RecyclerView nach UpdateInfo-Änderung neu setzen
                        runOnUiThread(() -> {
                            adapter = new AppAdapter(appList);
                            recyclerView.setAdapter(adapter);
                        });
                    }
                }).start();
            }
        }

        // Erste Anzeige direkt setzen (auch wenn kein Update da ist)
        adapter = new AppAdapter(appList);
        recyclerView.setAdapter(adapter);
    }

    private boolean isApkDownloaded(Context context, String fileName) {
        File file = new File(context.getExternalFilesDir("apks"), fileName);
        return file.exists();
    }

    public UpdateInfo fetchUpdateInfo(String pkgName) {
        try {
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

    public void startApkDownload(Context context, String apkUrl) {
        try {
            Uri uri = Uri.parse(apkUrl);
            String fileName = uri.getLastPathSegment(); // z. B. brofinder-1.1.apk

            // Zielordner innerhalb der App (kein WRITE_EXTERNAL_STORAGE nötig)
            File dir = context.getExternalFilesDir("apks");
            if (dir != null && !dir.exists()) dir.mkdirs();

            File file = new File(dir, fileName);

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("BroStore Update");
            request.setDescription("Lade Update herunter...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationUri(Uri.fromFile(file));
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = dm.enqueue(request);

            // BroadcastReceiver, der bei Downloadende triggert
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        Toast.makeText(ctx, "Download abgeschlossen. Installation startet...", Toast.LENGTH_SHORT).show();

                        // Installation vorbereiten
                        Uri apkUri = FileProvider.getUriForFile(
                                ctx,
                                "com.bro.brostore.fileprovider", // Muss mit dem Manifest übereinstimmen!
                                file
                        );

                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ctx.startActivity(installIntent);

                        // Receiver abmelden
                        ctx.unregisterReceiver(this);
                    }
                }
            };

            // Android 13+ verlangt Flag für registerReceiver()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                ContextCompat.registerReceiver(context, receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
            }

        } catch (Exception e) {
            Log.e("BroStore", "Download fehlgeschlagen: " + e.getMessage());
            Toast.makeText(context, "Download fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    public void installApk(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
                return;
            }
        }

        Uri apkUri = FileProvider.getUriForFile(
                context,
                "com.bro.brostore.fileprovider",
                file
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
