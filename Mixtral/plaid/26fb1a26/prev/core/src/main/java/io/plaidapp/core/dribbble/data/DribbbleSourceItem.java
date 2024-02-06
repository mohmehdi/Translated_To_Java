




package io.plaidapp.core.dribbble.data;

import android.content.res.Resources;
import io.plaidapp.core.R;
import io.plaidapp.core.data.SourceItem;

public class DribbbleSourceItem extends SourceItem {

    public static final String DRIBBBLE_QUERY_PREFIX = "DRIBBBLE_QUERY_";
    private static final int SEARCH_SORT_ORDER = 400;
    
    public DribbbleSourceItem(String query, boolean active) {
        super(query, SEARCH_SORT_ORDER, "'" + query + "'", R.drawable.ic_dribbble, active, true);
        this.active = active;
    }
    

}