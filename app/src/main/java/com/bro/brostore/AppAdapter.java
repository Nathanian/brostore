package com.bro.brostore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private final List<AppModel> appList;

    public AppAdapter(List<AppModel> appList) {
        this.appList = appList;
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, version;

        public AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imageViewIcon);
            name = itemView.findViewById(R.id.textViewName);
            version = itemView.findViewById(R.id.textViewVersion);
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
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }
}

