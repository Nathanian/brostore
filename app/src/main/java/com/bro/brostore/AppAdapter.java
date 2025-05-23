package com.bro.brostore;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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

        public AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imageViewIcon);
            name = itemView.findViewById(R.id.textViewName);
            version = itemView.findViewById(R.id.textViewVersion);
            updateBtn = itemView.findViewById(R.id.buttonUpdate);
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
        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.name);
        holder.version.setText("Version: " + app.version);

        if (app.updateInfo != null) {
            holder.updateBtn.setVisibility(View.VISIBLE);

            holder.updateBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("Update auf " + app.updateInfo.version)
                        .setMessage(app.updateInfo.changelog)
                        .setPositiveButton("Update jetzt", (dialog, which) -> {
                            backupApk(holder.itemView.getContext(), app.packageName, app.version);
                            startApkDownload(holder.itemView.getContext(), app.updateInfo.apk_url);
                        })
                        .setNegativeButton("Abbrechen", null)
                        .show();
            });
        } else {
            holder.updateBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // 🔧 Methode zum Starten des Downloads (öffnet Browser oder Downloadmanager)
    public void startApkDownload(Context context, String apkUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(apkUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
}
