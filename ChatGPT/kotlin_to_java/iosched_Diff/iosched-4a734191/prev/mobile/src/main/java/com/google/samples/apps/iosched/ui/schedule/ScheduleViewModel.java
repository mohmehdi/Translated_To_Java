package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.usecases.LoadSessionsByDayUseCase;
import com.google.samples.apps.iosched.shared.usecases.LoadTagsByCategoryUseCase;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_1;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_2;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_3;
import com.google.samples.apps.iosched.shared.util.map;
import timber.log.Timber;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener {

    private SessionFilters filters = new SessionFilters();

    private LiveData<Boolean> isLoading;

    private LiveData<List<Tag>> tags;

    private LiveData<String> errorMessage;
    private MutableLiveData<Boolean> errorMessageShown = new MutableLiveData<>();

    private MutableLiveData<Result<Map<ConferenceDay, List<Session>>>> loadSessionsResult = new MutableLiveData<>();
    private MutableLiveData<Result<List<Tag>>> loadTagsResult = new MutableLiveData<>();

    private LiveData<List<Session>> day1Sessions;
    private LiveData<List<Session>> day2Sessions;
    private LiveData<List<Session>> day3Sessions;

    @Inject
    public ScheduleViewModel(LoadSessionsByDayUseCase loadSessionsByDayUseCase, LoadTagsByCategoryUseCase loadTagsByCategoryUseCase) {
        this.loadSessionsByDayUseCase = loadSessionsByDayUseCase;
        this.loadTagsByCategoryUseCase = loadTagsByCategoryUseCase;

        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
        loadTagsByCategoryUseCase.invoke(loadTagsResult);

        day1Sessions = loadSessionsResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData().get(DAY_1);
            } else {
                return emptyList();
            }
        });
        day2Sessions = loadSessionsResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData().get(DAY_2);
            } else {
                return emptyList();
            }
        });
        day3Sessions = loadSessionsResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData().get(DAY_3);
            } else {
                return emptyList();
            }
        });

        isLoading = loadSessionsResult.map(result -> result == Result.Loading);

        errorMessage = loadSessionsResult.map(result -> {
            errorMessageShown.setValue(false);
            if (result instanceof Result.Error) {
                return ((Result.Error) result).getException().getMessage();
            } else {
                return "";
            }
        });

        tags = loadTagsResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<List<Tag>>) result).getData();
            } else {
                return emptyList();
            }
        });
    }

    public boolean wasErrorMessageShown() {
        return errorMessageShown.getValue() != null ? errorMessageShown.getValue() : false;
    }

    public void onErrorMessageShown() {
        errorMessageShown.setValue(true);
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
                return null;
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

interface ScheduleEventListener {
    void openSessionDetail(String id);
    void toggleFilter(Tag tag, boolean enabled);
    void clearFilters();
}