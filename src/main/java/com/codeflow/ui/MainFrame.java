package com.codeflow.ui;

import com.codeflow.model.CodeClass;
import com.codeflow.parser.FileWatcher;
import com.codeflow.parser.JavaSourceParser;
import com.codeflow.examples.ExampleSources;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    private final ProjectExplorerPanel explorerPanel;
    private final DiagramPanel diagramPanel;
    private final CodeEditorPanel editorPanel;
    private final JavaSourceParser parser;
    private final JLabel watchStatusLabel;
    private final JButton watchButton;

    private FileWatcher currentWatcher;
    private Thread watcherThread;
    private Path watchedDirectory;
    private Timer fileChangeDebounce;

    public MainFrame() {
        super("Code Flow Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        parser = new JavaSourceParser();
        explorerPanel = new ProjectExplorerPanel();
        diagramPanel = new DiagramPanel();
        editorPanel = new CodeEditorPanel();

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

        JSplitPane innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, diagramPanel, editorPanel);
        innerSplit.setDividerLocation(560);
        innerSplit.setDividerSize(4);
        innerSplit.setResizeWeight(0.55);
        innerSplit.setBorder(null);
        innerSplit.setContinuousLayout(true);

        JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorerPanel, innerSplit);
        outerSplit.setDividerLocation(220);
        outerSplit.setDividerSize(4);
        outerSplit.setResizeWeight(0.14);
        outerSplit.setBorder(null);
        outerSplit.setContinuousLayout(true);

        // Bottom toolbar with watch button
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        bottomBar.setBackground(new Color(35, 35, 38));

        watchButton = new JButton("Klasor Izle...");
        watchButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        watchButton.setToolTipText("Bir klasor secin, icindeki .java dosyalari izlenecek");
        watchButton.addActionListener(e -> chooseAndWatch());

        JButton stopButton = new JButton("Durdur");
        stopButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        stopButton.addActionListener(e -> stopWatching());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(watchButton);
        btnPanel.add(stopButton);

        watchStatusLabel = new JLabel("Mod: Editorden oku");
        watchStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        watchStatusLabel.setForeground(new Color(140, 140, 140));

        bottomBar.add(btnPanel, BorderLayout.WEST);
        bottomBar.add(watchStatusLabel, BorderLayout.EAST);

        getContentPane().add(outerSplit, BorderLayout.CENTER);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        editorPanel.setCode(ExampleSources.loadDefaultCartDemo());
        editorPanel.setOnChange(this::reparseFromEditor);

        setSize(1320, 800);
        setMinimumSize(new Dimension(980, 600));
        setLocationRelativeTo(null);

        reparseFromEditor();
    }

    private void reparseFromEditor() {
        String code = editorPanel.getCode();
        List<CodeClass> classes = parser.parse(code, null);
        List<String> classNames = new ArrayList<>();
        for (CodeClass c : classes) classNames.add(c.getName());
        SwingUtilities.invokeLater(() -> {
            diagramPanel.updateData(classes);
            editorPanel.updateClassList(classNames);
            explorerPanel.updateTree(watchedDirectory, classes);
        });
    }

    // ========== File watching ==========

    private void chooseAndWatch() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Izlenecek Java Proje Klasorunu Secin");

        if (watchedDirectory != null) {
            chooser.setCurrentDirectory(watchedDirectory.toFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        Path dir = chooser.getSelectedFile().toPath();
        startWatching(dir);
    }

    private void startWatching(Path dir) {
        stopWatching();
        watchedDirectory = dir;

        // Initial load
        loadAllJavaFiles(dir);

        watchStatusLabel.setText("Izleniyor: " + dir.getFileName());
        watchStatusLabel.setForeground(new Color(46, 160, 67));
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
    }

    private void stopWatching() {
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
        watchStatusLabel.setForeground(new Color(140, 140, 140));
        watchButton.setText("Klasor Izle...");
        SwingUtilities.invokeLater(this::reparseFromEditor);
    }

    private void loadAllJavaFiles(Path dir) {
        try {
            StringBuilder allCode = new StringBuilder();
            List<CodeClass> allClasses = new ArrayList<>();

            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        try {
                            String content = Files.readString(file);
                            String logical = dir.relativize(file).toString().replace('\\', '/');
                            allCode.append("\n// === ").append(logical).append(" ===\n");
                            allCode.append(content).append("\n");
                            allClasses.addAll(parser.parseUnresolved(content, logical));
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            parser.resolveCrossFile(allClasses);
            String combined = allCode.toString();

            List<String> classNames = new ArrayList<>();
            for (CodeClass c : allClasses) classNames.add(c.getName());

            SwingUtilities.invokeLater(() -> {
                editorPanel.setCode(combined);
                editorPanel.updateClassList(classNames);
                diagramPanel.updateData(allClasses);
                explorerPanel.updateTree(dir, allClasses);
                watchStatusLabel.setText("Izleniyor: " + dir.getFileName() + " (" + allClasses.size() + " sinif)");
            });

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
