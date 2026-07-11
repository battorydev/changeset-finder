package com.changesetfinder;

import com.changesetfinder.model.Changeset;
import com.changesetfinder.parser.LiquibaseParser;
import com.changesetfinder.parser.LiquibaseParser.ParseResult;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class App extends JFrame {
    private JLabel lblFolderPath;
    private JLabel lblStatus;
    private JComboBox<String> cbContextFilter;
    private JList<Changeset> lvChangesets;
    private JTextArea taChangesetSql;

    // Loading overlay fields
    private JTabbedPane tabPane;
    private GlassLoadingOverlay loadingOverlay;
    private SwingWorker<ParseResult, Void> activeWorker;

    // Tab 2 SQL statements
    private JTree tvFiles;
    private JTextArea taFullFileSql;

    // Duplicates tab components
    private JList<String> lvDuplicateIds;
    private JList<Changeset> lvDuplicateOccurrences;
    private JTextArea taDuplicateSql;
    private CardLayout duplicatesCardLayout;
    private JPanel duplicatesContainerPanel;
    private Map<String, List<Changeset>> duplicateChangesets = new java.util.HashMap<>();

    // Database objects tab components
    private JTree tvObjects;
    private JTextArea taObjectSql;
    private JComboBox<String> cbObjectsContextFilter;

    // Parsed data
    private List<Changeset> allChangesets = new ArrayList<>();
    private Map<String, String> fileContents = java.util.Collections.emptyMap();
    private List<String> uniqueContexts = new ArrayList<>();

    public App() {
        super("Changeset Finder");
        initUI();
    }

    private void initUI() {
        // Initialize Nimbus Look and Feel with custom bright palette colors
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.put("control", Color.WHITE);
                    UIManager.put("info", Color.WHITE);
                    UIManager.put("nimbusLightBackground", Color.WHITE);
                    UIManager.put("nimbusSelectionBackground", new Color(219, 234, 254)); // Soft light blue #dbeafe
                    UIManager.put("nimbusSelectedText", new Color(30, 58, 138)); // Dark Blue #1e3a8a
                    UIManager.put("text", new Color(15, 23, 42)); // Dark Slate #0f172a
                    UIManager.put("nimbusBlueGrey", new Color(203, 213, 225)); // Border slate-gray #cbd5e1
                    UIManager.put("nimbusBase", new Color(30, 58, 138)); // Dark Blue base
                    
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Fallback to default
        }

        setLayout(new BorderLayout());

        // Header Pane
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Center Content (Tabs)
        tabPane = new JTabbedPane();
        tabPane.addTab("Changeset Explorer", createChangesetExplorerView());
        tabPane.addTab("All SQL Statements", createAllSqlView());
        tabPane.addTab("Duplicate Changesets", createDuplicatesView());
        tabPane.addTab("Database Objects", createDatabaseObjectsView());
        add(tabPane, BorderLayout.CENTER);

        // Loading Overlay (Glass Pane)
        loadingOverlay = new GlassLoadingOverlay();
        setGlassPane(loadingOverlay);

        setSize(1150, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());

        // Top Gradient Branding Banner
        GradientHeaderPanel branding = new GradientHeaderPanel();
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        branding.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        JLabel lblTitle = new JLabel("Changeset Finder");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 22f));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Analyze and explore changeset files recursively");
        lblSub.setFont(lblSub.getFont().deriveFont(12f));
        lblSub.setForeground(new Color(226, 232, 240));

        branding.add(lblTitle);
        branding.add(Box.createRigidArea(new Dimension(0, 4)));
        branding.add(lblSub);

        // Folder selection row
        JPanel selectionRow = new JPanel(new BorderLayout(16, 0));
        selectionRow.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        selectionRow.setBackground(Color.WHITE);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);
        JButton btnChooseFolder = new JButton("Choose Folder");
        btnChooseFolder.addActionListener(e -> handleChooseFolder());

        lblFolderPath = new JLabel("No directory selected");
        lblFolderPath.setFont(lblFolderPath.getFont().deriveFont(Font.ITALIC));
        lblFolderPath.setForeground(new Color(100, 116, 139));

        leftPanel.add(btnChooseFolder);
        leftPanel.add(lblFolderPath);

        lblStatus = new JLabel("Ready.");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
        lblStatus.setForeground(new Color(21, 128, 61)); // Dark green

        selectionRow.add(leftPanel, BorderLayout.WEST);
        selectionRow.add(lblStatus, BorderLayout.EAST);

        header.add(branding, BorderLayout.NORTH);
        header.add(selectionRow, BorderLayout.SOUTH);

        return header;
    }

    private JPanel createChangesetExplorerView() {
        JPanel explorer = new JPanel(new BorderLayout());
        explorer.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        explorer.setBackground(Color.WHITE);

        // Top Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel lblFilter = new JLabel("Context Filter:");
        lblFilter.setFont(lblFilter.getFont().deriveFont(Font.BOLD));
        lblFilter.setForeground(new Color(100, 116, 139));

        cbContextFilter = new JComboBox<>(new String[]{"All", "<No Context>"});
        cbContextFilter.setPreferredSize(new Dimension(200, 26));
        cbContextFilter.addActionListener(e -> applyFilter());

        JButton btnExport = new JButton("Export");
        btnExport.addActionListener(e -> handleExport());

        filterBar.add(lblFilter);
        filterBar.add(cbContextFilter);
        filterBar.add(btnExport);
        explorer.add(filterBar, BorderLayout.NORTH);

        // Center Split Layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(380);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // Left list panel
        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setOpaque(false);
        JLabel lblListTitle = new JLabel("Changeset List");
        lblListTitle.setFont(lblListTitle.getFont().deriveFont(Font.BOLD));
        lblListTitle.setForeground(new Color(100, 116, 139));

        lvChangesets = new JList<>();
        lvChangesets.setCellRenderer(new ChangesetCellRenderer());
        lvChangesets.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displayChangesetSql(lvChangesets.getSelectedValue());
            }
        });
        JScrollPane listScroll = new JScrollPane(lvChangesets);
        listScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));

        listPanel.add(lblListTitle, BorderLayout.NORTH);
        listPanel.add(listScroll, BorderLayout.CENTER);

        // Right details panel
        JPanel sqlPanel = new JPanel(new BorderLayout(0, 8));
        sqlPanel.setOpaque(false);
        JLabel lblSqlTitle = new JLabel("SQL Content");
        lblSqlTitle.setFont(lblSqlTitle.getFont().deriveFont(Font.BOLD));
        lblSqlTitle.setForeground(new Color(100, 116, 139));

        taChangesetSql = new JTextArea();
        JScrollPane sqlScroll = createMonospaceTextAreaWithScroll(taChangesetSql);

        sqlPanel.add(lblSqlTitle, BorderLayout.NORTH);
        sqlPanel.add(sqlScroll, BorderLayout.CENTER);

        splitPane.setLeftComponent(listPanel);
        splitPane.setRightComponent(sqlPanel);
        explorer.add(splitPane, BorderLayout.CENTER);

        return explorer;
    }

    private JPanel createAllSqlView() {
        JPanel allSql = new JPanel(new BorderLayout());
        allSql.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        allSql.setBackground(Color.WHITE);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(320);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // Left file tree panel
        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setOpaque(false);
        JLabel lblListTitle = new JLabel("SQL Files");
        lblListTitle.setFont(lblListTitle.getFont().deriveFont(Font.BOLD));
        lblListTitle.setForeground(new Color(100, 116, 139));

        tvFiles = new JTree(new DefaultMutableTreeNode("SQL Files"));
        tvFiles.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tvFiles.getLastSelectedPathComponent();
                if (node == null) return;
                Object[] paths = node.getUserObjectPath();
                if (paths.length <= 1) {
                    taFullFileSql.setText("");
                    return;
                }
                if (node.toString().equals("[All Combined Statements]")) {
                    displayFileSql("[All Combined Statements]");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < paths.length; i++) {
                    sb.append(paths[i].toString());
                    if (i < paths.length - 1) {
                        sb.append("/");
                    }
                }
                displayFileSql(sb.toString());
            }
        });
        JScrollPane treeScroll = new JScrollPane(tvFiles);
        treeScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));

        listPanel.add(lblListTitle, BorderLayout.NORTH);
        listPanel.add(treeScroll, BorderLayout.CENTER);

        // Right details panel
        JPanel sqlPanel = new JPanel(new BorderLayout(0, 8));
        sqlPanel.setOpaque(false);
        JLabel lblSqlTitle = new JLabel("SQL Output");
        lblSqlTitle.setFont(lblSqlTitle.getFont().deriveFont(Font.BOLD));
        lblSqlTitle.setForeground(new Color(100, 116, 139));

        taFullFileSql = new JTextArea();
        JScrollPane sqlScroll = createMonospaceTextAreaWithScroll(taFullFileSql);

        sqlPanel.add(lblSqlTitle, BorderLayout.NORTH);
        sqlPanel.add(sqlScroll, BorderLayout.CENTER);

        splitPane.setLeftComponent(listPanel);
        splitPane.setRightComponent(sqlPanel);
        allSql.add(splitPane, BorderLayout.CENTER);

        return allSql;
    }

    private JPanel createDuplicatesView() {
        duplicatesCardLayout = new CardLayout();
        duplicatesContainerPanel = new JPanel(duplicatesCardLayout);
        duplicatesContainerPanel.setBackground(Color.WHITE);

        // View 1: Placeholder Card when there are no duplicates
        JPanel placeholder = new JPanel(new GridBagLayout());
        placeholder.setBackground(Color.WHITE);
        JPanel placeholderInner = new JPanel();
        placeholderInner.setLayout(new BoxLayout(placeholderInner, BoxLayout.Y_AXIS));
        placeholderInner.setOpaque(false);

        JLabel lblNoDupesTitle = new JLabel("No Duplicate Changesets Found");
        lblNoDupesTitle.setFont(lblNoDupesTitle.getFont().deriveFont(Font.BOLD, 16f));
        lblNoDupesTitle.setForeground(new Color(21, 128, 61)); // Dark green
        lblNoDupesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblNoDupesSub = new JLabel("All changesets have unique IDs across all files.");
        lblNoDupesSub.setFont(lblNoDupesSub.getFont().deriveFont(12f));
        lblNoDupesSub.setForeground(new Color(100, 116, 139));
        lblNoDupesSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        placeholderInner.add(lblNoDupesTitle);
        placeholderInner.add(Box.createRigidArea(new Dimension(0, 8)));
        placeholderInner.add(lblNoDupesSub);
        placeholder.add(placeholderInner);

        // View 2: Splitpane Card when duplicates exist
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contentPanel.setOpaque(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // Left duplicate list
        JPanel leftPanel = new JPanel(new BorderLayout(0, 8));
        leftPanel.setOpaque(false);
        JLabel lblLeftTitle = new JLabel("Duplicate IDs");
        lblLeftTitle.setFont(lblLeftTitle.getFont().deriveFont(Font.BOLD));
        lblLeftTitle.setForeground(new Color(100, 116, 139));

        lvDuplicateIds = new JList<>();
        lvDuplicateIds.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displayDuplicateOccurrences(lvDuplicateIds.getSelectedValue());
            }
        });
        JScrollPane dupesScroll = new JScrollPane(lvDuplicateIds);
        dupesScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        leftPanel.add(lblLeftTitle, BorderLayout.NORTH);
        leftPanel.add(dupesScroll, BorderLayout.CENTER);

        // Right details panel
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setDividerLocation(250);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());

        // Top: Occurrences list
        JPanel occurrencesPanel = new JPanel(new BorderLayout(0, 8));
        occurrencesPanel.setOpaque(false);
        JLabel lblOccurTitle = new JLabel("Occurrences");
        lblOccurTitle.setFont(lblOccurTitle.getFont().deriveFont(Font.BOLD));
        lblOccurTitle.setForeground(new Color(100, 116, 139));

        lvDuplicateOccurrences = new JList<>();
        lvDuplicateOccurrences.setCellRenderer(new DuplicateOccurrenceCellRenderer());
        lvDuplicateOccurrences.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displayDuplicateSql(lvDuplicateOccurrences.getSelectedValue());
            }
        });
        JScrollPane occurrencesScroll = new JScrollPane(lvDuplicateOccurrences);
        occurrencesScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        occurrencesPanel.add(lblOccurTitle, BorderLayout.NORTH);
        occurrencesPanel.add(occurrencesScroll, BorderLayout.CENTER);

        // Bottom: SQL Preview text area
        JPanel sqlPanel = new JPanel(new BorderLayout(0, 8));
        sqlPanel.setOpaque(false);
        JLabel lblSqlTitle = new JLabel("SQL Statement of Selected Occurrence");
        lblSqlTitle.setFont(lblSqlTitle.getFont().deriveFont(Font.BOLD));
        lblSqlTitle.setForeground(new Color(100, 116, 139));

        taDuplicateSql = new JTextArea();
        JScrollPane sqlScroll = createMonospaceTextAreaWithScroll(taDuplicateSql);
        sqlPanel.add(lblSqlTitle, BorderLayout.NORTH);
        sqlPanel.add(sqlScroll, BorderLayout.CENTER);

        rightSplit.setTopComponent(occurrencesPanel);
        rightSplit.setBottomComponent(sqlPanel);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightSplit);
        contentPanel.add(splitPane, BorderLayout.CENTER);

        duplicatesContainerPanel.add(placeholder, "placeholder");
        duplicatesContainerPanel.add(contentPanel, "content");
        duplicatesCardLayout.show(duplicatesContainerPanel, "placeholder");

        return duplicatesContainerPanel;
    }

    private JPanel createDatabaseObjectsView() {
        JPanel objectsView = new JPanel(new BorderLayout());
        objectsView.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        objectsView.setBackground(Color.WHITE);

        // Top Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel lblFilter = new JLabel("Context Filter:");
        lblFilter.setFont(lblFilter.getFont().deriveFont(Font.BOLD));
        lblFilter.setForeground(new Color(100, 116, 139));

        cbObjectsContextFilter = new JComboBox<>(new String[]{"All", "<No Context>"});
        cbObjectsContextFilter.setPreferredSize(new Dimension(200, 26));
        cbObjectsContextFilter.addActionListener(e -> applyObjectsFilter());

        filterBar.add(lblFilter);
        filterBar.add(cbObjectsContextFilter);
        objectsView.add(filterBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(320);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // Left objects tree panel
        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setOpaque(false);
        JLabel lblListTitle = new JLabel("Grouped Objects");
        lblListTitle.setFont(lblListTitle.getFont().deriveFont(Font.BOLD));
        lblListTitle.setForeground(new Color(100, 116, 139));

        tvObjects = new JTree(new DefaultMutableTreeNode("Database Objects"));
        tvObjects.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tvObjects.getLastSelectedPathComponent();
                if (node == null) {
                    taObjectSql.setText("");
                    return;
                }
                Object userObj = node.getUserObject();
                if (userObj instanceof DbObjectNodeUserObject) {
                    DbObjectNodeUserObject item = (DbObjectNodeUserObject) userObj;
                    displayObjectSql(item.getObjectType(), item.getObjectName(), item.getChangesets());
                } else {
                    taObjectSql.setText("");
                }
            }
        });
        JScrollPane treeScroll = new JScrollPane(tvObjects);
        treeScroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));

        listPanel.add(lblListTitle, BorderLayout.NORTH);
        listPanel.add(treeScroll, BorderLayout.CENTER);

        // Right details panel
        JPanel sqlPanel = new JPanel(new BorderLayout(0, 8));
        sqlPanel.setOpaque(false);
        JLabel lblSqlTitle = new JLabel("SQL Content");
        lblSqlTitle.setFont(lblSqlTitle.getFont().deriveFont(Font.BOLD));
        lblSqlTitle.setForeground(new Color(100, 116, 139));

        taObjectSql = new JTextArea();
        JScrollPane sqlScroll = createMonospaceTextAreaWithScroll(taObjectSql);

        sqlPanel.add(lblSqlTitle, BorderLayout.NORTH);
        sqlPanel.add(sqlScroll, BorderLayout.CENTER);

        splitPane.setLeftComponent(listPanel);
        splitPane.setRightComponent(sqlPanel);
        objectsView.add(splitPane, BorderLayout.CENTER);

        return objectsView;
    }

    private void handleChooseFolder() {
        JFileChooser directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        directoryChooser.setDialogTitle("Choose Liquibase SQL Directory");

        File defaultDir = new File("h:/lab/antigravity/changeset-finder");
        if (defaultDir.exists() && defaultDir.isDirectory()) {
            directoryChooser.setCurrentDirectory(defaultDir);
        }

        int result = directoryChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = directoryChooser.getSelectedFile();
            lblFolderPath.setText(selectedDir.getAbsolutePath());
            
            showLoading(true);
            lblStatus.setText("Initializing parsing...");
            lblStatus.setForeground(new Color(30, 58, 138));

            activeWorker = new SwingWorker<>() {
                private ParseResult parseResult;

                @Override
                protected ParseResult doInBackground() throws Exception {
                    return LiquibaseParser.parseDirectory(selectedDir.toPath(), (processed, total, currentFile) -> {
                        int pct = total > 0 ? (processed * 100 / total) : 0;
                        setProgress(pct);
                        firePropertyChange("statusMsg", null, String.format("Parsing: %s (%d/%d)", currentFile, processed, total));
                    });
                }

                @Override
                protected void done() {
                    try {
                        if (isCancelled()) {
                            lblStatus.setText("Parsing cancelled by user.");
                            lblStatus.setForeground(new Color(180, 83, 9)); // dark orange
                            clearLoadedData();
                            showLoading(false);
                            return;
                        }
                        
                        parseResult = get();
                        
                        allChangesets = parseResult.getChangesets();
                        fileContents = parseResult.getFileContents();
                        uniqueContexts = parseResult.getSortedContexts();

                        // Group duplicate changesets
                        duplicateChangesets = new java.util.HashMap<>();
                        Map<String, List<Changeset>> groupedById = allChangesets.stream()
                                .collect(java.util.stream.Collectors.groupingBy(Changeset::getId));
                        for (Map.Entry<String, List<Changeset>> entry : groupedById.entrySet()) {
                            if (entry.getValue().size() > 1) {
                                duplicateChangesets.put(entry.getKey(), entry.getValue());
                            }
                        }

                        // Populate explorer dropdown
                        DefaultComboBoxModel<String> modelExplorer = new DefaultComboBoxModel<>();
                        modelExplorer.addElement("All");
                        modelExplorer.addElement("<No Context>");
                        for (String ctx : uniqueContexts) {
                            modelExplorer.addElement(ctx);
                        }
                        cbContextFilter.setModel(modelExplorer);
                        cbContextFilter.setSelectedItem("All");

                        // Populate objects dropdown
                        DefaultComboBoxModel<String> modelObjects = new DefaultComboBoxModel<>();
                        modelObjects.addElement("All");
                        modelObjects.addElement("<No Context>");
                        for (String ctx : uniqueContexts) {
                            modelObjects.addElement(ctx);
                        }
                        cbObjectsContextFilter.setModel(modelObjects);
                        cbObjectsContextFilter.setSelectedItem("All");

                        // Populate Duplicate ID JList
                        DefaultListModel<String> dupesListModel = new DefaultListModel<>();
                        if (duplicateChangesets.isEmpty()) {
                            duplicatesCardLayout.show(duplicatesContainerPanel, "placeholder");
                            lvDuplicateIds.setModel(dupesListModel);
                        } else {
                            duplicatesCardLayout.show(duplicatesContainerPanel, "content");
                            List<String> sortedDupes = new ArrayList<>(duplicateChangesets.keySet());
                            java.util.Collections.sort(sortedDupes);
                            for (String d : sortedDupes) {
                                dupesListModel.addElement(d);
                            }
                            lvDuplicateIds.setModel(dupesListModel);
                            lvDuplicateIds.setSelectedIndex(0);
                        }

                        // Populate Files JTree
                        populateFileTree(fileContents);

                        // Trigger filters
                        applyFilter();
                        applyObjectsFilter();

                        lblStatus.setText(String.format("Loaded %d changesets from %d files.", 
                                allChangesets.size(), fileContents.size()));
                        lblStatus.setForeground(new Color(21, 128, 61)); // dark green

                        // Select first file in Files Tree
                        if (tvFiles.getRowCount() > 1) {
                            tvFiles.setSelectionRow(1); // Select first file node under root
                        }

                    } catch (Exception ex) {
                        lblStatus.setText("Error parsing folder: " + ex.getMessage());
                        lblStatus.setForeground(new Color(185, 28, 28)); // dark red
                        clearLoadedData();
                    } finally {
                        showLoading(false);
                    }
                }
            };

            // Listener to update progress bar and status message in GlassPane
            activeWorker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    loadingOverlay.setProgress((Integer) evt.getNewValue());
                } else if ("statusMsg".equals(evt.getPropertyName())) {
                    loadingOverlay.setStatusMessage((String) evt.getNewValue());
                }
            });

            activeWorker.execute();
        }
    }

    private void clearLoadedData() {
        allChangesets.clear();
        fileContents = java.util.Collections.emptyMap();
        uniqueContexts.clear();
        duplicateChangesets.clear();

        cbContextFilter.setModel(new DefaultComboBoxModel<>(new String[]{"All", "<No Context>"}));
        cbObjectsContextFilter.setModel(new DefaultComboBoxModel<>(new String[]{"All", "<No Context>"}));
        
        lvChangesets.setModel(new DefaultListModel<>());
        taChangesetSql.setText("");

        tvFiles.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("SQL Files")));
        taFullFileSql.setText("");

        duplicatesCardLayout.show(duplicatesContainerPanel, "placeholder");
        lvDuplicateIds.setModel(new DefaultListModel<>());
        lvDuplicateOccurrences.setModel(new DefaultListModel<>());
        taDuplicateSql.setText("");

        tvObjects.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Database Objects")));
        taObjectSql.setText("");
    }

    private void handleCancelParsing() {
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
        }
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
        tabPane.setEnabled(!show);
    }

    private void applyFilter() {
        String selectedContext = (String) cbContextFilter.getSelectedItem();
        if (selectedContext == null) return;

        DefaultListModel<Changeset> filteredModel = new DefaultListModel<>();
        for (Changeset cs : allChangesets) {
            // Exclude duplicate changesets from Changeset Explorer view
            if (duplicateChangesets.containsKey(cs.getId())) {
                continue;
            }

            if (selectedContext.equals("All")) {
                filteredModel.addElement(cs);
            } else if (selectedContext.equals("<No Context>")) {
                if (cs.getContexts().isEmpty()) {
                    filteredModel.addElement(cs);
                }
            } else {
                if (cs.getContexts().contains(selectedContext)) {
                    filteredModel.addElement(cs);
                }
            }
        }

        lvChangesets.setModel(filteredModel);

        if (!filteredModel.isEmpty()) {
            lvChangesets.setSelectedIndex(0);
        } else {
            taChangesetSql.setText("");
        }
    }

    private void applyObjectsFilter() {
        String selectedContext = (String) cbObjectsContextFilter.getSelectedItem();
        if (selectedContext == null) return;

        List<Changeset> filtered = new ArrayList<>();
        for (Changeset cs : allChangesets) {
            // Exclude duplicate changesets from Database Objects Tree
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

        populateObjectsTree(filtered);
    }

    private void displayChangesetSql(Changeset changeset) {
        if (changeset == null) {
            taChangesetSql.setText("");
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
        taChangesetSql.setCaretPosition(0);
    }

    private void displayFileSql(String path) {
        if (path == null) {
            taFullFileSql.setText("");
            return;
        }

        if (path.equals("[All Combined Statements]")) {
            StringBuilder sb = new StringBuilder();
            sb.append("-- ==================================================\n");
            sb.append("-- COMBINED SQL STATEMENTS FROM ALL FILES\n");
            sb.append(String.format("-- Total Files: %d\n", fileContents.size()));
            sb.append("-- ==================================================\n\n");
            for (Map.Entry<String, String> entry : fileContents.entrySet()) {
                sb.append(String.format("-- File: %s\n", entry.getKey()));
                sb.append(entry.getValue().trim()).append("\n\n");
            }
            taFullFileSql.setText(sb.toString().trim());
            taFullFileSql.setCaretPosition(0);
        } else {
            if (fileContents.containsKey(path)) {
                taFullFileSql.setText(fileContents.get(path).trim());
                taFullFileSql.setCaretPosition(0);
            } else {
                taFullFileSql.setText("");
            }
        }
    }

    private void displayObjectSql(String type, String name, List<Changeset> changesets) {
        if (changesets == null || changesets.isEmpty()) {
            taObjectSql.setText("");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- ==================================================\n");
        sb.append(String.format("-- Object Type:  %s\n", type));
        sb.append(String.format("-- Object Name:  %s\n", name));
        sb.append(String.format("-- Changesets:   %d\n", changesets.size()));
        sb.append("-- ==================================================\n\n");

        for (int i = 0; i < changesets.size(); i++) {
            Changeset cs = changesets.get(i);
            sb.append(String.format("-- Changeset %d of %d: %s (by %s in %s)\n", 
                    (i + 1), changesets.size(), cs.getId(), cs.getAuthor(), cs.getFilePath()));
            if (!cs.getContexts().isEmpty()) {
                sb.append(String.format("-- Contexts:     %s\n", String.join(", ", cs.getContexts())));
            }
            sb.append(cs.getSqlContent().trim()).append("\n\n");
        }

        taObjectSql.setText(sb.toString().trim());
        taObjectSql.setCaretPosition(0);
    }

    private void displayDuplicateOccurrences(String id) {
        if (id == null || !duplicateChangesets.containsKey(id)) {
            lvDuplicateOccurrences.setModel(new DefaultListModel<>());
            taDuplicateSql.setText("");
            return;
        }

        List<Changeset> occurrences = duplicateChangesets.get(id);
        DefaultListModel<Changeset> occurrencesModel = new DefaultListModel<>();
        for (Changeset cs : occurrences) {
            occurrencesModel.addElement(cs);
        }
        lvDuplicateOccurrences.setModel(occurrencesModel);
        lvDuplicateOccurrences.setSelectedIndex(0);
    }

    private void displayDuplicateSql(Changeset cs) {
        if (cs == null) {
            taDuplicateSql.setText("");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- ==================================================\n");
        sb.append("-- DUPLICATE OCCURRENCE DETAILS\n");
        sb.append(String.format("-- Changeset ID: %s\n", cs.getId()));
        sb.append(String.format("-- Author:       %s\n", cs.getAuthor()));
        sb.append(String.format("-- File Path:    %s\n", cs.getFilePath()));
        sb.append(String.format("-- Contexts:     %s\n", cs.getContexts().isEmpty() ? "None" : String.join(", ", cs.getContexts())));
        sb.append("-- ==================================================\n\n");
        sb.append(cs.getSqlContent().trim());

        taDuplicateSql.setText(sb.toString());
        taDuplicateSql.setCaretPosition(0);
    }

    private void populateFileTree(Map<String, String> fileContents) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("SQL Files");

        DefaultMutableTreeNode combinedNode = new DefaultMutableTreeNode("[All Combined Statements]");
        root.add(combinedNode);

        for (String relativePath : fileContents.keySet()) {
            String[] parts = relativePath.split("/");
            DefaultMutableTreeNode current = root;
            for (String part : parts) {
                DefaultMutableTreeNode found = null;
                for (int i = 0; i < current.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) current.getChildAt(i);
                    if (child.toString().equals(part)) {
                        found = child;
                        break;
                    }
                }
                if (found == null) {
                    found = new DefaultMutableTreeNode(part);
                    current.add(found);
                }
                current = found;
            }
        }

        tvFiles.setModel(new DefaultTreeModel(root));
        // Expand directories
        for (int i = 0; i < tvFiles.getRowCount(); i++) {
            tvFiles.expandRow(i);
        }
    }

    private void populateObjectsTree(List<Changeset> changesets) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Database Objects");

        Map<String, Map<String, List<Changeset>>> grouped = new java.util.HashMap<>();
        String[] categories = {"Table", "View", "Procedure", "Function", "Sequence", "Type", "Index", "Trigger", "Other"};
        for (String cat : categories) {
            grouped.put(cat, new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        }

        for (Changeset cs : changesets) {
            Map<String, List<Changeset>> nameMap = grouped.get(cs.getDbObjectType());
            nameMap.computeIfAbsent(cs.getDbObjectName(), k -> new ArrayList<>()).add(cs);
        }

        for (String cat : categories) {
            Map<String, List<Changeset>> nameMap = grouped.get(cat);
            if (nameMap != null && !nameMap.isEmpty()) {
                int totalObjects = nameMap.size();
                DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(String.format("%s (%d)", cat, totalObjects));

                for (Map.Entry<String, List<Changeset>> entry : nameMap.entrySet()) {
                    String objName = entry.getKey();
                    List<Changeset> list = entry.getValue();

                    DbObjectNodeUserObject userObj = new DbObjectNodeUserObject(cat, objName, list);
                    DefaultMutableTreeNode objNode = new DefaultMutableTreeNode(userObj);
                    catNode.add(objNode);
                }
                root.add(catNode);
            }
        }

        tvObjects.setModel(new DefaultTreeModel(root));
        // Expand category folders
        for (int i = 0; i < tvObjects.getRowCount(); i++) {
            tvObjects.expandRow(i);
        }
    }

    private void handleExport() {
        if (allChangesets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No changesets loaded to export.", "Export warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Exported Changesets");
        fileChooser.setSelectedFile(new File("exported_changesets.txt"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("================================================================================\n");
                sb.append("EXPORTED LIQUIBASE CHANGESETS\n");
                sb.append(String.format("Filter:            Context = %s\n", cbContextFilter.getSelectedItem()));
                int listSize = lvChangesets.getModel().getSize();
                sb.append(String.format("Total Changesets:  %d\n", listSize));
                sb.append(String.format("Exported On:       %s\n", java.time.LocalDateTime.now().toString()));
                sb.append("================================================================================\n\n");

                for (int i = 0; i < listSize; i++) {
                    Changeset cs = lvChangesets.getModel().getElementAt(i);
                    sb.append("--------------------------------------------------------------------------------\n");
                    sb.append(String.format("Changeset ID: %s\n", cs.getId()));
                    sb.append(String.format("Author:       %s\n", cs.getAuthor()));
                    sb.append(String.format("File Path:    %s\n", cs.getFilePath()));
                    sb.append(String.format("Contexts:     %s\n", cs.getContexts().isEmpty() ? "None" : String.join(", ", cs.getContexts())));
                    sb.append("--------------------------------------------------------------------------------\n");
                    sb.append(cs.getSqlContent().trim()).append("\n\n");
                }

                Files.writeString(file.toPath(), sb.toString());
                JOptionPane.showMessageDialog(this, "Successfully exported " + listSize + " changesets to:\n" + file.getAbsolutePath(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
                lblStatus.setText("Exported " + listSize + " changesets.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save file:\n" + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JScrollPane createMonospaceTextAreaWithScroll(JTextArea textArea) {
        textArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        textArea.setEditable(false);
        textArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
        return scroll;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.setVisible(true);
        });
    }

    // =========================================================================
    // Inner Panels & Helpers
    // =========================================================================

    private static class GradientHeaderPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            // Gradient from dark blue (#1e3a8a) to dark red (#7f1d1d)
            GradientPaint gp = new GradientPaint(0, 0, new Color(30, 58, 138), w, 0, new Color(127, 29, 29));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, w, h);
            g2d.dispose();
        }
    }

    private class GlassLoadingOverlay extends JPanel {
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final JLabel lblTitle = new JLabel("Parsing Liquibase Files");
        private final JLabel lblStatusMsg = new JLabel("Initializing directory scan...");
        private final JButton btnCancel = new JButton("Cancel");

        public GlassLoadingOverlay() {
            setLayout(new GridBagLayout());
            setOpaque(false);
            setVisible(false);

            // Centered panel card
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                    BorderFactory.createEmptyBorder(24, 24, 24, 24)
            ));
            card.setBackground(Color.WHITE);
            card.setAlignmentX(Component.CENTER_ALIGNMENT);

            lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 15f));
            lblTitle.setForeground(new Color(30, 58, 138)); // Dark blue
            lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            progressBar.setPreferredSize(new Dimension(350, 20));
            progressBar.setMaximumSize(new Dimension(350, 20));
            progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

            lblStatusMsg.setFont(lblStatusMsg.getFont().deriveFont(12f));
            lblStatusMsg.setForeground(new Color(100, 116, 139));
            lblStatusMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

            btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnCancel.addActionListener(e -> handleCancelParsing());

            card.add(lblTitle);
            card.add(Box.createRigidArea(new Dimension(0, 16)));
            card.add(progressBar);
            card.add(Box.createRigidArea(new Dimension(0, 12)));
            card.add(lblStatusMsg);
            card.add(Box.createRigidArea(new Dimension(0, 16)));
            card.add(btnCancel);

            add(card);

            // Intercept mouse & focus events
            addMouseListener(new java.awt.event.MouseAdapter() {});
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {});
            addKeyListener(new java.awt.event.KeyAdapter() {});
            setFocusTraversalKeysEnabled(false);
        }

        public void setProgress(int value) {
            progressBar.setValue(value);
        }

        public void setStatusMessage(String message) {
            lblStatusMsg.setText(message);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(255, 255, 255, 215)); // rgba(255,255,255,0.85)
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.dispose();
            super.paintComponent(g);
        }
    }

    private class ChangesetCellRenderer extends JPanel implements ListCellRenderer<Changeset> {
        private final JLabel lblId = new JLabel();
        private final JLabel lblSub = new JLabel();
        private final JPanel tagsPane = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        public ChangesetCellRenderer() {
            setLayout(new BorderLayout(4, 4));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setOpaque(true);

            lblId.setFont(lblId.getFont().deriveFont(Font.BOLD, 13f));
            lblSub.setFont(lblSub.getFont().deriveFont(11f));
            tagsPane.setOpaque(false);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 2, 2));
            textPanel.setOpaque(false);
            textPanel.add(lblId);
            textPanel.add(lblSub);

            add(textPanel, BorderLayout.CENTER);
            add(tagsPane, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Changeset> list, Changeset value, int index, boolean isSelected, boolean cellHasFocus) {
            lblId.setText(value.getId());
            lblSub.setText("by " + value.getAuthor() + " | " + value.getFilePath());

            tagsPane.removeAll();
            if (value.getContexts().isEmpty()) {
                JLabel tag = createTagLabel("no-context", new Color(241, 245, 249), new Color(100, 116, 139));
                tagsPane.add(tag);
            } else {
                for (String ctx : value.getContexts()) {
                    JLabel tag = createTagLabel(ctx, new Color(219, 234, 254), new Color(30, 58, 138));
                    tagsPane.add(tag);
                }
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                lblId.setForeground(list.getSelectionForeground());
                lblSub.setForeground(new Color(30, 58, 138));
            } else {
                setBackground(list.getBackground());
                lblId.setForeground(list.getForeground());
                lblSub.setForeground(new Color(100, 116, 139));
            }

            return this;
        }

        private JLabel createTagLabel(String text, Color bg, Color fg) {
            JLabel tag = new JLabel(text);
            tag.setOpaque(true);
            tag.setBackground(bg);
            tag.setForeground(fg);
            tag.setFont(tag.getFont().deriveFont(Font.BOLD, 10f));
            tag.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return tag;
        }
    }

    private class DuplicateOccurrenceCellRenderer extends JPanel implements ListCellRenderer<Changeset> {
        private final JLabel lblFile = new JLabel();
        private final JLabel lblDetails = new JLabel();

        public DuplicateOccurrenceCellRenderer() {
            setLayout(new GridLayout(2, 1, 2, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setOpaque(true);

            lblFile.setFont(lblFile.getFont().deriveFont(Font.BOLD, 12f));
            lblDetails.setFont(lblDetails.getFont().deriveFont(11f));

            add(lblFile);
            add(lblDetails);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Changeset> list, Changeset value, int index, boolean isSelected, boolean cellHasFocus) {
            lblFile.setText("File: " + value.getFilePath());
            lblDetails.setText(String.format("Author: %s | Contexts: %s",
                    value.getAuthor(),
                    value.getContexts().isEmpty() ? "None" : String.join(", ", value.getContexts())));

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                lblFile.setForeground(list.getSelectionForeground());
                lblDetails.setForeground(new Color(30, 58, 138));
            } else {
                setBackground(list.getBackground());
                lblFile.setForeground(new Color(15, 23, 42)); // Dark slate
                lblDetails.setForeground(new Color(100, 116, 139)); // Slate gray
            }

            return this;
        }
    }

    private static class DbObjectNodeUserObject {
        private final String objectType;
        private final String objectName;
        private final List<Changeset> changesets;

        public DbObjectNodeUserObject(String type, String name, List<Changeset> changesets) {
            this.objectType = type;
            this.objectName = name;
            this.changesets = changesets;
        }

        public String getObjectType() { return objectType; }
        public String getObjectName() { return objectName; }
        public List<Changeset> getChangesets() { return changesets; }

        @Override
        public String toString() {
            return objectName;
        }
    }
}
