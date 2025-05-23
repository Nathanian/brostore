package com.bro.brostore;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private final List<AppModel> appList;

    public AppAdapter(List<AppModel> appList) {
        this.appList = appList;
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, version;
        Button updateBtn;
        Button rollbackBtn;

        public AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imageViewIcon);
            name = itemView.findViewById(R.id.textViewName);
            version = itemView.findViewById(R.id.textViewVersion);
            updateBtn = itemView.findViewById(R.id.buttonUpdate);
            rollbackBtn = itemView.findViewById(R.id.buttonRollback); // ➕ NEU
        }
    }

    @Override
    public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(AppViewHolder holder, int position) {
        AppModel app = appList.get(position);
        Context context = holder.itemView.getContext();

        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.name);
        holder.version.setText("Version: " + app.version);

        // 🔁 Backup-Datei prüfen
        File backupFile = new File(context.getExternalFilesDir("backups"),
                app.packageName + "/" + app.version + ".apk");

        if (backupFile.exists()) {
            holder.rollbackBtn.setVisibility(View.VISIBLE);
            holder.rollbackBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Backup installieren")
                        .setMessage("Vorherige Version wiederherstellen?")
                        .setPositiveButton("Ja", (dialog, which) -> {
                            ((MainActivity) context).installApk(context, backupFile);
                        })
                        .setNegativeButton("Abbrechen", null)
                        .show();
            });
        } else {
            holder.rollbackBtn.setVisibility(View.GONE);
        }

        // 📦 Update-Logik
        if (app.updateInfo != null) {
            String fileName = Uri.parse(app.updateInfo.apk_url).getLastPathSegment();
            File downloadedApk = new File(context.getExternalFilesDir("apks"), fileName);
            boolean alreadyDownloaded = downloadedApk.exists();
            boolean isSameVersion = false;

            if (alreadyDownloaded) {
                String downloadedVersion = getVersionFromApk(context, downloadedApk);
                isSameVersion = downloadedVersion != null && downloadedVersion.equals(app.updateInfo.version);
            }

            if (!isSameVersion) {
                holder.updateBtn.setVisibility(View.VISIBLE);

                if (alreadyDownloaded) {
                    holder.updateBtn.setText("Jetzt installieren");
                    holder.updateBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(context)
                                .setTitle("Installation")
                                .setMessage("Update wurde bereits geladen. Jetzt installieren?")
                                .setPositiveButton("Ja", (dialog, which) -> {
                                    ((MainActivity) context).installApk(context, downloadedApk);
                                })
                                .setNegativeButton("Abbrechen", null)
                                .show();
                    });
                } else {
                    holder.updateBtn.setText("Update verfügbar");
                    holder.updateBtn.setOnClickListener(v -> {
                        new AlertDialog.Builder(context)
                                .setTitle("Update auf " + app.updateInfo.version)
                                .setMessage(app.updateInfo.changelog)
                                .setPositiveButton("Download", (dialog, which) -> {
                                    ((MainActivity) context).backupApk(context, app.packageName, app.version);
                                    ((MainActivity) context).startApkDownload(context, app.updateInfo.apk_url);
                                })
                                .setNegativeButton("Abbrechen", null)
                                .show();
                    });
                }
            } else {
                holder.updateBtn.setVisibility(View.GONE);
            }

        } else {
            holder.updateBtn.setVisibility(View.GONE);
        }
    }


    private String getVersionFromApk(Context context, File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                return info.versionName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public int getItemCount() {
        return appList.size();
    }

    // 🔐 Methode zum Backup der alten APK
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startApkDownload(Context context, String apkUrl) {
        try {
            Uri uri = Uri.parse(apkUrl);
            String fileName = uri.getLastPathSegment(); // z. B. brofinder-1.1.apk

            File dir = new File(Environment.getExternalStorageDirectory(),
                    "BroStore/apks/" + apkUrl.split("/")[apkUrl.split("/").length - 1].replace(".apk", ""));

            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, fileName);

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("BroStore Update");
            request.setDescription("Lade Update herunter...");
            request.setDestinationUri(Uri.fromFile(file));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(request);

            Toast.makeText(context, "Download gestartet...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(context, "Download fehlgeschlagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

}
