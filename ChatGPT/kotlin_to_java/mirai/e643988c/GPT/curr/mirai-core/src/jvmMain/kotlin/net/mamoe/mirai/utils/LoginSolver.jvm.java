
package net.mamoe.mirai.utils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import kotlinx.coroutines.CoroutineName;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.channels.ByteWriteChannel;
import kotlinx.coroutines.channels.ClosedWriteChannelException;
import kotlinx.coroutines.io.jvm.nio.copyTo;
import kotlinx.coroutines.withContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.MiraiExperimentalAPI;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.network.NoStandardInputForCaptchaException;
import kotlin.coroutines.CoroutineContext;
import kotlin.math.min;

@MiraiExperimentalAPI
public class DefaultLoginSolver extends LoginSolver {
    public final LoginSolver delegate;

    public DefaultLoginSolver(suspend () -> String input) {
        this(input, null);
    }

    public DefaultLoginSolver(suspend () -> String input, MiraiLogger overrideLogger) {
        if (WindowHelperJvm.isDesktopSupported()) {
            this.delegate = SwingSolver.INSTANCE;
        } else {
            this.delegate = new StandardCharImageLoginSolver(input, overrideLogger);
        }
    }

    @Override
    public suspend String onSolvePicCaptcha(Bot bot, byte[] data) {
        return delegate.onSolvePicCaptcha(bot, data);
    }

    @Override
    public suspend String onSolveSliderCaptcha(Bot bot, String url) {
        return delegate.onSolveSliderCaptcha(bot, url);
    }

    @Override
    public suspend String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        return delegate.onSolveUnsafeDeviceLoginVerify(bot, url);
    }
}

@MiraiExperimentalAPI
class StandardCharImageLoginSolver extends LoginSolver {
    private final MiraiLogger overrideLogger;
    private final suspend () -> String input;

    public StandardCharImageLoginSolver(suspend () -> String input) {
        this(input, null);
    }

    public StandardCharImageLoginSolver(suspend () -> String input, MiraiLogger overrideLogger) {
        this.input = () -> {
            try {
                return withContext(Dispatchers.IO, input);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read input", e);
            }
        };
        this.overrideLogger = overrideLogger;
    }

    private final Lock loginSolverLock = new ReentrantLock();

    @Override
    public suspend String onSolvePicCaptcha(Bot bot, byte[] data) {
        MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
        File tempFile = null;
        try {
            tempFile = File.createTempFile("", ".png");
            tempFile.deleteOnExit();
            try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                raf.write(data);
            } catch (IOException e) {
                logger.info("Unable to write captcha image file: " + e.getMessage());
            }

            BufferedImage img;
            try {
                img = ImageIO.read(tempFile);
                if (img == null) {
                    logger.info("Unable to create image from file");
                } else {
                    logger.info("\n" + createCharImg(img));
                }
            } catch (IOException e) {
                logger.info("Error creating image: " + e.getMessage());
            }

            logger.info("Please enter the 4-letter captcha. Press Enter to submit.");
            String input = this.input.invoke();
            if (input.isEmpty() || input.length() != 4) {
                return null;
            }
            logger.info("Submitting [" + input + "]...");
            return input;
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                logger.info("Failed to delete temp file: " + tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    public suspend String onSolveSliderCaptcha(Bot bot, String url) {
        MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
        logger.info("Slider captcha required");
        logger.info("Open the following URL in any browser and complete the captcha.");
        logger.info("Press Enter after completing.");
        logger.info(url);
        String input = this.input.invoke();
        logger.info("Submitting...");
        return input;
    }

    @Override
    public suspend String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
        logger.info("Account safety verification required");
        logger.info("This account has issues such as [device lock]/[unusual login location]/[unusual device login]");
        logger.info("Complete the account verification by opening the link in the QQ browser, and press Enter after successful verification.");
        logger.info("This step will be optimized in future versions.");
        logger.info(url);
        String input = this.input.invoke();
        logger.info("Submitting...");
        return input;
    }

    

    private int gray(int rgb) {
        int r = (rgb & 0xff0000) >> 16;
        int g = (rgb & 0x00ff00) >> 8;
        int b = rgb & 0x0000ff;
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }

    private boolean grayCompare(int g1, int g2) {
        return min(g1, g2) / (double) Math.max(g1, g2) >= ignoreRate;
    }
}

public abstract class LoginSolver {
    public abstract suspend String onSolvePicCaptcha(Bot bot, byte[] data);
    public abstract suspend String onSolveSliderCaptcha(Bot bot, String url);
    public abstract suspend String onSolveUnsafeDeviceLoginVerify(Bot bot, String url);

