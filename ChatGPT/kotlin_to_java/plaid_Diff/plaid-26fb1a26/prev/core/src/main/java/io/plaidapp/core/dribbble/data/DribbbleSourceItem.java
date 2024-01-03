package io.plaidapp.core.dribbble.data;

import io.plaidapp.core.R;
import io.plaidapp.core.data.SourceItem;

public class DribbbleSourceItem extends SourceItem {
    private String query;
    private boolean active;

    public DribbbleSourceItem(String query) {
        super(query, SEARCH_SORT_ORDER, "“" + query + "”", R.drawable.ic_dribbble, true, true);
        this.query = query;
        this.active = true;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public static final String DRIBBBLE_QUERY_PREFIX = "DRIBBBLE_QUERY_";
    private static final int SEARCH_SORT_ORDER = 400;
}