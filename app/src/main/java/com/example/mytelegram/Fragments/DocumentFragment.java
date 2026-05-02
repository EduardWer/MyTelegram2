package com.example.mytelegram.Fragments;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
    private static final int REQUEST_MANAGE_STORAGE = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    private RecyclerView recyclerView;
    private DocumentAdapter adapter;
    private List<DocumentItem> documentItems = new ArrayList<>();
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_documents, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        setupRecyclerView();

        // Проверяем разрешения перед загрузкой
        checkPermissionsAndLoad();

        return view;
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            if (Environment.isExternalStorageManager()) {
                loadDocuments();
            } else {
                requestManageStoragePermission();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                loadDocuments();
            } else {
                requestStoragePermission();
            }
        } else {
            // Android 5 и ниже
            loadDocuments();
        }
    }

    private void requestManageStoragePermission() {
        Toast.makeText(getContext(), "Для доступа к документам нужно разрешение", Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        }
    }

    private void requestStoragePermission() {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDocuments();
            } else {
                Toast.makeText(getContext(), "Нет разрешения на чтение файлов", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                loadDocuments();
            } else {
                Toast.makeText(getContext(), "Нет разрешения на управление файлами", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DocumentAdapter(documentItems);
        adapter.setListener(item -> {
            Log.d(TAG, "Выбран документ: " + item.name);

            Uri uri;
            if (item.uri != null) {
                uri = item.uri;
            } else if (item.path != null) {
                uri = Uri.fromFile(new File(item.path));
            } else {
                Toast.makeText(getContext(), "Ошибка: не удалось получить URI файла", Toast.LENGTH_SHORT).show();
                return;
            }

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
        if (isLoading) return;
        isLoading = true;
        documentItems.clear();

        new Thread(() -> {
            List<DocumentItem> items = new ArrayList<>();

            // Пробуем загрузить через MediaStore
            loadDocumentsFromMediaStore(items);

            // Если MediaStore не дал результатов, пробуем сканировать вручную
            if (items.isEmpty()) {
                scanDocumentsManually(items);
            }

            final List<DocumentItem> finalItems = items;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    documentItems.addAll(finalItems);
                    adapter.notifyDataSetChanged();
                    isLoading = false;

                    if (documentItems.isEmpty()) {
                        Toast.makeText(getContext(), "Документы не найдены", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void loadDocumentsFromMediaStore(List<DocumentItem> items) {
        try {
            String[] projection = {
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.DATA
            };

            String selection = MediaStore.Files.FileColumns.MIME_TYPE + " IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String[] selectionArgs = {
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/plain",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/zip",
                    "application/x-rar-compressed"
            };

            String sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC";

            Uri collectionUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                collectionUri = MediaStore.Files.getContentUri("external");
            }

            Cursor cursor = requireContext().getContentResolver().query(
                    collectionUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            );

            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
                int nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
                int mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
                int dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);

                while (cursor.moveToNext() && items.size() < 50) {
                    String name = cursor.getString(nameIndex);
                    long size = cursor.getLong(sizeIndex);
                    String mimeType = cursor.getString(mimeIndex);
                    long id = cursor.getLong(idIndex);
                    String path = dataIndex >= 0 ? cursor.getString(dataIndex) : null;

                    if (TextUtils.isEmpty(name) || name.startsWith(".")) continue;

                    DocumentItem item = new DocumentItem();
                    item.name = name;
                    item.size = formatFileSize(size);
                    item.mimeType = mimeType != null ? mimeType : "application/octet-stream";
                    item.icon = getDocumentIcon(mimeType, name);
                    item.uri = getDocumentUri(id, mimeType);
                    item.path = path;

                    items.add(item);
                }
                cursor.close();
                Log.d(TAG, "MediaStore загрузил " + items.size() + " документов");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка загрузки из MediaStore: " + e.getMessage());
        }
    }

    private Uri getDocumentUri(long id, String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
        } else if (mimeType != null && mimeType.startsWith("video/")) {
            return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
        } else {
            return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), String.valueOf(id));
        }
    }

    private void scanDocumentsManually(List<DocumentItem> items) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

            String[] extensions = {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".txt", ".ppt", ".pptx", ".zip", ".rar"};

            scanDirectory(downloadDir, extensions, items);
            scanDirectory(documentsDir, extensions, items);

            Log.d(TAG, "Ручное сканирование добавило " + items.size() + " документов");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка ручного сканирования: " + e.getMessage());
        }
    }

    private void scanDirectory(File dir, String[] extensions, List<DocumentItem> items) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (items.size() >= 50) break;

            if (file.isFile()) {
                String name = file.getName();
                for (String ext : extensions) {
                    if (name.toLowerCase().endsWith(ext)) {
                        DocumentItem item = new DocumentItem();
                        item.name = name;
                        item.size = formatFileSize(file.length());
                        item.mimeType = getMimeTypeFromExtension(ext);
                        item.icon = getDocumentIcon(item.mimeType, name);
                        item.uri = Uri.fromFile(file);
                        item.path = file.getAbsolutePath();
                        items.add(item);
                        break;
                    }
                }
            } else if (file.isDirectory()) {
                // Рекурсивно сканируем подпапки (не глубже 2 уровней)
                scanDirectory(file, extensions, items);
            }
        }
    }

    private String getMimeTypeFromExtension(String extension) {
        switch (extension.toLowerCase()) {
            case ".pdf": return "application/pdf";
            case ".doc": return "application/msword";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls": return "application/vnd.ms-excel";
            case ".xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".txt": return "text/plain";
            case ".ppt": return "application/vnd.ms-powerpoint";
            case ".pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".zip": return "application/zip";
            case ".rar": return "application/x-rar-compressed";
            default: return "application/octet-stream";
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
        public Uri uri;
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