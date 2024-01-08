package com.google.samples.apps.iosched.shared.domain.agenda;

import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.data.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.domain.UseCase;

import javax.inject.Inject;

public class LoadAgendaUseCase extends UseCase<Unit, List<Block>> {

    private AgendaRepository repository;

    @Inject
    public LoadAgendaUseCase(AgendaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Block> execute(Unit parameters) {
        return repository.getAgenda();
    }
}