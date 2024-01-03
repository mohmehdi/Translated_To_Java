package io.plaidapp.core.designernews.data;

import io.plaidapp.core.R;
import io.plaidapp.core.data.SourceItem;

public class DesignerNewsSourceItem extends SourceItem {

    public DesignerNewsSourceItem(String id, String key, int sortOrder, String name, boolean active) {
        super(id, key, sortOrder, name, R.drawable.ic_designer_news, active, true);
    }
}

public class DesignerNewsSearchSourceItem extends DesignerNewsSourceItem {

    private static final String SOURCE_DESIGNER_NEWS_POPULAR = "SOURCE_DESIGNER_NEWS_POPULAR";
    private static final String DESIGNER_NEWS_QUERY_PREFIX = "DESIGNER_NEWS_QUERY_";
    private static final int SEARCH_SORT_ORDER = 200;

    private String query;

    public DesignerNewsSearchSourceItem(String query, boolean active) {
        super(DESIGNER_NEWS_QUERY_PREFIX + query, query, SEARCH_SORT_ORDER, "“" + query + "”", active);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}