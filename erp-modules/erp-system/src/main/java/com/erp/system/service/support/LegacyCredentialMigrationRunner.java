package com.erp.system.service.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.erp.system.domain.maintenance.LegacyCredentialMigrationAudit;
import com.erp.system.mapper.LegacyCredentialMaintenanceMapper;

@Component
@Profile("credential-maintenance")
@ConditionalOnProperty(prefix = "system.credential-maintenance", name = "enabled", havingValue = "true")
public class LegacyCredentialMigrationRunner implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(LegacyCredentialMigrationRunner.class);
    private static final String REQUIRED_CONFIRMATION = "I_UNDERSTAND_CREDENTIAL_MAINTENANCE";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final LegacyCredentialMaintenanceProperties properties;
    private final LegacyCredentialMaintenanceMapper mapper;
    private final DataSource dataSource;
    private final ConfigurableApplicationContext context;
    private final LegacyCredentialAuditor auditor = new LegacyCredentialAuditor();

    public LegacyCredentialMigrationRunner(LegacyCredentialMaintenanceProperties properties,
            LegacyCredentialMaintenanceMapper mapper, DataSource dataSource,
            ConfigurableApplicationContext context)
    {
        this.properties = properties;
        this.mapper = mapper;
        this.dataSource = dataSource;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception
    {
        validateStaticGuards();
        String actualDatabase = currentDatabase();
        validateDatabase(actualDatabase);
        String legacyPassword = mapper.selectConfigValueForMaintenance(properties.getLegacyConfigKey());
        LegacyCredentialAuditResult result;
        try
        {
            result = auditor.audit(mapper.selectCandidates(), legacyPassword);
        }
        finally
        {
            legacyPassword = null;
        }

        String mode = properties.getMode().trim().toUpperCase(Locale.ROOT);
        String phase = "FINAL_READINESS".equals(mode) ? "FINAL_READINESS" : "DISCOVERY";
        String status = "FINAL_READINESS".equals(mode) && result.getSharedMatches() == 0
                ? "CONFIRMED" : ("FINAL_READINESS".equals(mode) ? "BLOCKED" : "COMPLETED");
        mapper.insertAudit(toAudit(result, actualDatabase, phase, status));

        result.getRows().forEach(row -> log.info(
                "CREDENTIAL_MAINTENANCE_ROW userId={} deptId={} accountStatus={} classification={}",
                row.getUserId(), row.getDeptId(), row.getAccountStatus(), row.getClassification()));
        log.info("CREDENTIAL_MAINTENANCE_SUMMARY phase={} total={} sharedMatches={} activeSharedMatches={} changeRequired={} temporary={} status={} digest={}",
                phase, result.getTotal(), result.getSharedMatches(), result.getActiveSharedMatches(),
                result.getChangeRequired(), result.getTemporary(), status, result.getDigest());

        if ("BLOCKED".equals(status))
        {
            throw new IllegalStateException("credential finalization readiness is blocked");
        }
        context.close();
    }

    void validateStaticGuards()
    {
        if (!REQUIRED_CONFIRMATION.equals(properties.getConfirmation()))
        {
            throw new IllegalStateException("explicit credential maintenance confirmation is required");
        }
        if (!SAFE_VALUE.matcher(value(properties.getBatchId())).matches())
        {
            throw new IllegalStateException("safe maintenance batch id is required");
        }
        if (!SAFE_VALUE.matcher(value(properties.getTargetDatabase())).matches())
        {
            throw new IllegalStateException("safe target database is required");
        }
        if (properties.getOperatorUserId() == null || properties.getOperatorUserId() <= 0)
        {
            throw new IllegalStateException("numeric operator user id is required");
        }
        if (properties.getLegacyConfigKey() == null || properties.getLegacyConfigKey().isBlank())
        {
            throw new IllegalStateException("legacy config key must be supplied explicitly");
        }
        String mode = value(properties.getMode()).toUpperCase(Locale.ROOT);
        if (!Set.of("AUDIT", "FINAL_READINESS").contains(mode))
        {
            throw new IllegalStateException("unsupported credential maintenance mode");
        }
        String webType = context.getEnvironment().getProperty("spring.main.web-application-type", "");
        if (!"none".equalsIgnoreCase(webType))
        {
            throw new IllegalStateException("credential maintenance must run as a non-Web process");
        }
    }

    void validateDatabase(String actualDatabase)
    {
        List<String> approved = properties.getApprovedDatabases();
        if (!properties.getTargetDatabase().equals(actualDatabase)
                || approved == null || !approved.contains(actualDatabase))
        {
            throw new IllegalStateException("connected database is not explicitly approved");
        }
    }

    private String currentDatabase() throws Exception
    {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()"))
        {
            if (!resultSet.next() || resultSet.getString(1) == null)
            {
                throw new IllegalStateException("database name is unavailable");
            }
            return resultSet.getString(1);
        }
    }

    private LegacyCredentialMigrationAudit toAudit(LegacyCredentialAuditResult result,
            String database, String phase, String status)
    {
        LegacyCredentialMigrationAudit audit = new LegacyCredentialMigrationAudit();
        audit.setBatchId(properties.getBatchId());
        audit.setPhase(phase);
        audit.setTargetDatabase(database);
        audit.setTotalUserCount(result.getTotal());
        audit.setSharedMatchCount(result.getSharedMatches());
        audit.setActiveSharedMatchCount(result.getActiveSharedMatches());
        audit.setChangeRequiredCount(result.getChangeRequired());
        audit.setTemporaryCount(result.getTemporary());
        audit.setOperatorUserId(properties.getOperatorUserId());
        audit.setCandidateDigest(result.getDigest());
        audit.setStatus(status);
        return audit;
    }

    private static String value(String value)
    {
        return value == null ? "" : value.trim();
    }
}
