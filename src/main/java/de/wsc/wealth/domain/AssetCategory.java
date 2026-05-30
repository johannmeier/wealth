package de.wsc.wealth.domain;

public enum AssetCategory {
    BOERSENGEHANDELT("Börsengehandelt"),
    EDELMETALL("Edelmetall"),
    SONSTIGE("Sonstige");

    private final String label;

    AssetCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
