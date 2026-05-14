package com.codeflow.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Metin alanında Ctrl+F ile açılan basit ara / değiştir iletişim kutusu.
 */
public class EditorFindDialog extends JDialog {

    private final JTextArea textArea;
    private final JTextField findField = new JTextField(26);
    private final JTextField replaceField = new JTextField(26);
    private final JCheckBox caseSensitive = new JCheckBox("Buyuk/kucuk harf esles");

    public EditorFindDialog(Window owner, JTextArea textArea) {
        super(owner, "Ara ve degistir", Dialog.ModalityType.MODELESS);
        this.textArea = textArea;
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        form.add(new JLabel("Ara:"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        form.add(findField, g);
        g.gridy = 1;
        g.gridx = 0;
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
        form.add(new JLabel("Degistir:"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        form.add(replaceField, g);
        g.gridy = 2;
        g.gridx = 0;
        g.gridwidth = 2;
        form.add(caseSensitive, g);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton next = new JButton("Sonraki");
        next.addActionListener(e -> findNext());
        JButton prev = new JButton("Onceki");
        prev.addActionListener(e -> findPrevious());
        JButton replaceOne = new JButton("Degistir");
        replaceOne.addActionListener(e -> replaceOne());
        JButton replaceAll = new JButton("Tumunu degistir");
        replaceAll.addActionListener(e -> replaceAll());
        JButton close = new JButton("Kapat");
        close.addActionListener(e -> setVisible(false));
        buttons.add(prev);
        buttons.add(next);
        buttons.add(replaceOne);
        buttons.add(replaceAll);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(next);
        pack();
        setResizable(false);

        findField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        findField.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                setVisible(false);
            }
        });
    }

    public void openWithSelection() {
        String sel = textArea.getSelectedText();
        if (sel != null && !sel.isBlank() && sel.indexOf('\n') < 0) {
            findField.setText(sel.trim());
        }
        setLocationRelativeTo(getOwner());
        setVisible(true);
        findField.requestFocusInWindow();
    }

    private Pattern buildPattern(String needle) throws PatternSyntaxException {
        String quoted = Pattern.quote(needle);
        int flags = Pattern.MULTILINE;
        if (!caseSensitive.isSelected()) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return Pattern.compile(quoted, flags);
    }

    private void findNext() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        try {
            Pattern pat = buildPattern(needle);
            String doc = textArea.getText();
            Matcher m = pat.matcher(doc);
            int caret = textArea.getCaretPosition();
            int foundStart = -1;
            int foundEnd = -1;
            if (m.find(caret)) {
                foundStart = m.start();
                foundEnd = m.end();
            } else if (caret > 0 && m.find(0)) {
                foundStart = m.start();
                foundEnd = m.end();
            }
            if (foundStart >= 0) {
                textArea.setCaretPosition(foundEnd);
                textArea.select(foundStart, foundEnd);
                textArea.requestFocusInWindow();
            } else {
                UIManager.getLookAndFeel().provideErrorFeedback(textArea);
            }
        } catch (PatternSyntaxException ex) {
            showInvalidPattern(UiMessages.DIALOG_FIND);
        }
    }

    private void findPrevious() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        try {
            Pattern pat = buildPattern(needle);
            String doc = textArea.getText();
            Matcher m = pat.matcher(doc);
            int caret = textArea.getSelectionStart();
            int best = -1;
            int bestEnd = -1;
            while (m.find()) {
                if (m.end() <= caret) {
                    best = m.start();
                    bestEnd = m.end();
                }
            }
            if (best < 0 && caret > 0) {
                m.reset();
                while (m.find()) {
                    best = m.start();
                    bestEnd = m.end();
                }
            }
            if (best >= 0) {
                textArea.select(best, bestEnd);
                textArea.requestFocusInWindow();
            } else {
                UIManager.getLookAndFeel().provideErrorFeedback(textArea);
            }
        } catch (PatternSyntaxException ex) {
            showInvalidPattern(UiMessages.DIALOG_FIND);
        }
    }

    private void replaceOne() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        try {
            Pattern pat = buildPattern(needle);
            String doc = textArea.getText();
            int a = textArea.getSelectionStart();
            int b = textArea.getSelectionEnd();
            if (a >= b) {
                findNext();
                a = textArea.getSelectionStart();
                b = textArea.getSelectionEnd();
            }
            if (a < b) {
                String sel = doc.substring(a, b);
                Matcher mm = pat.matcher(sel);
                if (mm.matches()) {
                    String rep = replaceField.getText();
                    textArea.replaceRange(rep, a, b);
                }
            }
        } catch (PatternSyntaxException ignored) {
        }
    }

    private void replaceAll() {
        String needle = findField.getText();
        if (needle.isEmpty()) return;
        try {
            Pattern pat = buildPattern(needle);
            String rep = replaceField.getText();
            String doc = textArea.getText();
            String next = pat.matcher(doc).replaceAll(Matcher.quoteReplacement(rep));
            if (!doc.equals(next)) {
                textArea.setText(next);
            }
        } catch (PatternSyntaxException ex) {
            showInvalidPattern(UiMessages.DIALOG_REPLACE);
        }
    }

    private void showInvalidPattern(String title) {
        JOptionPane.showMessageDialog(this, UiMessages.INVALID_PATTERN, title, JOptionPane.WARNING_MESSAGE);
    }
}
