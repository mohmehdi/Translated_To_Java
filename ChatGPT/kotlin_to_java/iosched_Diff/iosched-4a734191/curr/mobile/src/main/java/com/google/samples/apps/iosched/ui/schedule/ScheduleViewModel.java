package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.model.Tag;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.usecases.invoke;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_1;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_2;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_3;
import com.google.samples.apps.iosched.shared.util.map;
import com.google.samples.apps.iosched.ui.schedule.agenda.LoadAgendaUseCase;
import timber.log.Timber;
import javax.inject.Inject;

public class ScheduleViewModel extends ViewModel implements ScheduleEventListener {

    private LoadSessionsByDayUseCase loadSessionsByDayUseCase;
    private LoadAgendaUseCase loadAgendaUseCase;
    private LoadTagsByCategoryUseCase loadTagsByCategoryUseCase;

    private SessionFilters filters;

    private LiveData<Boolean> isLoading;

    private LiveData<List<Tag>> tags;

    private LiveData<String> errorMessage;
    private MutableLiveData<Boolean> errorMessageShown;

    private MutableLiveData<Result<Map<ConferenceDay, List<Session>>>> loadSessionsResult;
    private MutableLiveData<Result<List<Block>>> loadAgendaResult;
    private MutableLiveData<Result<List<Tag>>> loadTagsResult;

    private LiveData<List<Session>> day1Sessions;
    private LiveData<List<Session>> day2Sessions;
    private LiveData<List<Session>> day3Sessions;

    private LiveData<List<Block>> agenda;

    @Inject
    public ScheduleViewModel(LoadSessionsByDayUseCase loadSessionsByDayUseCase,
                             LoadAgendaUseCase loadAgendaUseCase,
                             LoadTagsByCategoryUseCase loadTagsByCategoryUseCase) {
        this.loadSessionsByDayUseCase = loadSessionsByDayUseCase;
        this.loadAgendaUseCase = loadAgendaUseCase;
        this.loadTagsByCategoryUseCase = loadTagsByCategoryUseCase;

        filters = new SessionFilters();

        loadSessionsResult = new MutableLiveData<>();
        loadAgendaResult = new MutableLiveData<>();
        loadTagsResult = new MutableLiveData<>();

        day1Sessions = new MutableLiveData<>();
        day2Sessions = new MutableLiveData<>();
        day3Sessions = new MutableLiveData<>();

        isLoading = loadSessionsResult.map(result -> result == Result.Loading);

        errorMessage = loadSessionsResult.map(result -> {
            errorMessageShown.setValue(false);
            return (result instanceof Result.Error) ? ((Result.Error) result).getException().getMessage() : "";
        });

        agenda = loadAgendaResult.map(result -> (result instanceof Result.Success) ? ((Result.Success) result).getData() : new ArrayList<>());

        tags = loadTagsResult.map(result -> (result instanceof Result.Success) ? ((Result.Success) result).getData() : new ArrayList<>());

        loadSessionsByDayUseCase.invoke(filters, loadSessionsResult);
        loadAgendaUseCase.invoke(loadAgendaResult);
        loadTagsByCategoryUseCase.invoke(loadTagsResult);
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