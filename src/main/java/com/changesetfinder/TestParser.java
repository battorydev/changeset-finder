package com.changesetfinder;

import com.changesetfinder.model.Changeset;
import com.changesetfinder.parser.LiquibaseParser;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestParser {
    public static void main(String[] args) throws IOException {
        System.out.println("Parsing demo1 directory...");
        LiquibaseParser.ParseResult result = LiquibaseParser.parseDirectory(Paths.get("demo1"));
        System.out.println("Files parsed: " + result.getFileContents().keySet());
        System.out.println("Contexts found: " + result.getSortedContexts());
        System.out.println("Changesets found: " + result.getChangesets().size());
        
        // Find duplicates
        Map<String, List<Changeset>> groupedById = result.getChangesets().stream()
                .collect(Collectors.groupingBy(Changeset::getId));
        Map<String, List<Changeset>> duplicates = groupedById.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        System.out.println("\nDuplicate changesets found: " + duplicates.size());
        for (Map.Entry<String, List<Changeset>> entry : duplicates.entrySet()) {
            System.out.println("Duplicate ID: " + entry.getKey() + " (" + entry.getValue().size() + " occurrences)");
            for (Changeset cs : entry.getValue()) {
                System.out.println("  - Author: " + cs.getAuthor() + " | File: " + cs.getFilePath() + " | Contexts: " + cs.getContexts());
            }
        }
    }
}
