package com.example.mytelegram;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MediaHelper {

    // Создание альбома
    public static boolean createGalleryAlbum(Context context, String albumName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return createAlbumAndroid10Plus(context, albumName);
        } else {
            return createAlbumLegacy(albumName);
        }
    }

    private static boolean createAlbumAndroid10Plus(Context context, String albumName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + albumName + "/");
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "temp.jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            Uri uri = context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri != null) {
                context.getContentResolver().delete(uri, null, null);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean createAlbumLegacy(String albumName) {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File albumDir = new File(picturesDir, albumName);
        return albumDir.exists() || albumDir.mkdirs();
    }

    // Получение фото из альбома
    public static List<Uri> getPhotosFromAlbum(Context context, String albumName) {
        List<Uri> photoUris = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String selection;
        String[] selectionArgs;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{"%/" + albumName + "/%"};
        } else {
            selection = MediaStore.Images.Media.DATA + " LIKE ?";
            selectionArgs = new String[]{"%/Pictures/" + albumName + "/%"};
        }

        String[] projection = {MediaStore.Images.Media._ID};

        try (Cursor cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(id)
                    );
                    photoUris.add(contentUri);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return photoUris;
    }

    // Создание тестовых фото
    public static void createTestPhotos(Context context, String albumName, int count) {
        for (int i = 0; i < count; i++) {
            String fileName = "photo_" + System.currentTimeMillis() + "_" + i + ".jpg";
            saveTestImage(context, albumName, fileName);
        }
    }

    private static void saveTestImage(Context context, String albumName, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageAndroid10Plus(context, albumName, fileName);
        } else {
            saveImageLegacy(albumName, fileName);
        }
    }

    private static void saveImageAndroid10Plus(Context context, String albumName, String fileName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + albumName + "/");

            Uri uri = context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri != null) {
                // Просто создаем запись в медиасторе
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveImageLegacy(String albumName, String fileName) {
        try {
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File albumDir = new File(picturesDir, albumName);
            if (!albumDir.exists()) {
                albumDir.mkdirs();
            }
            new File(albumDir, fileName).createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
