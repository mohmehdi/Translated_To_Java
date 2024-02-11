

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.samples.apps.iosched.shared.model.Block;
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
import com.google.samples.apps.iosched.ui.schedule.agenda.LoadAgendaUseCase;
import timber.log.Timber;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener {

    private SessionFilters filters;

    public LiveData<Boolean> isLoading;

    public LiveData<List<Tag>> tags;

    public LiveData<String> errorMessage;
    public MutableLiveData<Boolean> errorMessageShown;

    public LiveData<Result<Map<ConferenceDay, List<Session>>>> loadSessionsResult;
    public LiveData<Result<List<Block>>> loadAgendaResult;
    public LiveData<Result<List<Tag>>> loadTagsResult;

    public LiveData<List<Session>> day1Sessions;
    public LiveData<List<Session>> day2Sessions;
    public LiveData<List<Session>> day3Sessions;

    public LiveData<List<Block>> agenda;

    @Inject
    public ScheduleViewModel(
            LoadSessionsByDayUseCase loadSessionsByDayUseCase,
            LoadAgendaUseCase loadAgendaUseCase,
            LoadTagsByCategoryUseCase loadTagsByCategoryUseCase,
            Executor executor) {

        this.filters = new SessionFilters();

        this.loadSessionsByDayUseCase = loadSessionsByDayUseCase;
        this.loadAgendaUseCase = loadAgendaUseCase;
        this.loadTagsByCategoryUseCase = loadTagsByCategoryUseCase;
        this.executor = executor;

        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
        loadAgendaUseCase.invoke(loadAgendaResult);
        loadTagsByCategoryUseCase.invoke(loadTagsResult);

        day1Sessions = map(loadSessionsResult, result -> {
            if (result instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData();
                return data.get(DAY_1);
            } else {
                return null;
            }
        });

        day2Sessions = map(loadSessionsResult, result -> {
            if (result instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData();
                return data.get(DAY_2);
            } else {
                return null;
            }
        });

        day3Sessions = map(loadSessionsResult, result -> {
            if (result instanceof Result.Success) {
                Map<ConferenceDay, List<Session>> data = ((Result.Success<Map<ConferenceDay, List<Session>>>) result).getData();
                return data.get(DAY_3);
            } else {
                return null;
            }
        });

        isLoading = map(loadSessionsResult, result -> {
            if (result instanceof Result.Loading) {
                return true;
            } else {
                return false;
            }
        });

        errorMessage = map(loadSessionsResult, result -> {
            if (result instanceof Result.Error) {
                String errorMessage = ((Result.Error<Map<ConferenceDay, List<Session>>>) result).getException().getMessage();
                return errorMessage;
            } else {
                return "";
            }
        });

        agenda = map(loadAgendaResult, result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<List<Block>>) result).getData();
            } else {
                return null;
            }
        });

        tags = map(loadTagsResult, result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<List<Tag>>) result).getData();
            } else {
                return null;
            }
        });
    }

    public boolean wasErrorMessageShown() {
        Boolean value = errorMessageShown.getValue();
        return value == null ? false : value;
    }

    public void onErrorMessageShown() {
        errorMessageShown.setValue(true);
    }

    public LiveData<List<Session>> getSessionsForDay(ConferenceDay day) {
        if (day == DAY_1) {
            return day1Sessions;
        } else if (day == DAY_2) {
            return day2Sessions;
        } else {
            return day3Sessions;
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