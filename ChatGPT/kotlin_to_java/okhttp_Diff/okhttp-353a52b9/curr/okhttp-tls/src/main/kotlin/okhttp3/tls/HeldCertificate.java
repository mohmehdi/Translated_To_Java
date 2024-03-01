package okhttp3.tls;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import okio.ByteString;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.internal.canParseAsIpAddress;
import okhttp3.tls.internal.der.*;
import okhttp3.tls.internal.der.CertificateAdapters;
import okhttp3.tls.internal.der.Extension;
import okhttp3.tls.internal.der.ObjectIdentifiers;
import okio.ByteString.Companion.decodeBase64;
import okio.ByteString.Companion.toByteString;

@Suppress("DEPRECATION")
public class HeldCertificate {
    private static final long DEFAULT_DURATION_MILLIS = 1000L * 60 * 60 * 24; // 24 hours.

    private static final Pattern PEM_REGEX = Pattern.compile("-----BEGIN ([!-,.-~ ]*)-----(.*?)-----END \\1-----", Pattern.DOTALL);

    private final KeyPair keyPair;
    private final X509Certificate certificate;

    @Deprecated(message = "moved to val", replaceWith = @ReplaceWith(expression = "certificate"), level = DeprecationLevel.ERROR)
    @JvmName("-deprecated_certificate")
    public X509Certificate certificate() {
        return certificate;
    }

    @Deprecated(message = "moved to val", replaceWith = @ReplaceWith(expression = "keyPair"), level = DeprecationLevel.ERROR)
    @JvmName("-deprecated_keyPair")
    public KeyPair keyPair() {
        return keyPair;
    }

    public HeldCertificate(KeyPair keyPair, X509Certificate certificate) {
        this.keyPair = keyPair;
        this.certificate = certificate;
    }

    public String certificatePem() {
        return certificate.certificatePem();
    }

    public String privateKeyPkcs8Pem() {
        return buildString(sb -> {
            sb.append("-----BEGIN PRIVATE KEY-----\n");
            encodeBase64Lines(keyPair.getPrivate().getEncoded().toByteString(), sb);
            sb.append("-----END PRIVATE KEY-----\n");
        });
    }

    public String privateKeyPkcs1Pem() {
        check(keyPair.getPrivate() instanceof RSAPrivateKey, "PKCS1 only supports RSA keys");
        return buildString(sb -> {
            sb.append("-----BEGIN RSA PRIVATE KEY-----\n");
            encodeBase64Lines(pkcs1Bytes(), sb);
            sb.append("-----END RSA PRIVATE KEY-----\n");
        });
    }

    private ByteString pkcs1Bytes() {
        CertificateAdapters.PrivateKeyInfo decoded = CertificateAdapters.privateKeyInfo.fromDer(keyPair.getPrivate().getEncoded().toByteString());
        return decoded.privateKey;
    }

    public static class Builder {
        private static final String EC_ALGORITHM = "EC";
        private static final String RSA_ALGORITHM = "RSA";

        private long notBefore = -1L;
        private long notAfter = -1L;
        private String commonName = null;
        private String organizationalUnit = null;
        private final List<String> altNames = new ArrayList<>();
        private BigInteger serialNumber = null;
        private KeyPair keyPair = null;
        private HeldCertificate signedBy = null;
        private int maxIntermediateCas = -1;
        private String keyAlgorithm = null;
        private int keySize = 0;

        public Builder() {
            ecdsa256();
        }

        public Builder validityInterval(long notBefore, long notAfter) {
            require(notBefore <= notAfter && (notBefore == -1L) == (notAfter == -1L),
                    "invalid interval: " + notBefore + ".." + notAfter);
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            return this;
        }

        public Builder duration(long duration, TimeUnit unit) {
            long now = System.currentTimeMillis();
            validityInterval(now, now + unit.toMillis(duration));
            return this;
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
            keyPair(new KeyPair(publicKey, privateKey));
            return this;
        }

        public Builder signedBy(HeldCertificate signedBy) {
            this.signedBy = signedBy;
            return this;
        }

        public Builder certificateAuthority(int maxIntermediateCas) {
            require(maxIntermediateCas >= 0, "maxIntermediateCas < 0: " + maxIntermediateCas);
            this.maxIntermediateCas = maxIntermediateCas;
            return this;
        }

        public Builder ecdsa256() {
            keyAlgorithm = EC_ALGORITHM;
            keySize = 256;
            return this;
        }

        public Builder rsa2048() {
            keyAlgorithm = RSA_ALGORITHM;
            keySize = 2048;
            return this;
        }

