package de.wsc.wealth.license;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseCodecTest {

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @BeforeEach
    void setUp() {
        KeyPair keyPair = LicenseCodec.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    @Test
    void sign_thenVerify_roundTripsFeaturesAndNoExpiry() {
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS, LicenseFeature.CUSTOM_CRITERIA), null, privateKey);

        Optional<LicensePayload> result = LicenseCodec.verify(key, publicKey);

        assertThat(result).isPresent();
        assertThat(result.get().features()).containsExactlyInAnyOrder(LicenseFeature.COINS, LicenseFeature.CUSTOM_CRITERIA);
        assertThat(result.get().expiresOn()).isNull();
    }

    @Test
    void sign_thenVerify_roundTripsExpiry() {
        LocalDate expires = LocalDate.of(2027, 12, 31);
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS), expires, privateKey);

        Optional<LicensePayload> result = LicenseCodec.verify(key, publicKey);

        assertThat(result).isPresent();
        assertThat(result.get().expiresOn()).isEqualTo(expires);
    }

    @Test
    void sign_withEmptyFeatures_roundTrips() {
        String key = LicenseCodec.sign(Set.of(), null, privateKey);

        Optional<LicensePayload> result = LicenseCodec.verify(key, publicKey);

        assertThat(result).isPresent();
        assertThat(result.get().features()).isEmpty();
    }

    @Test
    void verify_toleratesDashesAndWhitespace() {
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS), null, privateKey);
        String messy = "  " + key.replace("-", " - ") + "  ";

        assertThat(LicenseCodec.verify(messy, publicKey)).isPresent();
    }

    @Test
    void verify_withWrongPublicKey_fails() {
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS), null, privateKey);
        PublicKey otherPublicKey = LicenseCodec.generateKeyPair().getPublic();

        assertThat(LicenseCodec.verify(key, otherPublicKey)).isEmpty();
    }

    @Test
    void verify_withTamperedPayload_fails() {
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS), null, privateKey);
        // Flip one character in the middle of the encoded string to simulate tampering
        // (e.g. someone hand-editing a key to add a feature it wasn't signed for).
        char[] chars = key.toCharArray();
        int i = chars.length / 2;
        chars[i] = chars[i] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThat(LicenseCodec.verify(tampered, publicKey)).isEmpty();
    }

    @Test
    void verify_withGarbageInput_returnsEmpty() {
        assertThat(LicenseCodec.verify("not-a-license-key", publicKey)).isEmpty();
        assertThat(LicenseCodec.verify("", publicKey)).isEmpty();
        assertThat(LicenseCodec.verify(null, publicKey)).isEmpty();
    }

    @Test
    void verify_withTruncatedKey_returnsEmpty() {
        String key = LicenseCodec.sign(Set.of(LicenseFeature.COINS), null, privateKey);
        String truncated = key.substring(0, key.length() / 2).replace("-", "");

        assertThat(LicenseCodec.verify(truncated, publicKey)).isEmpty();
    }

    @Test
    void encodeDecodePublicKey_roundTrips() {
        String encoded = LicenseCodec.encodePublicKey(publicKey);
        PublicKey decoded = LicenseCodec.decodePublicKey(encoded);

        assertThat(decoded).isEqualTo(publicKey);
    }

    @Test
    void encodeDecodePrivateKey_roundTrips() {
        String encoded = LicenseCodec.encodePrivateKey(privateKey);
        PrivateKey decoded = LicenseCodec.decodePrivateKey(encoded);

        assertThat(decoded).isEqualTo(privateKey);
    }

    @Test
    void licensePayload_isExpired_pastDateIsExpired() {
        LicensePayload payload = new LicensePayload(Set.of(), LocalDate.now().minusDays(1));
        assertThat(payload.isExpired()).isTrue();
    }

    @Test
    void licensePayload_isExpired_futureDateIsNotExpired() {
        LicensePayload payload = new LicensePayload(Set.of(), LocalDate.now().plusDays(1));
        assertThat(payload.isExpired()).isFalse();
    }

    @Test
    void licensePayload_isExpired_noExpiryIsNeverExpired() {
        LicensePayload payload = new LicensePayload(Set.of(LicenseFeature.COINS), null);
        assertThat(payload.isExpired()).isFalse();
    }
}
