package com.example.mytelegram.Fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mytelegram.ChatActivity;
import com.example.mytelegram.R;

import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment {

    private RecyclerView recyclerView;
    private List<MediaItem> mediaItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery2, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));

        loadMedia();

        return view;
    }

    private void loadMedia() {
        mediaItems.clear();

        // Загружаем изображения
        loadImages();

        // Загружаем видео
        loadVideos();

        recyclerView.setAdapter(new MediaAdapter(mediaItems));
    }

    private void loadImages() {
        String[] projection = {
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media._ID
        };

        Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

            while (cursor.moveToNext() && mediaItems.size() < 50) {
                String path = cursor.getString(dataIndex);
                long id = cursor.getLong(idIndex);

                MediaItem item = new MediaItem();
                item.path = path;
                item.type = "image";
                item.contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

                mediaItems.add(item);
            }
            cursor.close();
        }
    }

    private void loadVideos() {
        String[] projection = {
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media._ID
        };

        Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);

            while (cursor.moveToNext() && mediaItems.size() < 50) {
                String path = cursor.getString(dataIndex);
                long id = cursor.getLong(idIndex);

                MediaItem item = new MediaItem();
                item.path = path;
                item.type = "video";
                item.contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

                mediaItems.add(item);
            }
            cursor.close();
        }
    }

    // Модель медиафайла
    public static class MediaItem {
        public String path;
        public String type;
        public Uri contentUri;
    }

    // Адаптер
    private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
        private List<MediaItem> items;

        public MediaAdapter(List<MediaItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_media, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MediaItem item = items.get(position);

            // Загружаем превью
            Glide.with(holder.itemView.getContext())
                    .load(item.contentUri)
                    .centerCrop()
                    .into(holder.imageView);

            // Показываем иконку для видео
            if ("video".equals(item.type)) {
                holder.videoIcon.setVisibility(View.VISIBLE);
            } else {
                holder.videoIcon.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (getActivity() instanceof ChatActivity) {
                    ((ChatActivity) getActivity()).sendMediaFromGallery(item.contentUri, item.type);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ImageView videoIcon;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageView);
                videoIcon = itemView.findViewById(R.id.videoIcon);
            }
        }
    }
}