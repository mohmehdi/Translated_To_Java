




package io.plaidapp.core.dribbble.data;

import android.content.res.Resources;

import io.plaidapp.core.R;
import io.plaidapp.core.data.SourceItem;

public class DribbbleSourceItem extends SourceItem {
    public String query;
    public boolean active;

    public DribbbleSourceItem(String query, boolean active) {
        super(
            "DRIBBBLE_QUERY_" + query,
            query,
            400,
            String.format("“%s”", query),
            R.drawable.ic_dribbble,
            active,
            true
        );
        this.query = query;
        this.active = active;
    }

        public static final String DRIBBBLE_QUERY_PREFIX = "DRIBBBLE_QUERY_";
        public static final int SEARCH_SORT_ORDER = 400;
}