package de.wsc.wealth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fixes stale FK constraints left behind by H2 when Hibernate's ddl-auto=update
 * adds columns to a table. H2 copies the table (e.g. ACCOUNT → ACCOUNT_COPY_3_0)
 * and the referencing FK constraints end up pointing to the copy instead of the real table.
 */
@Component
public class DatabaseMigration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final JdbcTemplate jdbc;

    public DatabaseMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void dropStaleH2CopyConstraints() {
        log.info("Checking for stale H2 copy-table FK constraints...");
        List<Map<String, Object>> stale = jdbc.queryForList(
            "SELECT TABLE_NAME, CONSTRAINT_NAME " +
            "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
            "WHERE CONSTRAINT_NAME LIKE '%COPY%'"
        );

        log.info("Found {} potential stale constraint(s)", stale.size());
        for (Map<String, Object> row : stale) {
            String table      = row.get("TABLE_NAME").toString();
            String constraint = row.get("CONSTRAINT_NAME").toString();
            log.info("Stale constraint: {} on table {}", constraint, table);
            try {
                jdbc.execute("ALTER TABLE \"" + table + "\" DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");
                log.info("Dropped stale H2 copy-table FK constraint {} on {}", constraint, table);
            } catch (Exception e) {
                log.warn("Could not drop stale constraint {} on {}: {}", constraint, table, e.getMessage());
            }
        }
    }
}
