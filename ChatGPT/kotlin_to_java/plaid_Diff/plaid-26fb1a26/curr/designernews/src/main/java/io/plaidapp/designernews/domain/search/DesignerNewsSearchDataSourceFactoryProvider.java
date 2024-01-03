package io.plaidapp.designernews.domain.search;

import android.content.Context;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.interfaces.SearchDataSourceFactoryProvider;
import io.plaidapp.designernews.dagger.DaggerDesignerNewsSearchComponent;
import io.plaidapp.designernews.dagger.DesignerNewsPreferencesModule;

public class DesignerNewsSearchDataSourceFactoryProvider implements SearchDataSourceFactoryProvider {

    @Override
    public SearchDataSourceFactory getFactory(Context context) {
        return DaggerDesignerNewsSearchComponent.builder()
                .designerNewsPreferencesModule(new DesignerNewsPreferencesModule(context))
                .build().factory();
    }
}