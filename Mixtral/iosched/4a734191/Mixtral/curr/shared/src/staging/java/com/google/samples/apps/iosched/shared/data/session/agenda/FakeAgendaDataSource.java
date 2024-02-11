

package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Session;
import com.google.samples.apps.iosched.shared.util.ConferenceDataJsonParser;

public class FakeAgendaDataSource implements AgendaDataSource {
    @Override
    public Session[] getAgenda() {
        return ConferenceDataJsonParser.getAgenda();
    }
}