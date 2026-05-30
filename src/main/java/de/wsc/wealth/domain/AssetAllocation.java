package de.wsc.wealth.domain;

public enum AssetAllocation {
    RISIKOBEHAFTET("Risikobehaftet"),
    RISIKOFREI("Risikofrei");

    private final String label;

    AssetAllocation(String label) { this.label = label; }

    public String getLabel() { return label; }
}
