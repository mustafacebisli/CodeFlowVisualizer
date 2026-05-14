package com.codeflow.ui;

import com.codeflow.model.CodeClass;
import com.codeflow.model.DependencyGraph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.List;

public class DependencyRenderer extends JPanel {

    private DependencyGraph graph;
    private List<CodeClass> classes;
    private Map<String, Point> nodePositions = new HashMap<>();
    private String selectedNode = null;

    private static final int NODE_R = 40;

    public DependencyRenderer() {
        setBackground(AppTheme.CANVAS_BG);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String clicked = findNodeAt(e.getX(), e.getY());
                selectedNode = Objects.equals(selectedNode, clicked) ? null : clicked;
                repaint();
            }
        });
    }

    public void setData(DependencyGraph graph, List<CodeClass> classes) {
        this.graph = graph;
        this.classes = classes;
        layoutNodes();
        repaint();
    }

    private void layoutNodes() {
        if (graph == null || graph.getNodes().isEmpty()) {
            nodePositions.clear();
            return;
        }

        List<String> nodes = graph.getNodes();
        int cx = 320, cy = 260;
        int rx = 220, ry = 180;

        nodePositions.clear();
        for (int i = 0; i < nodes.size(); i++) {
            double angle = 2.0 * Math.PI / nodes.size() * i - Math.PI / 2;
            int x = cx + (int) (rx * Math.cos(angle));
            int y = cy + (int) (ry * Math.sin(angle));
            nodePositions.put(nodes.get(i), new Point(x, y));
        }

        setPreferredSize(new Dimension(cx * 2, cy * 2));
        revalidate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (graph == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw edges
        for (DependencyGraph.Edge edge : graph.getEdges()) {
            Point from = nodePositions.get(edge.getSource());
            Point to = nodePositions.get(edge.getTarget());
            if (from == null || to == null) continue;

            boolean hl = edge.getSource().equals(selectedNode) || edge.getTarget().equals(selectedNode);
            boolean inject = edge.getKind() == DependencyGraph.EdgeKind.INJECTION
                    || edge.getKind() == DependencyGraph.EdgeKind.MIXED;

            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist == 0) continue;

            double off = NODE_R / dist;
            int sx = from.x + (int)(dx * off), sy = from.y + (int)(dy * off);
            int ex = to.x - (int)(dx * off), ey = to.y - (int)(dy * off);

            Color lineColor;
            if (hl) {
                lineColor = inject ? AppTheme.INJECT_CYAN : AppTheme.WARNING_AMBER;
            } else {
                lineColor = inject ? AppTheme.withAlpha(AppTheme.INJECT_CYAN, 140)
                        : AppTheme.withAlpha(AppTheme.ARROW, 130);
            }
            g2.setColor(lineColor);
            if (inject) {
                g2.setStroke(new BasicStroke(hl ? 2.5f : 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[]{5, 4}, 0));
            } else {
                g2.setStroke(new BasicStroke(hl ? 2.5f : 1.5f));
            }
            g2.drawLine(sx, sy, ex, ey);

            drawArrowHead(g2, sx, sy, ex, ey, 8);
            g2.setStroke(new BasicStroke(1.5f));

            String label = String.join(", ", edge.getMethods());
            if (label.length() > 42) label = label.substring(0, 39) + "...";
            g2.setFont(AppTheme.mono(Font.PLAIN, 10));
            g2.setColor(hl ? lineColor : AppTheme.GRAY_MID);
            int mx = (sx + ex) / 2, my = (sy + ey) / 2 - 8;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, mx - fm.stringWidth(label) / 2, my);
        }

        // Draw nodes
        for (String name : graph.getNodes()) {
            Point pos = nodePositions.get(name);
            if (pos == null) continue;

            boolean isSel = name.equals(selectedNode);
            Color nodeColor = AppTheme.NODE_PRIMARY;

            g2.setColor(isSel ? AppTheme.withAlpha(AppTheme.NODE_PRIMARY, 60)
                    : AppTheme.withAlpha(AppTheme.NODE_PRIMARY, 25));
            g2.fill(new Ellipse2D.Double(pos.x - NODE_R, pos.y - NODE_R, NODE_R * 2, NODE_R * 2));
            g2.setColor(nodeColor);
            g2.setStroke(new BasicStroke(isSel ? 3f : 2f));
            g2.draw(new Ellipse2D.Double(pos.x - NODE_R, pos.y - NODE_R, NODE_R * 2, NODE_R * 2));

            g2.setFont(AppTheme.mono(Font.BOLD, 11));
            g2.setColor(AppTheme.TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, pos.x - fm.stringWidth(name) / 2, pos.y + 4);

            // Show methods on selection
            if (isSel && classes != null) {
                CodeClass cc = classes.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
                if (cc != null && !cc.getMethods().isEmpty()) {
                    int boxY = pos.y + NODE_R + 8;
                    g2.setFont(AppTheme.mono(Font.PLAIN, 10));
                    fm = g2.getFontMetrics();
                    int maxW = 0;
                    for (var m : cc.getMethods()) maxW = Math.max(maxW, fm.stringWidth(m.getName()));
                    int boxW = maxW + 20;
                    int boxH = cc.getMethods().size() * 16 + 10;

                    g2.setColor(AppTheme.TOOLTIP_BG);
                    g2.fillRoundRect(pos.x - boxW / 2, boxY, boxW, boxH, 8, 8);
                    g2.setColor(AppTheme.TOOLTIP_BORDER);
                    g2.drawRoundRect(pos.x - boxW / 2, boxY, boxW, boxH, 8, 8);

                    int ty = boxY + 14;
                    g2.setColor(AppTheme.NODE_TEAL);
                    for (var m : cc.getMethods()) {
                        g2.drawString(m.getName() + "()", pos.x - boxW / 2 + 10, ty);
                        ty += 16;
                    }
                }
            }
        }

        g2.dispose();
    }

    private void drawArrowHead(Graphics2D g, int x1, int y1, int x2, int y2, int size) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int ax1 = x2 - (int)(size * Math.cos(angle - Math.PI / 6));
        int ay1 = y2 - (int)(size * Math.sin(angle - Math.PI / 6));
        int ax2 = x2 - (int)(size * Math.cos(angle + Math.PI / 6));
        int ay2 = y2 - (int)(size * Math.sin(angle + Math.PI / 6));
        g.fillPolygon(new int[]{x2, ax1, ax2}, new int[]{y2, ay1, ay2}, 3);
    }

    private String findNodeAt(int mx, int my) {
        for (var entry : nodePositions.entrySet()) {
            Point p = entry.getValue();
            if (p.distance(mx, my) <= NODE_R) return entry.getKey();
        }
        return null;
    }
}
