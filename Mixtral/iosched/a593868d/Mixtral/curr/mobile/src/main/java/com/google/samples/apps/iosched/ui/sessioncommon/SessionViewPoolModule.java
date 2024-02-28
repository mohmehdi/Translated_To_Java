package com.google.samples.apps.iosched.ui.sessioncommon;

import android.support.v7.widget.RecyclerView;
import dagger.Module;
import dagger.Provides;
import dagger.Scope;
import javax.inject.Named;

@Module
public class SessionViewPoolModule {

  @SessionScoped
  @Provides
  @Named("sessionViewPool")
  public RecyclerView.RecycledViewPool providesSessionViewPool() {
    return new RecyclerView.RecycledViewPool();
  }

  @SessionScoped
  @Provides
  @Named("tagViewPool")
  public RecyclerView.RecycledViewPool providesTagViewPool() {
    return new RecyclerView.RecycledViewPool();
  }
}

@Scope
@Retention(SOURCE)
public @interface SessionScoped {
}
