package de.wsc.wealth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTest {

    @Test
    void isAutoPrice_withSymbol_returnsTrue() {
        Asset asset = new Asset();
        asset.setSymbol("GC=F");

        assertThat(asset.isAutoPrice()).isTrue();
    }

    @Test
    void isAutoPrice_withBlankSymbol_returnsFalse() {
        Asset asset = new Asset();
        asset.setSymbol("  ");

        assertThat(asset.isAutoPrice()).isFalse();
    }

    @Test
    void isAutoPrice_withNoSymbol_returnsFalse() {
        Asset asset = new Asset();

        assertThat(asset.isAutoPrice()).isFalse();
    }
}
