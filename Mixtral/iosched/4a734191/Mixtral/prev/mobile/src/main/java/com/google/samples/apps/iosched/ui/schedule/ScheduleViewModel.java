

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.map;
import timber.log.Timber;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Singleton
public class ScheduleViewModel extends ViewModel implements ScheduleEventListener {

    private SessionFilters filters;

    private MutableLiveData<Boolean> isLoading;
    private MutableLiveData<String> errorMessage;
    private MutableLiveData<List<Tag>> tags;
    private MutableLiveData<Boolean> errorMessageShown;

    private MutableLiveData<Result<Map<ConferenceDay, List<Session>>>> loadSessionsResult;
    private MutableLiveData<Result<List<Tag>>> loadTagsResult;

    private LiveData<List<Session>> day1Sessions;
    private LiveData<List<Session>> day2Sessions;
    private LiveData<List<Session>> day3Sessions;

    @Inject
    public ScheduleViewModel(
            @NonNull LoadSessionsByDayUseCase loadSessionsByDayUseCase,
            @NonNull LoadTagsByCategoryUseCase loadTagsByCategoryUseCase
    ) {
        this.loadSessionsByDayUseCase = loadSessionsByDayUseCase;
        this.loadTagsByCategoryUseCase = loadTagsByCategoryUseCase;

        filters = new SessionFilters();

        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
        loadTagsByCategoryUseCase.invoke(loadTagsResult);

        day1Sessions = new Transformations.Map<>(loadSessionsResult, input -> {
            if (input instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) input).getData();
                return data.get(DAY_1);
            }
            return Collections.emptyList();
        });

        day2Sessions = new Transformations.Map<>(loadSessionsResult, input -> {
            if (input instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) input).getData();
                return data.get(DAY_2);
            }
            return Collections.emptyList();
        });

        day3Sessions = new Transformations.Map<>(loadSessionsResult, input -> {
            if (input instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) input).getData();
                return data.get(DAY_3);
            }
            return Collections.emptyList();
        });

        isLoading = new Transformations.Map<>(loadSessionsResult, input -> {
            if (input instanceof Result.Loading) {
                return true;
            }
            return false;
        });

        errorMessage = new Transformations.Map<>(loadSessionsResult, input -> {
            if (input instanceof Result.Error) {
                String errorMessage = ((Result.Error<Map<ConferenceDay, List<Session>>>) input).getException().getMessage();
                if (errorMessageShown.getValue() == null) {
                    errorMessageShown.postValue(false);
                }
                return errorMessage;
            }
            return "";
        });

        tags = new Transformations.Map<>(loadTagsResult, input -> {
            if (input instanceof Result.Success) {
                return ((Result.Success<List<Tag>>) input).getData();
            }
            return Collections.emptyList();
        });
    }

    public boolean wasErrorMessageShown() {
        Boolean value = errorMessageShown.getValue();
        return value == null ? false : value;
    }

    public void onErrorMessageShown() {
        errorMessageShown.postValue(true);
    }

    public LiveData<List<Session>> getSessionsForDay(ConferenceDay day) {
        switch (day) {
            case DAY_1:
                return day1Sessions;
            case DAY_2:
                return day2Sessions;
            case DAY_3:
                return day3Sessions;
            default:
                throw new IllegalStateException("Invalid conference day: " + day);
        }
    }

    @Override
    public void openSessionDetail(String id) {
        Timber.d("TODO: Open session detail for id: " + id);
    }

    @Override
    public void toggleFilter(Tag tag, boolean enabled) {
        if (enabled) {
            filters.add(tag);
        } else {
            filters.remove(tag);
        }
        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
    }

    @Override
    public void clearFilters() {
        filters.clearAll();
        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
    }
}
public interface ScheduleEventListener {
    void openSessionDetail(String id);
    void toggleFilter(Tag tag, boolean enabled);
    void clearFilters();
}