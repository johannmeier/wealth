package de.wsc.wealth.domain;

public enum AssetType {
    AKTIE("Aktie"),
    AKTIENFONDS("Aktienfonds"),
    ETF("ETF"),
    ANLEIHE("Anleihe"),
    WAEHRUNG("Währung"),
    EDELMETALL("Edelmetall"),
    SONSTIGE("Sonstige");

    private final String label;

    AssetType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
