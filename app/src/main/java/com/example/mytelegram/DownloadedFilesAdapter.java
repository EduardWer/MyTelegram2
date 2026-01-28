package com.example.mytelegram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.mytelegram.R;

import java.io.File;
import java.util.List;

public class DownloadedFilesAdapter extends RecyclerView.Adapter<DownloadedFilesAdapter.FileViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    private List<File> files;
    private OnFileClickListener listener;

    public DownloadedFilesAdapter(List<File> files, OnFileClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_downloaded_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.bind(file, listener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public void updateFiles(List<File> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private ImageView fileIcon;
        private TextView fileName;
        private View itemView;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
        }

        public void bind(File file, OnFileClickListener listener) {
            fileName.setText(file.getName());

            // Устанавливаем иконку в зависимости от типа файла
            setFileIcon(file);

            // Загружаем превью для изображений
            if (isImageFile(file)) {
                Glide.with(itemView.getContext())
                        .load(file)
                        .placeholder(R.drawable.ic_image)
                        .error(R.drawable.ic_image)
                        .centerCrop()
                        .into(fileIcon);
            }

            itemView.setOnClickListener(v -> listener.onFileClick(file));
        }

        private void setFileIcon(File file) {
            String fileName = file.getName().toLowerCase();

            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") || fileName.endsWith(".gif")) {
                fileIcon.setImageResource(R.drawable.ic_image);
            } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                    fileName.endsWith(".mkv") || fileName.endsWith(".mov")) {
                fileIcon.setImageResource(R.drawable.ic_video);
            } else if (fileName.endsWith(".pdf")) {
                fileIcon.setImageResource(R.drawable.ic_pdf);
            } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                fileIcon.setImageResource(R.drawable.ic_document);
            } else if (fileName.endsWith(".zip") || fileName.endsWith(".rar")) {
                fileIcon.setImageResource(R.drawable.ic_zip);
            } else {
                fileIcon.setImageResource(R.drawable.ic_file);
            }
        }

        private boolean isImageFile(File file) {
            String name = file.getName().toLowerCase();
            return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".gif");
        }
    }
}