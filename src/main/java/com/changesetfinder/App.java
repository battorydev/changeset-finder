package com.changesetfinder;

import com.changesetfinder.model.Changeset;
import com.changesetfinder.parser.LiquibaseParser;
import com.changesetfinder.parser.LiquibaseParser.ParseResult;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App extends Application {

    private Stage primaryStage;
    private Label lblFolderPath;
    private Label lblStatus;
    private ComboBox<String> cbContextFilter;
    private ListView<Changeset> lvChangesets;
    private TextArea taChangesetSql;

    private ListView<String> lvFiles;
    private TextArea taFullFileSql;

    // Duplicates tab components
    private ListView<String> lvDuplicateIds;
    private ListView<Changeset> lvDuplicateOccurrences;
    private TextArea taDuplicateSql;
    private Pane paneNoDuplicates;
    private SplitPane splitPaneDuplicates;
    private Map<String, List<Changeset>> duplicateChangesets = new java.util.HashMap<>();

    // Parsed data
    private List<Changeset> allChangesets = new ArrayList<>();
    private Map<String, String> fileContents = java.util.Collections.emptyMap();
    private List<String> uniqueContexts = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Liquibase Changeset Finder");

        // Root layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // Header Pane
        VBox headerPane = createHeaderPane();
        root.setTop(headerPane);

        // Center Content (Tabs)
        TabPane tabPane = new TabPane();
        
        Tab tabChangesets = new Tab("Changeset Explorer");
        tabChangesets.setClosable(false);
        tabChangesets.setContent(createChangesetExplorerView());

        Tab tabSqlStatements = new Tab("All SQL Statements");
        tabSqlStatements.setClosable(false);
        tabSqlStatements.setContent(createAllSqlView());

        Tab tabDuplicates = new Tab("Duplicate Changesets");
        tabDuplicates.setClosable(false);
        tabDuplicates.setContent(createDuplicatesView());

        tabPane.getTabs().addAll(tabChangesets, tabSqlStatements, tabDuplicates);
        root.setCenter(tabPane);

        // Scene setup
        Scene scene = new Scene(root, 1000, 700);
        
        // Load stylesheet
        try {
            String cssPath = getClass().getResource("/styles.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("Could not load styles.css: " + e.getMessage());
        }

        stage.setScene(scene);
        stage.show();
    }

    private VBox createHeaderPane() {
        VBox header = new VBox(12);
        header.getStyleClass().add("header-pane");

        // App Branding
        VBox branding = new VBox(2);
        Label title = new Label("Liquibase Changeset Explorer");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Parse, group, and inspect database changesets by context");
        subtitle.getStyleClass().add("subtitle-label");
        branding.getChildren().addAll(title, subtitle);

        // Folder selection row
        HBox selectionRow = new HBox(12);
        selectionRow.setAlignment(Pos.CENTER_LEFT);

        Button btnChooseFolder = new Button("Select Folder");
        btnChooseFolder.setOnAction(e -> handleChooseFolder());

        lblFolderPath = new Label("No directory selected");
        lblFolderPath.getStyleClass().add("info-label");
        lblFolderPath.setStyle("-fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblStatus = new Label("Ready to parse.");
        lblStatus.getStyleClass().add("info-label");
        lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #10b981;");

        selectionRow.getChildren().addAll(btnChooseFolder, lblFolderPath, spacer, lblStatus);

        header.getChildren().addAll(branding, selectionRow);
        return header;
    }

    private Pane createChangesetExplorerView() {
        BorderPane explorer = new BorderPane();
        explorer.setPadding(new Insets(16));

        // Filter bar at the top
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(0, 0, 12, 0));

        Label lblFilter = new Label("Context Filter:");
        lblFilter.getStyleClass().add("section-label");

        cbContextFilter = new ComboBox<>();
        cbContextFilter.getItems().add("All");
        cbContextFilter.setValue("All");
        cbContextFilter.setPrefWidth(200);
        cbContextFilter.setOnAction(e -> applyFilter());

        filterBar.getChildren().addAll(lblFilter, cbContextFilter);
        explorer.setTop(filterBar);

        // Split pane for changesets list and SQL viewer
        SplitPane splitPane = new SplitPane();
        
        // Left side: Changeset ListView
        lvChangesets = new ListView<>();
        lvChangesets.setPlaceholder(new Label("No changesets loaded"));
        lvChangesets.setCellFactory(param -> new ChangesetListCell());
        lvChangesets.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            displayChangesetSql(newVal);
        });

        VBox listContainer = new VBox(8);
        Label lblListTitle = new Label("Changeset List");
        lblListTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        listContainer.getChildren().addAll(lblListTitle, lvChangesets);
        VBox.setVgrow(lvChangesets, Priority.ALWAYS);

        // Right side: SQL Preview Area
        taChangesetSql = new TextArea();
        taChangesetSql.setEditable(false);
        taChangesetSql.setPromptText("Select a changeset to display its SQL statements");

        VBox sqlContainer = new VBox(8);
        Label lblSqlTitle = new Label("SQL Content");
        lblSqlTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        sqlContainer.getChildren().addAll(lblSqlTitle, taChangesetSql);
        VBox.setVgrow(taChangesetSql, Priority.ALWAYS);

        splitPane.getItems().addAll(listContainer, sqlContainer);
        splitPane.setDividerPositions(0.4);

        explorer.setCenter(splitPane);
        return explorer;
    }

    private Pane createAllSqlView() {
        BorderPane allSqlView = new BorderPane();
        allSqlView.setPadding(new Insets(16));

        SplitPane splitPane = new SplitPane();

        // Left side: File ListView
        lvFiles = new ListView<>();
        lvFiles.setPlaceholder(new Label("No files loaded"));
        lvFiles.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            displayFileSql(newVal);
        });

        VBox listContainer = new VBox(8);
        Label lblListTitle = new Label("SQL Files");
        lblListTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        listContainer.getChildren().addAll(lblListTitle, lvFiles);
        VBox.setVgrow(lvFiles, Priority.ALWAYS);

        // Right side: SQL Content Area
        taFullFileSql = new TextArea();
        taFullFileSql.setEditable(false);
        taFullFileSql.setPromptText("Select a file or the combined view to display SQL statements");

        VBox sqlContainer = new VBox(8);
        Label lblSqlTitle = new Label("SQL Output");
        lblSqlTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        sqlContainer.getChildren().addAll(lblSqlTitle, taFullFileSql);
        VBox.setVgrow(taFullFileSql, Priority.ALWAYS);

        splitPane.getItems().addAll(listContainer, sqlContainer);
        splitPane.setDividerPositions(0.3);

        allSqlView.setCenter(splitPane);
        return allSqlView;
    }

    private void handleChooseFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose Liquibase SQL Directory");
        
        // Default to current workspace directory if it exists
        File defaultDir = new File("h:/lab/antigravity/changeset-finder");
        if (defaultDir.exists() && defaultDir.isDirectory()) {
            directoryChooser.setInitialDirectory(defaultDir);
        }

        File selectedDir = directoryChooser.showDialog(primaryStage);
        if (selectedDir != null) {
            lblFolderPath.setText(selectedDir.getAbsolutePath());
            try {
                ParseResult result = LiquibaseParser.parseDirectory(selectedDir.toPath());
                
                this.allChangesets = result.getChangesets();
                this.fileContents = result.getFileContents();
                this.uniqueContexts = result.getSortedContexts();

                // Find duplicate changesets
                this.duplicateChangesets = new java.util.HashMap<>();
                Map<String, List<Changeset>> groupedById = allChangesets.stream()
                        .collect(java.util.stream.Collectors.groupingBy(Changeset::getId));
                for (Map.Entry<String, List<Changeset>> entry : groupedById.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        this.duplicateChangesets.put(entry.getKey(), entry.getValue());
                    }
                }

                // Update Duplicates tab view
                if (duplicateChangesets.isEmpty()) {
                    paneNoDuplicates.setVisible(true);
                    splitPaneDuplicates.setVisible(false);
                    lvDuplicateIds.getItems().clear();
                    lvDuplicateOccurrences.getItems().clear();
                    taDuplicateSql.clear();
                } else {
                    paneNoDuplicates.setVisible(false);
                    splitPaneDuplicates.setVisible(true);
                    ObservableList<String> dupesList = FXCollections.observableArrayList(duplicateChangesets.keySet());
                    java.util.Collections.sort(dupesList);
                    lvDuplicateIds.setItems(dupesList);
                    lvDuplicateIds.getSelectionModel().selectFirst();
                }

                // Populate Context ComboBox
                ObservableList<String> contextsList = FXCollections.observableArrayList();
                contextsList.add("All");
                contextsList.add("<No Context>");
                contextsList.addAll(uniqueContexts);
                cbContextFilter.setItems(contextsList);
                cbContextFilter.setValue("All");

                // Populate Tab 2 File List
                ObservableList<String> filesList = FXCollections.observableArrayList();
                if (!fileContents.isEmpty()) {
                    filesList.add("[All Combined Statements]");
                    filesList.addAll(fileContents.keySet());
                }
                lvFiles.setItems(filesList);

                applyFilter();
                lblStatus.setText(String.format("Loaded %d changesets from %d files.", 
                        allChangesets.size(), fileContents.size()));

                // Auto select first file in Tab 2
                if (!filesList.isEmpty()) {
                    lvFiles.getSelectionModel().selectFirst();
                }

            } catch (IOException ex) {
                lblStatus.setText("Error parsing folder: " + ex.getMessage());
                lblStatus.setStyle("-fx-font-weight: bold; -fx-text-fill: #ef4444;");
            }
        }
    }

    private void applyFilter() {
        String selectedContext = cbContextFilter.getValue();
        if (selectedContext == null) return;

        ObservableList<Changeset> filtered = FXCollections.observableArrayList();
        for (Changeset cs : allChangesets) {
            // Exclude duplicate changesets from Changeset Explorer
            if (duplicateChangesets.containsKey(cs.getId())) {
                continue;
            }

            if (selectedContext.equals("All")) {
                filtered.add(cs);
            } else if (selectedContext.equals("<No Context>")) {
                if (cs.getContexts().isEmpty()) {
                    filtered.add(cs);
                }
            } else {
                if (cs.getContexts().contains(selectedContext)) {
                    filtered.add(cs);
                }
            }
        }

        lvChangesets.setItems(filtered);

        if (!filtered.isEmpty()) {
            lvChangesets.getSelectionModel().selectFirst();
        } else {
            taChangesetSql.clear();
        }
    }

    private void displayChangesetSql(Changeset changeset) {
        if (changeset == null) {
            taChangesetSql.clear();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- ==================================================\n");
        sb.append(String.format("-- Changeset ID: %s\n", changeset.getId()));
        sb.append(String.format("-- Author:       %s\n", changeset.getAuthor()));
        sb.append(String.format("-- Contexts:     %s\n", 
                changeset.getContexts().isEmpty() ? "None" : String.join(", ", changeset.getContexts())));
        sb.append(String.format("-- File Path:    %s\n", changeset.getFilePath()));
        sb.append("-- ==================================================\n\n");
        sb.append(changeset.getSqlContent().trim());

        taChangesetSql.setText(sb.toString());
    }

    private void displayFileSql(String fileName) {
        if (fileName == null) {
            taFullFileSql.clear();
            return;
        }

        if (fileName.equals("[All Combined Statements]")) {
            StringBuilder sb = new StringBuilder();
            sb.append("-- ==================================================\n");
            sb.append("-- COMBINED SQL STATEMENTS FROM ALL FILES\n");
            sb.append("-- ==================================================\n\n");

            for (Map.Entry<String, String> entry : fileContents.entrySet()) {
                sb.append("-- --------------------------------------------------\n");
                sb.append(String.format("-- File: %s\n", entry.getKey()));
                sb.append("-- --------------------------------------------------\n");
                sb.append(entry.getValue().trim()).append("\n\n");
            }
            taFullFileSql.setText(sb.toString());
        } else {
            String content = fileContents.get(fileName);
            if (content != null) {
                taFullFileSql.setText(content);
            } else {
                taFullFileSql.clear();
            }
        }
    }

    /**
     * Custom ListCell to display Changesets with rich styling, showing IDs, Authors and Context tags.
     */
    private static class ChangesetListCell extends ListCell<Changeset> {
        private final VBox layout;
        private final Label lblId;
        private final Label lblAuthor;
        private final FlowPane tagsPane;

        public ChangesetListCell() {
            layout = new VBox(4);
            layout.setPadding(new Insets(4, 0, 4, 0));

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            lblId = new Label();
            lblId.getStyleClass().add("cell-header");
            
            lblAuthor = new Label();
            lblAuthor.getStyleClass().add("cell-sub");

            header.getChildren().addAll(lblId, lblAuthor);

            tagsPane = new FlowPane(4, 4);
            tagsPane.setAlignment(Pos.CENTER_LEFT);

            layout.getChildren().addAll(header, tagsPane);
        }

        @Override
        protected void updateItem(Changeset item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                lblId.setText(item.getId());
                lblAuthor.setText("by " + item.getAuthor());

                tagsPane.getChildren().clear();
                if (item.getContexts().isEmpty()) {
                    Label noCtxTag = new Label("no-context");
                    noCtxTag.getStyleClass().add("cell-context-tag");
                    noCtxTag.setStyle("-fx-background-color: #2a2a35; -fx-text-fill: #94a3b8;");
                    tagsPane.getChildren().add(noCtxTag);
                } else {
                    for (String ctx : item.getContexts()) {
                        Label tag = new Label(ctx);
                        tag.getStyleClass().add("cell-context-tag");
                        tagsPane.getChildren().add(tag);
                    }
                }

                setGraphic(layout);
                setText(null);
            }
        }
    }

    private Pane createDuplicatesView() {
        StackPane container = new StackPane();
        container.setPadding(new Insets(16));

        // View 1: Placeholder when there are no duplicates
        VBox placeholder = new VBox(12);
        placeholder.setAlignment(Pos.CENTER);
        Label lblNoDupesTitle = new Label("No Duplicate Changesets Found");
        lblNoDupesTitle.getStyleClass().add("title-label");
        lblNoDupesTitle.setStyle("-fx-text-fill: #10b981;"); // Green
        Label lblNoDupesSub = new Label("All changesets have unique IDs across all files.");
        lblNoDupesSub.getStyleClass().add("subtitle-label");
        placeholder.getChildren().addAll(lblNoDupesTitle, lblNoDupesSub);
        this.paneNoDuplicates = placeholder;

        // View 2: Split pane when duplicates exist
        SplitPane splitPane = new SplitPane();
        this.splitPaneDuplicates = splitPane;

        // Left side: List of duplicate IDs
        lvDuplicateIds = new ListView<>();
        lvDuplicateIds.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int size = duplicateChangesets.containsKey(item) ? duplicateChangesets.get(item).size() : 0;
                    setText(String.format("%s (%d occurrences)", item, size));
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        lvDuplicateIds.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            displayDuplicateOccurrences(newVal);
        });

        VBox leftContainer = new VBox(8);
        Label lblLeftTitle = new Label("Duplicate IDs");
        lblLeftTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        leftContainer.getChildren().addAll(lblLeftTitle, lvDuplicateIds);
        VBox.setVgrow(lvDuplicateIds, Priority.ALWAYS);

        // Right side: Vertical split for occurrences and SQL content
        SplitPane rightSplit = new SplitPane();
        rightSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Right-Top: Occurrences list
        lvDuplicateOccurrences = new ListView<>();
        lvDuplicateOccurrences.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Changeset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox occurrenceLayout = new VBox(4);
                    Label fileLabel = new Label("File: " + item.getFilePath());
                    fileLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #ffffff;");
                    Label detailsLabel = new Label(String.format("Author: %s | Contexts: %s",
                            item.getAuthor(),
                            item.getContexts().isEmpty() ? "None" : String.join(", ", item.getContexts())));
                    detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                    occurrenceLayout.getChildren().addAll(fileLabel, detailsLabel);
                    setGraphic(occurrenceLayout);
                }
            }
        });
        lvDuplicateOccurrences.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            displayDuplicateSql(newVal);
        });

        VBox occurrencesContainer = new VBox(8);
        Label lblOccurTitle = new Label("Occurrences");
        lblOccurTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        occurrencesContainer.getChildren().addAll(lblOccurTitle, lvDuplicateOccurrences);
        VBox.setVgrow(lvDuplicateOccurrences, Priority.ALWAYS);

        // Right-Bottom: SQL TextArea
        taDuplicateSql = new TextArea();
        taDuplicateSql.setEditable(false);
        taDuplicateSql.setPromptText("Select an occurrence to display its SQL statements");

        VBox sqlContainer = new VBox(8);
        Label lblSqlTitle = new Label("SQL Statement of Selected Occurrence");
        lblSqlTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
        sqlContainer.getChildren().addAll(lblSqlTitle, taDuplicateSql);
        VBox.setVgrow(taDuplicateSql, Priority.ALWAYS);

        rightSplit.getItems().addAll(occurrencesContainer, sqlContainer);
        rightSplit.setDividerPositions(0.4);

        splitPane.getItems().addAll(leftContainer, rightSplit);
        splitPane.setDividerPositions(0.3);

        container.getChildren().addAll(placeholder, splitPane);

        // Default state: show placeholder
        placeholder.setVisible(true);
        splitPane.setVisible(false);

        return container;
    }

    private void displayDuplicateOccurrences(String duplicateId) {
        if (duplicateId == null) {
            lvDuplicateOccurrences.getItems().clear();
            taDuplicateSql.clear();
            return;
        }

        List<Changeset> occurrences = duplicateChangesets.get(duplicateId);
        if (occurrences != null) {
            lvDuplicateOccurrences.setItems(FXCollections.observableArrayList(occurrences));
            lvDuplicateOccurrences.getSelectionModel().selectFirst();
        } else {
            lvDuplicateOccurrences.getItems().clear();
            taDuplicateSql.clear();
        }
    }

    private void displayDuplicateSql(Changeset changeset) {
        if (changeset == null) {
            taDuplicateSql.clear();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- ==================================================\n");
        sb.append(String.format("-- DUPLICATE OCCURRENCE INFO\n"));
        sb.append(String.format("-- Changeset ID: %s\n", changeset.getId()));
        sb.append(String.format("-- Author:       %s\n", changeset.getAuthor()));
        sb.append(String.format("-- Contexts:     %s\n", 
                changeset.getContexts().isEmpty() ? "None" : String.join(", ", changeset.getContexts())));
        sb.append(String.format("-- File Path:    %s\n", changeset.getFilePath()));
        sb.append("-- ==================================================\n\n");
        sb.append(changeset.getSqlContent().trim());

        taDuplicateSql.setText(sb.toString());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
