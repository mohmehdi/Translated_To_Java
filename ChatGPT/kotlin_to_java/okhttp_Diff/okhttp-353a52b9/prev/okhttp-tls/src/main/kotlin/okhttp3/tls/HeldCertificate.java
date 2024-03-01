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
import okhttp3.tls.internal.der.AlgorithmIdentifier;
import okhttp3.tls.internal.der.AttributeTypeAndValue;
import okhttp3.tls.internal.der.BasicConstraints;
import okhttp3.tls.internal.der.BitString;
import okhttp3.tls.internal.der.Certificate;
import okhttp3.tls.internal.der.CertificateAdapters;
import okhttp3.tls.internal.der.CertificateAdapters.generalNameDnsName;
import okhttp3.tls.internal.der.CertificateAdapters.generalNameIpAddress;
import okhttp3.tls.internal.der.Extension;
import okhttp3.tls.internal.der.ObjectIdentifiers;
import okhttp3.tls.internal.der.TbsCertificate;
import okhttp3.tls.internal.der.Validity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeldCertificate {
    private final KeyPair keyPair;
    private final X509Certificate certificate;

    @Deprecated
    public X509Certificate certificate() {
        return certificate;
    }

    @Deprecated
    public KeyPair keyPair() {
        return keyPair;
    }

    public String certificatePem() {
        return certificate.certificatePem();
    }

    public String privateKeyPkcs8Pem() {
        return new StringBuilder()
                .append("-----BEGIN PRIVATE KEY-----\n")
                .append(ByteString.Companion.of(keyPair.getPrivate().getEncoded()).base64())
                .append("-----END PRIVATE KEY-----\n")
                .toString();
    }

    public String privateKeyPkcs1Pem() {
        check(keyPair.getPrivate() instanceof RSAPrivateKey, "PKCS1 only supports RSA keys");
        return new StringBuilder()
                .append("-----BEGIN RSA PRIVATE KEY-----\n")
                .append(pkcs1Bytes().base64())
                .append("-----END RSA PRIVATE KEY-----\n")
                .toString();
    }

    private ByteString pkcs1Bytes() {
        ByteString decoded = CertificateAdapters.privateKeyInfo.fromDer(ByteString.Companion.of(keyPair.getPrivate().getEncoded()));
        return decoded.getPrivateKey();
    }

    public static class Builder {
        private long notBefore = -1L;
        private long notAfter = -1L;
        private String cn = null;
        private String ou = null;
        private final List<String> altNames = new java.util.ArrayList<>();
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
            serialNumber(BigInteger.valueOf(serialNumber));
            return this;
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
            KeyPair subjectKeyPair = (keyPair != null) ? keyPair : generateKeyPair();
            ByteString subjectPublicKeyInfo = CertificateAdapters.subjectPublicKeyInfo.fromDer(
                    ByteString.Companion.of(subjectKeyPair.getPublic().getEncoded()));
            List<List<AttributeTypeAndValue>> subject = subject();

            KeyPair issuerKeyPair;
            List<List<AttributeTypeAndValue>> issuer;
            if (signedBy != null) {
                issuerKeyPair = signedBy.keyPair;
                issuer = CertificateAdapters.rdnSequence.fromDer(
                        ByteString.Companion.of(signedBy.certificate.getSubjectX500Principal().getEncoded()));
            } else {
                issuerKeyPair = subjectKeyPair;
                issuer = subject;
            }
            AlgorithmIdentifier signatureAlgorithm = signatureAlgorithm(issuerKeyPair);

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

            ByteString signature;
            try {
                Signature sig = Signature.getInstance(tbsCertificate.signatureAlgorithmName);
                sig.initSign(issuerKeyPair.getPrivate());
                sig.update(CertificateAdapters.tbsCertificate.toDer(tbsCertificate).toByteArray());
                signature = ByteString.Companion.of(sig.sign());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate signature", e);
            }

            Certificate cert = new Certificate(
                    tbsCertificate,
                    signatureAlgorithm,
                    new BitString(signature, 0)
            );

            return new HeldCertificate(subjectKeyPair, cert.toX509Certificate());
        }

        private List<List<AttributeTypeAndValue>> subject() {
            List<List<AttributeTypeAndValue>> result = new java.util.ArrayList<>();

            if (ou != null) {
                result.add(java.util.Collections.singletonList(new AttributeTypeAndValue(
                        ObjectIdentifiers.organizationalUnitName,
                        ou
                )));
            }

            result.add(java.util.Collections.singletonList(new AttributeTypeAndValue(
                    ObjectIdentifiers.commonName,
                    (cn != null) ? cn : UUID.randomUUID().toString()
            )));

            return result;
        }

        private Validity validity() {
            long now = (notBefore != -1L) ? notBefore : System.currentTimeMillis();
            long notAfter = (this.notAfter != -1L) ? this.notAfter : now + DEFAULT_DURATION_MILLIS;
            return new Validity(now, notAfter);
        }

        private java.util.List<Extension> extensions() {
            java.util.List<Extension> result = new java.util.ArrayList<>();

            if (maxIntermediateCas != -1) {
                result.add(new Extension(
                        ObjectIdentifiers.basicConstraints,
                        true,
                        new BasicConstraints(
                                true,
                                3
                        )
                ));
            }

            if (!altNames.isEmpty()) {
                java.util.List<Pair<String, ByteString>> extensionValue = new java.util.ArrayList<>();
                for (String altName : altNames) {
                    if (canParseAsIpAddress(altName)) {
                        extensionValue.add(new Pair<>(generalNameIpAddress, ByteString.Companion.of(InetAddress.getByName(altName).getAddress())));
                    } else {
                        extensionValue.add(new Pair<>(generalNameDnsName, ByteString.Companion.encodeUtf8(altName)));
                    }
                }
                result.add(new Extension(
                        ObjectIdentifiers.subjectAlternativeName,
                        true,
                        extensionValue
                ));
            }

            return result;
        }

        private AlgorithmIdentifier signatureAlgorithm(KeyPair signedByKeyPair) {
            return (signedByKeyPair.getPrivate() instanceof RSAPrivateKey) ?
                    new AlgorithmIdentifier(
                            ObjectIdentifiers.sha256WithRSAEncryption,
                            null
                    ) :
                    new AlgorithmIdentifier(
                            ObjectIdentifiers.sha256withEcdsa,
                            ByteString.Companion.EMPTY
                    );
        }

        private KeyPair generateKeyPair() {
            try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(keyAlgorithm);
                keyPairGenerator.initialize(keySize, new SecureRandom());
                return keyPairGenerator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed to generate key pair", e);
            }
        }

        private static final long DEFAULT_DURATION_MILLIS = 1000L * 60 * 60 * 24; // 24 hours.

        static {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final Pattern PEM_REGEX = Pattern.compile("-----BEGIN ([!-,.-~ ]*)-----(.*?)-----END \\1-----");

    public static HeldCertificate decode(String certificateAndPrivateKeyPem) {
        String certificatePem = null;
        String pkcs8Base64 = null;
        Matcher matcher = PEM_REGEX.matcher(certificateAndPrivateKeyPem);
        while (matcher.find()) {
            String label = matcher.group(1);
            switch (label) {
                case "CERTIFICATE":
                    if (certificatePem != null) {
                        throw new IllegalArgumentException("string includes multiple certificates");
                    }
                    certificatePem = matcher.group(0); // Keep --BEGIN-- and --END-- for certificates.
                    break;
                case "PRIVATE KEY":
                    if (pkcs8Base64 != null) {
                        throw new IllegalArgumentException("string includes multiple private keys");
                    }
                    pkcs8Base64 = matcher.group(2); // Include the contents only for PKCS8.
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
        X509Certificate certificate = CertificateAdapters.decodeCertificatePem(certificatePem);

        ByteString pkcs8Bytes = ByteString.Companion.decodeBase64(pkcs8Base64Text);
        String keyType;
        if (certificate.getPublicKey() instanceof ECPublicKey) {
            keyType = "EC";
        } else if (certificate.getPublicKey() instanceof RSAPublicKey) {
            keyType = "RSA";
        } else {
            throw new IllegalArgumentException("unexpected key type: " + certificate.getPublicKey());
        }

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
