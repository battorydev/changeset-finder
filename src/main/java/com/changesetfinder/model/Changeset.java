package com.changesetfinder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Changeset {
    private final String id;
    private final String author;
    private final String filePath;
    private final List<String> contexts;
    private final String sqlContent;

    public Changeset(String id, String author, String filePath, List<String> contexts, String sqlContent) {
        this.id = id != null ? id.trim() : "";
        this.author = author != null ? author.trim() : "";
        this.filePath = filePath != null ? filePath.trim() : "";
        this.contexts = contexts != null ? new ArrayList<>(contexts) : new ArrayList<>();
        this.sqlContent = sqlContent != null ? sqlContent : "";
    }

    public String getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<String> getContexts() {
        return Collections.unmodifiableList(contexts);
    }

    public String getSqlContent() {
        return sqlContent;
    }

    @Override
    public String toString() {
        return String.format("%s (Author: %s) [%s]", id, author, String.join(", ", contexts));
    }
}
