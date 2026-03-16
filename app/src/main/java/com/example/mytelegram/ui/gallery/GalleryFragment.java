package com.example.mytelegram.ui.gallery;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.mytelegram.R;
import com.example.mytelegram.DownloadedFilesAdapter;
import com.example.mytelegram.databinding.FragmentGalleryBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Измените имя класса на SlideshowFragment
public class GalleryFragment extends Fragment {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    private FragmentGalleryBinding binding;
    private DownloadedFilesAdapter filesAdapter;
    private List<File> downloadedFiles = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Настройка RecyclerView
        setupRecyclerView();

        // Проверяем разрешения при создании фрагмента
        checkPermissions();

        setupRefreshButton();
    }

    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        filesAdapter = new DownloadedFilesAdapter(downloadedFiles, this::onFileClicked);
        binding.recyclerView.setAdapter(filesAdapter);
    }

    private void setupRefreshButton() {
        binding.emptyState.setOnClickListener(v -> checkPermissions());
        binding.titleText.setOnClickListener(v -> checkPermissions());
    }

    // Проверка и запрос разрешений
    private void checkPermissions() {
        if (hasStoragePermission()) {
            loadDownloadedFiles();
        } else {
            requestStoragePermission();
        }
    }

    // Проверка наличия разрешений
    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        String[] permissions;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        if (shouldShowRequestPermissionRationale(permissions[0])) {
            Toast.makeText(requireContext(),
                    "Разрешение нужно для доступа к скачанным файлам",
                    Toast.LENGTH_LONG).show();
        }

        requestPermissions(permissions, REQUEST_STORAGE_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Разрешения получены", Toast.LENGTH_SHORT).show();
                loadDownloadedFiles();
            } else {
                Toast.makeText(requireContext(),
                        "Разрешения не предоставлены. Невозможно загрузить файлы.",
                        Toast.LENGTH_LONG).show();
                showEmptyState();
            }
        }
    }

    // Загрузка всех скачанных файлов
    private void loadDownloadedFiles() {
        if (downloadedFiles == null) {
            downloadedFiles = new ArrayList<>();
        } else {
            downloadedFiles.clear();
        }

        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File telegramPicturesDir = new File(picturesDir, "Pride/documents");

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File telegramDownloadsDir = new File(downloadsDir, "Pride/documents");

        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File telegramDocumentsDir = new File(documentsDir, "Pride/documents");

        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File telegramMoviesDir = new File(moviesDir, "Pride/documents");

        if (telegramPicturesDir.exists()) {
            addFilesFromDirectory(telegramPicturesDir);
        }

        if (telegramDownloadsDir.exists()) {
            addFilesFromDirectory(telegramDownloadsDir);
        }

        if (telegramDocumentsDir.exists()) {
            addFilesFromDirectory(telegramDocumentsDir);
        }

        if (telegramMoviesDir.exists()) {
            addFilesFromDirectory(telegramMoviesDir);
        }

        if (filesAdapter != null) {
            filesAdapter.updateFiles(downloadedFiles);
        }

        binding.fileCountText.setText("Файлов: " + downloadedFiles.size());

        if (downloadedFiles.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    // Рекурсивно добавляем файлы из директории
    private void addFilesFromDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addFilesFromDirectory(file);
                } else {
                    downloadedFiles.add(file);
                }
            }
        }
    }

    // Обработка клика по файлу
    private void onFileClicked(File file) {
        try {
            openFile(file);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
        }
    }

    // Открытие файла
    private void openFile(File file) {
        try {
            Uri fileUri = Uri.fromFile(file);
            String mimeType = getMimeType(file.getName());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(requireContext(), "Не найдено приложение для открытия файла", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Ошибка открытия файла: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Определение MIME типа
    private String getMimeType(String fileName) {
        if (fileName == null) return "*/*";

        String extension = getFileExtension(fileName);

        switch (extension.toLowerCase()) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                return "image/*";
            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
            case "3gp":
                return "video/*";
            case "pdf":
                return "application/pdf";
            case "doc":
            case "docx":
                return "application/msword";
            case "xls":
            case "xlsx":
                return "application/vnd.ms-excel";
            case "ppt":
            case "pptx":
                return "application/vnd.ms-powerpoint";
            case "zip":
            case "rar":
                return "application/zip";
            case "txt":
                return "text/plain";
            default:
                return "*/*";
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot == -1) ? "" : fileName.substring(lastDot + 1).toLowerCase();
    }

    private void showEmptyState() {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyStateText.setText("Скачанные файлы не найдены");
    }

    private void hideEmptyState() {
        binding.emptyState.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            loadDownloadedFiles();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}