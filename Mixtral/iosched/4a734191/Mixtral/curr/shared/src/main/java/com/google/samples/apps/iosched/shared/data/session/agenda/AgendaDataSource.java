

package com.google.samples.apps.iosched.shared.data.session.agenda;

import com.google.samples.apps.iosched.shared.model.Block;

import java.util.List;

public interface AgendaDataSource {
    List<Block> getAgenda();
}