package com.codeflow.ui;

import com.codeflow.model.CodeClass;
import com.codeflow.parser.FileWatcher;
import com.codeflow.parser.JavaSourceParser;
import com.codeflow.sample.SampleCode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

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
        diagramPanel = new DiagramPanel();
        editorPanel = new CodeEditorPanel();

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

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, diagramPanel, editorPanel);
        splitPane.setDividerLocation(560);
        splitPane.setDividerSize(4);
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(null);
        splitPane.setContinuousLayout(true);

        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        editorPanel.setCode(SampleCode.ECOMMERCE_CART);
        editorPanel.setOnChange(this::reparseFromEditor);

        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        reparseFromEditor();
    }

    private void reparseFromEditor() {
        String code = editorPanel.getCode();
        List<CodeClass> classes = parser.parse(code);
        List<String> classNames = new ArrayList<>();
        for (CodeClass c : classes) classNames.add(c.getName());
        SwingUtilities.invokeLater(() -> {
            diagramPanel.updateData(classes);
            editorPanel.updateClassList(classNames);
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
            fileChangeDebounce = new Timer(800, e -> {
                loadAllJavaFiles(dir);
            });
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
    }

    private void loadAllJavaFiles(Path dir) {
        try {
            StringBuilder allCode = new StringBuilder();
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        try {
                            String content = Files.readString(file);
                            allCode.append("\n// === ").append(file.getFileName()).append(" ===\n");
                            allCode.append(content).append("\n");
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            String combined = allCode.toString();
            List<CodeClass> classes = parser.parse(combined);

            List<String> classNames = new ArrayList<>();
            for (CodeClass c : classes) classNames.add(c.getName());

            SwingUtilities.invokeLater(() -> {
                editorPanel.setCode(combined);
                editorPanel.updateClassList(classNames);
                diagramPanel.updateData(classes);
                watchStatusLabel.setText("Izleniyor: " + dir.getFileName() + " (" + classes.size() + " sinif)");
            });

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
