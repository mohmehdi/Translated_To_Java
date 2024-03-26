package net.mamoe.mirai.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class WindowHelperJvm {
    private static final boolean isDesktopSupported;

    static {
        boolean desktopSupported;
        try {
            desktopSupported = System.getProperty("mirai.no-desktop") == null && Desktop.isDesktopSupported();
        } catch (Exception e) {
            desktopSupported = false;
        }
        isDesktopSupported = desktopSupported;
    }

    public static boolean isDesktopSupported() {
        return isDesktopSupported;
    }

    public static String openWindow(String title, WindowInitializer.WindowInitializerCallback initializer) {
        return openWindow(title, new WindowInitializer(initializer));
    }

    public static String openWindow(String title, WindowInitializer initializer) {
        JFrame frame = new JFrame();
        JTextField value = new JTextField();
        CompletableDeferred def = new CompletableDeferred();
        value.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case 27:
                    case 10:
                        def.complete(value.getText());
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {}
        });
        frame.setLayout(new BorderLayout(10, 5));
        frame.add(value, BorderLayout.SOUTH);
        initializer.init(frame);

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                def.complete(value.getText());
            }
        });
        frame.setLocationRelativeTo(null);
        frame.setTitle(title);
        frame.setVisible(true);

        String result = ((String) def.await()).trim();
        SwingUtilities.invokeLater(frame::dispose);
        return result;
    }
}

public class WindowInitializer {
    private JFrame frame0;
    private final WindowInitializerCallback initializer;

    public WindowInitializer(WindowInitializerCallback initializer) {
        this.initializer = initializer;
    }

    public void append(Component component) {
        frame0.add(component, BorderLayout.NORTH);
    }

    public void last(Component component) {
        frame0.add(component);
    }

    void init(JFrame frame) {
        this.frame0 = frame;
        initializer.initialize(frame);
    }

}