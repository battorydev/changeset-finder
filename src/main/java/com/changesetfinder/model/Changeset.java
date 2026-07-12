package com.changesetfinder.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Changeset {
    private final String id;
    private final String author;
    private final String filePath;
    private final String absoluteFilePath;
    private final List<String> contexts;
    private final int startLine;
    private final int endLine;
    private final String dbObjectType;
    private final String dbObjectName;
    
    private String loadedSqlContent = null;

    public Changeset(String id, String author, String filePath, String absoluteFilePath, List<String> contexts, int startLine, int endLine, String sqlForClassification) {
        this.id = id != null ? id.trim() : "";
        this.author = author != null ? author.trim() : "";
        this.filePath = filePath != null ? filePath.trim() : "";
        this.absoluteFilePath = absoluteFilePath != null ? absoluteFilePath.trim() : "";
        this.contexts = contexts != null ? new ArrayList<>(contexts) : new ArrayList<>();
        this.startLine = startLine;
        this.endLine = endLine;
        this.dbObjectType = classifySql(sqlForClassification);
        this.dbObjectName = extractObjectName(sqlForClassification, this.dbObjectType);
    }

    @Deprecated
    public Changeset(String id, String author, String filePath, List<String> contexts, String sqlContent) {
        this(id, author, filePath, "", contexts, 0, 0, sqlContent);
        this.loadedSqlContent = sqlContent;
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
        if (loadedSqlContent != null) {
            return loadedSqlContent;
        }
        if (absoluteFilePath == null || absoluteFilePath.isEmpty()) {
            return "";
        }
        try {
            java.nio.file.Path path = java.nio.file.Path.of(absoluteFilePath);
            if (java.nio.file.Files.exists(path)) {
                List<String> lines = java.nio.file.Files.readAllLines(path);
                int startIdx = Math.max(0, startLine - 1);
                int endIdx = Math.min(lines.size(), endLine);
                StringBuilder sb = new StringBuilder();
                for (int i = startIdx; i < endIdx; i++) {
                    sb.append(lines.get(i)).append("\n");
                }
                loadedSqlContent = sb.toString();
            } else {
                loadedSqlContent = "-- File not found: " + absoluteFilePath;
            }
        } catch (Exception e) {
            loadedSqlContent = "-- Error lazy-loading SQL content: " + e.getMessage();
        }
        return loadedSqlContent;
    }

    public String getDbObjectType() {
        return dbObjectType;
    }

    public String getDbObjectName() {
        return dbObjectName;
    }

    private static String classifySql(String sql) {
        if (sql == null || sql.trim().isEmpty()) return "Other";
        
        // Clean comments
        String cleanSql = sql.replaceAll("(?s)/\\*.*?\\*/", "")
                             .replaceAll("--.*", "")
                             .toLowerCase()
                             .replaceAll("\\s+", " ")
                             .trim();
                             
        if (cleanSql.matches(".*\\b(create|alter|drop|truncate)\\s+table\\b.*") ||
            cleanSql.matches(".*\\binsert\\s+into\\b.*") ||
            cleanSql.matches(".*\\bupdate\\s+\\w+\\b.*") ||
            cleanSql.matches(".*\\bdelete\\s+from\\b.*")) {
            return "Table";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+view\\b.*") ||
            cleanSql.matches(".*\\bcreate\\s+or\\s+replace\\s+view\\b.*")) {
            return "View";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+procedure\\b.*") ||
            cleanSql.matches(".*\\bcreate\\s+or\\s+replace\\s+procedure\\b.*")) {
            return "Procedure";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+function\\b.*") ||
            cleanSql.matches(".*\\bcreate\\s+or\\s+replace\\s+function\\b.*")) {
            return "Function";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+sequence\\b.*")) {
            return "Sequence";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+type\\b.*")) {
            return "Type";
        }
        if (cleanSql.matches(".*\\b(create|alter|drop)\\s+trigger\\b.*") ||
            cleanSql.matches(".*\\bcreate\\s+or\\s+replace\\s+trigger\\b.*")) {
            return "Trigger";
        }
        if (cleanSql.matches(".*\\b(create|drop)\\s+(unique\\s+)?index\\b.*")) {
            return "Index";
        }
        
        // Fallbacks
        if (cleanSql.contains("insert ") || cleanSql.contains("update ") || cleanSql.contains("delete ")) {
            return "Table";
        }
        
        return "Other";
    }

    private static String extractObjectName(String sql, String type) {
        if (sql == null || type == null || type.equals("Other")) return "UNKNOWN";

        String cleanSql = sql.replaceAll("(?s)/\\*.*?\\*/", "")
                             .replaceAll("--.*", "")
                             .replaceAll("\\s+", " ")
                             .trim();
        try {
            switch (type) {
                case "Table":
                    java.util.regex.Matcher mTable = java.util.regex.Pattern.compile(
                            "\\b(create|alter|drop|truncate)\\s+table\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mTable.find()) return mTable.group(2);
                    
                    java.util.regex.Matcher mInsert = java.util.regex.Pattern.compile(
                            "\\binsert\\s+into\\s+(?:table\\s+)?(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mInsert.find()) return mInsert.group(1);
                    
                    java.util.regex.Matcher mUpdate = java.util.regex.Pattern.compile(
                            "\\bupdate\\s+(?:table\\s+)?(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mUpdate.find()) return mUpdate.group(1);
                    
                    java.util.regex.Matcher mDelete = java.util.regex.Pattern.compile(
                            "\\bdelete\\s+from\\s+(?:table\\s+)?(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mDelete.find()) return mDelete.group(1);
                    break;

                case "View":
                    java.util.regex.Matcher mView = java.util.regex.Pattern.compile(
                            "\\b(create\\s+(or\\s+replace\\s+)?view|drop\\s+view)\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mView.find()) return mView.group(mView.groupCount());
                    break;

                case "Procedure":
                    java.util.regex.Matcher mProc = java.util.regex.Pattern.compile(
                            "\\b(create\\s+(or\\s+replace\\s+)?procedure|drop\\s+procedure)\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mProc.find()) return mProc.group(mProc.groupCount());
                    break;

                case "Function":
                    java.util.regex.Matcher mFunc = java.util.regex.Pattern.compile(
                            "\\b(create\\s+(or\\s+replace\\s+)?function|drop\\s+function)\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mFunc.find()) return mFunc.group(mFunc.groupCount());
                    break;

                case "Trigger":
                    java.util.regex.Matcher mTrig = java.util.regex.Pattern.compile(
                            "\\b(create\\s+(or\\s+replace\\s+)?trigger|drop\\s+trigger)\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mTrig.find()) return mTrig.group(mTrig.groupCount());
                    break;

                case "Sequence":
                    java.util.regex.Matcher mSeq = java.util.regex.Pattern.compile(
                            "\\b(create|drop|alter)\\s+sequence\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mSeq.find()) return mSeq.group(2);
                    break;

                case "Type":
                    java.util.regex.Matcher mType = java.util.regex.Pattern.compile(
                            "\\b(create|drop|alter)\\s+type\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mType.find()) return mType.group(2);
                    break;

                case "Index":
                    java.util.regex.Matcher mIdx = java.util.regex.Pattern.compile(
                            "\\b(create\\s+(unique\\s+)?index|drop\\s+index)\\s+(\\w+)", 
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleanSql);
                    if (mIdx.find()) return mIdx.group(mIdx.groupCount());
                    break;
            }
        } catch (Exception e) {
            // Ignore regex errors
        }

        return "UNKNOWN";
    }

    @Override
    public String toString() {
        return String.format("%s (Author: %s) [%s]", id, author, String.join(", ", contexts));
    }
}
