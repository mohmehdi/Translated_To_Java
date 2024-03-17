

package com.squareup.leakcanary;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Fragment;
import android.os.MessageQueue;
import android.view.View;
import java.lang.reflect.Field;
import java.util.ArrayList;

public enum AndroidReachabilityInspectors {
  VIEW(ViewInspector.class),
  ACTIVITY(ActivityInspector.class),
  DIALOG(DialogInspector.class),
  APPLICATION(ApplicationInspector.class),
  FRAGMENT(FragmentInspector.class),
  SUPPORT_FRAGMENT(SupportFragmentInspector.class),
  MESSAGE_QUEUE(MessageQueueInspector.class),
  MORTAR_PRESENTER(MortarPresenterInspector.class),
  VIEW_ROOT_IMPL(ViewImplInspector.class),
  MAIN_THEAD(MainThreadInspector.class),
  WINDOW(WindowInspector.class);

  private final Class<? extends Reachability.Inspector> inspectorClass;

  AndroidReachabilityInspectors(Class<? extends Reachability.Inspector> inspectorClass) {
    this.inspectorClass = inspectorClass;
  }

  public static class ViewInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      if (!element.isInstanceOf(View.class)) {
        return Reachability.unknown();
      }
      return unreachableWhen(element, View.class.getName(), "mAttachInfo", "null");
    }
  }

  public static class ActivityInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, Activity.class.getName(), "mDestroyed", "true");
    }
  }

  public static class DialogInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, Dialog.class.getName(), "mDecor", "null");
    }
  }

  public static class ApplicationInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return element.isInstanceOf(Application.class)
          ? Reachability.reachable("the application class is a singleton")
          : Reachability.unknown();
    }
  }

  public static class FragmentInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, Fragment.class.getName(), "mDetached", "true");
    }
  }

  public static class SupportFragmentInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, "android.support.v4.app.Fragment", "mDetached", "true");
    }
  }

  public static class MessageQueueInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      if (!element.isInstanceOf(MessageQueue.class)) {
        return Reachability.unknown();
      }
      Object mQuitting = element.getFieldReferenceValue("mQuitting");
      return "true".equals(mQuitting)
          ? Reachability.unreachable("MessageQueue#mQuitting is true")
          : Reachability.unknown();
    }
  }

  public static class MortarPresenterInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      if (!element.isInstanceOf("mortar.Presenter")) {
        return Reachability.unknown();
      }
      Object view = element.getFieldReferenceValue("view");
      return "null".equals(view)
          ? Reachability.unreachable("Presenter#view is null")
          : Reachability.unknown();
    }
  }

  public static class ViewImplInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, "android.view.ViewRootImpl", "mView", "null");
    }
  }

  public static class MainThreadInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      if (!element.isInstanceOf(Thread.class)) {
        return Reachability.unknown();
      }
      String name = (String) element.getFieldReferenceValue("name");
      return "main".equals(name)
          ? Reachability.reachable("the main thread always runs")
          : Reachability.unknown();
    }
  }

  public static class WindowInspector implements Reachability.Inspector {
    @Override
    public Reachability expectedReachability(LeakTraceElement element) {
      return unreachableWhen(element, "android.view.Window", "mDestroyed", "true");
    }
  }

  public static ArrayList<Class<? extends Reachability.Inspector>> defaultAndroidInspectors() {
    ArrayList<Class<? extends Reachability.Inspector>> inspectorClasses =
        new ArrayList<Class<? extends Reachability.Inspector>>();
    for (AndroidReachabilityInspectors enumValue : AndroidReachabilityInspectors.values()) {
      inspectorClasses.add(enumValue.inspectorClass);
    }
    return inspectorClasses;
  }

  private static Reachability unreachableWhen(
      LeakTraceElement element,
      String className,
      String fieldName,
      String unreachableValue) {
    if (!element.isInstanceOf(className)) {
      return Reachability.unknown();
    }
    Object fieldValue = element.getFieldReferenceValue(fieldName);
    return fieldValue == null
        ? Reachability.unknown()
        : unreachableWhen(element, className, fieldName, unreachableValue);
  }



  private static String simpleClassName(String className) {
    int separator = className.lastIndexOf('.');
    return separator == -1 ? className : className.substring(separator + 1);
  }
}