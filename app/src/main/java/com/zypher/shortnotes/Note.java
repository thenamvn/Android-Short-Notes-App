package com.zypher.shortnotes;

public class Note {
    private String title;
    private String content;
    private String filename; // Add filename field

    public Note() {

    }

    // Constructor including filename
    public Note(String title, String content, String filename) {
        this.title = title;
        this.content = content;
        this.filename = filename;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // Getter and Setter for filename
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}