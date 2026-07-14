package de.wsc.wealth.dto;

public class CriteriaBadge {
    private String label;
    private String messageKey;
    private String tooltip;

    public CriteriaBadge() {}

    public CriteriaBadge(String label, String messageKey, String tooltip) {
        this.label = label;
        this.messageKey = messageKey;
        this.tooltip = tooltip;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }
    public String getTooltip() { return tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
}
