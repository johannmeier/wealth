package de.wsc.wealth.dto;

public class CriteriaBadge {
    private String label;
    private String tooltip;
    private Integer colorIndex;

    public CriteriaBadge() {}

    public CriteriaBadge(String label, String tooltip, Integer colorIndex) {
        this.label = label;
        this.tooltip = tooltip;
        this.colorIndex = colorIndex;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getTooltip() { return tooltip; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
    public Integer getColorIndex() { return colorIndex; }
    public void setColorIndex(Integer colorIndex) { this.colorIndex = colorIndex; }
}
