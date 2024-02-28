

package com.google.samples.apps.iosched.ui.agenda;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.domain.agenda.LoadAgendaUseCase;
import com.google.samples.apps.iosched.shared.domain.invoke;
import com.google.samples.apps.iosched.shared.domain.settings.GetTimeZoneUseCase;
import com.google.samples.apps.iosched.shared.result.Result;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.shared.util.map;
import org.threeten.bp.ZoneId;
import javax.inject.Inject;

public class AgendaViewModel extends ViewModel {

    private final MutableLiveData<Result<List<Block>>> loadAgendaResult = new MutableLiveData<>();
    public final LiveData<List<Block>> agenda;

    private final MutableLiveData<Result<Boolean>> preferConferenceTimeZoneResult = new MutableLiveData<>();
    public final LiveData<ZoneId> timeZoneId;

    @Inject
    public AgendaViewModel(LoadAgendaUseCase loadAgendaUseCase, GetTimeZoneUseCase getTimeZoneUseCase) {
        agenda = map(loadAgendaResult, result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<List<Block>>) result).getData();
            } else {
                return null;
            }
        });

        LiveData<Boolean> showInConferenceTimeZone = map(preferConferenceTimeZoneResult, result -> {
            if (result instanceof Result.Success) {
                return ((Result.Success<Boolean>) result).getData();
            } else {
                return true;
            }
        });

        timeZoneId = map(showInConferenceTimeZone, inConferenceTimeZone -> {
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