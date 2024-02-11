package com.google.samples.apps.iosched.shared.util;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.os.Bundle;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Enum;

public class Extensions {

    public static <T> Lazy<T> lazyFast(Function0<T> operation) {
        return new Lazy<T>() {
            @Override
            protected T init() {
                return operation.invoke();
            }
        };
    }

    public static boolean consume(Runnable f) {
        f.run();
        return true;
    }

    public static View inflate(ViewGroup parent, int layout, boolean attachToRoot) {
        return LayoutInflater.from(parent.getContext()).inflate(layout, parent, attachToRoot);
    }

    public static void inTransaction(FragmentManager manager, Function0<FragmentTransaction> func) {
        FragmentTransaction transaction = manager.beginTransaction();
        func.invoke().commit();
    }

    public static <VM extends ViewModel> Lazy<VM> viewModelProvider(FragmentActivity activity, ViewModelProvider.Factory provider) {
        return new Lazy<VM>() {
            @Override
            protected VM init() {
                return ViewModelProviders.of(activity, provider).get(VM.class);
            }
        };
    }

    public static <VM extends ViewModel> Lazy<VM> viewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return new Lazy<VM>() {
            @Override
            protected VM init() {
                return ViewModelProviders.of(fragment, provider).get(VM.class);
            }
        };
    }

    public static <VM extends ViewModel> Lazy<VM> activityViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return new Lazy<VM>() {
            @Override
            protected VM init() {
                return ViewModelProviders.of(fragment.getActivity(), provider).get(VM.class);
            }
        };
    }

    public static <VM extends ViewModel> Lazy<VM> parentViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return new Lazy<VM>() {
            @Override
            protected VM init() {
                return ViewModelProviders.of(fragment.getParentFragment(), provider).get(VM.class);
            }
        };
    }

    public static <T extends Enum<T>> void writeEnum(Parcel dest, T value) {
        dest.writeString(value.name());
    }

    public static <T extends Enum<T>> T readEnum(Parcel src) {
        return (T) Enum.valueOf(src.readString());
    }

    public static <T extends Enum<T>> void putEnum(Bundle bundle, String key, T value) {
        bundle.putString(key, value.name());
    }

    public static <T extends Enum<T>> T getEnum(Bundle bundle, String key) {
        return (T) Enum.valueOf(bundle.getString(key));
    }

    public static <X, T> LiveData<X> map(LiveData<T> liveData, Function<T, X> body) {
        return Transformations.map(liveData, body);
    }

    public static <X, T> LiveData<X> switchMap(LiveData<T> liveData, Function<T, LiveData<X>> body) {
        return Transformations.switchMap(liveData, body);
    }
}