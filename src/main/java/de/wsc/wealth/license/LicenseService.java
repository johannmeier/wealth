package de.wsc.wealth.license;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Reads and verifies the license key stored in wealth-config.properties (license.key). A
 * missing or invalid key simply means no paid features are enabled — the app never refuses to
 * start and never deletes data because of a license state; see LicenseFeature callers for the
 * read-path checks this gates.
 */
@Service
public class LicenseService {

    // Public half of the Ed25519 signing key. The private half is kept outside the repository —
    // see LicenseKeyGenerator — so this constant is safe to ship; it can only verify, not mint.
    private static final String PUBLIC_KEY_BASE64 =
        "MCowBQYDK2VwAyEA6gMeUBzzc36+dzCA0i6oDC+wo5MtlIh294nx3rIe/Jo=";

    private final LicensePayload payload;

    public LicenseService() {
        this.payload = parse(readLicenseKeyFromConfig()).orElse(null);
    }

    /** Validates a raw key string without touching the persisted config — used to check a key before saving it. */
    public Optional<LicensePayload> parse(String rawKey) {
        return rawKey != null ? LicenseCodec.verify(rawKey, decodedPublicKey()) : Optional.empty();
    }

    public boolean isFeatureEnabled(String feature) {
        return payload != null && !payload.isExpired() && payload.features().contains(feature);
    }

    public boolean isValid() {
        return payload != null && !payload.isExpired();
    }

    public boolean isExpired() {
        return payload != null && payload.isExpired();
    }

    public Set<String> getFeatures() {
        return payload != null ? payload.features() : Set.of();
    }

    public LocalDate getExpiresOn() {
        return payload != null ? payload.expiresOn() : null;
    }

    private static PublicKey decodedPublicKey() {
        return LicenseCodec.decodePublicKey(PUBLIC_KEY_BASE64);
    }

    private static String readLicenseKeyFromConfig() {
        String configPath = System.getProperty("wealth.config.path");
        if (configPath == null) return null;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(configPath))) {
            props.load(in);
        } catch (IOException e) {
            return null;
        }
        return props.getProperty("license.key");
    }
}
