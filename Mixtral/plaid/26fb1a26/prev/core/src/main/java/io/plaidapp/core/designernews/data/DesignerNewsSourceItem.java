




package io.plaidapp.core.designernews.data;

import android.R;
import io.plaidapp.core.data.SourceItem;

public class DesignerNewsSourceItem extends SourceItem {

    public DesignerNewsSourceItem(String key, int sortOrder, String name, int iconResId,
                                  boolean active, boolean isRemote) {
        super(key, sortOrder, name, iconResId, active, isRemote);
    }
}

class DesignerNewsSearchSource extends DesignerNewsSourceItem {

    public static final String SOURCE_DESIGNER_NEWS_POPULAR = "SOURCE_DESIGNER_NEWS_POPULAR";
    public static final String DESIGNER_NEWS_QUERY_PREFIX = "DESIGNER_NEWS_QUERY_";
    private static final int SEARCH_SORT_ORDER = 200;

    private final String query;

    public DesignerNewsSearchSource(String query, boolean active) {
        super(DESIGNER_NEWS_QUERY_PREFIX + query, SEARCH_SORT_ORDER,
                "”" + query + "”", R.drawable.ic_designer_news, active, true);
        this.query = query;
    }
}