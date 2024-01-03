package io.plaidapp.core.interfaces;

import android.content.Context;

public interface SearchDataSourceFactoryProvider {

    SearchDataSourceFactory getFactory(Context context);
}