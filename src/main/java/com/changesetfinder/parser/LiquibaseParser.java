package com.changesetfinder.parser;

import com.changesetfinder.model.Changeset;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LiquibaseParser {

    public static class ParseResult {
        private final List<Changeset> changesets;
        private final Map<String, String> fileContents;
        private final Set<String> uniqueContexts;

        public ParseResult(List<Changeset> changesets, Map<String, String> fileContents, Set<String> uniqueContexts) {
            this.changesets = changesets;
            this.fileContents = fileContents;
            this.uniqueContexts = uniqueContexts;
        }

        public List<Changeset> getChangesets() {
            return changesets;
        }

        public Map<String, String> getFileContents() {
            return fileContents;
        }

        public List<String> getSortedContexts() {
            List<String> list = new ArrayList<>(uniqueContexts);
            Collections.sort(list);
            return list;
        }
    }

    public interface ProgressListener {
        void onProgress(int processed, int total, String currentFile);
    }

    /**
     * Recursively parses all Liquibase SQL files in the given root folder.
     */
    public static ParseResult parseDirectory(Path rootDir) throws IOException {
        return parseDirectory(rootDir, null);
    }

    /**
     * Recursively parses all Liquibase SQL files in the given root folder with progress reporting.
     */
    public static ParseResult parseDirectory(Path rootDir, ProgressListener listener) throws IOException {
        List<Changeset> allChangesets = new ArrayList<>();
        Map<String, String> fileContents = new LinkedHashMap<>();
        Set<String> allContexts = new HashSet<>();

        if (!Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            return new ParseResult(allChangesets, fileContents, allContexts);
        }

        List<Path> sqlFiles;
        try (Stream<Path> walk = Files.walk(rootDir)) {
            sqlFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".sql"))
                    .collect(Collectors.toList());
        }

        int total = sqlFiles.size();
        int processed = 0;

        for (Path file : sqlFiles) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Parsing interrupted by user");
            }
            String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
            
            if (listener != null) {
                listener.onProgress(processed, total, relativePath);
            }

            String fullContent = Files.readString(file);
            
            // Verify if it is a Liquibase formatted SQL file
            if (isLiquibaseFile(fullContent)) {
                fileContents.put(relativePath, fullContent);
                List<Changeset> fileChangesets = parseSqlFile(file, relativePath);
                allChangesets.addAll(fileChangesets);
                
                // Collect all unique contexts
                for (Changeset cs : fileChangesets) {
                    allContexts.addAll(cs.getContexts());
                }
            }
            
            processed++;
            if (listener != null) {
                listener.onProgress(processed, total, relativePath);
            }
        }

        return new ParseResult(allChangesets, fileContents, allContexts);
    }

    private static boolean isLiquibaseFile(String content) {
        // Look at the first few lines of the file for "--liquibase formatted sql"
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(content))) {
            String line;
            int lineCount = 0;
            // Check first 10 lines to be flexible
            while ((line = reader.readLine()) != null && lineCount < 10) {
                lineCount++;
                if (line.trim().toLowerCase().startsWith("--liquibase formatted sql")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }

    private static List<Changeset> parseSqlFile(Path file, String relativePath) {
        List<Changeset> changesets = new ArrayList<>();
        String absolutePathStr = file.toAbsolutePath().toString().replace('\\', '/');
        
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int lineNum = 0;
            
            // Temporary parsing state
            String activeId = null;
            String activeAuthor = null;
            List<String> activeContexts = new ArrayList<>();
            StringBuilder activeSql = new StringBuilder();
            int changesetStartLine = -1;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                
                if (trimmed.toLowerCase().startsWith("--changeset ")) {
                    // If we have an active changeset, save it before starting the next one
                    if (activeId != null) {
                        changesets.add(new Changeset(
                            activeId, 
                            activeAuthor, 
                            relativePath, 
                            absolutePathStr, 
                            activeContexts, 
                            changesetStartLine, 
                            lineNum - 1, 
                            activeSql.toString()
                        ));
                        // Reset
                        activeId = null;
                        activeAuthor = null;
                        activeContexts = new ArrayList<>();
                        activeSql = new StringBuilder();
                        changesetStartLine = -1;
                    }
                    
                    // Parse the new changeset header
                    // Example: --changeset author:id context:UAT,PRD failOnError:false
                    String headerData = trimmed.substring("--changeset ".length()).trim();
                    String[] tokens = headerData.split("\\s+");
                    
                    if (tokens.length > 0) {
                        String authorIdPart = tokens[0];
                        int colonIdx = authorIdPart.indexOf(':');
                        if (colonIdx > 0) {
                            activeAuthor = authorIdPart.substring(0, colonIdx);
                            activeId = authorIdPart.substring(colonIdx + 1);
                        } else {
                            activeAuthor = "unknown";
                            activeId = authorIdPart;
                        }
                        
                        // Parse attributes
                        for (int i = 1; i < tokens.length; i++) {
                            String token = tokens[i];
                            int eqIdx = token.indexOf(':');
                            if (eqIdx > 0) {
                                String key = token.substring(0, eqIdx).trim().toLowerCase();
                                String val = token.substring(eqIdx + 1).trim();
                                if (key.equals("context") || key.equals("contexts")) {
                                    // Split by comma
                                    String[] ctxVals = val.split(",");
                                    for (String cv : ctxVals) {
                                        String cleanCv = cv.trim();
                                        if (!cleanCv.isEmpty()) {
                                            activeContexts.add(cleanCv);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    changesetStartLine = lineNum + 1;
                } else {
                    // If we are currently parsing a changeset, accumulate the SQL statement lines
                    if (activeId != null) {
                        activeSql.append(line).append("\n");
                    }
                }
            }
            
            // Add the last changeset in the file if exists
            if (activeId != null) {
                changesets.add(new Changeset(
                    activeId, 
                    activeAuthor, 
                    relativePath, 
                    absolutePathStr, 
                    activeContexts, 
                    changesetStartLine, 
                    lineNum, 
                    activeSql.toString()
                ));
            }
            
        } catch (IOException e) {
            // Ignore
        }
        
        return changesets;
    }
}