        public HeldCertificate build() {
            // Subject keys & identity.
            KeyPair subjectKeyPair = (keyPair != null) ? keyPair : generateKeyPair();
            CertificateAdapters.SubjectPublicKeyInfo subjectPublicKeyInfo = CertificateAdapters.subjectPublicKeyInfo.fromDer(
                    subjectKeyPair.getPublic().getEncoded().toByteString());
            List<List<AttributeTypeAndValue>> subject = subject();

            // Issuer/signer keys & identity. May be the subject if it is self-signed.
            KeyPair issuerKeyPair;
            List<List<AttributeTypeAndValue>> issuer;
            if (signedBy != null) {
                issuerKeyPair = signedBy.keyPair;
                issuer = CertificateAdapters.rdnSequence.fromDer(
                        signedBy.certificate.getSubjectX500Principal().getEncoded().toByteString());
            } else {
                issuerKeyPair = subjectKeyPair;
                issuer = subject;
            }
            AlgorithmIdentifier signatureAlgorithm = signatureAlgorithm(issuerKeyPair);

            // Subset of certificate data that's covered by the signature.
            TbsCertificate tbsCertificate = new TbsCertificate(
                    2L, // v3.
                    (serialNumber != null) ? serialNumber : BigInteger.ONE,
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
            Signature signature = Signature.getInstance(tbsCertificate.signatureAlgorithmName);
            signature.initSign(issuerKeyPair.getPrivate());
            signature.update(CertificateAdapters.tbsCertificate.toDer(tbsCertificate).toByteArray());
            ByteString signatureBytes = ByteString.Companion.of(signature.sign());

            // Complete signed certificate.
            Certificate certificate = new Certificate(
                    tbsCertificate,
                    signatureAlgorithm,
                    new BitString(signatureBytes, 0)
            );

            return new HeldCertificate(subjectKeyPair, certificate.toX509Certificate());
        }

        private List<List<AttributeTypeAndValue>> subject() {
            List<List<AttributeTypeAndValue>> result = new ArrayList<>();

            if (organizationalUnit != null) {
                result.add(Collections.singletonList(new AttributeTypeAndValue(
                        ObjectIdentifiers.organizationalUnitName,
                        organizationalUnit
                )));
            }

            result.add(Collections.singletonList(new AttributeTypeAndValue(
                    ObjectIdentifiers.commonName,
                    (commonName != null) ? commonName : UUID.randomUUID().toString()
            )));

            return result;
        }

        private Validity validity() {
            long notBeforeTime = (notBefore != -1L) ? notBefore : System.currentTimeMillis();
            long notAfterTime = (notAfter != -1L) ? notAfter : notBeforeTime + DEFAULT_DURATION_MILLIS;
            return new Validity(notBeforeTime, notAfterTime);
        }

        private List<Extension> extensions() {
            List<Extension> result = new ArrayList<>();

            if (maxIntermediateCas != -1) {
                result.add(new Extension(
                        ObjectIdentifiers.basicConstraints,
                        true,
                        new BasicConstraints(true, maxIntermediateCas)
                ));
            }

            if (!altNames.isEmpty()) {
                List<Pair<String, ByteString>> extensionValue = altNames.stream()
                        .map(it -> it.canParseAsIpAddress()
                                ? new Pair<>(CertificateAdapters.generalNameIpAddress, InetAddress.getByName(it).getAddress().toByteString())
                                : new Pair<>(CertificateAdapters.generalNameDnsName, ByteString.Companion.encodeUtf8(it)))
                        .collect(Collectors.toList());

                result.add(new Extension(
                        ObjectIdentifiers.subjectAlternativeName,
                        true,
                        extensionValue
                ));
            }

            return result;
        }

        private AlgorithmIdentifier signatureAlgorithm(KeyPair signedByKeyPair) {
            return (signedByKeyPair.getPrivate() instanceof RSAPrivateKey)
                    ? new AlgorithmIdentifier(ObjectIdentifiers.sha256WithRSAEncryption, null)
                    : new AlgorithmIdentifier(ObjectIdentifiers.sha256withEcdsa, ByteString.Companion.EMPTY);
        }

        private KeyPair generateKeyPair() {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(keyAlgorithm);
                keyPairGenerator.initialize(keySize, new SecureRandom());
                return keyPairGenerator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        static {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static HeldCertificate decode(String certificateAndPrivateKeyPem) {
        String certificatePem = null;
        String pkcs8Base64 = null;
        Matcher matcher = PEM_REGEX.matcher(certificateAndPrivateKeyPem);
        while (matcher.find()) {
            String label = matcher.group(1);
            switch (label) {
                case "CERTIFICATE":
                    require(certificatePem == null, "string includes multiple certificates");
                    certificatePem = matcher.group(0); // Keep --BEGIN-- and --END-- for certificates.
                    break;
                case "PRIVATE KEY":
                    require(pkcs8Base64 == null, "string includes multiple private keys");
                    pkcs8Base64 = matcher.group(2); // Include the contents only for PKCS8.
                    break;
                default:
                    throw new IllegalArgumentException("unexpected type: " + label);
            }
        }
        require(certificatePem != null, "string does not include a certificate");
        require(pkcs8Base64 != null, "string does not include a private key");

        return decode(certificatePem, pkcs8Base64);
    }

    private static HeldCertificate decode(String certificatePem, String pkcs8Base64Text) {
        X509Certificate certificate = CertificateAdapters.decodeCertificatePem(certificatePem);

        ByteString pkcs8Bytes = decodeBase64(pkcs8Base64Text);
        String keyType = (certificate.getPublicKey() instanceof ECPublicKey) ? "EC" : "RSA";
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
