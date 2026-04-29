package com.example.mytelegram.Fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mytelegram.ChatActivity;
import com.example.mytelegram.GroupChatActivity;
import com.example.mytelegram.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DocumentFragment extends Fragment {

    private static final String TAG = "DocumentFragment";

    private RecyclerView recyclerView;
    private DocumentAdapter adapter;
    private List<DocumentItem> documentItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_documents, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        setupRecyclerView();
        loadDocuments();
        return view;
    }



    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DocumentAdapter(documentItems);
        adapter.setListener(item -> {
            Log.d(TAG, "Выбран документ: " + item.name);

            Uri uri = Uri.fromFile(new File(item.path));

            // Универсальная отправка для любого типа чата
            if (getActivity() instanceof ChatActivity) {
                ((ChatActivity) getActivity()).sendDocumentFromPicker(uri);
                ((ChatActivity) getActivity()).closeMediaPanel();
            } else if (getActivity() instanceof GroupChatActivity) {
                ((GroupChatActivity) getActivity()).sendDocumentFromPicker(uri);
                ((GroupChatActivity) getActivity()).closeMediaPanel();
            } else {
                Toast.makeText(getContext(), "Ошибка: неизвестный тип чата", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);
    }




    private void loadDocuments() {
        documentItems.clear();
        loadDocumentsFromStorage();
        adapter.notifyDataSetChanged();
    }

    private void loadDocumentsFromStorage() {
        String[] projection = {
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
        };

        String selection = MediaStore.Files.FileColumns.MIME_TYPE + " IN (?, ?, ?, ?, ?, ?, ?)";
        String[] selectionArgs = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain",
                "application/vnd.ms-powerpoint"
        };

        String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";

        try {
            Cursor cursor = requireContext().getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            );

            if (cursor != null) {
                int dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                int nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
                int mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);

                while (cursor.moveToNext() && documentItems.size() < 50) {
                    String path = cursor.getString(dataIndex);
                    String name = cursor.getString(nameIndex);
                    long size = cursor.getLong(sizeIndex);
                    String mimeType = cursor.getString(mimeIndex);

                    if (path != null && new File(path).exists()) {
                        DocumentItem item = new DocumentItem();
                        item.name = name != null ? name : "Документ";
                        item.path = path;
                        item.size = formatFileSize(size);
                        item.mimeType = mimeType;
                        item.icon = getDocumentIcon(mimeType, name);
                        documentItems.add(item);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка загрузки документов: " + e.getMessage());
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private String getDocumentIcon(String mimeType, String fileName) {
        if (mimeType == null) mimeType = "";

        switch (mimeType) {
            case "application/pdf": return "📕";
            case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "📘";
            case "application/vnd.ms-excel":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "📗";
            case "application/vnd.ms-powerpoint": return "📙";
            case "text/plain": return "📄";
        }

        if (fileName != null) {
            String ext = getFileExtension(fileName).toLowerCase();
            switch (ext) {
                case "pdf": return "📕";
                case "doc":
                case "docx": return "📘";
                case "xls":
                case "xlsx": return "📗";
                case "ppt":
                case "pptx": return "📙";
                case "txt": return "📄";
                case "zip":
                case "rar":
                case "7z": return "🗜️";
                default: return "📎";
            }
        }
        return "📎";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot == -1) ? "" : fileName.substring(lastDot + 1);
    }

    public static class DocumentItem {
        public String name;
        public String path;
        public String size;
        public String mimeType;
        public String icon;
    }

    private static class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.ViewHolder> {

        private List<DocumentItem> items;
        private ItemClickListener listener;

        public interface ItemClickListener {
            void onItemClick(DocumentItem item);
        }

        public DocumentAdapter(List<DocumentItem> items) {
            this.items = items;
        }

        public void setListener(ItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_document, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentItem item = items.get(position);

            holder.iconText.setText(item.icon);
            holder.nameText.setText(item.name);
            holder.sizeText.setText(item.size);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView iconText;
            TextView nameText;
            TextView sizeText;

            ViewHolder(View itemView) {
                super(itemView);
                iconText = itemView.findViewById(R.id.iconText);
                nameText = itemView.findViewById(R.id.nameText);
                sizeText = itemView.findViewById(R.id.sizeText);
            }
        }
    }
}