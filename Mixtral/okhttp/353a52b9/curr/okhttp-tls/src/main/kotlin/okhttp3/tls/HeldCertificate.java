

package okhttp3.tls;

import okhttp3.internal.canParseAsIpAddress;
import okio.ByteString;
import okio.ByteString.decodeBase64;
import okio.ByteString.toByteString;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class HeldCertificate {

  private final KeyPair keyPair;
  private final X509Certificate certificate;

  public HeldCertificate(KeyPair keyPair, X509Certificate certificate) {
    this.keyPair = keyPair;
    this.certificate = certificate;
  }

  public X509Certificate certificate() {
    return certificate;
  }

  public KeyPair keyPair() {
    return keyPair;
  }

  public String certificatePem() {
    return certificate.certificatePem();
  }

  public String privateKeyPkcs8Pem() {
    return buildString(
        "-----BEGIN PRIVATE KEY-----\n",
        encodeBase64Lines(keyPair.private.getEncoded().toByteString()),
        "-----END PRIVATE KEY-----\n");
  }

  public String privateKeyPkcs1Pem() {
    if (!(keyPair.private instanceof RSAPrivateKey)) {
      throw new IllegalArgumentException("PKCS1 only supports RSA keys");
    }
    return buildString(
        "-----BEGIN RSA PRIVATE KEY-----\n",
        encodeBase64Lines(pkcs1Bytes()),
        "-----END RSA PRIVATE KEY-----\n");
  }

  private ByteString pkcs1Bytes() {
    ByteString decoded = CertificateAdapters.privateKeyInfo.fromDer(ByteString.copyFrom(keyPair.private().getEncoded()));
    return decoded.privateKey();
  }

  public static class Builder {
    private long notBefore = -1;
    private long notAfter = -1;
    private String commonName;
    private String organizationalUnit;
    private final List<String> altNames = new ArrayList<>();
    private BigInteger serialNumber;
    private KeyPair keyPair;
    private HeldCertificate signedBy;
    private int maxIntermediateCas = -1;
    private String keyAlgorithm;
    private int keySize;

    public Builder() {
      ecdsa256();
    }

    public Builder validityInterval(long notBefore, long notAfter) {
      require(notBefore <= notAfter && notBefore == -1L == (notAfter == -1L));
      this.notBefore = notBefore;
      this.notAfter = notAfter;
      return this;
    }

    public Builder duration(long duration, TimeUnit unit) {
      long now = System.currentTimeMillis();
      return validityInterval(now, now + unit.toMillis(duration));
    }

    public Builder addSubjectAlternativeName(String altName) {
      altNames.add(altName);
      return this;
    }

    public Builder commonName(String cn) {
      this.commonName = cn;
      return this;
    }

    public Builder organizationalUnit(String ou) {
      this.organizationalUnit = ou;
      return this;
    }

    public Builder serialNumber(BigInteger serialNumber) {
      this.serialNumber = serialNumber;
      return this;
    }

    public Builder serialNumber(long serialNumber) {
      return serialNumber(BigInteger.valueOf(serialNumber));
    }

    public Builder keyPair(KeyPair keyPair) {
      this.keyPair = keyPair;
      return this;
    }

    public Builder keyPair(PublicKey publicKey, PrivateKey privateKey) {
      return keyPair(new KeyPair(publicKey, privateKey));
    }

    public Builder signedBy(HeldCertificate signedBy) {
      this.signedBy = signedBy;
      return this;
    }

    public Builder certificateAuthority(int maxIntermediateCas) {
      require(maxIntermediateCas >= 0);
      this.maxIntermediateCas = maxIntermediateCas;
      return this;
    }

    public Builder ecdsa256() {
      keyAlgorithm = "EC";
      keySize = 256;
      return this;
    }

    public Builder rsa2048() {
      keyAlgorithm = "RSA";
      keySize = 2048;
      return this;
    }

    public HeldCertificate build() throws Exception {
      // Subject keys & identity.
      KeyPair subjectKeyPair = keyPair != null ? keyPair : generateKeyPair();
      PublicKey subjectPublicKey = subjectKeyPair.getPublic();
      AlgorithmIdentifier subjectPublicKeyInfo = CertificateAdapters.subjectPublicKeyInfo(subjectPublicKey);
      List < List < AttributeTypeAndValue >> subject = subject();

      // Issuer/signer keys & identity. May be the subject if it is self-signed.
      KeyPair issuerKeyPair;
      List < List < AttributeTypeAndValue >> issuer;
      if (signedBy != null) {
        issuerKeyPair = signedBy.getKeyPair();
        RDNSequence issuerRDNSequence = CertificateAdapters.rdnSequence(signedBy.getCertificate().getSubjectX500Principal().getEncoded());
        issuer = Arrays.asList(Arrays.asList(issuerRDNSequence.getObjects()));
      } else {
        issuerKeyPair = subjectKeyPair;
        issuer = subject;
      }
      AlgorithmIdentifier signatureAlgorithm = signatureAlgorithm(issuerKeyPair);

      // Subset of certificate data that's covered by the signature.
      TBSCertificate tbsCertificate = new TBSCertificate(
        2 L, // v3.
        serialNumber != null ? serialNumber : BigInteger.ONE,
        signatureAlgorithm,
        issuer,
        validity(),
        subject,
        subjectPublicKeyInfo,
        null,
        null,
        extensions()
      );

      // Signature.
      Signature signature = Signature.getInstance(tbsCertificate.getSignatureAlgorithm().getAlgorithm().getId(), new BouncyCastleProvider());
      signature.initSign(issuerKeyPair.getPrivate());
      signature.update(CertificateAdapters.tbsCertificate(tbsCertificate).getEncoded());
      byte[] signatureValue = signature.sign();
      BitString signatureBitString = new BitString(signatureValue, 0);

      // Complete signed certificate.
      X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
        subjectFromX500Principal(issuer),
        serialNumber != null ? serialNumber : BigInteger.ONE,
        validity().getNotBefore(),
        validity().getNotAfter(),
        subjectFromX500Principal(subject),
        subjectPublicKeyInfo
      );

      ContentSigner contentSigner = new JcaContentSignerBuilder(tbsCertificate.getSignatureAlgorithm().getAlgorithm().getId()).build(issuerKeyPair.getPrivate());
      X509CertificateHolder certificateHolder = certificateBuilder.build(contentSigner);
      X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certificateHolder);

      return new HeldCertificate(subjectKeyPair, certificate);
    }

    private List<List<AttributeTypeAndValue>> subject() {
      List<List<AttributeTypeAndValue>> result = new ArrayList<>();

      if (organizationalUnit != null) {
        result.add(
            List.of(
                new AttributeTypeAndValue(
                    organizationalUnitName, organizationalUnit)));
      }

      result.add(
          List.of(
              new AttributeTypeAndValue(
                  ObjectIdentifiers.commonName,
                  commonName != null ? commonName : UUID.randomUUID().toString())));

      return result;
    }

    private Validity validity() {
      long notBefore =
          notBefore != -1L ? notBefore : System.currentTimeMillis();
      long notAfter =
          notAfter != -1L ? notAfter : notBefore + DEFAULT_DURATION_MILLIS;
      return new Validity(notBefore, notAfter);
    }

    private List<Extension> extensions() {
      List<Extension> result = new ArrayList<>();

      if (maxIntermediateCas != -1) {
        result.add(
            new Extension(
                basicConstraints,
                true,
                new BasicConstraints(true, maxIntermediateCas)));
      }

      if (!altNames.isEmpty()) {
        List<Extension.Value> extensionValue =
            altNames.stream()
                .map(
                    altName -> {
                      if (canParseAsIpAddress(altName)) {
                        return new Extension.Value(
                            generalNameIpAddress,
                            InetAddress.getByName(altName).getAddress().toByteString());
                      } else {
                        return new Extension.Value(
                            generalNameDnsName, altName);
                      }
                    })
                .toList();
        result.add(
            new Extension(
                subjectAlternativeName,
                true,
                extensionValue));
      }

      return result;
    }

    private AlgorithmIdentifier signatureAlgorithm(KeyPair signedByKeyPair) {
      if (signedByKeyPair.getPrivate() instanceof RSAPrivateKey) {
        return new AlgorithmIdentifier(sha256WithRSAEncryption, null);
      } else {
        return new AlgorithmIdentifier(sha256withEcdsa, ByteString.EMPTY);
      }
    }

    private KeyPair generateKeyPair() {
      try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(keyAlgorithm);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
      } catch (GeneralSecurityException e) {
        throw new IllegalArgumentException("failed to generate key pair", e);
      }
    }
  }

  public static HeldCertificate decode(String certificateAndPrivateKeyPem) {
    String certificatePem = null;
    String pkcs8Base64 = null;
    Pattern PEM_REGEX =
        Pattern.compile("-----BEGIN ([!-,.-~ ]*)-----([^-]*)-----END \\1-----");
    Matcher matcher = PEM_REGEX.matcher(certificateAndPrivateKeyPem);
    while (matcher.find()) {
      String label = matcher.group(1);
      switch (label) {
        case "CERTIFICATE":
          if (certificatePem != null) {
            throw new IllegalArgumentException("string includes multiple certificates");
          }
          certificatePem = matcher.group(0);
          break;
        case "PRIVATE KEY":
          if (pkcs8Base64 != null) {
            throw new IllegalArgumentException("string includes multiple private keys");
          }
          pkcs8Base64 = matcher.group(2);
          break;
        default:
          throw new IllegalArgumentException("unexpected type: " + label);
      }
    }
    if (certificatePem == null) {
      throw new IllegalArgumentException("string does not include a certificate");
    }
    if (pkcs8Base64 == null) {
      throw new IllegalArgumentException("string does not include a private key");
    }

    return decode(certificatePem, pkcs8Base64);
  }

  private static HeldCertificate decode(String certificatePem, String pkcs8Base64Text) {
    X509Certificate certificate =
        certificatePem.decodeCertificatePem();

    ByteString pkcs8Bytes =
        pkcs8Base64Text.decodeBase64()
            .orElseThrow(() -> new IllegalArgumentException("failed to decode private key"));

    String keyType =
        switch (certificate.getPublicKey()) {
          case ECPublicKey ecpk -> "EC";
          case RSAPublicKey rspk -> "RSA";
          default -> throw new IllegalArgumentException("unexpected key type: " + certificate.getPublicKey());
        };

    PrivateKey privateKey = decodePkcs8(pkcs8Bytes, keyType);

    KeyPair keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
    return new HeldCertificate(keyPair, certificate);
  }

  private static PrivateKey decodePkcs8(ByteString data, String keyAlgorithm) {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);
      return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(data.toByteArray()));
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("failed to decode private key", e);
    }
  }
}