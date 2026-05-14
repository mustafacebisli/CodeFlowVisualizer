package com.codeflow.ui.migration;

import com.codeflow.migration.model.*;
import com.codeflow.migration.workspace.MigrationWorkspaceService;
import com.codeflow.model.CodeClass;
import com.codeflow.ui.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * COBOL→Java migration pipeline, golden test listesi ve metrik ozeti.
 */
public class MigrationDashboardPanel extends JPanel {

    private final MigrationWorkspaceService workspaceService = new MigrationWorkspaceService();
    private final JPanel pipelinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    private final JLabel accuracyLabel = new JLabel(MigrationUiMessages.ACCURACY_EMPTY);
    private final DefaultListModel<GoldenCase> caseListModel = new DefaultListModel<>();
    private final JList<GoldenCase> caseList = new JList<>(caseListModel);
    private final JTextArea detailArea = new JTextArea();
    private final JLabel workspaceLabel = new JLabel(MigrationUiMessages.WORKSPACE_NONE);

    private final JButton openBtn = new JButton(MigrationUiMessages.OPEN_WORKSPACE);
    private final JButton scanBtn = new JButton(MigrationUiMessages.RESCAN);
    private final JButton testBtn = new JButton(MigrationUiMessages.RUN_TESTS);

    private Consumer<List<CodeClass>> onJavaTypesDiscovered;

    public MigrationDashboardPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(AppTheme.PANEL_BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel(MigrationUiMessages.TITLE);
        title.setFont(AppTheme.sans(Font.BOLD, 14));
        title.setForeground(AppTheme.ACCENT_BLUE_LIGHT);
        header.add(title, BorderLayout.WEST);
        accuracyLabel.setFont(AppTheme.mono(Font.BOLD, 13));
        accuracyLabel.setForeground(AppTheme.ACCENT_GREEN_BRIGHT);
        header.add(accuracyLabel, BorderLayout.EAST);

        pipelinePanel.setOpaque(false);
        JScrollPane pipeScroll = new JScrollPane(pipelinePanel);
        pipeScroll.setBorder(new LineBorder(AppTheme.BORDER_SUBTLE));
        pipeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pipeScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        pipeScroll.setPreferredSize(new Dimension(100, 72));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);
        north.add(pipeScroll, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        caseList.setCellRenderer(new GoldenCaseCellRenderer());
        caseList.setBackground(AppTheme.MIGRATION_LIST_BG);
        caseList.setSelectionBackground(AppTheme.MIGRATION_SELECTION_BG);
        caseList.addListSelectionListener(e -> showSelectedCase());

        detailArea.setEditable(false);
        detailArea.setFont(AppTheme.mono(Font.PLAIN, 11));
        detailArea.setBackground(AppTheme.CANVAS_BG);
        detailArea.setForeground(AppTheme.TEXT_SECONDARY);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(caseList),
                new JScrollPane(detailArea));
        split.setDividerLocation(280);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        testBtn.setFont(testBtn.getFont().deriveFont(Font.BOLD));

        openBtn.addActionListener(e -> openWorkspace());
        scanBtn.addActionListener(e -> rescan());
        testBtn.addActionListener(e -> runTests());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.add(openBtn);
        btns.add(scanBtn);
        btns.add(testBtn);
        bottom.add(btns, BorderLayout.WEST);
        workspaceLabel.setFont(AppTheme.sans(Font.ITALIC, 11));
        workspaceLabel.setForeground(AppTheme.TEXT_DIM);
        bottom.add(workspaceLabel, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        renderPipeline(List.of());
    }

    public void setOnJavaTypesDiscovered(Consumer<List<CodeClass>> listener) {
        this.onJavaTypesDiscovered = listener;
    }

