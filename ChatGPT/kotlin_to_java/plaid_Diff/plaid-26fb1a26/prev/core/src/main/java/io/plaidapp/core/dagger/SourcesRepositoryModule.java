package io.plaidapp.core.dagger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.ArrayList;

import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.R;
import io.plaidapp.core.dagger.scope.FeatureScope;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.data.SourceItem;
import io.plaidapp.core.data.prefs.SourcesLocalDataSource;
import io.plaidapp.core.data.prefs.SourcesRepository;
import io.plaidapp.core.designernews.data.DesignerNewsSearchSource.Companion.SOURCE_DESIGNER_NEWS_POPULAR;
import io.plaidapp.core.designernews.data.DesignerNewsSourceItem;
import io.plaidapp.core.dribbble.data.DribbbleSourceItem;
import io.plaidapp.core.producthunt.data.ProductHuntSourceItem;

@Module
public class SourcesRepositoryModule {

    @Provides
    @FeatureScope
    public SourcesRepository provideSourceRepository(
        Context context,
        CoroutinesDispatcherProvider dispatcherProvider
    ) {
        List<SourceItem> defaultSources = provideDefaultSources(context);
        SharedPreferences sharedPrefs = context.getSharedPreferences(SOURCES_PREF, Context.MODE_PRIVATE);
        SourcesLocalDataSource localDataSource = new SourcesLocalDataSource(sharedPrefs);
        return SourcesRepository.getInstance(defaultSources, localDataSource, dispatcherProvider);
    }

    private List<SourceItem> provideDefaultSources(Context context) {
        String defaultDesignerNewsSourceName = context.getString(R.string.source_designer_news_popular);
        String defaultDribbbleSourceName = context.getString(R.string.source_dribbble_search_material_design);
        String defaultProductHuntSourceName = context.getString(R.string.source_product_hunt);

        List<SourceItem> defaultSources = new ArrayList<>();
        defaultSources.add(
            new DesignerNewsSourceItem(
                SOURCE_DESIGNER_NEWS_POPULAR,
                100,
                defaultDesignerNewsSourceName,
                true)
        );
        
        defaultSources.add(new DribbbleSourceItem(defaultDribbbleSourceName, true));
        
        defaultSources.add(new ProductHuntSourceItem(defaultProductHuntSourceName));
        return defaultSources;
    }

    private static final String SOURCES_PREF = "SOURCES_PREF";
}