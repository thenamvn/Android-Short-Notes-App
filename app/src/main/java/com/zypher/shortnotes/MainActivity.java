package com.zypher.shortnotes;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
// Remove SharedPreferences import
// import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment; // Import for external storage
import android.util.Log; // Import Log for debugging
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast; // Import Toast for feedback

import java.io.BufferedReader; // For reading files
import java.io.File; // For file operations
import java.io.FileOutputStream; // For writing files
import java.io.FileReader; // For reading files
import java.io.IOException; // For exception handling
import java.io.OutputStreamWriter; // For writing files
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // For unique filenames

public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 101; // Request code for permissions
    private static final String NOTES_DIRECTORY_NAME = "NotesApp"; // Directory name
    private static final String TAG = "MainActivityNotes"; // Log tag

    private LinearLayout notesContainer;
    private List<Note> noteList;
    private EditText titleEditText; // Make EditTexts member variables for easier access
    private EditText contentEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notesContainer = findViewById(R.id.notesContainer);
        Button saveButton = findViewById(R.id.saveButton);
        titleEditText = findViewById(R.id.titleEditText); // Initialize member variables
        contentEditText = findViewById(R.id.contentEditText);

        noteList = new ArrayList<>();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check permissions before attempting to save
                checkPermissionAndSaveNote();
            }
        });

        // Load notes from file system instead of preferences
        loadNotesFromFileSystem();
        displayNotes();
    }

    // --- Permission Handling ---

    private void checkPermissionAndSaveNote() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            // Permission not granted, request it
            requestStoragePermission();
        } else {
            // Permission already granted, proceed with saving
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
                // Permission granted by user
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
                // Now attempt to save again
                saveNote();
            } else {
                // Permission denied by user
                Toast.makeText(this, "Storage Permission Denied. Cannot save note.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- File System Operations ---

    private File getNotesDirectory() {
        // Using public Documents directory. Alternatives: getExternalFilesDir(null) for app-specific private storage
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File notesDir = new File(documentsDir, NOTES_DIRECTORY_NAME);
        if (!notesDir.exists()) {
            if (!notesDir.mkdirs()) {
                Log.e(TAG, "Failed to create notes directory");
                Toast.makeText(this, "Error: Could not create notes directory!", Toast.LENGTH_LONG).show();
                // Optionally, disable saving if directory creation fails
            } else {
                Log.i(TAG, "Notes directory created at: " + notesDir.getAbsolutePath());
            }
        }
        return notesDir;
    }

    private void loadNotesFromFileSystem() {
        noteList.clear(); // Clear existing list before loading
        File notesDir = getNotesDirectory();

        if (!notesDir.exists() || !notesDir.isDirectory()) {
            Log.w(TAG, "Notes directory doesn't exist or is not a directory.");
            return; // Nothing to load
        }

        File[] noteFiles = notesDir.listFiles();
        if (noteFiles != null) {
            for (File file : noteFiles) {
                if (file.isFile() && file.getName().endsWith(".txt")) { // Simple check for note files
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String title = reader.readLine(); // First line is title
                        StringBuilder contentBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            contentBuilder.append(line).append("\n");
                        }
                        // Remove trailing newline if present
                        String content = contentBuilder.length() > 0 ? contentBuilder.substring(0, contentBuilder.length() -1) : "";

                        if (title != null) {
                            Note note = new Note(title, content, file.getName()); // Store filename
                            noteList.add(note);
                            Log.d(TAG, "Loaded note: " + title + " from " + file.getName());
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading note file: " + file.getName(), e);
                        Toast.makeText(this, "Error reading note: " + file.getName(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        Log.i(TAG, "Loaded " + noteList.size() + " notes from file system.");
    }

    private void saveNote() {
        // No need to find views again, use member variables
        // EditText titleEditText = findViewById(R.id.titleEditText);
        // EditText contentEditText = findViewById(R.id.contentEditText);

        String title = titleEditText.getText().toString().trim(); // Trim whitespace
        String content = contentEditText.getText().toString().trim();

        if (!title.isEmpty() && !content.isEmpty()) {
            // Generate a unique filename (e.g., using UUID or timestamp)
            String filename = "note_" + System.currentTimeMillis() + ".txt";
            Note note = new Note(title, content, filename); // Create note with filename

            if (saveNoteToFile(note)) { // Try saving to file
                Log.i(TAG, "Note saved successfully to file: " + filename);
                noteList.add(note); // Add to list only if save was successful
                // No need to call saveNotesToPreferences()
                createNoteView(note);
                clearInputFields();
                Toast.makeText(this, "Note Saved", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Failed to save note to file: " + filename);
                Toast.makeText(this, "Error: Could not save note!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Title and Content cannot be empty", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean saveNoteToFile(Note note) {
        File notesDir = getNotesDirectory();
        if (!notesDir.exists() && !notesDir.mkdirs()) {
            Log.e(TAG, "Cannot save note, directory creation failed.");
            return false; // Cannot save if directory doesn't exist/can't be created
        }

        File noteFile = new File(notesDir, note.getFilename());

        try (FileOutputStream fos = new FileOutputStream(noteFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {
            writer.write(note.getTitle() + "\n"); // Write title on the first line
            writer.write(note.getContent()); // Write content on subsequent lines
            writer.flush();
            Log.d(TAG, "Note data written to: " + noteFile.getAbsolutePath());
            return true; // Indicate success
        } catch (IOException e) {
            Log.e(TAG, "Error writing note to file: " + note.getFilename(), e);
            return false; // Indicate failure
        }
    }


    // --- UI Update Methods (Mostly unchanged, but interact with file system on delete) ---

    private void displayNotes() {
        notesContainer.removeAllViews(); // Clear existing views first
        for (Note note : noteList) {
            createNoteView(note);
        }
    }

    private void clearInputFields() {
        // Use member variables
        titleEditText.getText().clear();
        contentEditText.getText().clear();
    }

    private void createNoteView(final Note note) {
        View noteView = getLayoutInflater().inflate(R.layout.note_item, notesContainer, false); // Attach to parent here
        TextView titleTextView = noteView.findViewById(R.id.titleTextView);
        TextView contentTextView = noteView.findViewById(R.id.contentTextView);

        titleTextView.setText(note.getTitle());
        contentTextView.setText(note.getContent());

        noteView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showDeleteDialog(note);
                return true;
            }
        });

        notesContainer.addView(noteView);
    }

    private void showDeleteDialog(final Note note) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete this note?"); // Slightly better title
        builder.setMessage("Are you sure you want to delete the note titled '" + note.getTitle() + "'?"); // More specific message
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteNoteAndRefresh(note);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteNoteAndRefresh(Note note) {
        // 1. Delete the file
        File notesDir = getNotesDirectory();
        File noteFile = new File(notesDir, note.getFilename());
        boolean deleted = false;
        if (noteFile.exists()) {
            deleted = noteFile.delete();
            if (deleted) {
                Log.i(TAG, "Deleted note file: " + note.getFilename());
            } else {
                Log.e(TAG, "Failed to delete note file: " + note.getFilename());
                Toast.makeText(this, "Error: Could not delete note file.", Toast.LENGTH_SHORT).show();
                // Optional: Decide if you should proceed if file deletion fails
            }
        } else {
            Log.w(TAG, "Note file not found for deletion: " + note.getFilename());
            // File doesn't exist, maybe already deleted? Proceed with list removal.
            deleted = true; // Treat as success for list removal purpose
        }


        // 2. Remove from the list (only if file deletion was successful or file didn't exist)
        if(deleted) {
            noteList.remove(note);
        }

        // 3. Refresh the UI
        refreshNoteViews(); // Use refresh which calls displayNotes after clearing
        if(deleted) {
            Toast.makeText(this, "Note Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshNoteViews() {
        notesContainer.removeAllViews();
        displayNotes(); // Re-display notes from the updated noteList
    }
}