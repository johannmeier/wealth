package de.wsc.wealth.domain;

public enum CoinMetal {
    GOLD("Gold", "GC=F"),
    SILBER("Silber", "SI=F"),
    PLATIN("Platin", "PL=F");

    private final String label;
    private final String yahooSymbol;

    CoinMetal(String label, String yahooSymbol) {
        this.label = label;
        this.yahooSymbol = yahooSymbol;
    }

    public String getLabel() { return label; }
    public String getYahooSymbol() { return yahooSymbol; }
}
