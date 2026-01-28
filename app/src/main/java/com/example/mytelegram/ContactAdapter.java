package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private List<User> contacts;
    private OnContactClickListener listener;

    public interface OnContactClickListener {
        void onContactClick(User user);
    }

    public ContactAdapter(List<User> contacts, OnContactClickListener listener) {
        this.contacts = contacts != null ? contacts : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        User user = contacts.get(position);
        holder.bind(user);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }


    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nameTextView;
        private final TextView statusTextView;
        

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.ImageView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);

        }

        public void bind(User user) {
            // Установка имени
            nameTextView.setText(user.getUsername() != null ? user.getUsername() : "Без имени");

            // Установка статуса (био или email)
            String status = user.getBio();
            if (status == null || status.isEmpty()) {
                status = user.getEmail() != null ? user.getEmail() : "";
            }
            statusTextView.setText(status);

            // Загрузка аватара с помощью Glide
            if (user.getAvatarUrls() != null && !user.getAvatarUrls().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(user.getAvatarUrls())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person) // Заглушка
                        .error(R.drawable.ic_person) // Иконка при ошибке
                        .into(avatarImageView);
            } else {
                // Устанавливаем аватар по умолчанию
                avatarImageView.setImageResource(R.drawable.ic_person);
            }



        }
    }
}