package de.wsc.wealth.domain;

public enum OrderInterval {
    MONATLICH("Monatlich"),
    QUARTALSWEISE("Quartalsweise"),
    HALBJAEHRLICH("Halbjährlich"),
    JAEHRLICH("Jährlich");

    private final String label;

    OrderInterval(String label) { this.label = label; }

    public String getLabel() { return label; }
}
