package com.google.samples.apps.iosched.shared.data.agenda;

import com.google.samples.apps.iosched.model.Block;

public interface AgendaDataSource {
    List<Block> getAgenda();
}