package com.bro.brostore;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
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

                AppModel app = new AppModel(icon, label, pkgName, version);

                // ✅ JSON vom Server laden
                UpdateInfo info = fetchUpdateInfo(pkgName);
                if (info != null && !info.version.equals(version)) {
                    Log.d("BroStore", "Update verfügbar für " + label);
                    app.updateInfo = info;
                }

                appList.add(app);
            }
        }
    }


    public UpdateInfo fetchUpdateInfo(String pkgName) {
        try {
            URL url = new URL("https://deinserver.com/updates/" + pkgName + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            InputStream input = conn.getInputStream();
            InputStreamReader reader = new InputStreamReader(input);
            return new Gson().fromJson(reader, UpdateInfo.class);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
