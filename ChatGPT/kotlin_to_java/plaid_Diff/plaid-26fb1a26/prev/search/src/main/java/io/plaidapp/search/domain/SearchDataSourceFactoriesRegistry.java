package io.plaidapp.search.domain;

import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.Set;

public class SearchDataSourceFactoriesRegistry {

    private Set<SearchDataSourceFactory> _dataSourceFactories;

    @Inject
    public SearchDataSourceFactoriesRegistry(Provider<Set<SearchDataSourceFactory>> defaultFactories) {
        _dataSourceFactories = new HashSet<>();
        defaultFactories.get().apply(_dataSourceFactories::addAll);
    }

    public Set<SearchDataSourceFactory> getDataSourceFactories() {
        return _dataSourceFactories;
    }

    public void add(SearchDataSourceFactory dataSourceFactory) {
        if (_dataSourceFactories.contains(dataSourceFactory)) return;
        _dataSourceFactories.add(dataSourceFactory);
    }

    public void remove(SearchDataSourceFactory dataSourceFactory) {
        if (_dataSourceFactories.contains(dataSourceFactory)) return;
        _dataSourceFactories.remove(dataSourceFactory);
    }
}