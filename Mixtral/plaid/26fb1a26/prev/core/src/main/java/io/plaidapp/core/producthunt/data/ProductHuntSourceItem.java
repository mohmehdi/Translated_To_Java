




package io.plaidapp.core.producthunt.data;

import android.content.res.Resources;
import io.plaidapp.core.R;
import io.plaidapp.core.data.SourceItem;

public class ProductHuntSourceItem extends SourceItem {
    public ProductHuntSourceItem(String name) {
        super(
            "SOURCE_PRODUCT_HUNT",
            500,
            name,
            R.drawable.ic_product_hunt,
            false
        );
    }

        public static final String SOURCE_PRODUCT_HUNT = "SOURCE_PRODUCT_HUNT";
}