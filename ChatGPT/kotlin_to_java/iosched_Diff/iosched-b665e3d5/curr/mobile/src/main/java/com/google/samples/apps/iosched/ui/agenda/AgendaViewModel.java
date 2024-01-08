package com.google.samples.apps.iosched.ui.agenda;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.shared.util.map;
import org.threeten.bp.ZoneId;

public class AgendaViewModel extends ViewModel {

    private MutableLiveData<Result<List<Block>>> loadAgendaResult = new MutableLiveData<>();
    public LiveData<List<Block>> agenda;

    private MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult = new MutableLiveData<>();
    public LiveData<ZoneId> timeZoneId;

    public AgendaViewModel(LoadAgendaUseCase loadAgendaUseCase, GetTimeZoneUseCase getTimeZoneUseCase) {
        agenda = loadAgendaResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<List<Block>>) result).getData();
            } else {
                return emptyList();
            }
        });

        LiveData<Boolean> showInConferenceTimeZone = preferConferenceTimeZoneResult.map(result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<Boolean>) result).getData();
            } else {
                return true;
            }
        });
        timeZoneId = showInConferenceTimeZone.map(inConferenceTimeZone -> {
            if (inConferenceTimeZone) {
                return TimeUtils.CONFERENCE_TIMEZONE;
            } else {
                return ZoneId.systemDefault();
            }
        });

        getTimeZoneUseCase.invoke(preferConferenceTimeZoneResult);
        loadAgendaUseCase.invoke(loadAgendaResult);
    }
}