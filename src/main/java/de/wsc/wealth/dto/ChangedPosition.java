package de.wsc.wealth.dto;

import java.math.BigDecimal;

public record ChangedPosition(String name, String identifier, BigDecimal oldQuantity, BigDecimal newQuantity) {}
