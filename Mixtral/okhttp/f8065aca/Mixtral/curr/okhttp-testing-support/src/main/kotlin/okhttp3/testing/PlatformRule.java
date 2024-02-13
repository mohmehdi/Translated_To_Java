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
import org.junit.runners.model.Statement;
import org.openjsse.net.ssl.OpenJSSE;

import java.lang.reflect.Method;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public class PlatformRule implements TestRule, BeforeEachCallback, AfterEachCallback {
  private final String requiredPlatformName;
  private final Platform platform;
  private final List < Pair < Matcher < Object > , Matcher < Object >>> versionChecks = new ArrayList < > ();

  public PlatformRule(String requiredPlatformName, Platform platform) {
    this.requiredPlatformName = requiredPlatformName;
    this.platform = platform;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    setupPlatform();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    resetPlatform();

    // TODO(jwilson): JUnit 4 discards the exception if it is expected. Can we do that in JUnit 5?

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

  private void setupPlatform() {
    if (requiredPlatformName != null) {
      assumeThat(getPlatformSystemProperty(), equalTo(requiredPlatformName));
    }

    if (platform != null) {
      Platform.resetForTests(platform);
    } else {
      Platform.resetForTests();
    }

    if (requiredPlatformName != null) {
      System.err.println("Running with " + Platform.get().getClass().getSimpleName());
    }
  }

  private void resetPlatform() {
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

  private void expectFailure(Matcher < Object > versionMatcher, Matcher < Object > failureMatcher) {
    versionChecks.add(new Pair < > (versionMatcher, failureMatcher));
  }

  private Matcher < Object > platformMatches(String platform) {
    return new BaseMatcher < Object > () {
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

  private Matcher < PlatformVersion > fromMajor(int version) {
    return new TypeSafeMatcher < PlatformVersion > () {
      @Override
      public void describeTo(Description description) {
        description.appendText("JDK with version from " + version);
      }

      @Override
      protected boolean matchesSafely(PlatformVersion item) {
        return item.getMajorVersion() >= version;
      }
    };
  }

  private Matcher < PlatformVersion > onMajor(int version) {
    return new TypeSafeMatcher < PlatformVersion > () {
      @Override
      public void describeTo(Description description) {
        description.appendText("JDK with version " + version);
      }

      @Override
      protected boolean matchesSafely(PlatformVersion item) {
        return item.getMajorVersion() == version;
      }
    };
  }

  private void rethrowIfNotExpected(Throwable e) {
    for (Pair < Matcher < Object > , Matcher < Object >> check: versionChecks) {
      if (check.getFirst().matches(PlatformVersion.getInstance()) && check.getSecond().matches(e)) {
        return;
      }
    }

    throw e;
  }

  private void failIfExpected() {
    for (Pair < Matcher < Object > , Matcher < Object >> check: versionChecks) {
      if (check.getFirst().matches(PlatformVersion.getInstance())) {
        StringDescription description = new StringDescription();
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
    assumeThat(getPlatformSystemProperty(), equalTo(CONSCRYPT_PROPERTY));
  }

  public void assumeJdk9() {
    assumeThat(getPlatformSystemProperty(), equalTo(JDK9_PROPERTY));
  }

  public void assumeOpenJSSE() {
    assumeThat(getPlatformSystemProperty(), equalTo(OPENJSSE_PROPERTY));
  }

  public void assumeJdk8() {
    assumeThat(getPlatformSystemProperty(), equalTo(JDK8_PROPERTY));
  }

  public void assumeJdk8Alpn() {
    assumeThat(getPlatformSystemProperty(), equalTo(JDK8_ALPN_PROPERTY));
  }

  public void assumeCorretto() {
    assumeThat(getPlatformSystemProperty(), equalTo(CORRETTO_PROPERTY));
  }

  public void assumeBouncyCastle() {
    assumeThat(getPlatformSystemProperty(), equalTo(BOUNCYCASTLE_PROPERTY));
  }

  public void assumeHttp2Support() {
    assumeThat(getPlatformSystemProperty(), not(JDK8_PROPERTY));
  }

  public void assumeAndroid() {
    assumeFalse("Only Android platform supported", Platform.isAndroid());
  }

  public void assumeNotConscrypt() {
    assumeThat(getPlatformSystemProperty(), not(CONSCRYPT_PROPERTY));
  }

  public void assumeNotJdk9() {
    assumeThat(getPlatformSystemProperty(), not(JDK9_PROPERTY));
  }

  public void assumeNotJdk8() {
    assumeThat(getPlatformSystemProperty(), not(JDK8_PROPERTY));
  }

  public void assumeNotJdk8Alpn() {
    assumeThat(getPlatformSystemProperty(), not(JDK8_ALPN_PROPERTY));
  }

  public void assumeNotOpenJSSE() {
    assumeThat(getPlatformSystemProperty(), not(OPENJSSE_PROPERTY));
  }

  public void assumeNotCorretto() {
    assumeThat(getPlatformSystemProperty(), not(CORRETTO_PROPERTY));
  }

  public void assumeNotBouncyCastle() {
    assumeThat(getPlatformSystemProperty(), not(BOUNCYCASTLE_PROPERTY));
  }

  public void assumeNotHttp2Support() {
    assumeThat(getPlatformSystemProperty(), equalTo(JDK8_PROPERTY));
  }

  public void assumeJettyBootEnabled() {
    assumeTrue("ALPN Boot not enabled", isAlpnBootEnabled());
  }

  public void assumeNotAndroid() {
    assumeFalse("Android platform not supported", Platform.isAndroid());
  }

  private String getPlatformSystemProperty() {
    String property = System.getProperty(PROPERTY_NAME);

    if (property == null) {
      Platform currentPlatform = Platform.get();
      if (currentPlatform instanceof ConscryptPlatform) {
        property = CONSCRYPT_PROPERTY;
      } else if (currentPlatform instanceof OpenJSSEPlatform) {
        property = OPENJSSE_PROPERTY;
      } else if (currentPlatform instanceof Jdk8WithJettyBootPlatform) {
        property = CONSCRYPT_PROPERTY;
      } else if (currentPlatform instanceof Jdk9Platform) {
        if (isCorrettoInstalled) {
          property = CORRETTO_PROPERTY;
        } else {
          property = JDK9_PROPERTY;
        }
      } else {
        property = JDK8_PROPERTY;
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
  @JvmStatic
  public static PlatformRule jdk8alpn() {
    return new PlatformRule(JDK8_ALPN_PROPERTY);
  }

  @JvmStatic
  public static PlatformRule bouncycastle() {
    return new PlatformRule(BOUNCYCASTLE_PROPERTY);
  }

  @JvmStatic
  public static boolean isAlpnBootEnabled() {
    try {
      Class.forName("org.eclipse.jetty.alpn.ALPN", true, null);
      return true;
    } catch (ClassNotFoundException cnfe) {
      return false;
    }
  }

  public static boolean isCorrettoSupported() {
    try {
      // Trigger an early exception over a fatal error, prefer a RuntimeException over Error.
      Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");

      return AmazonCorrettoCryptoProvider.INSTANCE.loadingError == null &&
        AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests() == SelfTestStatus.PASSED;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public static boolean isCorrettoInstalled() {
    return isCorrettoSupported() &&
      Security.getProviders().get(0).getName().equals(AmazonCorrettoCryptoProvider.PROVIDER_NAME);
  }

}