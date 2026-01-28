package com.example.mytelegram;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {

    public static final String APP_FOLDER_NAME = "MyAppGallery";

    // Создание папки приложения
    public static File createAppFolder() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), APP_FOLDER_NAME);

        if (!folder.exists()) {
            if (folder.mkdirs()) {
                return folder;
            }
        }
        return folder;
    }

    // Создание файла для изображения
    public static File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";

        File storageDir = createAppFolder();

        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    // Получение списка изображений из папки
    public static File[] getImagesFromAppFolder() {
        File folder = createAppFolder();

        if (folder.exists() && folder.isDirectory()) {
            return folder.listFiles((dir, name) -> {
                String lowerCaseName = name.toLowerCase();
                return lowerCaseName.endsWith(".jpg") ||
                        lowerCaseName.endsWith(".jpeg") ||
                        lowerCaseName.endsWith(".png") ||
                        lowerCaseName.endsWith(".gif");
            });
        }
        return new File[0];
    }

    // Проверка существования папки
    public static boolean isAppFolderExists() {
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), APP_FOLDER_NAME);
        return folder.exists() && folder.isDirectory();
    }
}
