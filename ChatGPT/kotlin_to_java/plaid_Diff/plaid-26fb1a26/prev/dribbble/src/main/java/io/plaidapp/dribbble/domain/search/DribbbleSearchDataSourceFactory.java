package io.plaidapp.dribbble.domain.search;

import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;

public class DribbbleSearchDataSourceFactory implements SearchDataSourceFactory {

    private ShotsRepository repository;

    public DribbbleSearchDataSourceFactory(ShotsRepository repository) {
        this.repository = repository;
    }

    @Override
    public PlaidDataSource create(String query) {
        DribbbleSourceItem sourceItem = new DribbbleSourceItem(query);
        return new DribbbleDataSource(sourceItem, repository);
    }
}