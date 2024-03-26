package net.mamoe.mirai.utils;

import kotlinx.coroutines.CompletableDeferred;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class WindowHelperJvm {

  static boolean isDesktopSupported = false;

  static {
    if (System.getProperty("mirai.no-desktop") == null) {
      try {
        Class.forName("java.awt.Desktop");
        Class.forName("java.awt.Toolkit");
      } catch (ClassNotFoundException e) {
        // Android OS
      }
      try {
        Toolkit.getDefaultToolkit();
      } catch (AWTError e) {
        // AWT Error, #270
      }
      try {
        isDesktopSupported = Desktop.isDesktopSupported();
        if (isDesktopSupported) {
          MiraiLogger.info("Mirai 正在使用桌面环境,\n" +
            "如果你正在使用SSH, 或无法访问桌面等,\n" +
            "请将 mirai.no-desktop 添加到 JVM 系统属性中 (-Dmirai.no-desktop)\n" +
            "然后重启 Mirai");
          MiraiLogger.info("Mirai using DesktopCaptcha System.\n" +
            "If you are running on SSH, cannot access desktop or more.\n" +
            "Please add mirai.no-desktop to JVM properties (-Dmirai.no-desktop)\n" +
            "Then restart mirai");
        }
      } catch (HeadlessException e) {
        // Should not happen
        MiraiLogger.warning("Exception in checking desktop support.", e);
        isDesktopSupported = false;
      }
    } else {
      isDesktopSupported = false;
    }
  }
  private static BufferedImage windowIcon;

  static {
    windowIcon = ImageIO.read(WindowHelperJvm.class.getResourceAsStream("/project-mirai.png"));
  }

    public String openWindow(String title, JFrame frame) {
        if (initializer != null) {
            initializer.accept(title, frame);
        }
        frame.setVisible(true);
        return title;
    }
  public static CompletableDeferred openWindow(String title, WindowInitializer.initializer) {
    final CompletableDeferred def = new CompletableDeferred();
    EventQueue.invokeLater(() -> {
      JFrame frame = new JFrame();
      frame.setIconImage(windowIcon);
      frame.setMinimumSize(new Dimension(228, 62)); // From Windows 10
      JTextField value = new JTextField();
      value.addKeyListener(new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
          int keyCode = e.getKeyCode();
          if (keyCode == 27 || keyCode == 10) {
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
    });
    return def;
  }
}

  public static class WindowInitializer {
    private JFrame frame0;
    private java.awt.Component.append() {
      frame.add(this, BorderLayout.NORTH);
    }

    private java.awt.Component.last() {
      frame.add(this);
    }

    private JFrame frame;

    public WindowInitializer(WindowInitializer.initializer) {
      this.frame0 = new JFrame();
      this.frame = this.frame0;
      initializer.init(frame);
    }
    public void init(JFrame frame) {
        this.frame0 = frame;
        initializer(frame);
    }
  }

  