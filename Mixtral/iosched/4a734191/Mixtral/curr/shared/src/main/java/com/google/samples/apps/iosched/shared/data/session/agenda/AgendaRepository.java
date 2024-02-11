

package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class AgendaRepository {

    private final AgendaDataSource dataSource;

    @Inject
    public AgendaRepository(AgendaDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Block> getAgenda() {
        return dataSource.getAgenda();
    }
}