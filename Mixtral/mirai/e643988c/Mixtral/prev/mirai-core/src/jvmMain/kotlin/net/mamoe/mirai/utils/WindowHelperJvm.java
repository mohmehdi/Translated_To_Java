package net.mamoe.mirai.utils;

import kotlinx.coroutines.CompletableDeferred;
import java.awt.;
import java.awt.event.;
import javax.swing.*;

public class WindowHelperJvm {
  public static boolean isDesktopSupported = false;
  static {
    isDesktopSupported = (System.getProperty("mirai.no-desktop") == null) && Desktop.isDesktopSupported();
  }

  public suspend String openWindow(String title, WindowInitialzier initializer) {
  return openWindow(title, new WindowInitialzier() {
    @Override
    public void init(JFrame frame) {
      initializer.init(frame);
    }
  });
}

public suspend String openWindow(String title, final WindowInitialzier initializer) {
  JFrame frame = new JFrame();
  JTextField value = new JTextField();
  final CompletableDeferred def = new CompletableDeferred();

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

  String result = null;
  try {
    result = def.get().trim();
  } catch (InterruptedException e) {
    e.printStackTrace();
  } catch (ExecutionException e) {
    e.printStackTrace();
  }

  SwingUtilities.invokeLater(() -> frame.dispose());
  return result;
}
}

public class WindowInitialzier {
  private JFrame frame0;
  public JFrame frame;

  public void append(java.awt.Component c) {
    frame.add(c, BorderLayout.NORTH);
  }

  public void last(java.awt.Component c) {
    frame.add(c);
  }

  public void init(JFrame frame) {
    this.frame0 = frame;
    this.frame = frame0;
  }
}

