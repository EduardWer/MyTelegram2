package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserSelectionAdapter extends RecyclerView.Adapter<UserSelectionAdapter.ViewHolder> {

    private List<UserModel> userList;
    private Set<String> selectedIds;
    private String currentUserId;

    public UserSelectionAdapter(List<UserModel> userList, String currentUserId) {
        this.userList = userList;
        this.currentUserId = currentUserId;
        this.selectedIds = new HashSet<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserModel user = userList.get(position);
        holder.username.setText(user.getUsername());
        // Не даём выбрать самого себя (уже отфильтровано, но на всякий случай)
        if (user.getUid().equals(currentUserId)) {
            holder.checkBox.setVisibility(View.GONE);
        } else {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedIds.contains(user.getUid()));
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedIds.add(user.getUid());
                else selectedIds.remove(user.getUid());
            });
        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public List<String> getSelectedUserIds() {
        return new ArrayList<>(selectedIds);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username;
        CheckBox checkBox;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.userNameTextView);
            checkBox = itemView.findViewById(R.id.userCheckBox);
        }
    }
}