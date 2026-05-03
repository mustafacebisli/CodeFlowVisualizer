package com.codeflow.ui;

import com.codeflow.model.CodeMethod;
import com.codeflow.model.FlowNode;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Akış diyagramı — sadeleştirilmiş görsellik:
 *  - Karar ve döngü: tek şekil (amber elmas); if/else T/F dalları; döngüde gövde + tekrar oku
 *  - switch / try-catch: üst elmas, altında yatay kolonlar (case veya try/catch/finally), altta birleşim
 *  - Diğer adımlar (atama, çağrı, return, throw, genel): tek yuvarlak dikdörtgen, tek renk
 *  - Başla / Son: kapsül
 */
public class FlowchartRenderer extends JPanel {

    private static final int SECTION_BAR = 34;
    private static final int SECTION_GAP = 10;
    private static final int METHOD_BLOCK_GAP = 24;

    private CodeMethod singleMethod;
    private List<CodeMethod> multiMethods;

    private static final int NODE_W = 180;
    private static final int NODE_H = 36;
    private static final int DIAMOND_H = 46;
    private static final int V_GAP = 22;
    private static final int H_GAP = 60;
    /** switch / try-catch kolonları arası yatay boşluk */
    private static final int SW_COL_GAP = 20;
    /** İçerik ölçüsüne göre hesaplanan kenar boşlukları (sabit piksel yerine) */
    private static final int PAD_X_MIN = 44;
    private static final int PAD_X_MAX = 200;
    private static final int PAD_Y_MIN = 32;
    private static final int PAD_Y_MAX = 120;
    private static final int MIN_CANVAS_W = 280;

    private static final Color BG = new Color(25, 25, 28);
    private static final Color ARROW_COLOR = new Color(100, 100, 110);
    /** Tek tip “işlem” düğümü (atama, çağrı, return, throw, genel ifade) */
    private static final Color PROCESS = new Color(71, 85, 105);

    private int canvasW, canvasH;
    /** Tuval kenarı — {@link #recomputeLogicalCanvasSize()} ile güncellenir */
    private int padX = 56;
    private int padY = 48;
    private double zoom = 1.0;

    public FlowchartRenderer() {
        setBackground(BG);
    }

    public double getZoom() {
        return zoom;
    }

    /** Yaklasik %15 adimlar; 0.25x ... 3x */
    public void setZoom(double zoom) {
        this.zoom = Math.max(0.25, Math.min(3.0, zoom));
        applyZoomedPreferredSize();
        revalidate();
        repaint();
    }

    public void setMethod(CodeMethod method) {
        this.multiMethods = null;
        this.singleMethod = method;
        recomputeLogicalCanvasSize();
        applyZoomedPreferredSize();
        revalidate();
        repaint();
    }

    /** Ust uste tum metot akislari (tek sinif). Bos veya null tek metot gibi davranir. */
    public void setClassMethods(List<CodeMethod> methods) {
        this.singleMethod = null;
        if (methods == null || methods.isEmpty()) {
            this.multiMethods = null;
        } else {
            this.multiMethods = new ArrayList<>(methods);
        }
        recomputeLogicalCanvasSize();
        applyZoomedPreferredSize();
        revalidate();
        repaint();
    }

    private void recomputeLogicalCanvasSize() {
        if (multiMethods != null && !multiMethods.isEmpty()) {
            int rawMaxW = NODE_W + 40;
            int totalH = 0;
            for (CodeMethod cm : multiMethods) {
                int[] size = measureBranch(cm.getFlowNodes());
                rawMaxW = Math.max(rawMaxW, size[0]);
                totalH += SECTION_BAR + SECTION_GAP + NODE_H + V_GAP + size[1] + V_GAP + NODE_H + METHOD_BLOCK_GAP;
            }
            padX = computePadX(rawMaxW);
            padY = computePadY(totalH);
            totalH += padY * 2;
            canvasW = Math.max(MIN_CANVAS_W, rawMaxW + padX * 2);
            canvasH = totalH;
        } else if (singleMethod != null) {
            List<FlowNode> nodes = singleMethod.getFlowNodes();
            int[] size = measureBranch(nodes);
            int rawW = Math.max(NODE_W + 40, size[0]);
            int rawH = size[1] + NODE_H * 2 + V_GAP * 2;
            padX = computePadX(rawW);
            padY = computePadY(rawH);
            canvasW = Math.max(MIN_CANVAS_W, rawW + padX * 2);
            canvasH = size[1] + padY * 2 + NODE_H * 2 + V_GAP * 2;
        } else {
            padX = 48;
            padY = 40;
            canvasW = 400;
            canvasH = 300;
        }
    }

