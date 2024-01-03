package io.plaidapp.search.domain;

import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.Set;

public class SearchDataSourceFactoriesRegistry {

    private Set<SearchDataSourceFactory> _dataSourceFactories = new HashSet<>();

    @Inject
    public SearchDataSourceFactoriesRegistry(Provider<Set<SearchDataSourceFactory>> defaultFactories) {
        defaultFactories.get().apply(_dataSourceFactories::addAll);
    }

    public Set<SearchDataSourceFactory> getDataSourceFactories() {
        return _dataSourceFactories;
    }

    public void add(SearchDataSourceFactory dataSourceFactory) {
        _dataSourceFactories.add(dataSourceFactory);
    }

    public void remove(SearchDataSourceFactory dataSourceFactory) {
        _dataSourceFactories.remove(dataSourceFactory);
    }
}