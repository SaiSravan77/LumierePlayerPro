package com.saisravan.lumiereplayerpro;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private List<VideoModel> videoList = new ArrayList<>();
    private static final int REQ_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (checkPermission()) {
            loadVideos();
        } else {
            requestPermission();
        }
    }

    private boolean checkPermission() {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? 
                      Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? 
                      Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            Toast.makeText(this, "Permission Required to show videos", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadVideos() {
        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ?
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) :
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
        };

        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, null)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    int duration = cursor.getInt(durCol);
                    int size = cursor.getInt(sizeCol);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

                    videoList.add(new VideoModel(name, contentUri, duration, size));
                }
                VideoAdapter adapter = new VideoAdapter(this, videoList);
                recyclerView.setAdapter(adapter);
            }
        }
    }
}