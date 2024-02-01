package com.squareup.picasso3;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(MockitoJUnitRunner.class)
class BaseDispatcherTest {
  @Mock private Context context;
  @Mock private ConnectivityManager connectivityManager;
  private NetworkInfo networkInfo;

  @Before public void setUp() {
    networkInfo = mock(NetworkInfo.class);
    when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
  }

  @Test public void nullIntentOnReceiveDoesNothing() {
    BaseDispatcher dispatcher = mock(BaseDispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);

    receiver.onReceive(context, null);

    verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test public void nullExtrasOnReceiveConnectivityAreOk() {
    Intent intent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
    receiver.onReceive(context, intent);

    verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test public void nullExtrasOnReceiveAirplaneDoesNothing() {
    receiver.onReceive(context, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));

    verifyNoInteractions(dispatcher);
  }

  @Test public void correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false);
    setAndVerifyAirplaneMode(true);
  }

  private void setAndVerifyAirplaneMode(final boolean airplaneOn) {
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE, airplaneOn);
    shadowOf(context).receiveBroadcast(intent);

    verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }
}