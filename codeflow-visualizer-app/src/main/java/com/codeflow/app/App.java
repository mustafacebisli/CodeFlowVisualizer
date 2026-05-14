package com.codeflow.app;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * Bağımsız masaüstü uygulamasının giriş noktası. Yeniden kullanılabilir bileşenler için
 * {@link com.codeflow.ui.DiagramPanel} ve {@code codeflow-core} modülüne bakın.
 */
public class App {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ignored) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored2) {
            }
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
