package com.codeflow.app;

import com.codeflow.model.CodeClass;
import com.codeflow.parser.EditorBufferParser;
import com.codeflow.parser.FileWatcher;
import com.codeflow.parser.JavaProjectSources;
import com.codeflow.parser.JavaSourceParser;
import com.codeflow.app.sample.ExampleSources;
import com.codeflow.ui.CodeEditorPanel;
import com.codeflow.ui.DiagramPanel;
import com.codeflow.ui.ProjectExplorerPanel;
import com.codeflow.ui.migration.MigrationDashboardPanel;
import com.codeflow.ui.AppTheme;
import com.codeflow.util.AppPreferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bağımsız uygulama penceresi (gezgin, diyagram, editör, klasör izleme).
 * Kurumsal entegrasyonlarda genelde kendi kabuğunuzu yazıp yalnızca {@link DiagramPanel} kullanırsınız.
 */
public class MainFrame extends JFrame {

    private final ProjectExplorerPanel explorerPanel;
    private final DiagramPanel diagramPanel;
    private final CodeEditorPanel editorPanel;
    private final MigrationDashboardPanel migrationPanel;
    private final EditorBufferParser bufferParser;
    private final JavaProjectSources projectSources;
    private final JLabel watchStatusLabel;
    private final JButton watchButton;

    private FileWatcher currentWatcher;
    private Thread watcherThread;
    private Path watchedDirectory;
    private Timer fileChangeDebounce;
    private JSplitPane outerSplit;
    private JSplitPane innerSplit;
    private SwingWorker<?, ?> directoryLoadWorker;

    public MainFrame() {
        super("Code Flow Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JavaSourceParser parser = new JavaSourceParser();
        bufferParser = new EditorBufferParser(parser);
        projectSources = new JavaProjectSources(parser);
        explorerPanel = new ProjectExplorerPanel();
        diagramPanel = new DiagramPanel();
        editorPanel = new CodeEditorPanel();
        migrationPanel = new MigrationDashboardPanel();
        migrationPanel.setOnJavaTypesDiscovered(types -> {
            if (types == null || types.isEmpty()) return;
            SwingUtilities.invokeLater(() -> diagramPanel.updateData(types, null));
        });

        explorerPanel.setOnNodeSelected(data -> {
            if (data.kind == ProjectExplorerPanel.NodeKind.FILE) {
                editorPanel.scrollToFileMarker(data.sourceFile);
            } else if (data.kind == ProjectExplorerPanel.NodeKind.TYPE) {
                if (data.sourceFile != null && !data.sourceFile.isBlank()) {
                    editorPanel.scrollToFileMarker(data.sourceFile);
                }
                diagramPanel.setSelectedClassByName(data.displayName);
                editorPanel.setSelectedClassByName(data.displayName, true);
            }
        });
        diagramPanel.setOnClassSelectionFromUser(() -> {
            String n = diagramPanel.getSelectedClassName();
            if (n != null) explorerPanel.highlightType(n);
        });

        innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, diagramPanel, editorPanel);
        innerSplit.setDividerLocation(560);
        innerSplit.setDividerSize(4);
        innerSplit.setResizeWeight(0.55);
        innerSplit.setBorder(null);
        innerSplit.setContinuousLayout(true);

        outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, innerSplit);
        outerSplit.setDividerLocation(220);
        outerSplit.setDividerSize(4);
        outerSplit.setResizeWeight(0.14);
        outerSplit.setBorder(null);
        outerSplit.setContinuousLayout(true);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        bottomBar.setBackground(AppTheme.BOTTOM_BAR_BG);

        watchButton = new JButton("Klasor Izle...");
        watchButton.setFont(AppTheme.sans(Font.PLAIN, 11));
        watchButton.setToolTipText("Izlenecek Java klasorunu sec");
        watchButton.addActionListener(e -> chooseAndWatch());

        JButton stopButton = new JButton("Durdur");
        stopButton.setFont(AppTheme.sans(Font.PLAIN, 11));
        stopButton.addActionListener(e -> stopWatching());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(watchButton);
        btnPanel.add(stopButton);

        watchStatusLabel = new JLabel("Mod: Editorden oku");
        watchStatusLabel.setFont(AppTheme.sans(Font.ITALIC, 11));
        watchStatusLabel.setForeground(AppTheme.TEXT_DIM);

        bottomBar.add(btnPanel, BorderLayout.WEST);
        bottomBar.add(watchStatusLabel, BorderLayout.EAST);

