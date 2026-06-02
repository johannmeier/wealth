package de.wsc.wealth.controller;

import de.wsc.wealth.service.ExchangeRateService;
import jakarta.servlet.http.HttpServletRequest;
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
    public void addAttributes(
            @CookieValue(name = "wealth-currency", defaultValue = "EUR") String displayCurrency,
            HttpServletRequest request,
            Model model) {
        BigDecimal rate = exchangeRateService.getEurToRate(displayCurrency);
        model.addAttribute("displayCurrency", displayCurrency);
        model.addAttribute("eurToDisplayRate", rate != null ? rate : BigDecimal.ONE);
        model.addAttribute("currentUri", request.getRequestURI());
    }
}
