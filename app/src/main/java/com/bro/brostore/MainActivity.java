package com.bro.brostore;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private final List<AppModel> appList = new ArrayList<>();
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
        appList.clear();
        List<PackageInfo> packages = pm.getInstalledPackages(0);

        for (PackageInfo pkg : packages) {
            String pkgName = pkg.packageName;

            if (pkgName.startsWith("com.bro")) {
                String version = pkg.versionName;
                Drawable icon = pkg.applicationInfo.loadIcon(pm);
                String label = pkg.applicationInfo.loadLabel(pm).toString();

                AppModel app = new AppModel(icon, label, pkgName, version);
                appList.add(app);

                new Thread(() -> {
                    UpdateInfo info = fetchUpdateInfo(pkgName);
                    if (info != null && !info.version.equals(version)) {
                        app.updateInfo = info;
                        Log.d("BroStore", "UPDATE für " + label + ": " + version + " → " + info.version);
                        runOnUiThread(() -> {
                            adapter = new AppAdapter(appList);
                            recyclerView.setAdapter(adapter);
                        });
                    }
                }).start();
            }
        }

        adapter = new AppAdapter(appList);
        recyclerView.setAdapter(adapter);
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
                Log.e("BroStore", "Update JSON nicht gefunden für: " + pkgName);
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

            File backupDir = new File(context.getExternalFilesDir("backups"), packageName);
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

            Log.d("BroStore", "Backup gespeichert: " + backupFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("BroStore", "Backup fehlgeschlagen: " + e.getMessage());
        }
    }

    public void startApkDownload(Context context, String apkUrl) {
        try {
            Uri uri = Uri.parse(apkUrl);
            String fileName = uri.getLastPathSegment();
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

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        Toast.makeText(ctx, "Download abgeschlossen. Installation startet...", Toast.LENGTH_SHORT).show();
                        installApk(ctx, file);
                        ctx.unregisterReceiver(this);
                    }
                }
            };

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

        Uri apkUri = FileProvider.getUriForFile(context, "com.bro.brostore.fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
