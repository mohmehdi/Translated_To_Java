package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener extends SocketListener {

  private LocalSocket localSocket;
  private LocalServerSocket serverSocket;

  public LocalSocketListener(String name, File socketFile) {
    super(name);
    try {
      socketFile.delete();
      LocalSocketAddress address = new LocalSocketAddress(
        socketFile.getAbsolutePath(),
        LocalSocketAddress.Namespace.FILESYSTEM
      );
      localSocket = new LocalSocket();
      localSocket.bind(address);
    } catch (IOException e) {
      printLog(e);
    }
    serverSocket =
      new LocalServerSocket(localSocket.getFileDescriptor().getInt$());
  }

  @Override
  public final int getFileDescriptor() {
    return localSocket.getFileDescriptor().getInt$();
  }

  @Override
  protected void accept(LocalSocket socket) throws IOException {
    try {
      acceptInternal(socket);
    } finally {
      socket.close();
    }
  }

  protected abstract void acceptInternal(LocalSocket socket) throws IOException;

  @Override
  public final void run() {
    while (running) {
      try {
        accept(serverSocket.accept());
      } catch (IOException e) {
        if (running) {
          printLog(e);
        }
        continue;
      }
    }
    localSocket.close();
    try {
      serverSocket.close();
    } catch (IOException e) {
      printLog(e);
    }
  }
}
