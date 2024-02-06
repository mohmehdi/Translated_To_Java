




package io.plaidapp.core.designernews.data;

import android.R;
import io.plaidapp.core.data.SourceItem;

public class DesignerNewsSourceItem extends SourceItem {

    public DesignerNewsSourceItem(String id, String key, int sortOrder, String name,
                                  int iconResId, boolean active, boolean enabled) {
        super(id, key, sortOrder, name, iconResId, active, enabled);
    }
}

class DesignerNewsSearchSourceItem extends DesignerNewsSourceItem {

    public static final String SOURCE_DESIGNER_NEWS_POPULAR = "SOURCE_DESIGNER_NEWS_POPULAR";
    public static final String DESIGNER_NEWS_QUERY_PREFIX = "DESIGNER_NEWS_QUERY_";
    private static final int SEARCH_SORT_ORDER = 200;

    private final String query;

    public DesignerNewsSearchSourceItem(String query, boolean active) {
        super(DESIGNER_NEWS_QUERY_PREFIX + query, query, SEARCH_SORT_ORDER,
                "“" + query + "”", active, true);
        this.query = query;
    }
}