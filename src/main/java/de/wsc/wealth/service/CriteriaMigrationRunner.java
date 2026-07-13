package de.wsc.wealth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CriteriaMigrationRunner implements ApplicationRunner {

    private final CriteriaMigrationService migrationService;

    public CriteriaMigrationRunner(CriteriaMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrationService.seedSystemCriteria();
        migrationService.backfillAssetCriteriaValues();
        migrationService.dropLegacyAssetColumns();
    }
}
