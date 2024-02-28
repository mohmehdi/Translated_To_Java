

package com.google.samples.apps.iosched.shared.data.agenda;

import com.google.samples.apps.iosched.model.Block;

import java.util.List;

public interface AgendaDataSource {
    List<Block> getAgenda();
}