    private void openWorkspace() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Migration workspace kok klasoru");
        if (workspaceService.getCurrentRoot() != null) {
            chooser.setCurrentDirectory(workspaceService.getCurrentRoot().toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path root = chooser.getSelectedFile().toPath();
        workspaceLabel.setText(root.toString());
        scanWorkspace(root);
    }

    private void rescan() {
        Path root = workspaceService.getCurrentRoot();
        if (root == null) {
            JOptionPane.showMessageDialog(this, MigrationUiMessages.OPEN_WORKSPACE_FIRST,
                    MigrationUiMessages.WARNING, JOptionPane.WARNING_MESSAGE);
            return;
        }
        scanWorkspace(root);
    }

    private void scanWorkspace(Path root) {
        setScanControlsEnabled(false);
        new SwingWorker<MigrationWorkspaceState, Void>() {
            @Override
            protected MigrationWorkspaceState doInBackground() throws Exception {
                return workspaceService.scan(root);
            }

            @Override
            protected void done() {
                setScanControlsEnabled(true);
                try {
                    applyState(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MigrationDashboardPanel.this,
                            ex.getMessage(), MigrationUiMessages.SCAN_ERROR, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void setScanControlsEnabled(boolean enabled) {
        openBtn.setEnabled(enabled);
        scanBtn.setEnabled(enabled);
    }

    private void runTests() {
        if (workspaceService.getCurrentRoot() == null) {
            JOptionPane.showMessageDialog(this, MigrationUiMessages.OPEN_WORKSPACE_FIRST,
                    MigrationUiMessages.WARNING, JOptionPane.WARNING_MESSAGE);
            return;
        }
        testBtnSetEnabled(false);
        new SwingWorker<MigrationWorkspaceState, Void>() {
            @Override
            protected MigrationWorkspaceState doInBackground() throws Exception {
                return workspaceService.runTests();
            }

            @Override
            protected void done() {
                testBtnSetEnabled(true);
                try {
                    applyState(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MigrationDashboardPanel.this,
                            ex.getMessage(), MigrationUiMessages.TEST_ERROR, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void testBtnSetEnabled(boolean enabled) {
        testBtn.setEnabled(enabled);
    }

    private void applyState(MigrationWorkspaceState state) {
        renderPipeline(state.steps());
        caseListModel.clear();
        for (GoldenCase c : state.goldenManifest().cases()) {
            caseListModel.addElement(c);
        }
        if (state.testsTotal() > 0) {
            accuracyLabel.setText(String.format("Is dogrulugu: %.1f%% (%d/%d)",
                    state.businessAccuracyPercent(), state.testsPassed(), state.testsTotal()));
        } else {
            accuracyLabel.setText("Is dogrulugu: — (golden: " + state.goldenManifest().cases().size() + " case)");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Workspace: ").append(state.workspaceRoot()).append("\n\n");
        if (!state.flowWarnings().isEmpty()) {
            sb.append("=== Akis uyarilari ===\n");
            for (String w : state.flowWarnings()) sb.append("• ").append(w).append("\n");
            sb.append("\n");
        }
        if (!state.lastTestLog().isBlank()) {
            sb.append("=== Son test ciktisi ===\n");
            sb.append(state.lastTestLog());
        }
        if (sb.length() < 80) {
            sb.append(MigrationUiMessages.goldenHelpHint());
        }
        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);

        if (onJavaTypesDiscovered != null && !state.javaTypes().isEmpty()) {
            onJavaTypesDiscovered.accept(state.javaTypes());
        }
    }

    private void showSelectedCase() {
        GoldenCase c = caseList.getSelectedValue();
        if (c == null) return;
        MigrationWorkspaceState st = workspaceService.getLastState();
        String extra = "\n\n--- Secili case ---\n" + c.id() + " / " + c.program()
                + " / " + c.status() + (c.message().isBlank() ? "" : " — " + c.message());
        detailArea.append(extra);
    }

    private void renderPipeline(List<MigrationStepView> steps) {
        pipelinePanel.removeAll();
        if (steps.isEmpty()) {
            pipelinePanel.add(placeholderStep(MigrationUiMessages.PLACEHOLDER_OPEN));
        } else {
            for (MigrationStepView s : steps) {
                pipelinePanel.add(stepChip(s));
            }
        }
        pipelinePanel.revalidate();
        pipelinePanel.repaint();
    }

    private JComponent placeholderStep(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(AppTheme.TEXT_MUTED);
        return l;
    }

    private JComponent stepChip(MigrationStepView step) {
        JPanel chip = new JPanel(new BorderLayout(4, 2));
        chip.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(colorFor(step.status()), 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        chip.setBackground(AppTheme.CHIP_BG);
        chip.setOpaque(true);
        JLabel name = new JLabel(step.kind().label);
        name.setFont(AppTheme.sans(Font.BOLD, 11));
        name.setForeground(Color.WHITE);
        JLabel det = new JLabel(step.detail());
        det.setFont(AppTheme.sans(Font.PLAIN, 10));
        det.setForeground(AppTheme.TEXT_CHIP);
        chip.add(name, BorderLayout.NORTH);
        chip.add(det, BorderLayout.SOUTH);
        return chip;
    }

    private Color colorFor(StepStatus status) {
        return switch (status) {
            case OK -> AppTheme.OK_CHIP;
            case WARNING -> AppTheme.WARNING_CHIP;
            case ERROR -> AppTheme.ERROR_CHIP;
            case RUNNING -> AppTheme.ACCENT_BLUE;
            case PENDING -> AppTheme.PENDING_CHIP;
        };
    }

    private static final class GoldenCaseCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof GoldenCase c) {
                setText(c.id() + "  [" + c.status() + "]");
                if (!isSelected) {
                    setForeground(switch (c.status()) {
                        case FAIL -> AppTheme.FAIL_RED;
                        case PASS -> AppTheme.ACCENT_GREEN_BRIGHT;
                        default -> AppTheme.TEXT_SECONDARY;
                    });
                }
            }
            return this;
        }
    }
}
