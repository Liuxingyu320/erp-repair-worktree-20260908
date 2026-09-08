package com.erp.inventory.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvTransferReservation;

/**
 * Opt-in MySQL 8 contract test for the replenishment source SQL.
 * Run with ERP_REPLENISHMENT_MYSQL_INTEGRATION=1. The test only creates and
 * drops the exact isolated database named below.
 */
@DisplayName("补货来源真实 MySQL 隔离验证")
class InvTransferSourceMySqlIntegrationTest
{
    private static final String ENABLE_ENV =
            "ERP_REPLENISHMENT_MYSQL_INTEGRATION";
    private static final String DATABASE =
            "erp_replenishment_source_integration";
    private static final String SERVER_URL =
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DATABASE_URL =
            "jdbc:mysql://127.0.0.1:3306/" + DATABASE
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static SqlSessionFactory sqlSessionFactory;
    private static boolean enabled;

    @BeforeAll
    static void createIsolatedDatabase() throws Exception
    {
        enabled = "1".equals(System.getenv(ENABLE_ENV));
        Assumptions.assumeTrue(enabled,
                "set " + ENABLE_ENV + "=1 to run the local MySQL contract");
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = serverConnection();
                Statement statement = connection.createStatement())
        {
            statement.execute("drop database if exists " + DATABASE);
            statement.execute("create database " + DATABASE
                    + " character set utf8mb4 collate utf8mb4_unicode_ci");
        }
        try (Connection connection = databaseConnection();
                Statement statement = connection.createStatement())
        {
            statement.execute("""
                    create table sys_dept (
                        dept_id bigint primary key,
                        parent_id bigint,
                        ancestors varchar(255),
                        dept_name varchar(100),
                        order_num int default 0,
                        dept_type varchar(32),
                        status char(1),
                        del_flag char(1)
                    ) engine=InnoDB
                    """);
            statement.execute("""
                    create table inv_product (
                        product_id bigint primary key,
                        product_name varchar(100),
                        product_code varchar(64),
                        shop_dept_id bigint,
                        status char(1),
                        del_flag char(1)
                    ) engine=InnoDB
                    """);
            statement.execute("""
                    create table inv_gift_box (
                        gift_id bigint primary key,
                        gift_name varchar(100),
                        gift_code varchar(64),
                        status char(1),
                        del_flag char(1)
                    ) engine=InnoDB
                    """);
            statement.execute("""
                    create table inv_stock (
                        stock_id bigint primary key,
                        item_type varchar(32),
                        item_id bigint,
                        product_id bigint,
                        shop_dept_id bigint not null,
                        warehouse_id bigint not null,
                        current_quantity decimal(18,4),
                        locked_quantity decimal(18,4),
                        available_quantity decimal(18,4),
                        cost_price decimal(18,4),
                        total_cost decimal(18,4),
                        version bigint not null default 0,
                        update_by varchar(64),
                        update_time datetime
                    ) engine=InnoDB
                    """);
            statement.execute("""
                    create table inv_transfer_reservation (
                        reservation_id bigint primary key auto_increment,
                        transfer_id bigint,
                        transfer_detail_id bigint,
                        reservation_round int,
                        stock_id bigint,
                        item_type varchar(32),
                        item_id bigint,
                        source_location_dept_id bigint,
                        reserved_quantity decimal(18,4),
                        consumed_quantity decimal(18,4),
                        released_quantity decimal(18,4),
                        status varchar(32),
                        version bigint,
                        create_by varchar(64),
                        create_time datetime,
                        update_by varchar(64),
                        update_time datetime
                    ) engine=InnoDB
                    """);
            statement.execute("""
                    insert into sys_dept
                        (dept_id,parent_id,ancestors,dept_name,dept_type,status,del_flag)
                    values
                        (100,0,'0','业务根A','GROUP','0','0'),
                        (105,100,'0,100','白茶归属组织','COMPANY','0','0'),
                        (110,105,'0,100,105','主仓库','WAREHOUSE','0','0'),
                        (120,100,'0,100','测试门店','STORE','0','0'),
                        (200,0,'0','业务根B','GROUP','0','0'),
                        (210,200,'0,200','跨根仓库','WAREHOUSE','0','0'),
                        (211,200,'0,200,100','路径包含错误根的仓库','WAREHOUSE','0','0'),
                        (220,200,'0,200','跨根门店','STORE','0','0'),
                        (300,0,'0','停用业务根','GROUP','1','0'),
                        (310,300,'0,300','停用根仓库','WAREHOUSE','0','0'),
                        (320,300,'0,300','停用根门店','STORE','0','0'),
                        (410,400,'0,400','缺失根仓库','WAREHOUSE','0','0'),
                        (420,400,'0,400','缺失根门店','STORE','0','0')
                    """);
            statement.execute("""
                    insert into inv_product
                        (product_id,product_name,product_code,shop_dept_id,status,del_flag)
                    values
                        (501,'白茶富阳专用','WHITE-TEA-501',105,'0','0'),
                        (502,'跨根商品','CROSS-ROOT-502',200,'0','0'),
                        (503,'停用商品','DISABLED-503',100,'1','0'),
                        (504,'零库存商品','ZERO-504',100,'0','0')
                    """);
            statement.execute("""
                    insert into inv_gift_box
                        (gift_id,gift_name,gift_code,status,del_flag)
                    values (601,'测试礼盒','GIFT-601','0','0')
                    """);
            statement.execute("""
                    insert into inv_stock
                        (stock_id,item_type,item_id,product_id,shop_dept_id,
                         warehouse_id,current_quantity,locked_quantity,
                         available_quantity,cost_price,total_cost,version)
                    values
                        (1501,'product',501,501,110,110,1000,0,1000,1,1000,0),
                        (1502,'product',502,502,110,110,10,0,10,1,10,0),
                        (1503,'product',503,503,110,110,10,0,10,1,10,0),
                        (1504,'product',504,504,110,110,0,0,0,1,0,0),
                        (1601,'gift',601,null,110,110,8,0,8,0,0,0)
                    """);
            connection.commit();
        }

