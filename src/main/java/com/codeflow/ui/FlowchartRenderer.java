package com.codeflow.ui;

import com.codeflow.model.CodeMethod;
import com.codeflow.model.FlowNode;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Draws a proper branching flowchart:
 *  - if/else: diamond node with TRUE branch going left, FALSE branch going right
 *  - for/while: loop node with body below and a loop-back arrow
 *  - All branches converge back to a merge point
 */
public class FlowchartRenderer extends JPanel {

    private CodeMethod method;

    private static final int NODE_W = 180;
    private static final int NODE_H = 36;
    private static final int DIAMOND_H = 46;
    private static final int V_GAP = 22;
    private static final int H_GAP = 60;
    private static final int PAD = 50;
    private static final Color BG = new Color(25, 25, 28);
    private static final Color ARROW_COLOR = new Color(100, 100, 110);

    private int canvasW, canvasH;

    public FlowchartRenderer() {
        setBackground(BG);
    }

    public void setMethod(CodeMethod method) {
        this.method = method;
        if (method != null) {
            List<FlowNode> nodes = method.getFlowNodes();
            int[] size = measureBranch(nodes);
            canvasW = Math.max(size[0] + PAD * 2, 500);
            canvasH = size[1] + PAD * 2 + NODE_H * 2 + V_GAP * 2;
        } else {
            canvasW = 400;
            canvasH = 300;
        }
        setPreferredSize(new Dimension(canvasW, canvasH));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (method == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = canvasW / 2;
        int y = PAD;

        // Start
        drawCapsule(g2, cx, y, "Basla", new Color(46, 160, 67));
        y += NODE_H;

        // Draw tree
        y = drawNodeList(g2, method.getFlowNodes(), cx, y);

        // End
        drawArrowDown(g2, cx, y, cx, y + V_GAP);
        y += V_GAP;
        drawCapsule(g2, cx, y, "Son", new Color(220, 38, 38));

        g2.dispose();
    }

    // ========== Recursive tree drawing ==========

    private int drawNodeList(Graphics2D g, List<FlowNode> nodes, int cx, int y) {
        for (FlowNode node : nodes) {
            drawArrowDown(g, cx, y, cx, y + V_GAP);
            y += V_GAP;

            switch (node.getType()) {
                case CONDITION -> y = drawConditionBranch(g, node, cx, y);
                case LOOP -> y = drawLoopBranch(g, node, cx, y);
                case RETURN -> { drawCapsule(g, cx, y, "return " + trunc(node.getDetail(), 22), new Color(220, 38, 38)); y += NODE_H; }
                case THROW -> { drawCapsule(g, cx, y, "throw " + trunc(node.getDetail(), 22), new Color(234, 88, 12)); y += NODE_H; }
                case VARIABLE_DECL -> { drawRect(g, cx, y, trunc(node.getLabel(), 28), new Color(37, 99, 235)); y += NODE_H; }
                case METHOD_CALL -> { drawRoundRect(g, cx, y, trunc(node.getLabel(), 28), new Color(20, 184, 166)); y += NODE_H; }
                default -> { drawRect(g, cx, y, trunc(node.getLabel(), 28), new Color(100, 100, 110)); y += NODE_H; }
            }
        }
        return y;
    }

    /**
     * Draws if/else with true-branch going LEFT, false-branch going RIGHT,
     * both converging at a merge point below.
     */
    private int drawConditionBranch(Graphics2D g, FlowNode node, int cx, int y) {
        String label = node.getLabel();
        String detail = node.getDetail();
        String text = label + (detail.isEmpty() ? "" : " (" + trunc(detail, 20) + ")");

        drawDiamond(g, cx, y, text);
        int diamondBottom = y + DIAMOND_H;

        boolean hasTrue = !node.getTrueBranch().isEmpty();
        boolean hasFalse = !node.getFalseBranch().isEmpty();

        if (!hasTrue && !hasFalse) {
            return diamondBottom;
        }

        int trueW = hasTrue ? Math.max(branchWidth(node.getTrueBranch()), NODE_W + 40) : 0;
        int falseW = hasFalse ? Math.max(branchWidth(node.getFalseBranch()), NODE_W + 40) : 0;

        int leftCx = cx - Math.max(trueW / 2 + H_GAP, NODE_W / 2 + H_GAP);
        int rightCx = cx + Math.max(falseW / 2 + H_GAP, NODE_W / 2 + H_GAP);

        int trueBottom = diamondBottom;
        int falseBottom = diamondBottom;

        // TRUE branch (left)
        if (hasTrue) {
            drawArrowBranch(g, cx - NODE_W / 2 - 10, y + DIAMOND_H / 2, leftCx, diamondBottom + V_GAP);
            drawLabel(g, cx - NODE_W / 2 - 14, y + DIAMOND_H / 2 - 6, "T", new Color(46, 160, 67));
            trueBottom = drawNodeList(g, node.getTrueBranch(), leftCx, diamondBottom + V_GAP);
        }

        // FALSE branch (right)
        if (hasFalse) {
            drawArrowBranch(g, cx + NODE_W / 2 + 10, y + DIAMOND_H / 2, rightCx, diamondBottom + V_GAP);
            drawLabel(g, cx + NODE_W / 2 + 14, y + DIAMOND_H / 2 - 6, "F", new Color(220, 38, 38));
            falseBottom = drawNodeList(g, node.getFalseBranch(), rightCx, diamondBottom + V_GAP);
        } else if (hasTrue) {
            // No else: straight down from diamond is the false path
            drawArrowDown(g, cx, diamondBottom, cx, diamondBottom + V_GAP);
            drawLabel(g, cx + 10, diamondBottom + V_GAP / 2 - 2, "F", new Color(220, 38, 38));
            falseBottom = diamondBottom + V_GAP;
        }

        // Merge point
        int mergeY = Math.max(trueBottom, falseBottom) + V_GAP;

        if (hasTrue) {
            drawArrowDown(g, leftCx, trueBottom, leftCx, mergeY - 5);
            drawHorizontalLine(g, leftCx, mergeY - 5, cx);
        }
        if (hasFalse) {
            drawArrowDown(g, rightCx, falseBottom, rightCx, mergeY - 5);
            drawHorizontalLine(g, rightCx, mergeY - 5, cx);
        }
        if (!hasFalse && hasTrue) {
            drawArrowDown(g, cx, falseBottom, cx, mergeY - 5);
        }

        // Merge dot
        g.setColor(new Color(100, 100, 110));
        g.fillOval(cx - 4, mergeY - 8, 8, 8);

        return mergeY;
    }

    /**
     * Draws a loop with the body below and a loop-back arrow on the right side.
     */
    private int drawLoopBranch(Graphics2D g, FlowNode node, int cx, int y) {
        String label = node.getLabel();
        String detail = node.getDetail();
        String text = label + (detail.isEmpty() ? "" : " (" + trunc(detail, 18) + ")");

        drawLoopBox(g, cx, y, text);
        int loopTop = y;
        int loopBottom = y + NODE_H;

        if (!node.getTrueBranch().isEmpty()) {
            int bodyBottom = drawNodeList(g, node.getTrueBranch(), cx, loopBottom);

            // Loop-back arrow on the right side
            int loopBackX = cx + NODE_W / 2 + 30;
            g.setColor(new Color(147, 51, 234, 150));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));

            // Down from body bottom, right, up, back to loop top
            g.drawLine(cx + NODE_W / 2 + 5, bodyBottom - NODE_H / 2, loopBackX, bodyBottom - NODE_H / 2);
            g.drawLine(loopBackX, bodyBottom - NODE_H / 2, loopBackX, loopTop + NODE_H / 2);
            g.drawLine(loopBackX, loopTop + NODE_H / 2, cx + NODE_W / 2 + 5, loopTop + NODE_H / 2);

            // Arrow head pointing left
            g.setStroke(new BasicStroke(1.5f));
            int ax = cx + NODE_W / 2 + 5;
            int ay = loopTop + NODE_H / 2;
            g.fillPolygon(new int[]{ax, ax + 6, ax + 6}, new int[]{ay, ay - 4, ay + 4}, 3);

            // "repeat" label
            g.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 9));
            g.drawString("tekrar", loopBackX + 4, (loopTop + bodyBottom) / 2);

            g.setStroke(new BasicStroke(1.5f));
            return bodyBottom;
        }

        return loopBottom;
    }

    // ========== Measurement ==========

    private int[] measureBranch(List<FlowNode> nodes) {
        int w = NODE_W;
        int h = 0;
        for (FlowNode n : nodes) {
            h += V_GAP;
            if (n.getType() == FlowNode.Type.CONDITION && n.hasBranches()) {
                int tw = branchWidth(n.getTrueBranch());
                int fw = branchWidth(n.getFalseBranch());
                w = Math.max(w, tw + fw + H_GAP * 4);
                int th = branchHeight(n.getTrueBranch());
                int fh = branchHeight(n.getFalseBranch());
                h += DIAMOND_H + Math.max(th, fh) + V_GAP * 2;
            } else if (n.getType() == FlowNode.Type.LOOP && n.hasBranches()) {
                int bh = branchHeight(n.getTrueBranch());
                int bw = branchWidth(n.getTrueBranch());
                w = Math.max(w, bw + 80);
                h += NODE_H + bh;
            } else {
                h += NODE_H;
            }
        }
        return new int[]{w, h};
    }

    private int branchWidth(List<FlowNode> nodes) {
        int w = NODE_W;
        for (FlowNode n : nodes) {
            if (n.getType() == FlowNode.Type.CONDITION && n.hasBranches()) {
                int tw = branchWidth(n.getTrueBranch());
                int fw = branchWidth(n.getFalseBranch());
                w = Math.max(w, tw + fw + H_GAP * 3);
            }
        }
        return w;
    }

    private int branchHeight(List<FlowNode> nodes) {
        int h = 0;
        for (FlowNode n : nodes) {
            h += V_GAP;
            if (n.getType() == FlowNode.Type.CONDITION && n.hasBranches()) {
                int th = branchHeight(n.getTrueBranch());
                int fh = branchHeight(n.getFalseBranch());
                h += DIAMOND_H + Math.max(th, fh) + V_GAP * 2;
            } else if (n.getType() == FlowNode.Type.LOOP && n.hasBranches()) {
                h += NODE_H + branchHeight(n.getTrueBranch());
            } else {
                h += NODE_H;
            }
        }
        return h;
    }

    // ========== Drawing primitives ==========

    private void drawCapsule(Graphics2D g, int cx, int y, String text, Color color) {
        int x = cx - NODE_W / 2;
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, NODE_W, NODE_H, NODE_H, NODE_H);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 35));
        g.fill(shape);
        g.setColor(color);
        g.setStroke(new BasicStroke(2));
        g.draw(shape);
        drawText(g, text, cx, y + NODE_H / 2, color);
    }

    private void drawDiamond(Graphics2D g, int cx, int y, String text) {
        int w = NODE_W + 20;
        int my = y + DIAMOND_H / 2;
        Path2D d = new Path2D.Double();
        d.moveTo(cx, y);
        d.lineTo(cx + w / 2, my);
        d.lineTo(cx, y + DIAMOND_H);
        d.lineTo(cx - w / 2, my);
        d.closePath();
        Color c = new Color(234, 160, 0);
        g.setColor(new Color(234, 160, 0, 30));
        g.fill(d);
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.draw(d);
        drawText(g, text, cx, my, c);
    }

    private void drawLoopBox(Graphics2D g, int cx, int y, String text) {
        int x = cx - NODE_W / 2;
        Color c = new Color(147, 51, 234);
        g.setColor(new Color(147, 51, 234, 25));
        g.fill(new RoundRectangle2D.Double(x, y, NODE_W, NODE_H, 6, 6));
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, NODE_W, NODE_H, 6, 6));
        g.draw(new RoundRectangle2D.Double(x + 3, y + 3, NODE_W - 6, NODE_H - 6, 4, 4));
        drawText(g, text, cx, y + NODE_H / 2, c);
    }

    private void drawRect(Graphics2D g, int cx, int y, String text, Color c) {
        int x = cx - NODE_W / 2;
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 25));
        g.fillRect(x, y, NODE_W, NODE_H);
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x, y, NODE_W, NODE_H);
        drawText(g, text, cx, y + NODE_H / 2, c);
    }

    private void drawRoundRect(Graphics2D g, int cx, int y, String text, Color c) {
        int x = cx - NODE_W / 2;
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 25));
        g.fill(new RoundRectangle2D.Double(x, y, NODE_W, NODE_H, 12, 12));
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(x, y, NODE_W, NODE_H, 12, 12));
        drawText(g, text, cx, y + NODE_H / 2, c);
    }

    private void drawArrowDown(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setColor(ARROW_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x1, y1, x2, y2);
        g.fillPolygon(new int[]{x2, x2 - 4, x2 + 4}, new int[]{y2, y2 - 6, y2 - 6}, 3);
    }

    private void drawArrowBranch(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setColor(ARROW_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x1, y1, x2, y1);
        g.drawLine(x2, y1, x2, y2);
        g.fillPolygon(new int[]{x2, x2 - 4, x2 + 4}, new int[]{y2, y2 - 6, y2 - 6}, 3);
    }

    private void drawHorizontalLine(Graphics2D g, int x1, int y, int x2) {
        g.setColor(ARROW_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x1, y, x2, y);
    }

    private void drawLabel(Graphics2D g, int x, int y, String text, Color c) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g.setColor(c);
        g.drawString(text, x, y);
    }

    private void drawText(Graphics2D g, String text, int cx, int cy, Color color) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 1);
    }

    private String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
