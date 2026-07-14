package de.wsc.wealth.license;

import java.time.LocalDate;
import java.util.Set;

public record LicensePayload(Set<String> features, LocalDate expiresOn) {

    public boolean isExpired() {
        return expiresOn != null && LocalDate.now().isAfter(expiresOn);
    }
}
