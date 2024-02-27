package okhttp3.testing;

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazon.corretto.crypto.provider.SelfTestStatus;
import okhttp3.internal.platform.ConscryptPlatform;
import okhttp3.internal.platform.Jdk8WithJettyBootPlatform;
import okhttp3.internal.platform.Jdk9Platform;
import okhttp3.internal.platform.OpenJSSEPlatform;
import okhttp3.internal.platform.Platform;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.conscrypt.Conscrypt;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.openjsse.net.ssl.OpenJSSE;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "MemberVisibilityCanBePrivate"})
public class PlatformRule implements TestRule, BeforeEachCallback, AfterEachCallback {
    private final String requiredPlatformName;
    private final Platform platform;
    private final List<Pair<Matcher<? extends Object>, Matcher<? extends Object>>> versionChecks;

    public PlatformRule(String requiredPlatformName, Platform platform) {
        this.requiredPlatformName = requiredPlatformName;
        this.platform = platform;
        this.versionChecks = new ArrayList<>();
    }

    public PlatformRule(String requiredPlatformName) {
        this(requiredPlatformName, null);
    }

    public PlatformRule() {
        this(null, null);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        setupPlatform();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        resetPlatform();

        if (!context.getExecutionException().isPresent()) {
            failIfExpected();
        }
    }

