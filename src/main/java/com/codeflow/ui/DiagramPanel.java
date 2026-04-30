package com.codeflow.ui;

import com.codeflow.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class DiagramPanel extends JPanel {

    public enum Mode { METHOD_FLOW, CLASS_DEPENDENCY, OVERVIEW }

    private final JComboBox<String> modeSelector;
    private final JComboBox<String> classSelector;
    private final JComboBox<String> methodSelector;
    private final JLabel statsLabel;

    private final FlowchartRenderer flowchartRenderer;
    private final DependencyRenderer dependencyRenderer;
    private final OverviewRenderer overviewRenderer;
    private final JScrollPane flowScroll;
    private final JScrollPane depScroll;
    private final JScrollPane overScroll;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private List<CodeClass> currentClasses;
    private Mode currentMode = Mode.METHOD_FLOW;

    public DiagramPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 30, 33));

        // --- Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(6, 10, 6, 10));
        header.setBackground(new Color(45, 45, 48));

        JLabel title = new JLabel("\u25C8 Kod Ak\u0131\u015f\u0131");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(new Color(66, 133, 244));
        header.add(title, BorderLayout.WEST);

        statsLabel = new JLabel("");
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsLabel.setForeground(new Color(150, 150, 150));
        header.add(statsLabel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // --- Toolbar ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.setBackground(new Color(37, 37, 40));

        modeSelector = new JComboBox<>(new String[]{"Ak\u0131\u015f Diyagram\u0131", "S\u0131n\u0131f Ba\u011f\u0131ml\u0131l\u0131klar\u0131", "Genel Bak\u0131\u015f"});
        modeSelector.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(new JLabel("Mod:"));
        toolbar.add(modeSelector);

        classSelector = new JComboBox<>();
        classSelector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(new JLabel("S\u0131n\u0131f:"));
        toolbar.add(classSelector);

        methodSelector = new JComboBox<>();
        methodSelector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(new JLabel("Method:"));
        toolbar.add(methodSelector);

        // Wrap header+toolbar
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- Renderers ---
        flowchartRenderer = new FlowchartRenderer();
        dependencyRenderer = new DependencyRenderer();
        overviewRenderer = new OverviewRenderer();

        flowScroll = new JScrollPane(flowchartRenderer);
        flowScroll.setBorder(null);
        flowScroll.getVerticalScrollBar().setUnitIncrement(16);

        depScroll = new JScrollPane(dependencyRenderer);
        depScroll.setBorder(null);
        depScroll.getVerticalScrollBar().setUnitIncrement(16);

        overScroll = new JScrollPane(overviewRenderer);
        overScroll.setBorder(null);
        overScroll.getVerticalScrollBar().setUnitIncrement(16);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(flowScroll, "FLOW");
        cardPanel.add(depScroll, "DEP");
        cardPanel.add(overScroll, "OVER");
        add(cardPanel, BorderLayout.CENTER);

        // --- Listeners ---
        modeSelector.addActionListener(e -> {
            int idx = modeSelector.getSelectedIndex();
            currentMode = Mode.values()[idx];
            updateVisibility();
            refreshCurrentView();
        });

        classSelector.addActionListener(e -> {
            updateMethodSelector();
            refreshCurrentView();
        });

        methodSelector.addActionListener(e -> refreshCurrentView());

        updateVisibility();
    }

    public void updateData(List<CodeClass> classes) {
        this.currentClasses = classes;

        String prevClass = (String) classSelector.getSelectedItem();
        String prevMethod = (String) methodSelector.getSelectedItem();

        classSelector.removeAllItems();
        for (CodeClass c : classes) classSelector.addItem(c.getName());

        if (prevClass != null) {
            for (int i = 0; i < classSelector.getItemCount(); i++) {
                if (classSelector.getItemAt(i).equals(prevClass)) {
                    classSelector.setSelectedIndex(i);
                    break;
                }
            }
        }

        updateMethodSelector();
        if (prevMethod != null) {
            for (int i = 0; i < methodSelector.getItemCount(); i++) {
                if (methodSelector.getItemAt(i).equals(prevMethod)) {
                    methodSelector.setSelectedIndex(i);
                    break;
                }
            }
        }

        statsLabel.setText(classes.size() + " s\u0131n\u0131f");
        refreshCurrentView();
    }

    private void updateMethodSelector() {
        methodSelector.removeAllItems();
        CodeClass selected = getSelectedClass();
        if (selected != null) {
            for (CodeMethod m : selected.getMethods()) {
                methodSelector.addItem(m.getName());
            }
        }
    }

    private void refreshCurrentView() {
        switch (currentMode) {
            case METHOD_FLOW -> {
                CodeMethod method = getSelectedMethod();
                flowchartRenderer.setMethod(method);
                cardLayout.show(cardPanel, "FLOW");
            }
            case CLASS_DEPENDENCY -> {
                if (currentClasses != null) {
                    DependencyGraph graph = DependencyGraph.build(currentClasses);
                    dependencyRenderer.setData(graph, currentClasses);
                }
                cardLayout.show(cardPanel, "DEP");
            }
            case OVERVIEW -> {
                overviewRenderer.setClasses(currentClasses);
                cardLayout.show(cardPanel, "OVER");
            }
        }
    }

    private void updateVisibility() {
        boolean isFlow = currentMode == Mode.METHOD_FLOW;
        classSelector.setVisible(isFlow);
        methodSelector.setVisible(isFlow);
    }

    private CodeClass getSelectedClass() {
        if (currentClasses == null) return null;
        String name = (String) classSelector.getSelectedItem();
        if (name == null) return null;
        return currentClasses.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }

    private CodeMethod getSelectedMethod() {
        CodeClass cc = getSelectedClass();
        if (cc == null) return null;
        String name = (String) methodSelector.getSelectedItem();
        if (name == null) return null;
        return cc.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }
}