        DataSource dataSource = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver", DATABASE_URL, "root", "");
        Configuration configuration = new Configuration(new Environment(
                "mysql-integration", new JdbcTransactionFactory(),
                dataSource));
        configuration.getTypeAliasRegistry().registerAlias("InvStock",
                InvStock.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "InvTransferReservation", InvTransferReservation.class);
        for (String xml : List.of(
                "mapper/inventory/InvDeptScopeMapper.xml",
                "mapper/inventory/InvTransferReservationMapper.xml"))
        {
            try (InputStream input = Resources.getResourceAsStream(xml))
            {
                new XMLMapperBuilder(input, configuration, xml,
                        configuration.getSqlFragments()).parse();
            }
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder()
                .build(configuration);
    }

    @BeforeEach
    void restoreMutableFacts() throws Exception
    {
        try (Connection connection = databaseConnection();
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate(
                    "update inv_product set status='0',shop_dept_id=105 where product_id=501");
            statement.executeUpdate(
                    "update sys_dept set status='0',del_flag='0' where dept_id=105");
            statement.executeUpdate(
                    "update sys_dept set parent_id=105,ancestors='0,100,105',status='0',del_flag='0' where dept_id=110");
            statement.executeUpdate(
                    "update sys_dept set ancestors='0,100',status='0',del_flag='0' where dept_id=120");
            connection.commit();
        }
    }

    @AfterAll
    static void dropOnlyIsolatedDatabase() throws Exception
    {
        if (!enabled)
        {
            return;
        }
        try (Connection connection = serverConnection();
                Statement statement = connection.createStatement())
        {
            statement.execute("drop database if exists " + DATABASE);
        }
    }

    @Test
    @DisplayName("同根白茶与礼盒可见，跨根、零库存和停用商品不可见")
    void shouldFilterRealSourceCandidates() throws Exception
    {
        assertThat(replenishmentWarehouseCandidates(120L))
                .containsExactly(110L);
        assertThat(replenishmentSourceItemCandidates(110L))
                .containsExactly(501L, 601L);
    }

