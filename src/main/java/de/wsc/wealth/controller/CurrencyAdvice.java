package de.wsc.wealth.controller;

import de.wsc.wealth.service.ExchangeRateService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.math.BigDecimal;

@ControllerAdvice
public class CurrencyAdvice {

    private final ExchangeRateService exchangeRateService;

    public CurrencyAdvice(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @ModelAttribute
    public void addCurrencyAttributes(
            @CookieValue(name = "wealth-currency", defaultValue = "EUR") String displayCurrency,
            Model model) {
        BigDecimal rate = exchangeRateService.getEurToRate(displayCurrency);
        model.addAttribute("displayCurrency", displayCurrency);
        model.addAttribute("eurToDisplayRate", rate != null ? rate : BigDecimal.ONE);
    }
}
