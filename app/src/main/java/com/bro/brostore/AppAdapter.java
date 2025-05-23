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
            rollbackBtn = itemView.findViewById(R.id.buttonRollback);
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

        // 🔁 Rollback prüfen
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

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // ✅ Version aus einer APK-Datei lesen
    private String getVersionFromApk(Context context, File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) return info.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

