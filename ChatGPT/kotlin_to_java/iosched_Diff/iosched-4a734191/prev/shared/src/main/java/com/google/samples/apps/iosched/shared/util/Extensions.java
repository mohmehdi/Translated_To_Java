package com.google.samples.apps.iosched.shared.util;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
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

import java.util.EnumSet;

public class Extensions {

    public static <T> Lazy<T> lazyFast(Operation<T> operation) {
        return new Lazy<>(operation);
    }

    public static boolean consume(Operation<Void> f) {
        f.invoke();
        return true;
    }

    public static View inflate(ViewGroup viewGroup, @LayoutRes int layout, boolean attachToRoot) {
        return LayoutInflater.from(viewGroup.getContext()).inflate(layout, viewGroup, attachToRoot);
    }

    public static void inTransaction(FragmentManager fragmentManager, Func<FragmentTransaction, FragmentTransaction> func) {
        fragmentManager.beginTransaction().func().commit();
    }

    public static <VM extends ViewModel> Lazy<VM> viewModelProvider(FragmentActivity fragmentActivity, ViewModelProvider.Factory provider) {
        return new Lazy<>(() -> ViewModelProviders.of(fragmentActivity, provider).get(VM.class));
    }

    public static <VM extends ViewModel> VM viewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return ViewModelProviders.of(fragment, provider).get(VM.class);
    }

    public static <VM extends ViewModel> VM activityViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return ViewModelProviders.of(fragment.getActivity(), provider).get(VM.class);
    }

    public static <VM extends ViewModel> VM parentViewModelProvider(Fragment fragment, ViewModelProvider.Factory provider) {
        return ViewModelProviders.of(fragment.getParentFragment(), provider).get(VM.class);
    }

    public static <T extends Enum<T>> void writeEnum(Parcel parcel, T value) {
        parcel.writeString(value.name());
    }

    public static <T extends Enum<T>> T readEnum(Parcel parcel, Class<T> enumClass) {
        return Enum.valueOf(enumClass, parcel.readString());
    }

    public static <T extends Enum<T>> void putEnum(Bundle bundle, String key, T value) {
        bundle.putString(key, value.name());
    }

    public static <T extends Enum<T>> T getEnum(Bundle bundle, String key, Class<T> enumClass) {
        return Enum.valueOf(enumClass, bundle.getString(key));
    }

    public static <X, T> LiveData<X> map(LiveData<T> liveData, Function<T, X> body) {
        return Transformations.map(liveData, body::apply);
    }

    public static <X, T> LiveData<X> switchMap(LiveData<T> liveData, Function<T, LiveData<X>> body) {
        return Transformations.switchMap(liveData, body::apply);
    }

    public interface Operation<T> {
        T invoke();
    }

    public interface Func<T, R> {
        R apply(T t);
    }

    public static class Lazy<T> {
        private final Operation<T> operation;
        private volatile T value;

        public Lazy(Operation<T> operation) {
            this.operation = operation;
        }

        public T getValue() {
            if (value == null) {
                synchronized (this) {
                    if (value == null) {
                        value = operation.invoke();
                    }
                }
            }
            return value;
        }
    }

    public interface Function<T, R> {
        R apply(T t);
    }
}