    @Override
    public Statement apply(Statement base, org.junit.runner.Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                boolean failed = false;
                try {
                    setupPlatform();
                    base.evaluate();
                } catch (AssumptionViolatedException e) {
                    throw e;
                } catch (Throwable e) {
                    failed = true;
                    rethrowIfNotExpected(e);
                } finally {
                    resetPlatform();
                }
                if (!failed) {
                    failIfExpected();
                }
            }
        };
    }

    public void setupPlatform() {
        if (requiredPlatformName != null) {
            Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(requiredPlatformName));
        }

        if (platform != null) {
            Platform.resetForTests(platform);
        } else {
            Platform.resetForTests();
        }

        if (requiredPlatformName != null) {
            System.err.println("Running with " + Platform.get().javaClass().getSimpleName());
        }
    }

    public void resetPlatform() {
        if (platform != null) {
            Platform.resetForTests();
        }
    }

    public void expectFailureOnConscryptPlatform() {
        expectFailure(platformMatches(CONSCRYPT_PROPERTY));
    }

    public void expectFailureOnCorrettoPlatform() {
        expectFailure(platformMatches(CORRETTO_PROPERTY));
    }

    public void expectFailureOnOpenJSSEPlatform() {
        expectFailure(platformMatches(OPENJSSE_PROPERTY));
    }

    public void expectFailureFromJdkVersion(int majorVersion) {
        expectFailure(fromMajor(majorVersion));
    }

    public void expectFailureOnJdkVersion(int majorVersion) {
        expectFailure(onMajor(majorVersion));
    }

    private void expectFailure(Matcher<? extends Object> versionMatcher, Matcher<? extends Object> failureMatcher) {
        versionChecks.add(new Pair<>(versionMatcher, failureMatcher));
    }

    public Matcher<Object> platformMatches(String platform) {
        return new BaseMatcher<Object>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(platform);
            }

            @Override
            public boolean matches(Object item) {
                return getPlatformSystemProperty().equals(platform);
            }
        };
    }

    public Matcher<PlatformVersion> fromMajor(int version) {
        return new TypeSafeMatcher<PlatformVersion>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JDK with version from " + version);
            }

            @Override
            protected boolean matchesSafely(PlatformVersion item) {
                return item.majorVersion() >= version;
            }
        };
    }

    public Matcher<PlatformVersion> onMajor(int version) {
        return new TypeSafeMatcher<PlatformVersion>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JDK with version " + version);
            }

            @Override
            protected boolean matchesSafely(PlatformVersion item) {
                return item.majorVersion() == version;
            }
        };
    }

    public void rethrowIfNotExpected(Throwable e) {
        for (Pair<Matcher<? extends Object>, Matcher<? extends Object>> check : versionChecks) {
            if (check.getFirst().matches(PlatformVersion) && check.getSecond().matches(e)) {
                return;
            }
        }
        throw e;
    }

    public void failIfExpected() {
        for (Pair<Matcher<? extends Object>, Matcher<? extends Object>> check : versionChecks) {
            if (check.getFirst().matches(PlatformVersion)) {
                Description description = new StringDescription();
                check.getFirst().describeTo(description);
                description.appendText(" expected to fail with exception that ");
                check.getSecond().describeTo(description);
                Assert.fail(description.toString());
            }
        }
    }

    public boolean isConscrypt() {
        return getPlatformSystemProperty().equals(CONSCRYPT_PROPERTY);
    }

    public boolean isJdk9() {
        return getPlatformSystemProperty().equals(JDK9_PROPERTY);
    }

    public boolean isJdk8() {
        return getPlatformSystemProperty().equals(JDK8_PROPERTY);
    }

    public boolean isJdk8Alpn() {
        return getPlatformSystemProperty().equals(JDK8_ALPN_PROPERTY);
    }

    public boolean isBouncyCastle() {
        return getPlatformSystemProperty().equals(BOUNCYCASTLE_PROPERTY);
    }

    public boolean hasHttp2Support() {
        return !isJdk8();
    }

    public void assumeConscrypt() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(CONSCRYPT_PROPERTY));
    }

    public void assumeJdk9() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(JDK9_PROPERTY));
    }

    public void assumeOpenJSSE() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(OPENJSSE_PROPERTY));
    }

    public void assumeJdk8() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(JDK8_PROPERTY));
    }

    public void assumeJdk8Alpn() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(JDK8_ALPN_PROPERTY));
    }

    public void assumeCorretto() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(CORRETTO_PROPERTY));
    }

    public void assumeBouncyCastle() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(BOUNCYCASTLE_PROPERTY));
    }

    public void assumeHttp2Support() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(JDK8_PROPERTY)));
    }

    public void assumeAndroid() {
        Assume.assumeTrue("Only Android platform supported", Platform.isAndroid());
    }

    public void assumeNotConscrypt() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(CONSCRYPT_PROPERTY)));
    }

    public void assumeNotJdk9() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(JDK9_PROPERTY)));
    }

    public void assumeNotJdk8() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(JDK8_PROPERTY)));
    }

    public void assumeNotJdk8Alpn() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(JDK8_ALPN_PROPERTY)));
    }

    public void assumeNotOpenJSSE() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(OPENJSSE_PROPERTY)));
    }

    public void assumeNotCorretto() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(CORRETTO_PROPERTY)));
    }

    public void assumeNotBouncyCastle() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.not(CoreMatchers.equalTo(BOUNCYCASTLE_PROPERTY)));
    }

    public void assumeNotHttp2Support() {
        Assume.assumeThat(getPlatformSystemProperty(), CoreMatchers.equalTo(JDK8_PROPERTY));
    }

    public void assumeJettyBootEnabled() {
        Assume.assumeTrue("ALPN Boot not enabled", isAlpnBootEnabled());
    }

    public void assumeNotAndroid() {
        Assume.assumeFalse("Android platform not supported", Platform.isAndroid());
    }

    public static final String PROPERTY_NAME = "okhttp.platform";
    public static final String CONSCRYPT_PROPERTY = "conscrypt";
    public static final String CORRETTO_PROPERTY = "corretto";
    public static final String JDK9_PROPERTY = "jdk9";
    public static final String JDK8_ALPN_PROPERTY = "jdk8alpn";
    public static final String JDK8_PROPERTY = "jdk8";
    public static final String OPENJSSE_PROPERTY = "openjsse";
    public static final String BOUNCYCASTLE_PROPERTY = "bouncycastle";

    static {
        String platformSystemProperty = getPlatformSystemProperty();

        if (platformSystemProperty.equals(JDK9_PROPERTY)) {
            if (System.getProperty("javax.net.debug") == null) {
                System.setProperty("javax.net.debug", "");
            }
        } else if (platformSystemProperty.equals(CONSCRYPT_PROPERTY)) {
            if (!Conscrypt.isAvailable()) {
                System.err.println("Warning: Conscrypt not available");
            }

            if (Security.getProviders()[0].name().equals("Conscrypt")) {
                Conscrypt.newProviderBuilder()
                        .provideTrustManager(true)
                        .build();
            }
        } else if (platformSystemProperty.equals(JDK8_ALPN_PROPERTY)) {
            if (!isAlpnBootEnabled()) {
                System.err.println("Warning: ALPN Boot not enabled");
            }
        } else if (platformSystemProperty.equals(JDK8_PROPERTY)) {
            if (isAlpnBootEnabled()) {
                System.err.println("Warning: ALPN Boot enabled unintentionally");
            }
        } else if (platformSystemProperty.equals(OPENJSSE_PROPERTY) && !Security.getProviders()[0].name().equals("OpenJSSE")) {
            if (!OpenJSSEPlatform.isSupported()) {
                System.err.println("Warning: OpenJSSE not available");
            }

            if (System.getProperty("javax.net.debug") == null) {
                System.setProperty("javax.net.debug", "");
            }

            Security.insertProviderAt(new OpenJSSE(), 1);
        } else if (platformSystemProperty.equals(BOUNCYCASTLE_PROPERTY) && !Security.getProviders()[0].name().equals("BC")) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
            Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
        } else if (platformSystemProperty.equals(CORRETTO_PROPERTY)) {
            AmazonCorrettoCryptoProvider.install();
            AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy();
        }

        Platform.resetForTests();

        System.err.println("Running Tests with " + Platform.get().javaClass().getSimpleName());
    }

    public static String getPlatformSystemProperty() {
        String property = System.getProperty(PROPERTY_NAME);

        if (property == null) {
            switch (Platform.get().getClass().getSimpleName()) {
                case "ConscryptPlatform":
                    property = CONSCRYPT_PROPERTY;
                    break;
                case "OpenJSSEPlatform":
                    property = OPENJSSE_PROPERTY;
                    break;
                case "Jdk8WithJettyBootPlatform":
                    property = CONSCRYPT_PROPERTY;
                    break;
                case "Jdk9Platform":
                    property = isCorrettoInstalled() ? CORRETTO_PROPERTY : JDK9_PROPERTY;
                    break;
                default:
                    property = JDK8_PROPERTY;
                    break;
            }
        }

        return property;
    }

    public static PlatformRule conscrypt() {
        return new PlatformRule(CONSCRYPT_PROPERTY);
    }

    public static PlatformRule openjsse() {
        return new PlatformRule(OPENJSSE_PROPERTY);
    }

    public static PlatformRule jdk9() {
        return new PlatformRule(JDK9_PROPERTY);
    }

    public static PlatformRule jdk8() {
        return new PlatformRule(JDK8_PROPERTY);
    }

    public static PlatformRule jdk8alpn() {
        return new PlatformRule(JDK8_ALPN_PROPERTY);
    }

    public static PlatformRule bouncycastle() {
        return new PlatformRule(BOUNCYCASTLE_PROPERTY);
    }

    public static boolean isAlpnBootEnabled() {
        try {
            Class.forName("org.eclipse.jetty.alpn.ALPN", true, null);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isCorrettoSupported() {
        try {
            Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");
            return AmazonCorrettoCryptoProvider.INSTANCE.loadingError() == null &&
                    AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests() == SelfTestStatus.PASSED;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isCorrettoInstalled() {
        return isCorrettoSupported() && Security.getProviders()[0].name().equals(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
    }
}
