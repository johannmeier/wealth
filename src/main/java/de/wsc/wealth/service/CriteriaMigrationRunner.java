package de.wsc.wealth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CriteriaMigrationRunner implements ApplicationRunner {

    private final CriteriaMigrationService migrationService;
    private final CriteriaService criteriaService;

    public CriteriaMigrationRunner(CriteriaMigrationService migrationService, CriteriaService criteriaService) {
        this.migrationService = migrationService;
        this.criteriaService = criteriaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrationService.seedSystemCriteria();
        migrationService.backfillAssetCriteriaValues();
        migrationService.dropLegacyAssetColumns();
        migrationService.clearLegacySystemCodes();
        criteriaService.assignMissingColorIndexes();
    }
}
