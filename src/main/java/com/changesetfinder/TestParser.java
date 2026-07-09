package com.changesetfinder;

import com.changesetfinder.model.Changeset;
import com.changesetfinder.parser.LiquibaseParser;
import java.io.IOException;
import java.nio.file.Paths;

public class TestParser {
    public static void main(String[] args) throws IOException {
        System.out.println("Parsing demo1 directory...");
        LiquibaseParser.ParseResult result = LiquibaseParser.parseDirectory(Paths.get("demo1"));
        System.out.println("Files parsed: " + result.getFileContents().keySet());
        System.out.println("Contexts found: " + result.getSortedContexts());
        System.out.println("Changesets found: " + result.getChangesets().size());
        for (Changeset cs : result.getChangesets()) {
            System.out.println("----------------------------------------");
            System.out.println(cs);
            System.out.println("SQL Content:\n" + cs.getSqlContent().trim());
        }
    }
}
