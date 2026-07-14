package de.wsc.wealth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCheckServiceTest {

    @Test
    void isNewer_withHigherPatchVersion_returnsTrue() {
        assertThat(UpdateCheckService.isNewer("1.0.12", "1.0.11")).isTrue();
    }

    @Test
    void isNewer_withHigherMinorVersion_returnsTrue() {
        assertThat(UpdateCheckService.isNewer("1.1.0", "1.0.99")).isTrue();
    }

    @Test
    void isNewer_withSameVersion_returnsFalse() {
        assertThat(UpdateCheckService.isNewer("1.0.12", "1.0.12")).isFalse();
    }

    @Test
    void isNewer_withOlderVersion_returnsFalse() {
        assertThat(UpdateCheckService.isNewer("1.0.11", "1.0.12")).isFalse();
    }

    @Test
    void isNewer_withDifferentSegmentCounts_comparesMissingAsZero() {
        assertThat(UpdateCheckService.isNewer("1.1", "1.0.99")).isTrue();
        assertThat(UpdateCheckService.isNewer("1.0", "1.0.0")).isFalse();
    }

    @Test
    void isUpdateAvailable_withoutFetchedVersion_returnsFalse() {
        UpdateCheckService service = new UpdateCheckService(new tools.jackson.databind.ObjectMapper());

        assertThat(service.isUpdateAvailable()).isFalse();
    }
}