        JPanel visualizeTab = new JPanel(new BorderLayout());
        visualizeTab.add(outerSplit, BorderLayout.CENTER);
        visualizeTab.add(bottomBar, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Gorsellestirme", visualizeTab);
        tabs.addTab("Migration COBOL\u2192Java", migrationPanel);

        getContentPane().add(tabs, BorderLayout.CENTER);

        editorPanel.setCode(ExampleSources.loadDefaultCartDemo());
        editorPanel.setOnChange(this::reparseFromEditor);

        setSize(1320, 800);
        setMinimumSize(new Dimension(980, 600));
        setLocationRelativeTo(null);
        AppPreferences.applyWindowBounds(this);
        AppPreferences.applySplitters(outerSplit, innerSplit);
        diagramPanel.setFlowZoom(AppPreferences.getFlowZoom());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                AppPreferences.saveWindowBounds(MainFrame.this);
                AppPreferences.saveSplitters(outerSplit, innerSplit);
                AppPreferences.setFlowZoom(diagramPanel.getFlowZoom());
            }
        });

        reparseFromEditor();
    }

    private void reparseFromEditor() {
        String code = editorPanel.getCode();
        List<CodeClass> classes = bufferParser.parse(code);
        List<String> classNames = new ArrayList<>();
        for (CodeClass c : classes) classNames.add(c.getName());
        String hint = "";
        if (classes.isEmpty() && code.replaceAll("\\s+", " ").trim().length() > 30) {
            hint = "Tip tan\u0131m\u0131 bulunamad\u0131 (class/interface/enum/record)";
        }
        String finalHint = hint;
        SwingUtilities.invokeLater(() -> {
            editorPanel.setParseHint(finalHint);
            diagramPanel.updateData(classes, JavaSourceParser.extractFirstPackageName(editorPanel.getCode()));
            editorPanel.updateClassList(classNames);
            explorerPanel.updateTree(watchedDirectory, classes);
        });
    }

    private void chooseAndWatch() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Izlenecek Java Proje Klasorunu Secin");

        if (watchedDirectory != null) {
            chooser.setCurrentDirectory(watchedDirectory.toFile());
        } else {
            java.io.File last = AppPreferences.lastWatchDirectoryAsFile();
            if (last != null) {
                chooser.setCurrentDirectory(last);
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        Path dir = chooser.getSelectedFile().toPath();
        startWatching(dir);
    }

    private void startWatching(Path dir) {
        stopWatching();
        watchedDirectory = dir;

        loadAllJavaFiles(dir);

        watchStatusLabel.setText("Izleniyor: " + dir.getFileName());
        watchStatusLabel.setForeground(AppTheme.WATCH_ACTIVE);
        watchButton.setText("Klasor Degistir...");

        currentWatcher = new FileWatcher(dir, changedPath -> {
            if (fileChangeDebounce != null) fileChangeDebounce.stop();
            fileChangeDebounce = new Timer(800, e -> loadAllJavaFiles(dir));
            fileChangeDebounce.setRepeats(false);
            fileChangeDebounce.start();
        });

        watcherThread = new Thread(currentWatcher, "FileWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();

        AppPreferences.setLastWatchDirectory(dir.toAbsolutePath().toString());
    }

    private void stopWatching() {
        if (directoryLoadWorker != null) {
            directoryLoadWorker.cancel(true);
            directoryLoadWorker = null;
        }
        if (currentWatcher != null) {
            currentWatcher.stop();
            currentWatcher = null;
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
        watchedDirectory = null;
        watchStatusLabel.setText("Mod: Editorden oku");
        watchStatusLabel.setForeground(AppTheme.TEXT_DIM);
        watchButton.setText("Klasor Izle...");
        SwingUtilities.invokeLater(this::reparseFromEditor);
    }

    private void loadAllJavaFiles(Path dir) {
        if (directoryLoadWorker != null) {
            directoryLoadWorker.cancel(true);
        }
        directoryLoadWorker = new SwingWorker<JavaProjectSources.LoadResult, Void>() {
            @Override
            protected JavaProjectSources.LoadResult doInBackground() throws Exception {
                return projectSources.loadDirectory(dir);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    JavaProjectSources.LoadResult loaded = get();
                    List<CodeClass> allClasses = loaded.classes();
                    String combined = loaded.combinedCode();
                    List<String> classNames = new ArrayList<>();
                    for (CodeClass c : allClasses) classNames.add(c.getName());

                    editorPanel.setCode(combined);
                    editorPanel.updateClassList(classNames);
                    diagramPanel.updateData(allClasses, JavaSourceParser.extractFirstPackageName(combined));
                    explorerPanel.updateTree(dir, allClasses);
                    watchStatusLabel.setText("Izleniyor: " + dir.getFileName() + " (" + allClasses.size() + " sinif)");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(MainFrame.this,
                            cause.getMessage(), "Klasor yukleme hatasi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        directoryLoadWorker.execute();
    }
}
