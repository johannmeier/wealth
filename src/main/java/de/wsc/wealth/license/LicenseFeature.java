package de.wsc.wealth.license;

/** Feature codes a license key can grant. Kept as plain strings so new features never require a schema/format change. */
public final class LicenseFeature {

    public static final String COINS = "COINS";
    public static final String CUSTOM_CRITERIA = "CUSTOM_CRITERIA";

    private LicenseFeature() {}
}
