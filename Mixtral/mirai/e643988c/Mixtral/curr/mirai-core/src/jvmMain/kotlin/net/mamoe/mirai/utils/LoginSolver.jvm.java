
package net.mamoe.mirai.utils;

import kotlin.jvm.Suspend;
import kotlin.jvm.functions.SuspendFunction1;
import net.mamoe.mirai.contact.Bot;
import net.mamoe.mirai.utils.MiraiLogger;

@net.mamoe.mirai.contact.contact.UserConfig.MiraiExperimentalAPI
public class DefaultLoginSolver implements LoginSolver {
    private final SuspendFunction1<String, String> input;
    private final MiraiLogger overrideLogger;
    private final LoginSolver delegate;

    public DefaultLoginSolver(SuspendFunction1<String, String> input, MiraiLogger overrideLogger) {
        this.input = input;
        this.overrideLogger = overrideLogger;
        this.delegate = WindowHelperJvm.isDesktopSupported()
                ? new SwingSolver()
                : new StandardCharImageLoginSolver(input, overrideLogger);
    }

    public DefaultLoginSolver(SuspendFunction1<String, String> input) {
        this(input, null);
    }

    @Suspend
    @Override
    public String onSolvePicCaptcha(Bot bot, byte[] data) {
        return delegate.onSolvePicCaptcha(bot, data);
    }

    @Suspend
    @Override
    public String onSolveSliderCaptcha(Bot bot, String url) {
        return delegate.onSolveSliderCaptcha(bot, url);
    }

    @Suspend
    @Override
    public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        return delegate.onSolveUnsafeDeviceLoginVerify(bot, url);
    }
}

@MiraiExperimentalAPI
public class StandardCharImageLoginSolver extends LoginSolver {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final suspendInput input;
    private final MiraiLogger overrideLogger;
    private final ReentrantLock loginSolverLock = new ReentrantLock();

    public StandardCharImageLoginSolver(SuspendInput input, MiraiLogger overrideLogger) {
        this.input = input;
        this.overrideLogger = overrideLogger;
    }

    public StandardCharImageLoginSolver(SuspendInput input) {
        this(input, null);
    }

    @Override
    public String onSolvePicCaptcha(Bot bot, byte[] data) throws IOException {
        return loginSolverLock.withLock(() -> {
            MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
            File tempFile = File.createTempFile("temp", ".png");
            tempFile.deleteOnExit();

            executor.submit(() -> {
                try {
                    Files.write(Paths.get(tempFile.getPath()), data);
                    logger.info("需要图片验证码登录, 验证码为 4 字母");
                    logger.info("将会显示字符图片. 若看不清字符图片, 请查看文件 " + tempFile.getAbsolutePath());

                    try (InputStream inputStream = new FileInputStream(tempFile);
                         OutputStream outputStream = new FileOutputStream(tempFile)) {
                        ImageIO.write(createCharImg(ImageIO.read(inputStream)), "png", outputStream);
                    }

                    logger.info("请输入 4 位字母验证码. 若要更换验证码, 请直接回车");
                } catch (Exception e) {
                    logger.info("无法写出验证码文件(" + e.getMessage() + "), 请尝试查看以上字符图片");
                }
            });

            return input.get().takeUnless(string -> string.isEmpty() || string.length() != 4)
                    .doFinally(() -> logger.info("正在提交[" + string + "]中..."));
        });
    }

    @Override
    public String onSolveSliderCaptcha(Bot bot, String url) {
        return loginSolverLock.withLock(() -> {
            MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
            logger.info("需要滑动验证码");
            logger.info("请在任意浏览器中打开以下链接并完成验证码. ");
            logger.info("完成后请输入任意字符 ");
            logger.info(url);

            return input.get().doFinally(() -> logger.info("正在提交中..."));
        });
    }

