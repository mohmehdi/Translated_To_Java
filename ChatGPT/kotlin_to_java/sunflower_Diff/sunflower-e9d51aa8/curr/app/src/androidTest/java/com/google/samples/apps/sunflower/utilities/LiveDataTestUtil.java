import android.arch.lifecycle.LiveData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LiveDataTestUtil {

    public static <T> T getValue(LiveData<T> liveData) throws InterruptedException {
        Object[] data = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        liveData.observeForever(o -> {
            data[0] = o;
            latch.countDown();
        });
        latch.await(2, TimeUnit.SECONDS);

        return (T) data[0];
    }
}