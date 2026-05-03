package com.codeflow.ui;

import com.codeflow.model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;

public class DiagramPanel extends JPanel {

    public enum Mode { METHOD_FLOW, CLASS_DEPENDENCY, OVERVIEW, INTRA_CLASS_CALLS }

    private static final String ALL_METHODS_ITEM = "(T\u00fcm metotlar)";

    private final JComboBox<String> modeSelector;
    private final JComboBox<String> classSelector;
    private final JComboBox<String> methodSelector;
    private final JLabel statsLabel;
    private final JButton zoomOutBtn;
    private final JButton zoomInBtn;
    private final JLabel zoomPercentLabel;
    private final JLabel zoomTitleLabel;
    private double flowZoom = 1.0;

    private final FlowchartRenderer flowchartRenderer;
    private final DependencyRenderer dependencyRenderer;
    private final OverviewRenderer overviewRenderer;
    private final IntraClassCallRenderer intraClassRenderer;
    private final JPanel flowchartHost;
    private final JScrollPane flowScroll;
    private final JScrollPane depScroll;
    private final JScrollPane overScroll;
    private final JScrollPane callScroll;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private List<CodeClass> currentClasses;
    private Mode currentMode = Mode.METHOD_FLOW;
    private boolean suppressClassEvents;
    private Runnable onClassSelectionFromUser;

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

        modeSelector = new JComboBox<>(new String[]{
                "Ak\u0131\u015f Diyagram\u0131",
                "S\u0131n\u0131f Ba\u011f\u0131ml\u0131l\u0131klar\u0131",
                "Genel Bak\u0131\u015f",
                "Sinif icinde metot cagrilari"
        });
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

        toolbar.add(Box.createHorizontalStrut(14));
        zoomTitleLabel = new JLabel("Yak\u0131n:");
        zoomTitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        zoomTitleLabel.setForeground(new Color(160, 160, 160));
        toolbar.add(zoomTitleLabel);
        zoomOutBtn = new JButton("\u2212");
        zoomOutBtn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        zoomOutBtn.setMargin(new Insets(1, 7, 1, 7));
        zoomOutBtn.setToolTipText("Uzaklast\u0131r; trackpad: Cmd/Ctrl/Alt + kayd\u0131r");
        zoomOutBtn.addActionListener(e -> adjustFlowZoom(1 / 1.15));
        toolbar.add(zoomOutBtn);
        zoomPercentLabel = new JLabel("100%");
        zoomPercentLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        zoomPercentLabel.setForeground(new Color(180, 180, 180));
        zoomPercentLabel.setPreferredSize(new Dimension(40, 22));
        toolbar.add(zoomPercentLabel);
        zoomInBtn = new JButton("+");
        zoomInBtn.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        zoomInBtn.setMargin(new Insets(1, 7, 1, 7));
        zoomInBtn.setToolTipText("Yak\u0131nlast\u0131r; trackpad: Cmd veya Ctrl veya Alt + kayd\u0131r");
        zoomInBtn.addActionListener(e -> adjustFlowZoom(1.15));
        toolbar.add(zoomInBtn);

        // Wrap header+toolbar
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // --- Renderers ---
        flowchartRenderer = new FlowchartRenderer();
        dependencyRenderer = new DependencyRenderer();
        overviewRenderer = new OverviewRenderer();
        intraClassRenderer = new IntraClassCallRenderer();

        /* Diyagram viewport’tan dar ise solda yapışmasın — host genişliği viewport ile büyür, GridBag ortalar */
        flowchartHost = new JPanel(new GridBagLayout());
        flowchartHost.setBackground(new Color(25, 25, 28));
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.gridx = 0;
        fgbc.gridy = 0;
        fgbc.weightx = 1;
        fgbc.weighty = 1;
        fgbc.anchor = GridBagConstraints.NORTH;
        fgbc.fill = GridBagConstraints.NONE;
        flowchartHost.add(flowchartRenderer, fgbc);