    @Override
    public String onSolveUnsafeDeviceLoginVerify(Bot bot, String url) {
        return loginSolverLock.withLock(() -> {
            MiraiLogger logger = overrideLogger != null ? overrideLogger : bot.getLogger();
            logger.info("需要进行账户安全认证");
            logger.info("该账户有[设备锁]/[不常用登录地点]/[不常用设备登录]的问题");
            logger.info("完成以下账号认证即可成功登录|理论本认证在mirai每个账户中最多出现1次");
            logger.info("请将该链接在QQ浏览器中打开并完成认证, 成功后输入任意字符");
            logger.info("这步操作将在后续的版本中优化");
            logger.info(url);

            return input.get().doFinally(() -> logger.info("正在提交中..."));
        });
    }

    

    private char getChar(int rgb) {
        double gray = 0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff);
        if (gray > 192) {
            return ' ';
        }
        if (gray > 128) {
            return 'x';
        }
        return 'o';
    }

}
public actual abstract class LoginSolver {
    public actual abstract String onSolvePicCaptcha(@NotNull Bot bot, @NotNull byte[] data) throws IOException;
    public actual abstract String onSolveSliderCaptcha(@NotNull Bot bot, @NotNull String url) throws IOException;
    public actual abstract String onSolveUnsafeDeviceLoginVerify(@NotNull Bot bot, @NotNull String url) throws IOException;

    public actual companion object {
        public actual LoginSolver Default = new DefaultLoginSolver(
                line -> !line.isBlank() ? line.stripLeading() : null
        );
    }




internal static Function<Context, DeviceInfo> getFileBasedDeviceInfoSupplier(String filename) {
    return context -> {
        try {
            return Files.newInputStream(Paths.get(filename)).use(inputStream -> {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(inputStream, DeviceInfo.class);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
}

private static ByteWriteChannel writeChannel(File file, CoroutineContext coroutineContext) throws IOException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (FileChannel fileChannel = new FileOutputStream(file).getChannel()) {
        return new ByteWriteChannel() {
            @Override
            public void write(@NotNull ByteString content) throws IOException {
                executor.submit(() -> {
                    try {
                        fileChannel.write(content.byteArray());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            @Override
            public void flush() throws IOException {
                executor.submit(fileChannel::force);
            }

            @Override
            public void close() throws IOException {
                executor.submit(fileChannel::close);
            }
        };
    }
}

private static final Mutex loginSolverLock = new Mutex();

private static String createCharImg(BufferedImage image, int outputWidth, double ignoreRate) {
    int newHeight = (int) (image.getHeight() * (outputWidth / (double) image.getWidth()));
    BufferedImage newImage = new BufferedImage(outputWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = newImage.createGraphics();
    g2d.drawImage(image.getScaledInstance(outputWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null);
    int background = newColorToGray(newImage.getRGB(0, 0));

    StringBuilder result = new StringBuilder();
    int minXPos = outputWidth;
    int maxXPos = 0;

    for (int y = 0; y < newImage.getHeight(); y++) {
        StringBuilder line = new StringBuilder();
        for (int x = 0; x < newImage.getWidth(); x++) {
            int gray = newColorToGray(newImage.getRGB(x, y));
            if (isGrayCompare(gray, background, ignoreRate)) {
                line.append(" ");
            } else {
                line.append("#");
                if (x < minXPos) {
                    minXPos = x;
                }
                if (x > maxXPos) {
                    maxXPos = x;
                }
            }
        }
        if (!line.toString().isBlank()) {
            result.append(line.substring(minXPos, maxXPos)).append("\n");
        }
    }
    return result.toString();
}
    public static int gray(int rgb) {
        int r = (rgb & 0xff0000) >> 16;
        int g = (rgb & 0x00ff00) >> 8;
        int b = (rgb & 0x0000ff);
        return (int) (Math.round((r * 30.0 + g * 59.0 + b * 11.0 + 50.0) / 100.0));
    }

    public static boolean grayCompare(int g1, int g2, double ignoreRate) {
        double minGray = Math.min(g1, g2);
        double maxGray = Math.max(g1, g2);
        BigDecimal comparisonValue = new BigDecimal(minGray / maxGray).setScale(2, BigDecimal.ROUND_HALF_UP);
        return comparisonValue.compareTo(new BigDecimal(ignoreRate)) >= 0;
    }
}