    public static final LoginSolver Default = new DefaultLoginSolver(() -> {
        try {
            String input = System.console().readLine();
            if (input == null) {
                throw new NoStandardInputForCaptchaException(null);
            }
            return input;
        } catch (Exception e) {
            throw new NoStandardInputForCaptchaException(e);
        }
    });

    public static Function1<Context, DeviceInfo> getFileBasedDeviceInfoSupplier(String filename) {
        return (Function1<Context, DeviceInfo>) (context -> {
            return new File(filename).loadAsDeviceInfo(json, context);
        });
    }

    private static ByteWriteChannel writeChannel(File file, CoroutineContext coroutineContext) {
        return GlobalScope.reader(new CoroutineContext() {
            @Override
            public <R> R fold(R initial, Function2<? super R, ? super CoroutineContext.Element, ? extends R> operation) {
                return null;
            }

            @Override
            public <E extends CoroutineContext.Element> E get(CoroutineContext.Key<E> key) {
                return null;
            }

            @Override
            public CoroutineContext minusKey(CoroutineContext.Key<?> key) {
                return null;
            }

            @Override
            public CoroutineContext plus(CoroutineContext context) {
                return null;
            }
        }, true, (Continuation<? super ChannelKt.ReadChannel>) IntrinsicsKt.createCoroutineUnintercepted(new Function2() {
            @Override
            public Object invoke(Object o, Object o2) {
                return null;
            }
        }, IntrinsicsKt.getCOROUTINE_SUSPENDED()));
    }

    private static Mutex loginSolverLock = new Mutex();

    private static String createCharImg(BufferedImage image, int outputWidth, double ignoreRate) {
        int newHeight = (int) (image.getHeight() * ((double) outputWidth / image.getWidth()));
        Image tmp = image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        Function2<Integer, Integer, Boolean> grayCompare = (g1, g2) -> {
            return Math.min(g1, g2) / (double) Math.max(g1, g2) >= ignoreRate;
        };

        int background = gray(scaledImage.getRGB(0, 0));

        StringBuilder result = new StringBuilder();
        CopyOnWriteArrayList<StringBuilder> lines = new CopyOnWriteArrayList<>();

        int minXPos = outputWidth;
        int maxXPos = 0;

        for (int y = 0; y < scaledImage.getHeight(); y++) {
            StringBuilder builderLine = new StringBuilder();
            for (int x = 0; x < scaledImage.getWidth(); x++) {
                int gray = gray(scaledImage.getRGB(x, y));
                if (grayCompare.invoke(gray, background)) {
                    builderLine.append(" ");
                } else {
                    builderLine.append("#");
                    if (x < minXPos) {
                        minXPos = x;
                    }
                    if (x > maxXPos) {
                        maxXPos = x;
                    }
                }
            }
            if (!builderLine.toString().isBlank()) {
                lines.add(builderLine);
            }
        }
        for (StringBuilder line : lines) {
            result.append(line.substring(minXPos, maxXPos)).append("\n");
        }

        return result.toString();
    }

    private static int gray(int rgb) {
        int r = (rgb & 0xff0000) >> 16;
        int g = (rgb & 0x00ff00) >> 8;
        int b = (rgb & 0x0000ff);
        return (r * 30 + g * 59 + b * 11 + 50) / 100;
    }
}