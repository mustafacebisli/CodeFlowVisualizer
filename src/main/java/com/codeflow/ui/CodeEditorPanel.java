package com.codeflow.ui;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.util.regex.Pattern;

public class CodeEditorPanel extends JPanel {

    private final JTextArea textArea;
    private final JLabel statusLabel;
    private final JComboBox<String> classSelector;
    private Runnable onChangeCallback;
    private boolean suppressClassScroll = false;

    private Timer debounceTimer;

    public CodeEditorPanel() {
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(6, 10, 6, 10));
        header.setBackground(new Color(45, 45, 48));

        JLabel title = new JLabel("\u2329/\u232A  Kod Edit\u00f6r\u00fc");
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(new Color(80, 200, 120));
        header.add(title, BorderLayout.WEST);

        statusLabel = new JLabel("0 sat\u0131r");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(150, 150, 150));
        header.add(statusLabel, BorderLayout.EAST);

        // Class selector toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        toolbar.setBackground(new Color(40, 40, 43));

        JLabel classLabel = new JLabel("S\u0131n\u0131f:");
        classLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        classLabel.setForeground(new Color(180, 180, 180));
        toolbar.add(classLabel);

        classSelector = new JComboBox<>();
        classSelector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        classSelector.addItem("T\u00fcm\u00fc");
        classSelector.addActionListener(e -> {
            if (!suppressClassScroll) scrollToSelectedClass();
        });
        toolbar.add(classSelector);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // Text Area
        textArea = new JTextArea();
        textArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        if (!isFontAvailable("JetBrains Mono")) {
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        }
        textArea.setTabSize(4);
        textArea.setBackground(new Color(30, 30, 30));
        textArea.setForeground(new Color(212, 212, 212));
        textArea.setCaretColor(Color.WHITE);
        textArea.setSelectionColor(new Color(38, 79, 120));
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        textArea.setLineWrap(false);

        LineNumberPanel lineNumbers = new LineNumberPanel(textArea);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleChange(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleChange(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleChange(); }
        });
    }

    public String getCode() {
        return textArea.getText();
    }

    public void setCode(String code) {
        textArea.setText(code);
        textArea.setCaretPosition(0);
        updateStatus();
    }

    public void setOnChange(Runnable callback) {
        this.onChangeCallback = callback;
    }

    public void updateClassList(java.util.List<String> classNames) {
        suppressClassScroll = true;
        String prev = (String) classSelector.getSelectedItem();
        classSelector.removeAllItems();
        classSelector.addItem("T\u00fcm\u00fc");
        for (String name : classNames) {
            classSelector.addItem(name);
        }
        if (prev != null) {
            for (int i = 0; i < classSelector.getItemCount(); i++) {
                if (classSelector.getItemAt(i).equals(prev)) {
                    classSelector.setSelectedIndex(i);
                    break;
                }
            }
        }
        suppressClassScroll = false;
    }

    private void scrollToSelectedClass() {
        String selected = (String) classSelector.getSelectedItem();
        if (selected == null || selected.equals("T\u00fcm\u00fc")) {
            textArea.setCaretPosition(0);
            return;
        }
        int idx = indexOfTypeDeclaration(selected);
        if (idx < 0) return;
        scrollToOffset(idx);
    }

    /** Scrolls to a combined-buffer marker line {@code // === path/to/File.java ===}. */
    public void scrollToFileMarker(String logicalPath) {
        if (logicalPath == null || logicalPath.isBlank()) return;
        String marker = "// === " + logicalPath + " ===";
        int idx = textArea.getText().indexOf(marker);
        if (idx < 0) return;
        scrollToOffset(idx);
    }

    private int indexOfTypeDeclaration(String typeName) {
        String code = textArea.getText();
        Pattern p = Pattern.compile(
                "\\b(class|interface|enum|record)\\s+" + Pattern.quote(typeName) + "\\b");
        var m = p.matcher(code);
        return m.find() ? m.start() : -1;
    }

    private void scrollToOffset(int idx) {
        textArea.setCaretPosition(idx);
        try {
            int line = textArea.getLineOfOffset(idx);
            int startOfLine = textArea.getLineStartOffset(line);
            Rectangle rect = textArea.modelToView(startOfLine);
            if (rect != null) {
                rect.height = textArea.getVisibleRect().height;
                textArea.scrollRectToVisible(rect);
            }
        } catch (BadLocationException ignored) {}
        textArea.requestFocusInWindow();
    }

    /** Selects a type in the dropdown; optionally scrolls the editor to its declaration. */
    public void setSelectedClassByName(String className, boolean scrollToDeclaration) {
        if (className == null) return;
        suppressClassScroll = true;
        for (int i = 0; i < classSelector.getItemCount(); i++) {
            if (className.equals(classSelector.getItemAt(i))) {
                classSelector.setSelectedIndex(i);
                break;
            }
        }
        suppressClassScroll = false;
        if (scrollToDeclaration) scrollToSelectedClass();
    }

    private void scheduleChange() {
        updateStatus();
        if (debounceTimer != null) debounceTimer.stop();
        debounceTimer = new Timer(500, e -> {
            if (onChangeCallback != null) onChangeCallback.run();
        });
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void updateStatus() {
        int lines = textArea.getLineCount();
        statusLabel.setText(lines + " sat\u0131r");
    }

    private boolean isFontAvailable(String name) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equals(name)) return true;
        }
        return false;
    }

    // --- Line Number Panel ---

    private static class LineNumberPanel extends JPanel {
        private final JTextArea textArea;

        LineNumberPanel(JTextArea textArea) {
            this.textArea = textArea;
            setPreferredSize(new Dimension(45, 0));
            setBackground(new Color(37, 37, 38));
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

            textArea.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { repaint(); }
                @Override public void removeUpdate(DocumentEvent e) { repaint(); }
                @Override public void changedUpdate(DocumentEvent e) { repaint(); }
            });
            textArea.addCaretListener(e -> repaint());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();

            int lineHeight = textArea.getFontMetrics(textArea.getFont()).getHeight();
            int startOffset = textArea.getInsets().top;
            int lineCount = textArea.getLineCount();

            for (int i = 0; i < lineCount; i++) {
                int y = startOffset + (i + 1) * lineHeight - fm.getDescent();
                String num = String.valueOf(i + 1);
                int x = getWidth() - fm.stringWidth(num) - 6;
                g2.setColor(new Color(100, 100, 100));
                g2.drawString(num, x, y);
            }
            g2.dispose();
        }
    }
}
