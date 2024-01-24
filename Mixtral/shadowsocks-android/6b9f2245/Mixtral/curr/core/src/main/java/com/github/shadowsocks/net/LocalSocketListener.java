package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.github.shadowsocks.utils.printLog;
import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.util.concurrent.Channel;
import java.util.concurrent.Channels;

public abstract class LocalSocketListener extends Thread {

  private final LocalSocket localSocket = new LocalSocket();
  private final LocalServerSocket serverSocket;
  private final Channel closeChannel;
  private volatile boolean running;

  protected LocalSocketListener(String name, File socketFile) {
    super(name);
    localSocket.delete(); // It's a must-have to close and reuse previous local socket.
    LocalSocketAddress localSocketAddress = new LocalSocketAddress(
      socketFile.getAbsolutePath(),
      LocalSocketAddress.Namespace.FILESYSTEM
    );
    try {
      localSocket.bind(localSocketAddress);
    } catch (IOException e) {
      printLog(e);
    }
    serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
    closeChannel = Channels.newChannel(1);
    this.running = true;
  }

  protected abstract void acceptInternal(LocalSocket socket) throws IOException;

  public final void run() {
    try {
      while (running) {
        try {
          accept(serverSocket.accept());
        } catch (IOException e) {
          if (running) printLog(e);
          continue;
        }
      }
    } finally {
      try {
        closeChannel.send(null);
      } catch (IOException e) {
        printLog(e);
      }
    }
  }

  protected final void accept(LocalSocket socket) throws IOException {
    try {
      return acceptInternal(socket);
    } finally {
      socket.close();
    }
  }

  public final void shutdown(CoroutineScope scope) {
    this.running = false;
    try {
      if (localSocket.getFileDescriptor().valid()) {
        Os.shutdown(localSocket.getFileDescriptor(), OsConstants.SHUT_RDWR);
      }
    } catch (ErrnoException e) {
      if (e.errno != OsConstants.EBADF && e.errno != OsConstants.ENOTCONN) {
        throw new IOException(e);
      }
    }
    scope.launch(() -> {
      try {
        closeChannel.receive();
      } catch (IOException e) {
        printLog(e);
      }
    });
  }
}
