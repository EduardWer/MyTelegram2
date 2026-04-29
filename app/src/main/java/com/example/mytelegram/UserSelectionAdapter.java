package com.example.mytelegram;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserSelectionAdapter extends RecyclerView.Adapter<UserSelectionAdapter.ViewHolder> {

    private List<UserModel> users;          // текущий список пользователей
    private Set<String> selectedIds;        // выбранные UID
    private String currentUserId;           // UID текущего пользователя (не используется, но оставлен)

    public UserSelectionAdapter(List<UserModel> users, String currentUserId) {
        this.users = users;
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
        UserModel user = users.get(position);

        // Имя пользователя
        if (holder.nameTextView != null) {
            holder.nameTextView.setText(
                    user.getUsername() != null ? user.getUsername() : "Пользователь"
            );
        }


        // Чекбокс
        if (holder.checkBox != null) {
            // Сбрасываем слушатель, чтобы избежать рекурсии
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selectedIds.contains(user.getUid()));

            // Устанавливаем новый слушатель
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String uid = user.getUid();
                if (uid == null || uid.equals(currentUserId)) {
                    // Не даем выбрать самого себя
                    holder.checkBox.setChecked(false);
                    return;
                }

                if (isChecked) {
                    selectedIds.add(uid);
                } else {
                    selectedIds.remove(uid);
                }
            });
        }

        // Аватар
        if (holder.avatarImageView != null) {
            if (!TextUtils.isEmpty(user.getAvatarUrl())) {
                Glide.with(holder.itemView.getContext())
                        .load(user.getAvatarUrl())
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(holder.avatarImageView);
            } else {
                holder.avatarImageView.setImageResource(R.drawable.ic_person);
            }
        }

        // Клик по всему элементу
        holder.itemView.setOnClickListener(v -> {
            String uid = user.getUid();
            if (uid == null || uid.equals(currentUserId)) return;

            if (holder.checkBox != null) {
                // Переключаем чекбокс
                holder.checkBox.setChecked(!holder.checkBox.isChecked());
            }
        });
    }

    @Override
    public int getItemCount() {
        return users != null ? users.size() : 0;
    }

    /** Замена всего списка (например, после фильтрации) */
    public void updateList(List<UserModel> newList) {
        this.users = newList;
        notifyDataSetChanged();
    }

    /** Получить список UID выбранных пользователей */
    public List<String> getSelectedUserIds() {
        return new ArrayList<>(selectedIds);
    }

    // ------------------- ViewHolder ----------------------
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView nameTextView;
        CheckBox checkBox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.userAvatar);
            nameTextView = itemView.findViewById(R.id.userNameTextView);   // точно как в XML
            checkBox = itemView.findViewById(R.id.userCheckBox);          // большая буква B
        }
    }
}