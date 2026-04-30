package com.codeflow.ui;

import com.codeflow.model.CodeClass;
import com.codeflow.model.CodeMethod;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class OverviewRenderer extends JPanel {

    private List<CodeClass> classes;

    private static final int CARD_W = 240;
    private static final int CARD_GAP = 20;
    private static final int CARD_PAD = 30;

    public OverviewRenderer() {
        setBackground(new Color(25, 25, 28));
    }

    public void setClasses(List<CodeClass> classes) {
        this.classes = classes;

        if (classes != null && !classes.isEmpty()) {
            int cols = Math.max(1, Math.min(classes.size(), 3));
            int rows = (int) Math.ceil((double) classes.size() / cols);
            int maxMethodCount = classes.stream().mapToInt(c -> c.getMethods().size()).max().orElse(0);
            int cardH = 60 + maxMethodCount * 20 + 40;
            int totalW = cols * (CARD_W + CARD_GAP) + CARD_PAD * 2;
            int totalH = rows * (cardH + CARD_GAP) + CARD_PAD * 2;
            setPreferredSize(new Dimension(totalW, totalH));
        }
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (classes == null || classes.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cols = Math.max(1, Math.min(classes.size(), 3));
        int x = CARD_PAD, y = CARD_PAD;
        int col = 0;

        for (CodeClass cc : classes) {
            drawClassCard(g2, cc, x, y);

            int cardH = calculateCardHeight(cc);
            col++;
            if (col >= cols) {
                col = 0;
                x = CARD_PAD;
                y += cardH + CARD_GAP;
            } else {
                x += CARD_W + CARD_GAP;
            }
        }

        g2.dispose();
    }

    private void drawClassCard(Graphics2D g, CodeClass cc, int x, int y) {
        int cardH = calculateCardHeight(cc);

        // Card background
        g.setColor(new Color(38, 38, 42));
        g.fill(new RoundRectangle2D.Double(x, y, CARD_W, cardH, 12, 12));
        g.setColor(new Color(70, 70, 78));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(x, y, CARD_W, cardH, 12, 12));

        // Class name header
        Color kindColor = switch (cc.getKind()) {
            case CLASS -> new Color(37, 99, 235);
            case INTERFACE -> new Color(46, 160, 67);
            case ENUM -> new Color(234, 160, 0);
            case RECORD -> new Color(147, 51, 234);
        };

        g.setColor(kindColor.darker());
        g.fillRoundRect(x, y, CARD_W, 30, 12, 12);
        g.fillRect(x, y + 18, CARD_W, 12);

        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(Color.WHITE);
        String kindLabel = cc.getKind().name().charAt(0) + " ";
        g.drawString(kindLabel + cc.getName(), x + 10, y + 20);

        // Fields
        int ty = y + 42;
        if (!cc.getFields().isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            g.setColor(new Color(140, 140, 150));
            g.drawString("FIELDS", x + 10, ty);
            ty += 14;

            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            for (var field : cc.getFields()) {
                g.setColor(new Color(37, 99, 235, 180));
                g.fillOval(x + 10, ty - 7, 5, 5);
                g.setColor(new Color(180, 180, 190));
                g.drawString(field.getTypeName() + " " + field.getName(), x + 20, ty);
                ty += 16;
            }
            ty += 4;
        }

        // Methods
        if (!cc.getMethods().isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            g.setColor(new Color(140, 140, 150));
            g.drawString("METHODS", x + 10, ty);
            ty += 14;

            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            for (CodeMethod m : cc.getMethods()) {
                g.setColor(new Color(20, 184, 166, 180));
                g.fillOval(x + 10, ty - 7, 5, 5);
                g.setColor(new Color(180, 180, 190));
                String sig = m.getReturnType() + " " + m.getName() + "()";
                if (sig.length() > 30) sig = sig.substring(0, 27) + "...";
                g.drawString(sig, x + 20, ty);
                ty += 16;
            }
        }
    }

    private int calculateCardHeight(CodeClass cc) {
        int h = 42;
        if (!cc.getFields().isEmpty()) h += 14 + cc.getFields().size() * 16 + 4;
        if (!cc.getMethods().isEmpty()) h += 14 + cc.getMethods().size() * 16;
        return h + 12;
    }
}