    @Test
    @DisplayName("锁内路线拒绝跨根、停用根和缺失根")
    void shouldFailClosedForInvalidBusinessRoots()
    {
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            InvTransferReservationMapper mapper = session.getMapper(
                    InvTransferReservationMapper.class);
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 120L)).isEqualTo(100L);
            session.rollback();
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 220L)).isNull();
            session.rollback();
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    310L, 320L)).isNull();
            session.rollback();
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    410L, 420L)).isNull();
            session.rollback();
        }
    }

    @Test
    @DisplayName("商品状态归属、零库存、礼盒与返仓契约真实执行")
    void shouldApplyAtomicItemQualificationAndKeepReturnIndependent()
    {
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            InvTransferReservationMapper mapper = session.getMapper(
                    InvTransferReservationMapper.class);
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 120L)).isEqualTo(100L);
            InvStock whiteTea = mapper.selectStockForUpdate("product",
                    501L, 110L, true);
            assertThat(whiteTea).isNotNull();
            assertThat(whiteTea.getAvailableQuantity())
                    .isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(mapper.selectStockForUpdate("product", 502L, 110L,
                    true)).isNull();
            assertThat(mapper.selectStockForUpdate("product", 503L, 110L,
                    true)).isNull();
            assertThat(mapper.selectStockForUpdate("product", 504L, 110L,
                    true).getAvailableQuantity()).isEqualByComparingTo(
                            BigDecimal.ZERO);
            assertThat(mapper.selectStockForUpdate("gift", 601L, 110L,
                    true)).isNotNull();
            assertThat(mapper.selectStockForUpdate("product", 502L, 110L,
                    false)).isNotNull();
            assertThat(mapper.selectStockForUpdate("product", 503L, 110L,
                    false)).isNull();
            session.rollback();
        }
    }

    @Test
    @DisplayName("停用或删除 owner 在专用范围、候选和锁内重验均失败关闭")
    void shouldRejectDisabledAndDeletedOwnersAtBothLayers()
            throws Exception
    {
        assertOwnerAvailable();

        executeUpdate(
                "update sys_dept set status='1' where dept_id=105");
        assertOwnerRejectedAtScopeCandidateAndLock();

        executeUpdate(
                "update sys_dept set status='0',del_flag='1' where dept_id=105");
        assertOwnerRejectedAtScopeCandidateAndLock();
    }

    @Test
    @DisplayName("停用或删除来源仓在专用范围、候选和锁内路线均失败关闭")
    void shouldRejectDisabledAndDeletedSourceWarehouseAtAllLayers()
            throws Exception
    {
        executeUpdate(
                "update sys_dept set status='1' where dept_id=110");
        assertSourceWarehouseRejectedAtAllLayers();

        executeUpdate(
                "update sys_dept set status='0',del_flag='1' where dept_id=110");
        assertSourceWarehouseRejectedAtAllLayers();
    }

    @Test
    @DisplayName("锁库后商品和路线事实不能并发漂移")
    void shouldBlockConcurrentEligibilityAndRouteDrift() throws Exception
    {
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            InvTransferReservationMapper mapper = session.getMapper(
                    InvTransferReservationMapper.class);
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 120L)).isEqualTo(100L);
            assertThat(mapper.selectStockForUpdate("product", 501L, 110L,
                    true)).isNotNull();

            assertLockTimeout(
                    "update inv_product set status='1' where product_id=501");
            assertLockTimeout(
                    "update inv_product set shop_dept_id=200 where product_id=501");
            assertLockTimeout(
                    "update sys_dept set status='1' where dept_id=105");
            assertLockTimeout(
                    "update sys_dept set ancestors='0,200' where dept_id=120");
            assertLockTimeout(
                    "update sys_dept set status='1' where dept_id=100");
            session.rollback();
        }
    }

    private static List<Long> replenishmentWarehouseCandidates(Long storeId)
            throws Exception
    {
        String sql = """
                select d.dept_id
                from sys_dept d
                inner join sys_dept current_dept
                    on current_dept.dept_id = ?
                inner join sys_dept scope_root on scope_root.dept_id = cast(
                    case
                        when current_dept.ancestors is null
                             or current_dept.ancestors = ''
                             or current_dept.ancestors = '0'
                            then current_dept.dept_id
                        when current_dept.ancestors like '0,%'
                            then substring_index(substring_index(
                                current_dept.ancestors, ',', 2), ',', -1)
                        else substring_index(current_dept.ancestors, ',', 1)
                    end as unsigned)
                where current_dept.del_flag='0'
                  and current_dept.status='0'
                  and current_dept.dept_type='STORE'
                  and scope_root.del_flag='0'
                  and scope_root.status='0'
                  and d.del_flag='0'
                  and d.status='0'
                  and d.dept_type='WAREHOUSE'
                  and cast(case
                      when d.ancestors is null or d.ancestors=''
                           or d.ancestors='0' then d.dept_id
                      when d.ancestors like '0,%'
                          then substring_index(substring_index(
                              d.ancestors,',',2),',',-1)
                      else substring_index(d.ancestors,',',1)
                  end as unsigned)=scope_root.dept_id
                order by d.dept_id
                """;
        return queryLongs(sql, storeId);
    }

    private static List<Long> replenishmentSourceItemCandidates(
            Long sourceWarehouseId) throws Exception
    {
        String sql = """
                select coalesce(s.item_id,s.product_id)
                from inv_stock s
                left join inv_product p
                  on p.product_id=s.product_id and p.del_flag='0'
                left join inv_gift_box gift
                  on coalesce(s.item_type,'product')='gift'
                 and gift.gift_id=coalesce(s.item_id,s.product_id)
                 and gift.del_flag='0'
                where s.shop_dept_id=? and s.warehouse_id=?
                  and exists (
                      select 1
                      from sys_dept stock_source
                      where stock_source.dept_id=?
                        and stock_source.del_flag='0'
                        and stock_source.status='0'
                        and stock_source.dept_type='WAREHOUSE')
                  and coalesce(s.available_quantity,s.current_quantity,0)>0
                  and ((coalesce(s.item_type,'product')='product'
                        and p.product_id is not null and p.status='0')
                       or (coalesce(s.item_type,'product')='gift'
                           and gift.gift_id is not null and gift.status='0'))
                  and (coalesce(s.item_type,'product')!='product'
                       or p.shop_dept_id in (
                           select owner.dept_id
                           from sys_dept owner
                           inner join sys_dept source_dept
                             on source_dept.dept_id=?
                           where source_dept.del_flag='0'
                             and source_dept.status='0'
                             and source_dept.dept_type='WAREHOUSE'
                             and owner.del_flag='0'
                             and owner.status='0'
                             and (owner.dept_id=source_dept.dept_id
                                  or find_in_set(source_dept.dept_id,
                                      owner.ancestors)
                                  or find_in_set(owner.dept_id,
                                      source_dept.ancestors))))
                order by coalesce(s.item_id,s.product_id)
                """;
        return queryLongs(sql, sourceWarehouseId, sourceWarehouseId,
                sourceWarehouseId, sourceWarehouseId);
    }

    private static void assertOwnerAvailable() throws Exception
    {
        assertThat(replenishmentSourceItemCandidates(110L))
                .containsExactly(501L, 601L);
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            assertThat(session.getMapper(InvDeptScopeMapper.class)
                    .selectActiveRelatedDeptIdsForReplenishment(110L))
                    .contains(105L, 110L);
            InvTransferReservationMapper mapper = session.getMapper(
                    InvTransferReservationMapper.class);
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 120L)).isEqualTo(100L);
            assertThat(mapper.selectStockForUpdate("product", 501L, 110L,
                    true)).isNotNull();
            session.rollback();
        }
    }

    private static void assertOwnerRejectedAtScopeCandidateAndLock()
            throws Exception
    {
        assertThat(replenishmentSourceItemCandidates(110L))
                .containsExactly(601L);
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            assertThat(session.getMapper(InvDeptScopeMapper.class)
                    .selectActiveRelatedDeptIdsForReplenishment(110L))
                    .doesNotContain(105L);
            InvTransferReservationMapper mapper = session.getMapper(
                    InvTransferReservationMapper.class);
            assertThat(mapper.selectWarehouseReplenishmentRouteForUpdate(
                    110L, 120L)).isEqualTo(100L);
            assertThat(mapper.selectStockForUpdate("product", 501L, 110L,
                    true)).isNull();
            session.rollback();
        }
    }

    private static void assertSourceWarehouseRejectedAtAllLayers()
            throws Exception
    {
        assertThat(replenishmentSourceItemCandidates(110L)).isEmpty();
        try (SqlSession session = sqlSessionFactory.openSession(false))
        {
            assertThat(session.getMapper(InvDeptScopeMapper.class)
                    .selectActiveRelatedDeptIdsForReplenishment(110L))
                    .isEmpty();
            assertThat(session.getMapper(InvTransferReservationMapper.class)
                    .selectWarehouseReplenishmentRouteForUpdate(110L, 120L))
                    .isNull();
            session.rollback();
        }
    }

    private static void executeUpdate(String sql) throws Exception
    {
        try (Connection connection = databaseConnection();
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate(sql);
            connection.commit();
        }
    }

    private static List<Long> queryLongs(String sql, Object... values)
            throws Exception
    {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(
                        sql))
        {
            for (int i = 0; i < values.length; i++)
            {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet resultSet = statement.executeQuery())
            {
                List<Long> result = new ArrayList<>();
                while (resultSet.next())
                {
                    result.add(resultSet.getLong(1));
                }
                return result;
            }
        }
    }

    private static void assertLockTimeout(String sql) throws Exception
    {
        try (Connection connection = databaseConnection();
                Statement statement = connection.createStatement())
        {
            statement.execute("set session innodb_lock_wait_timeout=1");
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(
                            ((SQLException) error).getErrorCode())
                                    .isEqualTo(1205));
            connection.rollback();
        }
    }

    private static Connection serverConnection() throws SQLException
    {
        return DriverManager.getConnection(SERVER_URL, "root", "");
    }

    private static Connection databaseConnection() throws SQLException
    {
        Connection connection = DriverManager.getConnection(DATABASE_URL,
                "root", "");
        connection.setAutoCommit(false);
        return connection;
    }
}
