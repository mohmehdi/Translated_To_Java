package io.plaidapp.dribbble.domain.search;

import android.content.Context;
import io.plaidapp.core.interfaces.SearchDataSourceFactory;
import io.plaidapp.core.interfaces.SearchDataSourceFactoryProvider;
import io.plaidapp.dribbble.dagger.DaggerDribbbleSearchComponent;
import io.plaidapp.ui.PlaidApplication;

public class DribbbleSearchDataSourceFactoryProvider implements SearchDataSourceFactoryProvider {

    @Override
    public SearchDataSourceFactory getFactory(Context context) {
        return DaggerDribbbleSearchComponent.builder()
                .coreComponent(PlaidApplication.Companion.coreComponent(context))
                .build().factory();
    }
}