package com.erp.inventory.sql;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class StockCheckRestartSqlSourceTest {
    @Test void initializationMirrorAndMigrationAreIdentical() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("sql")))root=root.getParent();
        assertThat(Files.readAllBytes(root.resolve("sql/erp_inventory_stock_check_restart_20260912.sql"))).isEqualTo(Files.readAllBytes(root.resolve("docker/mysql/db/erp_inventory_stock_check_restart_20260912.sql")));
    }
}
