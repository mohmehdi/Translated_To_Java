package io.plaidapp.designernews.domain.search;

import io.plaidapp.core.designernews.data.DesignerNewsSearchSource;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import io.plaidapp.core.interfaces.PlaidDataSource;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;

public class DesignerNewsSearchDataSourceFactory implements SearchDataSourceFactory {

    private StoriesRepository repository;

    public DesignerNewsSearchDataSourceFactory(StoriesRepository repository) {
        this.repository = repository;
    }

    @Override
    public PlaidDataSource create(String query) {
        DesignerNewsSearchSource sourceItem = new DesignerNewsSearchSource(query);
        return new DesignerNewsDataSource(sourceItem, repository);
    }
}