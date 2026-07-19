package de.wsc.wealth.license;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.SystemCriteria;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LicenseService's real constructor does file I/O against wealth.config.path and verifies
 * against the embedded production public key, neither of which is something a unit test should
 * depend on. These tests instead build an instance via the real (safe, side-effect-free) no-arg
 * constructor and then inject a known payload by reflection, to exercise the pure logic methods
 * in isolation.
 */
class LicenseServiceTest {

    @Test
    void isCriterionUsable_wittmannDefinition_requiresWittmannFeatureSpecifically() throws Exception {
        LicenseService wittmannOnly = withPayload(new LicensePayload(Set.of(LicenseFeature.WITTMANN), null));
        LicenseService customOnly = withPayload(new LicensePayload(Set.of(LicenseFeature.CUSTOM_CRITERIA), null));
        CriteriaDefinition wittmannDef = definition(SystemCriteria.WITTMANN);

        assertThat(wittmannOnly.isCriterionUsable(wittmannDef)).isTrue();
        assertThat(customOnly.isCriterionUsable(wittmannDef)).isFalse();
    }

    @Test
    void isCriterionUsable_customDefinition_requiresCustomCriteriaFeature() throws Exception {
        LicenseService customOnly = withPayload(new LicensePayload(Set.of(LicenseFeature.CUSTOM_CRITERIA), null));
        LicenseService wittmannOnly = withPayload(new LicensePayload(Set.of(LicenseFeature.WITTMANN), null));
        CriteriaDefinition customDef = definition(null);

        assertThat(customOnly.isCriterionUsable(customDef)).isTrue();
        assertThat(wittmannOnly.isCriterionUsable(customDef)).isFalse();
    }

    @Test
    void hasAnyCriteriaFeature_trueForEitherFeatureAloneOrTogether() throws Exception {
        assertThat(withPayload(new LicensePayload(Set.of(LicenseFeature.WITTMANN), null)).hasAnyCriteriaFeature()).isTrue();
        assertThat(withPayload(new LicensePayload(Set.of(LicenseFeature.CUSTOM_CRITERIA), null)).hasAnyCriteriaFeature()).isTrue();
        assertThat(withPayload(new LicensePayload(Set.of(LicenseFeature.WITTMANN, LicenseFeature.CUSTOM_CRITERIA), null)).hasAnyCriteriaFeature()).isTrue();
    }

    @Test
    void hasAnyCriteriaFeature_falseWithoutEitherFeature() throws Exception {
        assertThat(withPayload(new LicensePayload(Set.of(LicenseFeature.COINS), null)).hasAnyCriteriaFeature()).isFalse();
        assertThat(withPayload(null).hasAnyCriteriaFeature()).isFalse();
    }

    @Test
    void isFeatureEnabled_whenExpired_returnsFalseAndReportsExpired() throws Exception {
        LicenseService expired = withPayload(new LicensePayload(Set.of(LicenseFeature.COINS), LocalDate.now().minusDays(1)));

        assertThat(expired.isFeatureEnabled(LicenseFeature.COINS)).isFalse();
        assertThat(expired.isValid()).isFalse();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test
    void isFeatureEnabled_withNoPayload_returnsFalse() throws Exception {
        LicenseService unlicensed = withPayload(null);

        assertThat(unlicensed.isFeatureEnabled(LicenseFeature.COINS)).isFalse();
        assertThat(unlicensed.isValid()).isFalse();
        assertThat(unlicensed.isExpired()).isFalse();
        assertThat(unlicensed.getFeatures()).isEmpty();
        assertThat(unlicensed.getExpiresOn()).isNull();
    }

    private static CriteriaDefinition definition(String systemCode) {
        CriteriaDefinition d = new CriteriaDefinition();
        d.setSystemCode(systemCode);
        return d;
    }

    private static LicenseService withPayload(LicensePayload payload) throws Exception {
        LicenseService service = new LicenseService();
        Field field = LicenseService.class.getDeclaredField("payload");
        field.setAccessible(true);
        field.set(service, payload);
        return service;
    }
}
