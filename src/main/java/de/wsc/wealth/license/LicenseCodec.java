package de.wsc.wealth.license;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Signs and verifies license keys with Ed25519. The payload (feature list + optional expiry) is
 * plain UTF-8 text, appended with its 64-byte signature, then base64url-encoded — anyone can
 * decode and read a key's contents, but only the holder of the private key (see
 * LicenseKeyGenerator, kept outside this repository) can mint one that verifies against the
 * public key embedded in LicenseService.
 */
public final class LicenseCodec {

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "Ed25519";
    private static final int SIGNATURE_LENGTH = 64;

    private LicenseCodec() {}

    public static String sign(Set<String> features, LocalDate expiresOn, PrivateKey privateKey) {
        byte[] payloadBytes = encodePayload(features, expiresOn);
        byte[] signature = signBytes(payloadBytes, privateKey);
        byte[] combined = new byte[payloadBytes.length + signature.length];
        System.arraycopy(payloadBytes, 0, combined, 0, payloadBytes.length);
        System.arraycopy(signature, 0, combined, payloadBytes.length, signature.length);
        // Standard (not URL-safe) base64 is used deliberately: its alphabet never contains '-',
        // so the dash inserted by chunk() below can be stripped unambiguously on decode. The
        // URL-safe alphabet does use '-', which would corrupt payloads that happen to encode to
        // one — a bug caught by LicenseCodecTest's round-trip tests failing intermittently.
        return chunk(Base64.getEncoder().withoutPadding().encodeToString(combined));
    }

    public static Optional<LicensePayload> verify(String licenseKey, PublicKey publicKey) {
        if (licenseKey == null || licenseKey.isBlank()) return Optional.empty();
        try {
            byte[] combined = Base64.getDecoder().decode(licenseKey.replaceAll("[\\s-]", ""));
            if (combined.length <= SIGNATURE_LENGTH) return Optional.empty();
            byte[] payloadBytes = Arrays.copyOfRange(combined, 0, combined.length - SIGNATURE_LENGTH);
            byte[] signature = Arrays.copyOfRange(combined, combined.length - SIGNATURE_LENGTH, combined.length);
            if (!verifyBytes(payloadBytes, signature, publicKey)) return Optional.empty();
            return decodePayload(payloadBytes);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 key generation failed", e);
        }
    }

    public static String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encodePrivateKey(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublicKey(String base64) {
        try {
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid Ed25519 public key", e);
        }
    }

    public static PrivateKey decodePrivateKey(String base64) {
        try {
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid Ed25519 private key", e);
        }
    }

    private static byte[] encodePayload(Set<String> features, LocalDate expiresOn) {
        String featureList = features.stream().sorted().collect(Collectors.joining(","));
        String payload = VERSION + "|" + featureList + "|" + (expiresOn != null ? expiresOn.toString() : "");
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static Optional<LicensePayload> decodePayload(byte[] payloadBytes) {
        String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) return Optional.empty();
        Set<String> features = parts[1].isBlank() ? Set.of()
            : Arrays.stream(parts[1].split(",")).collect(Collectors.toSet());
        if (parts[2].isBlank()) return Optional.of(new LicensePayload(features, null));
        try {
            return Optional.of(new LicensePayload(features, LocalDate.parse(parts[2])));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static byte[] signBytes(byte[] data, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    private static boolean verifyBytes(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static String chunk(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 8) {
            if (i > 0) sb.append('-');
            sb.append(s, i, Math.min(i + 8, s.length()));
        }
        return sb.toString();
    }
}
