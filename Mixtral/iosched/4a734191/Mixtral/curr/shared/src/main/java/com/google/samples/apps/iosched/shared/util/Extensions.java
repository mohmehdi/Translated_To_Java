package com.google.samples.apps.iosched.shared.util;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

public class Extensions {

    @SuppressWarnings("unchecked")
    public static <T> Lazy<T> lazyFast(final Callable<T> operation) {
        return new Lazy<T>() {
            @Override
            public T get() {
                try {
                    return operation.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static boolean consume(final Runnable f) {
        f.run();
        return true;
    }

    public static View inflate(ViewGroup parent, @LayoutRes int layout, boolean attachToRoot) {
        return LayoutInflater.from(parent.getContext()).inflate(layout, parent, attachToRoot);
    }

    public static void inTransaction(FragmentManager manager, final FragmentTransaction.() -> FragmentTransaction func) {
        manager.beginTransaction().func().commit();
    }

    public static <VM extends ViewModel> Lazy<VM> viewModelProvider(FragmentActivity activity, ViewModelProvider.Factory provider) {
        return lazyFast(() -> ViewModelProviders.of(activity, provider).get(VM.class));
    }

    public static <VM extends ViewModel> Lazy<VM> viewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return lazyFast(() -> ViewModelProviders.of(fragment, provider).get(VM.class));
    }

    public static <VM extends ViewModel> Lazy<VM> activityViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return lazyFast(() -> ViewModelProviders.of(fragment.requireActivity(), provider).get(VM.class));
    }

    public static <VM extends ViewModel> Lazy<VM> parentViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return lazyFast(() -> ViewModelProviders.of(fragment.getParentFragment(), provider).get(VM.class));
    }

    public static <T extends Enum<T>> void writeEnum(Parcel dest, T value) {
        dest.writeString(value.name());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T readEnum(Parcel src) {
        String value = src.readString();
        return (T) Enum.valueOf(getEnumClass(src), value);
    }

    public static <T extends Enum<T>> void putEnum(Bundle bundle, String key, T value) {
        bundle.putString(key, value.name());
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> T getEnum(Bundle bundle, String key) {
        String value = bundle.getString(key);
        return (T) Enum.valueOf(getEnumClass(bundle), value);
    }


    public static <X, T> LiveData<X> map(LiveData<T> liveData, final Method body) {
        return Transformations.map(liveData, (t) -> {
            try {
                return (X) body.invoke(null, t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static <X, T> LiveData<X> switchMap(LiveData<T> liveData, final Method body) {
        return Transformations.switchMap(liveData, (t) -> {
            try {
                return (LiveData<X>) body.invoke(null, t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}