        flowScroll = new JScrollPane(flowchartHost);
        flowScroll.setBorder(null);
        flowScroll.getVerticalScrollBar().setUnitIncrement(16);
        flowScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayoutFlowchartHost();
            }
        });
        /* ScrollPane tekeri once yakalar; consume + Cmd(Meta)/Ctrl/Alt macOS trackpad icin */
        flowScroll.addMouseWheelListener(this::onFlowchartScrollWheel);

        depScroll = new JScrollPane(dependencyRenderer);
        depScroll.setBorder(null);
        depScroll.getVerticalScrollBar().setUnitIncrement(16);

        overScroll = new JScrollPane(overviewRenderer);
        overScroll.setBorder(null);
        overScroll.getVerticalScrollBar().setUnitIncrement(16);

        callScroll = new JScrollPane(intraClassRenderer);
        callScroll.setBorder(null);
        callScroll.getVerticalScrollBar().setUnitIncrement(16);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(flowScroll, "FLOW");
        cardPanel.add(depScroll, "DEP");
        cardPanel.add(overScroll, "OVER");
        cardPanel.add(callScroll, "CALL");
        add(cardPanel, BorderLayout.CENTER);

        // --- Listeners ---
        modeSelector.addActionListener(e -> {
            int idx = modeSelector.getSelectedIndex();
            currentMode = Mode.values()[idx];
            updateVisibility();
            refreshCurrentView();
        });

        classSelector.addActionListener(e -> {
            if (suppressClassEvents) return;
            updateMethodSelector();
            refreshCurrentView();
            if (onClassSelectionFromUser != null) onClassSelectionFromUser.run();
        });

        methodSelector.addActionListener(e -> refreshCurrentView());

        updateVisibility();

        SwingUtilities.invokeLater(this::relayoutFlowchartHost);
    }

    /**
     * Viewport genişliği diyagramdan büyükse host’u genişletir; GridBag ile diyagram yatayda ortalanır.
     * Yükseklik yalnızca diyagramın tercih edilen yüksekliği (gereksiz dikey kaydırma oluşturmaz).
     */
    private void relayoutFlowchartHost() {
        if (flowScroll == null) return;
        int vw = Math.max(1, flowScroll.getViewport().getWidth());
        Dimension rp = flowchartRenderer.getPreferredSize();
        int w = Math.max(vw, rp.width);
        int h = Math.max(1, rp.height);
        flowchartHost.setPreferredSize(new Dimension(w, h));
        flowchartHost.revalidate();
    }

    public void setOnClassSelectionFromUser(Runnable callback) {
        this.onClassSelectionFromUser = callback;
    }

    public String getSelectedClassName() {
        return (String) classSelector.getSelectedItem();
    }

    /** Selects a type for flow / overview; refreshes method list and diagram. */
    public void setSelectedClassByName(String name) {
        if (name == null || currentClasses == null) return;
        suppressClassEvents = true;
        for (int i = 0; i < classSelector.getItemCount(); i++) {
            if (name.equals(classSelector.getItemAt(i))) {
                classSelector.setSelectedIndex(i);
                break;
            }
        }
        suppressClassEvents = false;
        updateMethodSelector();
        refreshCurrentView();
    }

    public void updateData(List<CodeClass> classes) {
        this.currentClasses = classes;

        String prevClass = (String) classSelector.getSelectedItem();
        String prevMethod = (String) methodSelector.getSelectedItem();

        suppressClassEvents = true;
        try {
            classSelector.removeAllItems();
            if (classes != null) {
                for (CodeClass c : classes) classSelector.addItem(c.getName());
            }

            if (prevClass != null && classes != null) {
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

            statsLabel.setText(classes != null ? classes.size() + " s\u0131n\u0131f" : "0 s\u0131n\u0131f");
        } finally {
            suppressClassEvents = false;
        }
        refreshCurrentView();
    }

    private void updateMethodSelector() {
        methodSelector.removeAllItems();
        CodeClass selected = getSelectedClass();
        if (selected != null && !selected.getMethods().isEmpty()) {
            for (CodeMethod m : selected.getMethods()) {
                methodSelector.addItem(m.getName());
            }
            methodSelector.addItem(ALL_METHODS_ITEM);
        }
    }

    private void adjustFlowZoom(double factor) {
        flowZoom = Math.max(0.25, Math.min(3.0, flowZoom * factor));
        flowchartRenderer.setZoom(flowZoom);
        zoomPercentLabel.setText(Math.round(flowZoom * 100) + "%");
        SwingUtilities.invokeLater(this::relayoutFlowchartHost);
    }

    /** Trackpad: JScrollPane uzerinde; Cmd(Meta)/Ctrl/Alt basiliyken teker = zoom (pinch degil). */
    private void onFlowchartScrollWheel(MouseWheelEvent ev) {
        if (currentMode != Mode.METHOD_FLOW) return;
        boolean zoomMod = ev.isControlDown() || ev.isMetaDown() || ev.isAltDown();
        if (!zoomMod) return;
        ev.consume();
        double rot = ev.getPreciseWheelRotation();
        if (rot == 0.0) {
            rot = ev.getWheelRotation();
        }
        if (rot == 0.0) return;
        double factor = Math.pow(1.07, -rot);
        flowZoom = Math.max(0.25, Math.min(3.0, flowZoom * factor));
        flowchartRenderer.setZoom(flowZoom);
        zoomPercentLabel.setText(Math.round(flowZoom * 100) + "%");
        SwingUtilities.invokeLater(this::relayoutFlowchartHost);
    }

    private void refreshCurrentView() {
        switch (currentMode) {
            case METHOD_FLOW -> {
                CodeClass cc = getSelectedClass();
                String sel = (String) methodSelector.getSelectedItem();
                flowchartRenderer.setZoom(flowZoom);
                if (cc != null && ALL_METHODS_ITEM.equals(sel)) {
                    flowchartRenderer.setClassMethods(cc.getMethods());
                } else {
                    flowchartRenderer.setMethod(getSelectedMethod());
                }
                zoomPercentLabel.setText(Math.round(flowZoom * 100) + "%");
                cardLayout.show(cardPanel, "FLOW");
                SwingUtilities.invokeLater(this::relayoutFlowchartHost);
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
            case INTRA_CLASS_CALLS -> {
                intraClassRenderer.setClass(getSelectedClass());
                cardLayout.show(cardPanel, "CALL");
            }
        }
    }

    private void updateVisibility() {
        boolean isFlow = currentMode == Mode.METHOD_FLOW;
        boolean intra = currentMode == Mode.INTRA_CLASS_CALLS;
        classSelector.setVisible(isFlow || intra);
        methodSelector.setVisible(isFlow);
        zoomTitleLabel.setVisible(isFlow);
        zoomOutBtn.setVisible(isFlow);
        zoomInBtn.setVisible(isFlow);
        zoomPercentLabel.setVisible(isFlow);
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
        if (name == null || ALL_METHODS_ITEM.equals(name)) return null;
        return cc.getMethods().stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }
}
