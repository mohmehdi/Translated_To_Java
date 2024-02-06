




package io.plaidapp.search.domain;

import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Provider;
import javax.inject.Inject;

public class SearchDataSourceFactoriesRegistry {
    private final Set<SearchDataSourceFactory> _dataSourceFactories = new HashSet<>();

    @Inject
    public SearchDataSourceFactoriesRegistry(Provider<Set<SearchDataSourceFactory>> defaultFactories) {
        Set<SearchDataSourceFactory> defaultFactoriesSet = defaultFactories.get();
        if (defaultFactoriesSet != null) {
            _dataSourceFactories.addAll(defaultFactoriesSet);
        }
    }


    public void add(SearchDataSourceFactory dataSourceFactory) {
        _dataSourceFactories.add(dataSourceFactory);
    }

    public void remove(SearchDataSourceFactory dataSourceFactory) {
        _dataSourceFactories.remove(dataSourceFactory);
    }
}