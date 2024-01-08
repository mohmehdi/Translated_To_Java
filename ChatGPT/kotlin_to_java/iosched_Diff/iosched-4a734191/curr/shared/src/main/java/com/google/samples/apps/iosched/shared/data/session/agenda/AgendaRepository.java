package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Block;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AgendaRepository {

    private AgendaDataSource dataSource;

    @Inject
    public AgendaRepository(AgendaDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Block> getAgenda() {
        return dataSource.getAgenda();
    }
}