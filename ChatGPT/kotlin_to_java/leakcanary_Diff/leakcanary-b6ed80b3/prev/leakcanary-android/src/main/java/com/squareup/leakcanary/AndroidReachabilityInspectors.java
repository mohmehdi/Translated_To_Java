
package com.squareup.leakcanary;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Fragment;
import android.os.MessageQueue;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

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

    public static List<Class<? extends Reachability.Inspector>> defaultAndroidInspectors() {
        List<Class<? extends Reachability.Inspector>> inspectorClasses = new ArrayList<>();
        for (AndroidReachabilityInspectors enumValue : AndroidReachabilityInspectors.values()) {
            inspectorClasses.add(enumValue.inspectorClass);
        }
        return inspectorClasses;
    }

    private static Reachability unreachableWhen(LeakTraceElement element, String className, String fieldName, String unreachableValue) {
        if (!element.isInstanceOf(className)) {
            return Reachability.unknown();
        }
        String fieldValue = element.getFieldReferenceValue(fieldName);
        if (fieldValue == null) {
            return Reachability.unknown();
        }
        if (fieldValue.equals(unreachableValue)) {
            return Reachability.unreachable(simpleClassName(className) + "#" + fieldName + " is " + unreachableValue);
        } else {
            return Reachability.reachable(simpleClassName(className) + "#" + fieldName + " is not " + unreachableValue);
        }
    }

    private static String simpleClassName(String className) {
        int separator = className.lastIndexOf('.');
        if (separator == -1) {
            return className;
        } else {
            return className.substring(separator + 1);
        }
    }


    public static class ViewInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            if (!element.isInstanceOf(View.class)) {
                return Reachability.unknown();
            }
            return unreachableWhen(element, View.class.getName(), "mAttachInfo", "null");
        }
    }

    public static class ActivityInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, Activity.class.getName(), "mDestroyed", "true");
        }
    }

    public static class DialogInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, Dialog.class.getName(), "mDecor", "null");
        }
    }

    public static class ApplicationInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            if (element.isInstanceOf(Application.class)) {
                return Reachability.reachable("the application class is a singleton");
            } else {
                return Reachability.unknown();
            }
        }
    }

    public static class FragmentInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, Fragment.class.getName(), "mDetached", "true");
        }
    }

    public static class SupportFragmentInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, "android.support.v4.app.Fragment", "mDetached", "true");
        }
    }

    public static class MessageQueueInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            if (!element.isInstanceOf(MessageQueue.class)) {
                return Reachability.unknown();
            }
            String mQuitting = element.getFieldReferenceValue("mQuitting");
            if ("true".equals(mQuitting)) {
                return Reachability.unreachable("MessageQueue#mQuitting is true");
            } else {
                return Reachability.unknown();
            }
        }
    }

    public static class MortarPresenterInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            if (!element.isInstanceOf("mortar.Presenter")) {
                return Reachability.unknown();
            }
            String view = element.getFieldReferenceValue("view");
            if ("null".equals(view)) {
                return Reachability.unreachable("Presenter#view is null");
            } else {
                return Reachability.unknown();
            }
        }
    }

    public static class ViewImplInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, "android.view.ViewRootImpl", "mView", "null");
        }
    }

    public static class MainThreadInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            if (!element.isInstanceOf(Thread.class)) {
                return Reachability.unknown();
            }
            String name = element.getFieldReferenceValue("name");
            if ("main".equals(name)) {
                return Reachability.reachable("the main thread always runs");
            } else {
                return Reachability.unknown();
            }
        }
    }

    public static class WindowInspector implements Inspector {
        @Override
        public Reachability expectedReachability(LeakTraceElement element) {
            return unreachableWhen(element, "android.view.Window", "mDestroyed", "true");
        }
    }
}