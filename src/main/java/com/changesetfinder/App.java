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

        tabPane.getTabs().addAll(tabChangesets, tabSqlStatements);
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

    public static void main(String[] args) {
        launch(args);
    }
}
