package net.mamoe.mirai.utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("deprecation")
class WindowHelperJvm {
    static final boolean isDesktopSupported;

    static {
        isDesktopSupported = new kotlin.jvm.functions.Function0<Boolean>() {
        }.invoke();
    }

    internal static CompletableFuture<String> openWindow(String title, kotlin.jvm.functions.Function2<JFrame, WindowInitializer, Unit> initializer) {
    return openWindow(title, new WindowInitializer(initializer));
}

internal static CompletableFuture<String> openWindow(String title, WindowInitializer initializer) {
    JFrame frame = new JFrame();
    frame.setIconImage(WindowUtils.windowIcon);
    frame.setMinimumSize(new Dimension(228, 62));
    JTextField value = new JTextField();
    CompletableFuture<String> def = new CompletableFuture<>();
    value.addKeyListener(new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == 27 || e.getKeyCode() == 10) {
                def.complete(value.getText());
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
    return def.thenApply(result -> {
        SwingUtilities.invokeLater(frame::dispose);
        return result.trim();
    });
}
}

public class WindowInitialzier {
    private JFrame frame0;
    private final WindowInitializerCallback initializer;

    public WindowInitialzier(WindowInitializerCallback initializer) {
        this.initializer = initializer;
    }

    public void append(java.awt.Component component) {
        frame0.add(component, BorderLayout.NORTH);
    }

    public void last(java.awt.Component component) {
        frame0.add(component);
    }

    void init(JFrame frame) {
        this.frame0 = frame;
        initializer.initialize(this, frame);
    }


}


