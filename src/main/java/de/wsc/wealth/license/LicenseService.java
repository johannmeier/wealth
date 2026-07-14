package de.wsc.wealth.license;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.SystemCriteria;
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

    /**
     * The single source of truth for which criterion a given license unlocks. The Wittmann
     * criterion is seeded like any other system criterion (see CriteriaMigrationService) but is
     * gated by its own {@link LicenseFeature#WITTMANN} feature instead of
     * {@link LicenseFeature#CUSTOM_CRITERIA} — a Wittmann-only license makes exactly that one
     * criterion usable, without unlocking custom-criteria management or the other system
     * criteria (Kategorie, Wertpapierart, …).
     */
    public boolean isCriterionUsable(CriteriaDefinition definition) {
        if (SystemCriteria.WITTMANN.equals(definition.getSystemCode())) {
            return isFeatureEnabled(LicenseFeature.WITTMANN);
        }
        return isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA);
    }

    /** Whether any criterion at all could be usable — drives generic "criteria exist" UI (columns, badges). */
    public boolean hasAnyCriteriaFeature() {
        return isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA) || isFeatureEnabled(LicenseFeature.WITTMANN);
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
