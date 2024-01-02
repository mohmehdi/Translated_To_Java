package com.squareup.picasso3;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import com.squareup.picasso3.BaseDispatcher.NetworkBroadcastReceiver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BaseDispatcherTest {
  @Mock private Context context;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void nullIntentOnReceiveDoesNothing() {
    BaseDispatcher dispatcher = Mockito.mock(BaseDispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);

    receiver.onReceive(context, null);

    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void nullExtrasOnReceiveConnectivityAreOk() {
    ConnectivityManager connectivityManager = Mockito.mock(ConnectivityManager.class);
    NetworkInfo networkInfo = TestUtils.mockNetworkInfo();
    Mockito.when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    Mockito.when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);
    BaseDispatcher dispatcher = Mockito.mock(BaseDispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);

    receiver.onReceive(context, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

    Mockito.verify(dispatcher).dispatchNetworkStateChange(networkInfo);
  }

  @Test
  public void nullExtrasOnReceiveAirplaneDoesNothing() {
    BaseDispatcher dispatcher = Mockito.mock(BaseDispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);

    receiver.onReceive(context, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));

    Mockito.verifyNoInteractions(dispatcher);
  }

  @Test
  public void correctExtrasOnReceiveAirplaneDispatches() {
    setAndVerifyAirplaneMode(false);
    setAndVerifyAirplaneMode(true);
  }

  private void setAndVerifyAirplaneMode(boolean airplaneOn) {
    BaseDispatcher dispatcher = Mockito.mock(BaseDispatcher.class);
    NetworkBroadcastReceiver receiver = new NetworkBroadcastReceiver(dispatcher);
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra(NetworkBroadcastReceiver.EXTRA_AIRPLANE_STATE, airplaneOn);
    receiver.onReceive(context, intent);
    Mockito.verify(dispatcher).dispatchAirplaneModeChange(airplaneOn);
  }
}