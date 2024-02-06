




package io.plaidapp.dribbble.dagger;

import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.dagger.scope.FeatureScope;
import io.plaidapp.core.dribbble.data.ShotsRepository;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.dribbble.domain.search.DribbbleSearchDataSourceFactory;

@Module
public class SearchDataModule {

    @Provides
    @FeatureScope
    public SearchDataSourceFactory searchDataSourceFactory(
            ShotsRepository repository) {
        return new DribbbleSearchDataSourceFactory(repository);
    }
}