    /**
     * Geniş diyagramlarda (switch fan vb.) elmas ve okların taşmaması için yatay boşluk;
     * dar diyagramlarda minimum tutulur.
     */
    private static int computePadX(int contentLogicalWidth) {
        int diamondHalf = (NODE_W + 20) / 2 + 16;
        int proportional = Math.max(0, contentLogicalWidth / 12);
        return Math.min(PAD_X_MAX, Math.max(PAD_X_MIN, diamondHalf + proportional));
    }

    private static int computePadY(int contentLogicalHeight) {
        int proportional = Math.max(0, contentLogicalHeight / 28);
        return Math.min(PAD_Y_MAX, Math.max(PAD_Y_MIN, 36 + proportional));
    }

    private void applyZoomedPreferredSize() {
        setPreferredSize(new Dimension((int) Math.ceil(canvasW * zoom), (int) Math.ceil(canvasH * zoom)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (multiMethods == null && singleMethod == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.scale(zoom, zoom);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(BG);
        g2.fillRect(0, 0, canvasW, canvasH);

        int cx = canvasW / 2;

        if (multiMethods != null && !multiMethods.isEmpty()) {
            int y = padY;
            for (CodeMethod cm : multiMethods) {
                y = drawMethodSectionHeader(g2, cm, y);
                drawCapsule(g2, cx, y, "Basla", new Color(46, 160, 67));
                y += NODE_H;
                y = drawNodeList(g2, cm.getFlowNodes(), cx, y);
                drawArrowDown(g2, cx, y, cx, y + V_GAP);
                y += V_GAP;
                drawCapsule(g2, cx, y, "Son", new Color(220, 38, 38));
                y += NODE_H + METHOD_BLOCK_GAP;
            }
        } else {
            int y = padY;
            drawCapsule(g2, cx, y, "Basla", new Color(46, 160, 67));
            y += NODE_H;
            y = drawNodeList(g2, singleMethod.getFlowNodes(), cx, y);
            drawArrowDown(g2, cx, y, cx, y + V_GAP);
            y += V_GAP;
            drawCapsule(g2, cx, y, "Son", new Color(220, 38, 38));
        }

        g2.dispose();
    }

    private int drawMethodSectionHeader(Graphics2D g2, CodeMethod cm, int y) {
        int barW = canvasW - 2 * padX;
        int x = padX;
        RoundRectangle2D bar = new RoundRectangle2D.Double(x, y, barW, SECTION_BAR, 8, 8);
        g2.setColor(new Color(48, 54, 70));
        g2.fill(bar);
        g2.setColor(new Color(90, 108, 140));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(bar);
        String params = cm.getParameters();
        if (params != null && params.length() > 42) params = params.substring(0, 39) + "...";
        if (params == null) params = "";
        String title = cm.getReturnType() + " " + cm.getName() + "(" + params + ")";
        title = trunc(title, 76);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g2.setColor(new Color(200, 210, 230));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, x + 12, y + SECTION_BAR / 2 + fm.getAscent() / 2);
        return y + SECTION_BAR + SECTION_GAP;
    }

    // ========== Recursive tree drawing ==========

    private int drawNodeList(Graphics2D g, List<FlowNode> nodes, int cx, int y) {
        for (FlowNode node : nodes) {
            drawArrowDown(g, cx, y, cx, y + V_GAP);
            y += V_GAP;

            switch (node.getType()) {
                case CONDITION -> y = drawConditionBranch(g, node, cx, y);
                case SWITCH -> y = drawSwitchNode(g, node, cx, y);
                case TRY_CATCH -> y = drawTryCatchNode(g, node, cx, y);
                case SYNC_BLOCK -> y = drawSyncBlock(g, node, cx, y);
                case LOOP -> y = drawLoopBranch(g, node, cx, y);
                case RETURN -> { drawProcessStep(g, cx, y, "return " + trunc(node.getDetail(), 22)); y += NODE_H; }
                case THROW -> { drawProcessStep(g, cx, y, "throw " + trunc(node.getDetail(), 22)); y += NODE_H; }
                case YIELD_STMT -> { drawProcessStep(g, cx, y, "yield " + trunc(node.getDetail(), 20)); y += NODE_H; }
                case ASSERT_COND -> {
                    drawDiamond(g, cx, y, "assert " + trunc(node.getDetail(), 22));
                    y += DIAMOND_H;
                }
                case BREAK -> { drawProcessStep(g, cx, y, trunc(node.getLabel(), 28)); y += NODE_H; }
                case CONTINUE -> { drawProcessStep(g, cx, y, trunc(node.getLabel(), 28)); y += NODE_H; }
                case VARIABLE_DECL -> { drawProcessStep(g, cx, y, trunc(node.getLabel(), 28)); y += NODE_H; }
                case METHOD_CALL -> { drawProcessStep(g, cx, y, trunc(node.getLabel(), 28)); y += NODE_H; }
                default -> { drawProcessStep(g, cx, y, trunc(node.getLabel(), 28)); y += NODE_H; }
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
     * switch: üstte tek elmas; altında yatay otobüs ile her case ayrı kolonda,
     * kolonlar altta ortada birleşir (if/else ile aynı mantık).
     */
    private int drawSwitchNode(Graphics2D g, FlowNode node, int cx, int y) {
        String head = "switch" + (node.getDetail().isEmpty() ? "" : " (" + trunc(node.getDetail(), 24) + ")");
        drawDiamond(g, cx, y, head);
        int headBottom = y + DIAMOND_H;
        List<FlowNode.SwitchArm> arms = node.getSwitchArms();
        if (arms.isEmpty()) {
            return headBottom;
        }
        int n = arms.size();
        int[] colW = new int[n];
        int[] colCx = new int[n];
        for (int i = 0; i < n; i++) {
            colW[i] = Math.max(NODE_W + 28, branchWidth(arms.get(i).getBody()));
        }
        int totalW = SW_COL_GAP * (n + 1);
        for (int w : colW) {
            totalW += w;
        }
        int left = cx - totalW / 2;
        int cursor = left + SW_COL_GAP;
        for (int i = 0; i < n; i++) {
            colCx[i] = cursor + colW[i] / 2;
            cursor += colW[i] + SW_COL_GAP;
        }

        int yStemEnd = headBottom + Math.max(V_GAP / 2, 10);
        g.setColor(ARROW_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx, headBottom, cx, yStemEnd);
        int busY = yStemEnd;
        int leftBus = colCx[0];
        int rightBus = colCx[n - 1];
        g.drawLine(leftBus, busY, rightBus, busY);
        for (int colCx1 : colCx) {
            g.drawLine(colCx1, busY, colCx1, busY + 6);
        }

        int[] colBottom = new int[n];
        int yCase = busY + 6;
        for (int i = 0; i < n; i++) {
            String caseText = arms.get(i).getLabel().equals("default")
                    ? "default"
                    : "case " + trunc(arms.get(i).getLabel(), 16);
            drawDiamond(g, colCx[i], yCase, caseText);
            int bodyStart = yCase + DIAMOND_H;
            colBottom[i] = arms.get(i).getBody().isEmpty()
                    ? bodyStart
                    : drawNodeList(g, arms.get(i).getBody(), colCx[i], bodyStart);
        }

        int mergeY = colBottom[0];
        for (int b : colBottom) {
            mergeY = Math.max(mergeY, b);
        }
        mergeY += V_GAP;

        for (int i = 0; i < n; i++) {
            drawArrowDown(g, colCx[i], colBottom[i], colCx[i], mergeY - 5);
            drawHorizontalLine(g, colCx[i], mergeY - 5, cx);
        }
        g.setColor(new Color(100, 100, 110));
        g.fillOval(cx - 4, mergeY - 8, 8, 8);

        return mergeY;
    }

    /** try / catch / finally: switch ile aynı yatay kolon düzeni. */
    private int drawTryCatchNode(Graphics2D g, FlowNode node, int cx, int y) {
        drawDiamond(g, cx, y, "try / catch");
        int headBottom = y + DIAMOND_H;
        List<FlowNode.TryCatchArm> arms = node.getTryCatchArms();
        if (arms.isEmpty()) {
            return headBottom;
        }
        int n = arms.size();
        int[] colW = new int[n];
        int[] colCx = new int[n];
        for (int i = 0; i < n; i++) {
            colW[i] = Math.max(NODE_W + 28, branchWidth(arms.get(i).getBody()));
        }
        int totalW = SW_COL_GAP * (n + 1);
        for (int w : colW) {
            totalW += w;
        }
        int left = cx - totalW / 2;
        int cursor = left + SW_COL_GAP;
        for (int i = 0; i < n; i++) {
            colCx[i] = cursor + colW[i] / 2;
            cursor += colW[i] + SW_COL_GAP;
        }

        int yStemEnd = headBottom + Math.max(V_GAP / 2, 10);
        g.setColor(ARROW_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(cx, headBottom, cx, yStemEnd);
        int busY = yStemEnd;
        g.drawLine(colCx[0], busY, colCx[n - 1], busY);
        for (int colCx1 : colCx) {
            g.drawLine(colCx1, busY, colCx1, busY + 6);
        }

        int yArm = busY + 6;
        int[] colBottom = new int[n];
        for (int i = 0; i < n; i++) {
            String hdr = trunc(arms.get(i).getHeader(), 26);
            drawDiamond(g, colCx[i], yArm, hdr);
            int bodyStart = yArm + DIAMOND_H;
            colBottom[i] = arms.get(i).getBody().isEmpty()
                    ? bodyStart
                    : drawNodeList(g, arms.get(i).getBody(), colCx[i], bodyStart);
        }

        int mergeY = colBottom[0];
        for (int b : colBottom) {
            mergeY = Math.max(mergeY, b);
        }
        mergeY += V_GAP;
        for (int i = 0; i < n; i++) {
            drawArrowDown(g, colCx[i], colBottom[i], colCx[i], mergeY - 5);
            drawHorizontalLine(g, colCx[i], mergeY - 5, cx);
        }
        g.setColor(new Color(100, 100, 110));
        g.fillOval(cx - 4, mergeY - 8, 8, 8);
        return mergeY;
    }

    /** synchronized (lock): elmas + gövde; döngüdeki gibi tekrar oku yok. */
    private int drawSyncBlock(Graphics2D g, FlowNode node, int cx, int y) {
        String text = "sync" + (node.getDetail().isEmpty() ? "" : " (" + trunc(node.getDetail(), 20) + ")");
        drawDiamond(g, cx, y, text);
        int bottom = y + DIAMOND_H;
        if (!node.getTrueBranch().isEmpty()) {
            return drawNodeList(g, node.getTrueBranch(), cx, bottom);
        }
        return bottom;
    }

    /**
     * Döngü: karar ile aynı elmas başlık; gövde aşağıda, sağda kesik tekrar oku.
     */
    private int drawLoopBranch(Graphics2D g, FlowNode node, int cx, int y) {
        String label = node.getLabel();
        String detail = node.getDetail();
        String text = label + (detail.isEmpty() ? "" : " (" + trunc(detail, 18) + ")");

        drawDiamond(g, cx, y, text);
        int loopTop = y;
        int loopMidY = y + DIAMOND_H / 2;
        int loopBottom = y + DIAMOND_H;
        int diamondRight = cx + (NODE_W + 20) / 2 + 5;

        if (!node.getTrueBranch().isEmpty()) {
            int bodyBottom = drawNodeList(g, node.getTrueBranch(), cx, loopBottom);

            int loopBackX = cx + (NODE_W + 20) / 2 + 30;
            g.setColor(new Color(234, 160, 0, 140));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));

            g.drawLine(diamondRight, bodyBottom - NODE_H / 2, loopBackX, bodyBottom - NODE_H / 2);
            g.drawLine(loopBackX, bodyBottom - NODE_H / 2, loopBackX, loopMidY);
            g.drawLine(loopBackX, loopMidY, diamondRight, loopMidY);

            g.setStroke(new BasicStroke(1.5f));
            int ax = diamondRight;
            int ay = loopMidY;
            g.fillPolygon(new int[]{ax, ax + 6, ax + 6}, new int[]{ay, ay - 4, ay + 4}, 3);

            g.setFont(new Font(Font.MONOSPACED, Font.ITALIC, 9));
            g.setColor(new Color(180, 180, 190));
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
            } else if (n.getType() == FlowNode.Type.SWITCH && n.hasSwitchArms()) {
                int swW = switchFanWidth(n.getSwitchArms());
                int armMax = 0;
                for (FlowNode.SwitchArm arm : n.getSwitchArms()) {
                    armMax = Math.max(armMax, DIAMOND_H + branchHeight(arm.getBody()));
                }
                int swH = DIAMOND_H + Math.max(V_GAP / 2, 10) + 6 + armMax + V_GAP * 2;
                w = Math.max(w, swW);
                h += swH;
            } else if (n.getType() == FlowNode.Type.TRY_CATCH && n.hasTryCatchArms()) {
                int tw = tryFanWidth(n.getTryCatchArms());
                int armMax = 0;
                for (FlowNode.TryCatchArm arm : n.getTryCatchArms()) {
                    armMax = Math.max(armMax, DIAMOND_H + branchHeight(arm.getBody()));
                }
                int th = DIAMOND_H + Math.max(V_GAP / 2, 10) + 6 + armMax + V_GAP * 2;
                w = Math.max(w, tw);
                h += th;
            } else if (n.getType() == FlowNode.Type.SYNC_BLOCK && n.hasBranches()) {
                int bh = branchHeight(n.getTrueBranch());
                int bw = branchWidth(n.getTrueBranch());
                w = Math.max(w, bw);
                h += DIAMOND_H + bh;
            } else if (n.getType() == FlowNode.Type.LOOP && n.hasBranches()) {
                int bh = branchHeight(n.getTrueBranch());
                int bw = branchWidth(n.getTrueBranch());
                w = Math.max(w, bw + 80);
                h += DIAMOND_H + bh;
            } else if (n.getType() == FlowNode.Type.ASSERT_COND) {
                h += DIAMOND_H;
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
            } else if (n.getType() == FlowNode.Type.SWITCH && n.hasSwitchArms()) {
                w = Math.max(w, switchFanWidth(n.getSwitchArms()));
            } else if (n.getType() == FlowNode.Type.TRY_CATCH && n.hasTryCatchArms()) {
                w = Math.max(w, tryFanWidth(n.getTryCatchArms()));
            } else if (n.getType() == FlowNode.Type.SYNC_BLOCK && n.hasBranches()) {
                w = Math.max(w, branchWidth(n.getTrueBranch()));
            } else if (n.getType() == FlowNode.Type.LOOP && n.hasBranches()) {
                w = Math.max(w, branchWidth(n.getTrueBranch()) + 80);
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
            } else if (n.getType() == FlowNode.Type.SWITCH && n.hasSwitchArms()) {
                int armMax = 0;
                for (FlowNode.SwitchArm arm : n.getSwitchArms()) {
                    armMax = Math.max(armMax, DIAMOND_H + branchHeight(arm.getBody()));
                }
                h += DIAMOND_H + Math.max(V_GAP / 2, 10) + 6 + armMax + V_GAP * 2;
            } else if (n.getType() == FlowNode.Type.TRY_CATCH && n.hasTryCatchArms()) {
                int armMax = 0;
                for (FlowNode.TryCatchArm arm : n.getTryCatchArms()) {
                    armMax = Math.max(armMax, DIAMOND_H + branchHeight(arm.getBody()));
                }
                h += DIAMOND_H + Math.max(V_GAP / 2, 10) + 6 + armMax + V_GAP * 2;
            } else if (n.getType() == FlowNode.Type.SYNC_BLOCK && n.hasBranches()) {
                h += DIAMOND_H + branchHeight(n.getTrueBranch());
            } else if (n.getType() == FlowNode.Type.LOOP && n.hasBranches()) {
                h += DIAMOND_H + branchHeight(n.getTrueBranch());
            } else if (n.getType() == FlowNode.Type.ASSERT_COND) {
                h += DIAMOND_H;
            } else {
                h += NODE_H;
            }
        }
        return h;
    }

    private int switchFanWidth(List<FlowNode.SwitchArm> arms) {
        int n = arms.size();
        if (n == 0) return NODE_W;
        int sum = SW_COL_GAP * (n + 1);
        for (FlowNode.SwitchArm arm : arms) {
            sum += Math.max(NODE_W + 28, branchWidth(arm.getBody()));
        }
        return sum;
    }

    private int tryFanWidth(List<FlowNode.TryCatchArm> arms) {
        int n = arms.size();
        if (n == 0) return NODE_W;
        int sum = SW_COL_GAP * (n + 1);
        for (FlowNode.TryCatchArm arm : arms) {
            sum += Math.max(NODE_W + 28, branchWidth(arm.getBody()));
        }
        return sum;
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

    private void drawProcessStep(Graphics2D g, int cx, int y, String text) {
        drawRoundRect(g, cx, y, text, PROCESS);
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
