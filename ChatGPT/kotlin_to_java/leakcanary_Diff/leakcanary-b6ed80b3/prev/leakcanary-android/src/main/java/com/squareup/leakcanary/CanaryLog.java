package com.squareup.leakcanary;

import android.util.Log;

public class CanaryLog {

  interface Logger {
    void d(String message, Object... args);

    void d(Throwable throwable, String message, Object... args);
  }

  private static class DefaultLogger implements Logger {

    @Override
    public void d(String message, Object... args) {
      String formatted = String.format(message, args);
      if (formatted.length() < 4000) {
        Log.d("LeakCanary", formatted);
      } else {
        String[] lines = formatted.split("\n");
        for (String line : lines) {
          Log.d("LeakCanary", line);
        }
      }
    }

    @Override
    public void d(Throwable throwable, String message, Object... args) {
      d(String.format(message, args) + '\n' + Log.getStackTraceString(throwable));
    }
  }

  private CanaryLog() {
    throw new AssertionError();
  }

  private static volatile Logger logger = new DefaultLogger();

  public static void setLogger(Logger logger) {
    CanaryLog.logger = logger;
  }

  public static void d(String message, Object... args) {
    Logger logger = CanaryLog.logger;
    if (logger != null) {
      logger.d(message, args);
    }
  }

  public static void d(Throwable throwable, String message, Object... args) {
    Logger logger = CanaryLog.logger;
    if (logger != null) {
      logger.d(throwable, message, args);
    }
  }
}