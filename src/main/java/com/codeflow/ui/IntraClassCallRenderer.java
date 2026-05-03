package com.codeflow.ui;

import com.codeflow.model.CodeClass;
import com.codeflow.model.CodeMethod;
import com.codeflow.model.MethodCall;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * Secilen sinifta metotlar arasi cagri oklari (this / ayni sinif cozumlemesi).
 */
public class IntraClassCallRenderer extends JPanel {

    private static final int NODE_R = 36;

    private CodeClass codeClass;
    private final List<String> nodeNames = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Point> positions = new HashMap<>();

    private static final class Edge {
        final String from;
        final String to;
        final String label;

        Edge(String from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }
    }

    public IntraClassCallRenderer() {
        setBackground(new Color(25, 25, 28));
    }

    public void setClass(CodeClass cc) {
        this.codeClass = cc;
        nodeNames.clear();
        edges.clear();
        positions.clear();

        if (cc == null || cc.getMethods().isEmpty()) {
            setPreferredSize(new Dimension(400, 280));
            revalidate();
            repaint();
            return;
        }

        Set<String> methodSet = new LinkedHashSet<>();
        for (CodeMethod m : cc.getMethods()) {
            methodSet.add(m.getName());
        }
        nodeNames.addAll(methodSet);

        Set<String> dedup = new HashSet<>();
        for (CodeMethod m : cc.getMethods()) {
            for (MethodCall call : m.getMethodCalls()) {
                if (!isCallToOwnMethod(cc, call, methodSet)) continue;
                String to = call.getTargetMethod();
                if (to == null || to.isEmpty()) continue;
                if (m.getName().equals(to)) continue;
                String key = m.getName() + "->" + to;
                if (!dedup.add(key)) continue;
                edges.add(new Edge(m.getName(), to, call.getTargetExpression() + "." + to));
            }
        }

        int w = Math.max(420, 120 + nodeNames.size() * 55);
        int h = Math.max(320, 120 + nodeNames.size() * 45);
        setPreferredSize(new Dimension(w, h));
        layoutCircle(w, h);
        revalidate();
        repaint();
    }

    private static boolean isCallToOwnMethod(CodeClass c, MethodCall call, Set<String> methodNames) {
        String tm = call.getTargetMethod();
        if (tm == null || !methodNames.contains(tm)) return false;
        if (c.getName().equals(call.getResolvedTargetClass())) return true;
        return "this".equals(call.getTargetExpression());
    }

    private void layoutCircle(int pw, int ph) {
        int cx = pw / 2;
        int cy = ph / 2;
        int n = nodeNames.size();
        if (n == 1) {
            positions.put(nodeNames.get(0), new Point(cx, cy));
            return;
        }
        int rx = Math.max(140, n * 28);
        int ry = Math.max(110, n * 24);
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI / n * i - Math.PI / 2;
            int x = cx + (int) (rx * Math.cos(ang));
            int y = cy + (int) (ry * Math.sin(ang));
            positions.put(nodeNames.get(i), new Point(x, y));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (codeClass == null || nodeNames.isEmpty()) {
            Graphics2D g0 = (Graphics2D) g.create();
            g0.setColor(new Color(140, 140, 150));
            g0.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g0.drawString("Sinif secin veya bu sinifta metot yok.", 24, 48);
            g0.dispose();
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (Edge e : edges) {
            Point a = positions.get(e.from);
            Point b = positions.get(e.to);
            if (a == null || b == null) continue;
            double dx = b.x - a.x, dy = b.y - a.y;
            double dist = Math.hypot(dx, dy);
            if (dist < 1) continue;
            double off = NODE_R / dist;
            int sx = a.x + (int) (dx * off), sy = a.y + (int) (dy * off);
            int ex = b.x - (int) (dx * off), ey = b.y - (int) (dy * off);

            g2.setColor(new Color(20, 184, 166, 160));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawLine(sx, sy, ex, ey);
            drawArrowHead(g2, sx, sy, ex, ey, 7);

            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
            g2.setColor(new Color(160, 170, 185));
            String lab = trunc(e.label, 22);
            int mx = (sx + ex) / 2, my = (sy + ey) / 2 - 6;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(lab, mx - fm.stringWidth(lab) / 2, my);
        }

        for (String name : nodeNames) {
            Point p = positions.get(name);
            if (p == null) continue;
            g2.setColor(new Color(37, 99, 235, 40));
            g2.fill(new Ellipse2D.Double(p.x - NODE_R, p.y - NODE_R, NODE_R * 2, NODE_R * 2));
            g2.setColor(new Color(37, 99, 235));
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(p.x - NODE_R, p.y - NODE_R, NODE_R * 2, NODE_R * 2));
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            g2.setColor(new Color(230, 230, 235));
            FontMetrics fm = g2.getFontMetrics();
            String disp = trunc(name, 14);
            g2.drawString(disp, p.x - fm.stringWidth(disp) / 2, p.y + 4);
        }

        g2.dispose();
    }

    private static void drawArrowHead(Graphics2D g, int x1, int y1, int x2, int y2, int size) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int ax1 = x2 - (int) (size * Math.cos(angle - Math.PI / 6));
        int ay1 = y2 - (int) (size * Math.sin(angle - Math.PI / 6));
        int ax2 = x2 - (int) (size * Math.cos(angle + Math.PI / 6));
        int ay2 = y2 - (int) (size * Math.sin(angle + Math.PI / 6));
        g.fillPolygon(new int[]{x2, ax1, ax2}, new int[]{y2, ay1, ay2}, 3);
    }

    private static String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
