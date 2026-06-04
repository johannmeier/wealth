package de.wsc.wealth.domain;

public enum DistributionPolicy {
    AUSSCHUETTEND("Ausschüttend"),
    THESAURIEREND("Thesaurierend");

    private final String label;

    DistributionPolicy(String label) { this.label = label; }

    public String getLabel() { return label; }
}
