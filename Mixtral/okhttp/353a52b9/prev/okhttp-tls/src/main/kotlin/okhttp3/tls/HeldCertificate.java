

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
import java.util.EnumSet;
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
        "-----BEGIN PRIVATE KEY-----\n"
            + encodeBase64Lines(keyPair.private.getEncoded().toByteString())
            + "-----END PRIVATE KEY-----\n");
  }

  public String privateKeyPkcs1Pem() {
    if (!(keyPair.private instanceof RSAPrivateKey)) {
      throw new IllegalArgumentException("PKCS1 only supports RSA keys");
    }
    return buildString(
        "-----BEGIN RSA PRIVATE KEY-----\n"
            + encodeBase64Lines(pkcs1Bytes())
            + "-----END RSA PRIVATE KEY-----\n");
  }

  private ByteString pkcs1Bytes() {
      byte[] encoded = keyPair.getPrivate().getEncoded();
      PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
      byte[] pkcs1Bytes = privateKeyInfo.parsePrivateKey().getEncoded();
      return ByteString.copyFrom(pkcs1Bytes);
  }

  public static class Builder {
    private long notBefore = -1;
    private long notAfter = -1;
    private String cn;
    private String ou;
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
      this.cn = cn;
      return this;
    }

    public Builder organizationalUnit(String ou) {
      this.ou = ou;
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
public HeldCertificate build() {
  KeyPair subjectKeyPair = keyPair != null ? keyPair : generateKeyPair();
  ASN1EncodableVector subjectPublicKeyInfo = CertificateAdapters.subjectPublicKeyInfo.fromDer(
    subjectKeyPair.getPublic().getEncoded()
  );
  List < List < ASN1Encodable >> subject = subject();

  KeyPair issuerKeyPair;
  List < List < ASN1Encodable >> issuer;
  if (signedBy != null) {
    issuerKeyPair = signedBy.getKeyPair();
    issuer = CertificateAdapters.rdnSequence.fromDer(
      signedBy.getCertificate().getSubjectX500Principal().getEncoded()
    );
  } else {
    issuerKeyPair = subjectKeyPair;
    issuer = subject;
  }

  String signatureAlgorithm = signatureAlgorithm(issuerKeyPair);

  X509v3CertificateBuilder tbsCertificateBuilder = new X509v3CertificateBuilder(
    new X500Name(issuer.stream().flatMap(Collection::stream).map(ASN1Encodable::getId).toArray(String[]::new)),
    BigInteger.valueOf(Objects.requireNonNullElse(serialNumber, 1 L)),
    validity().getNotBefore(),
    validity().getNotAfter(),
    new X500Name(subject.stream().flatMap(Collection::stream).map(ASN1Encodable::getId).toArray(String[]::new)),
    subjectPublicKeyInfo
  );

  try {
    ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(issuerKeyPair.getPrivate());
    X509v3CertificateBuilder tbsCertificate = tbsCertificateBuilder.build(contentSigner);
    PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(
      new X500Name(issuer.stream().flatMap(Collection::stream).map(ASN1Encodable::getId).toArray(String[]::new)),
      subjectPublicKeyInfo
    ).build(contentSigner);

    byte[] signature = Signature.getInstance(tbsCertificate.getSignatureAlgorithm().getAlgorithm()).run {
      initSign(issuerKeyPair.getPrivate());
      update(CertificateAdapters.tbsCertificate.toDer(tbsCertificate).getEncoded());
      sign();
    };

    X509CertificateHolder certificateHolder = new X509v3CertificateBuilder(
      tbsCertificate.getSubject(),
      tbsCertificate.getSerialNumber(),
      tbsCertificate.getStartDate(),
      tbsCertificate.getEndDate(),
      tbsCertificate.getSubject(),
      tbsCertificate.getSubjectPublicKeyInfo()
    ).build(new JcaContentSignerBuilder(tbsCertificate.getSignatureAlgorithm().getAlgorithm()).build(issuerKeyPair.getPrivate()));

    X509Certificate certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);

    return new HeldCertificate(subjectKeyPair, certificate);
  } catch (OperatorCreationException | NoSuchAlgorithmException | InvalidKeySpecException e) {
    throw new RuntimeException(e);
  }
}


    private List<List<AttributeTypeAndValue>> subject() {
      List<List<AttributeTypeAndValue>> result = new ArrayList<>();

      if (ou != null) {
        result.add(
            List.of(
                new AttributeTypeAndValue(
                    ObjectIdentifiers.organizationalUnitName, ou)));
      }

      result.add(
          List.of(
              new AttributeTypeAndValue(
                  ObjectIdentifiers.commonName,
                  cn != null ? cn : UUID.randomUUID().toString())));

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
                ObjectIdentifiers.basicConstraints,
                true,
                new BasicConstraints(true, 3)));
      }

      if (!altNames.isEmpty()) {
        List<Extension.Value> extensionValue =
            altNames.stream()
                .map(
                    altName -> {
                      if (canParseAsIpAddress(altName)) {
                        return new Extension.Value(
                            Extension.Type.generalNameIpAddress,
                            InetAddress.getByName(altName).getAddress().toByteString());
                      } else {
                        return new Extension.Value(
                            Extension.Type.generalNameDnsName, altName);
                      }
                    })
                .toList();
        result.add(
            new Extension(
                ObjectIdentifiers.subjectAlternativeName,
                true,
                extensionValue));
      }

      return result;
    }

private AlgorithmIdentifier signatureAlgorithm(KeyPair signedByKeyPair) {
  return (RSAPrivateKey) signedByKeyPair.getPrivate() != null ?
    new AlgorithmIdentifier(new AlgorithmIdentifier(AlgorithmIdentifier.SHA256_RSA, null)) :
    new AlgorithmIdentifier(new AlgorithmIdentifier(AlgorithmIdentifier.SHA256_ECDSA, new DEROctetString(new byte[] {})), null);
}

    private KeyPair generateKeyPair() {
      KeyPairGenerator keyPairGenerator =
          KeyPairGenerator.getInstance(keyAlgorithm);
      keyPairGenerator.initialize(keySize, new SecureRandom());
      return keyPairGenerator.generateKeyPair();
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
          require(certificatePem == null);
          certificatePem = matcher.group(0);
          break;
        case "PRIVATE KEY":
          require(pkcs8Base64 == null);
          pkcs8Base64 = matcher.group(2);
          break;
        default:
          throw new IllegalArgumentException("unexpected type: " + label);
      }
    }
    require(certificatePem != null);
    require(pkcs8Base64 != null);

    return decode(certificatePem, pkcs8Base64);
  }

  private static HeldCertificate decode(String certificatePem, String pkcs8Base64Text) {
    X509Certificate certificate =
        certificatePem.decodeCertificatePem();

    ByteString pkcs8Bytes =
        decodeBase64(pkcs8Base64Text)
            .orElseThrow(() -> new IllegalArgumentException("failed to decode private key"));

    String keyType =
        switch (certificate.getPublicKey()) {
          case ECPublicKey ecpk -> "EC";
          case RSAPublicKey rspk -> "RSA";
          default ->
              throw new IllegalArgumentException(
                  "unexpected key type: " + certificate.getPublicKey());
        };

    PrivateKey privateKey =
        decodePkcs8(pkcs8Bytes, keyType);

    KeyPair keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
    return new HeldCertificate(keyPair, certificate);
  }

  private static PrivateKey decodePkcs8(ByteString data, String keyAlgorithm) {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);
      return keyFactory.generatePrivate(
          new PKCS8EncodedKeySpec(data.toByteArray()));
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("failed to decode private key", e);
    }
  }
}