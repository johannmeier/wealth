package de.wsc.wealth.controller;

import de.wsc.wealth.license.LicenseFeature;
import de.wsc.wealth.license.LicenseService;
import de.wsc.wealth.service.CriteriaService;
import de.wsc.wealth.service.ExchangeRateService;
import de.wsc.wealth.service.UpdateCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.math.BigDecimal;

@ControllerAdvice
public class CurrencyAdvice {

    private final ExchangeRateService exchangeRateService;
    private final CriteriaService criteriaService;
    private final LicenseService licenseService;
    private final UpdateCheckService updateCheckService;

    public CurrencyAdvice(ExchangeRateService exchangeRateService, CriteriaService criteriaService,
                          LicenseService licenseService, UpdateCheckService updateCheckService) {
        this.exchangeRateService = exchangeRateService;
        this.criteriaService = criteriaService;
        this.licenseService = licenseService;
        this.updateCheckService = updateCheckService;
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
        model.addAttribute("navCriteriaDefinitions", criteriaService.findAll());
        model.addAttribute("licensedCoins", licenseService.isFeatureEnabled(LicenseFeature.COINS));
        // Gates full criteria management (/criteria, "+ Neu"): custom-criteria creation and the
        // other system criteria, but not the Wittmann-only criterion.
        model.addAttribute("licensedCustomCriteria", licenseService.isFeatureEnabled(LicenseFeature.CUSTOM_CRITERIA));
        // Gates the generic "criteria exist" display (Eigenschaften/Index columns): true for
        // either a full custom-criteria license or a Wittmann-only one.
        model.addAttribute("licensedAnyCriteria", licenseService.hasAnyCriteriaFeature());
        model.addAttribute("updateAvailable", updateCheckService.isUpdateAvailable());
        model.addAttribute("latestVersion", updateCheckService.getLatestVersion());
        model.addAttribute("releaseUrl", updateCheckService.getReleaseUrl());
    }
}
