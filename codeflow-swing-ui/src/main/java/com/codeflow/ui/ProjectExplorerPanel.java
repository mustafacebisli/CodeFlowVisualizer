package com.codeflow.ui;

import com.codeflow.model.CodeClass;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * VS-style solution explorer: root → files (when watching) → types.
 */
public class ProjectExplorerPanel extends JPanel {

    public enum NodeKind { ROOT, FILE, TYPE }

    public static final class NodeData {
        public final NodeKind kind;
        /** Relative path for FILE; same for TYPE (declaring file). */
        public final String sourceFile;
        /** Simple name for TYPE; file name segment for FILE. */
        public final String displayName;

        public NodeData(NodeKind kind, String sourceFile, String displayName) {
            this.kind = kind;
            this.sourceFile = sourceFile != null ? sourceFile : "";
            this.displayName = displayName != null ? displayName : "";
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private Consumer<NodeData> onNodeSelected;
    private boolean suppressSelectionCallback;

    public ProjectExplorerPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setMinimumSize(new Dimension(160, 0));
        setBackground(AppTheme.PANEL_BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 10, 8, 10));
        header.setBackground(AppTheme.TOOLBAR_BG);
        JLabel title = new JLabel("C\u00f6z\u00fcm Gezgini");
        title.setFont(AppTheme.sans(Font.BOLD, 12));
        title.setForeground(AppTheme.TEXT_EXPLORER_TITLE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode(new NodeData(NodeKind.ROOT, "", "(y\u00fckleniyor)"));
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setBackground(AppTheme.PANEL_BG);
        tree.setForeground(AppTheme.TEXT_PRIMARY);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRowHeight(22);
        tree.setFont(AppTheme.mono(Font.PLAIN, 11));
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                JLabel c = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                c.setBackground(sel ? AppTheme.SELECTION_BG : AppTheme.PANEL_BG);
                c.setOpaque(true);
                c.setForeground(AppTheme.TEXT_PRIMARY);
                if (value instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof NodeData nd) {
                    if (nd.kind == NodeKind.FILE) {
                        c.setIcon(UIManager.getIcon("FileView.fileIcon"));
                    } else if (nd.kind == NodeKind.TYPE) {
                        c.setIcon(null);
                        c.setText(nd.displayName);
                    }
                }
                return c;
            }
        });

        tree.addTreeSelectionListener(this::onTreeSelection);
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public void setOnNodeSelected(Consumer<NodeData> onNodeSelected) {
        this.onNodeSelected = onNodeSelected;
    }

    public void updateTree(Path watchedDirectory, List<CodeClass> classes) {
        String rootLabel = watchedDirectory != null
                ? watchedDirectory.getFileName().toString()
                : "(Edit\u00f6r)";
        rootNode = new DefaultMutableTreeNode(new NodeData(NodeKind.ROOT, "", rootLabel));
        treeModel = new DefaultTreeModel(rootNode);
        tree.setModel(treeModel);

        if (classes == null || classes.isEmpty()) return;

        boolean anyFile = classes.stream().anyMatch(c -> c.getSourceFileName() != null && !c.getSourceFileName().isBlank());

        if (anyFile) {
            Map<String, DefaultMutableTreeNode> fileNodes = new TreeMap<>();
            for (CodeClass c : classes) {
                String file = c.getSourceFileName();
                if (file == null || file.isBlank()) file = "(bilinmeyen)";
                DefaultMutableTreeNode fileNode = fileNodes.computeIfAbsent(file, f -> {
                    DefaultMutableTreeNode n = new DefaultMutableTreeNode(new NodeData(NodeKind.FILE, f, shortFileName(f)));
                    return n;
                });
                fileNode.add(new DefaultMutableTreeNode(new NodeData(NodeKind.TYPE, file, c.getName())));
            }
            for (DefaultMutableTreeNode fn : fileNodes.values()) {
                rootNode.add(fn);
            }
        } else {
            List<CodeClass> sorted = new java.util.ArrayList<>(classes);
            sorted.sort(Comparator.comparing(CodeClass::getName));
            for (CodeClass c : sorted) {
                rootNode.add(new DefaultMutableTreeNode(new NodeData(NodeKind.TYPE, "", c.getName())));
            }
        }

        treeModel.reload();
        expandTwoLevels();
    }

    private static String shortFileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void expandTwoLevels() {
        tree.expandPath(new TreePath(rootNode.getPath()));
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode ch = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            tree.expandPath(new TreePath(ch.getPath()));
        }
    }

    private void onTreeSelection(TreeSelectionEvent e) {
        if (suppressSelectionCallback || onNodeSelected == null) return;
        TreePath path = e.getPath();
        if (path == null) return;
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode n)) return;
        if (!(n.getUserObject() instanceof NodeData data)) return;
        if (data.kind == NodeKind.ROOT) return;
        onNodeSelected.accept(data);
    }

    /** Highlights a type node without firing {@link #setOnNodeSelected}. */
    public void highlightType(String typeName) {
        if (typeName == null || rootNode == null) return;
        DefaultMutableTreeNode found = findTypeNode(rootNode, typeName);
        if (found == null) return;
        suppressSelectionCallback = true;
        tree.setSelectionPath(new TreePath(found.getPath()));
        suppressSelectionCallback = false;
    }

    private DefaultMutableTreeNode findTypeNode(DefaultMutableTreeNode node, String typeName) {
        if (node.getUserObject() instanceof NodeData nd
                && nd.kind == NodeKind.TYPE && typeName.equals(nd.displayName)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode ch = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode r = findTypeNode(ch, typeName);
            if (r != null) return r;
        }
        return null;
    }
}
