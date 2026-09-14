package com.erp.inventory.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;
import java.io.InputStream;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.erp.inventory.domain.dto.*;
import com.erp.inventory.mapper.*;
import com.erp.inventory.service.impl.*;

/**
 * Real service transaction proxies and production MyBatis SQL on isolated InnoDB.
 * Reservation/discrepancy/disposition/shipment schemas use existing migration CREATEs.
 * Root/detail/stock/status-log scaffolds include domain scalar columns; they are not a migration rehearsal.
 * Only department visibility and attachments are mocked. Deliberate mapper fault injection is named per test.
 */
class InvTransferDeletionReshipMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static SqlSessionTemplate session;
    static final List<String> TABLES=List.of("inv_transfer_command","inv_transfer_discrepancy_disposition","inv_transfer_discrepancy_detail","inv_transfer_discrepancy","inv_transfer_reservation","inv_transfer_shipment_detail","inv_transfer_shipment","inv_transfer_status_log","inv_transfer_detail","inv_transfer_order","inv_stock_log","inv_stock");
    InvTransferServiceImpl service;
    ExecutorService workers;

    @BeforeAll static void start() throws Exception
    {
        String version=System.getProperty("inventory.mysql.version","5.7.44");
        assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("joint_transfer_integrity").withUsername("transfer_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
            .withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withLabels(Map.of("erp.task","joint-transfer-integrity-20260913"))
            .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
        try {
            mysql.start();assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
            Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("sql/erp_inventory_transfer_reservation_20260802.sql")))root=root.getParent();assertThat(root).isNotNull();
            for(String file:List.of("erp_inventory_transfer_command_20260802.sql","erp_inventory_transfer_reservation_20260802.sql","erp_inventory_transfer_discrepancy_20260713.sql","erp_inventory_transfer_discrepancy_disposition_20260805.sql","erp_inventory_transfer_shipment_tables_20260607.sql")) {
                var matcher=Pattern.compile("(?is)CREATE TABLE IF NOT EXISTS inv_\\w+ \\(.*?;(?=\\s|$)").matcher(Files.readString(root.resolve("sql/"+file)));
                int created=0;while(matcher.find()){jdbc.execute(matcher.group());created++;}assertThat(created).isPositive();
            }
            createScalarTable("inv_transfer_order","transfer_id",InvTransferOrder.class);
            createScalarTable("inv_transfer_detail","detail_id",InvTransferDetail.class);
            createScalarTable("inv_transfer_status_log","log_id",InvTransferStatusLog.class);
            createScalarTable("inv_stock","stock_id",InvStock.class);
            createScalarTable("inv_stock_log","log_id",InvStockLog.class);
            addMissingScalarColumns("inv_transfer_shipment",InvTransferShipment.class);
            addMissingScalarColumns("inv_transfer_shipment_detail",InvTransferShipmentDetail.class);
            jdbc.execute("alter table inv_transfer_detail add index transfer_lines(transfer_id,detail_id)");
            jdbc.execute("alter table inv_stock add unique index stock_dimension(item_type,item_id,shop_dept_id,warehouse_id)");
            jdbc.execute("create table sys_dept(dept_id bigint primary key,dept_name varchar(64),dept_type varchar(32),ancestors varchar(256),status char(1),del_flag char(1)) engine=InnoDB");
            jdbc.update("insert into sys_dept values(201,'Source','STORE','0','0','0'),(202,'Target','STORE','0','0','0')");
            Configuration c=new Configuration(new Environment("transfer",new SpringManagedTransactionFactory(),source));
            c.getTypeAliasRegistry().registerAliases("com.erp.inventory.domain");
            for(String name:List.of("InvTransferCommandMapper","InvTransferOrderMapper","InvTransferDetailMapper","InvTransferDiscrepancyMapper","InvTransferDiscrepancyDispositionMapper","InvTransferShipmentMapper","InvTransferShipmentDetailMapper","InvTransferReservationMapper","InvTransferStatusLogMapper","InvStockMapper","InvStockLogMapper")) {
                String path="mapper/inventory/"+name+".xml";try(InputStream input=InvTransferDeletionReshipMySqlIT.class.getClassLoader().getResourceAsStream(path)){assertThat(input).isNotNull();new XMLMapperBuilder(input,c,path,c.getSqlFragments()).parse();}
            }
            session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(c));
        }catch(Throwable e){mysql.stop();throw e;}
    }
    @AfterAll static void stop(){if(mysql!=null)mysql.stop();}
    @BeforeEach void setup()
    {
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            for(String table:TABLES)jdbc.update("delete from "+table);
            jdbc.update("insert into inv_transfer_order(transfer_id,order_no,from_dept_id,from_warehouse_id,to_dept_id,to_warehouse_id,transfer_type,status,version,approval_round) values(900,'TF-900',201,201,202,202,'cross_store','draft',0,1)");
            jdbc.update("insert into inv_transfer_detail(detail_id,transfer_id,item_type,item_id,product_id,item_name,quantity,delivered_quantity,received_quantity,cost_price,sort_order) values(1011,900,'product',1001,1001,'Item',10,0,0,5,0)");
            jdbc.update("insert into inv_stock(stock_id,item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,locked_quantity,cost_price,total_cost,version) values(66,'product',1001,1001,201,201,10,10,0,5,50,0)");
        });
        identity();workers=Executors.newFixedThreadPool(2);service=newService(null,null);
    }
    @AfterEach void cleanup() throws Exception {workers.shutdownNow();assertThat(workers.awaitTermination(10,TimeUnit.SECONDS)).isTrue();SecurityContextHolder.remove();}

    @ParameterizedTest @ValueSource(strings={"draft","cancelled"})
    void untouchedDraftOrCancelledDeletesParentAndChildrenTogether(String status)
    {
        jdbc.update("update inv_transfer_order set status=? where transfer_id=900",status);
        service.deleteTransfer(900L,202L);
        assertThat(count("inv_transfer_order")).isZero();assertThat(count("inv_transfer_detail")).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"shipment","discrepancy","reservation"})
    void historicalFactsPreventDeletionOfCancelledOrder(String family)
    {
        jdbc.update("update inv_transfer_order set status='cancelled' where transfer_id=900");
        if(family.equals("shipment"))seedShipment();
        if(family.equals("discrepancy"))seedDiscrepancy();
        if(family.equals("reservation"))seedReservation();
        assertThatThrownBy(()->service.deleteTransfer(900L,202L)).isInstanceOf(ServiceException.class).hasMessageContaining("保留单据");
        assertParentAndDetail();assertThat(count("inv_transfer_"+family)).isEqualTo(1);
    }
    @Test void liveOrderCannotBeDeleted()
    {
        jdbc.update("update inv_transfer_order set status='submitted' where transfer_id=900");
        assertThatThrownBy(()->service.deleteTransfer(900L,202L)).isInstanceOf(ServiceException.class);
        assertParentAndDetail();
    }
    @Test void rootDeleteCasFailureRollsBackAlreadyDeletedDetails()
    {
        InvTransferOrderMapper faulty=intercept(InvTransferOrderMapper.class,"deleteDraftIfVersionMatches",false);
        service=newService(faulty,null);
        assertThatThrownBy(()->service.deleteTransfer(900L,202L)).isInstanceOf(ServiceException.class).hasMessageContaining("删除未生效");
        assertParentAndDetail();
    }
    @Test void deleteWaitsForParentLockThenObservesCommittedSubmission() throws Exception
    {
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), entered=new CountDownLatch(1);
        Future<?> writer=workers.submit(()->inTx(()->{
            mapper(InvTransferOrderMapper.class).selectInvTransferOrderByIdForUpdate(900L);locked.countDown();await(release);
            // Isolated competing state transition: real locked SQL, not a claim to exercise approval dispatch.
            jdbc.update("update inv_transfer_order set status='submitted',version=version+1 where transfer_id=900");
        }));
        await(locked);
        Future<Throwable> deletion=workers.submit(()->attempt(()->{entered.countDown();service.deleteTransfer(900L,202L);}));
        await(entered);assertThatThrownBy(()->deletion.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();writer.get(10,TimeUnit.SECONDS);
        assertThat(deletion.get(10,TimeUnit.SECONDS)).isInstanceOf(ServiceException.class);
        assertParentAndDetail();assertThat(text("select status from inv_transfer_order")).isEqualTo("submitted");
    }
    @Test void oldRepeatableReadSnapshotStillDetectsNewHistoricalFact() throws Exception
    {
        jdbc.update("update inv_transfer_order set status='cancelled' where transfer_id=900");
        CountDownLatch oldRead=new CountDownLatch(1), committed=new CountDownLatch(1);
        Future<Throwable> deletion=workers.submit(()->attempt(()->inTx(()->{
            assertThat(count("inv_transfer_reservation")).isZero();oldRead.countDown();await(committed);service.deleteTransfer(900L,202L);
        })));
        await(oldRead);
        inTx(()->{mapper(InvTransferOrderMapper.class).selectInvTransferOrderByIdForUpdate(900L);seedReservation();});
        committed.countDown();assertThat(deletion.get(10,TimeUnit.SECONDS)).isInstanceOf(ServiceException.class).hasMessageContaining("保留单据");
        assertParentAndDetail();assertThat(count("inv_transfer_reservation")).isEqualTo(1);
    }
    @Test void nonQcShortageReplenishesExhaustedReservationWithoutMovingPhysicalStockOrCost()
    {
        seedShortage();service.resolveTransferDiscrepancy(1L,request("resh-1"),202L);assertResolvedOnce();
    }
    @Test void exactRetryReturnsSavedResultWithoutAdditionalReservation()
    {
        seedShortage();service.resolveTransferDiscrepancy(1L,request("resh-1"),202L);
        service.resolveTransferDiscrepancy(1L,request("resh-1"),202L);assertResolvedOnce();
    }
    @Test void replayKeyWithChangedContentIsRejectedWithoutMutation()
    {
        seedShortage();service.resolveTransferDiscrepancy(1L,request("resh-1"),202L);
        var changed=request("resh-1");changed.setResponsibleParty("STORE");
        assertThatThrownBy(()->service.resolveTransferDiscrepancy(1L,changed,202L)).isInstanceOf(ServiceException.class);
        assertResolvedOnce();
    }
    @Test void concurrentSameRequestIsIdempotentAcrossTwoRealTransactions() throws Exception
    {
        seedShortage();List<Throwable> outcomes=race("same-key","same-key");assertThat(outcomes).containsOnlyNulls();assertResolvedOnce();
    }
    @Test void exactRetryWithOlderRepeatableReadSnapshotUsesCommittedRequestLedger() throws Exception
    {
        seedShortage();CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);
        Future<Throwable> retry=workers.submit(()->attempt(()->inTx(()->{
            assertThat(count("inv_transfer_discrepancy_disposition")).isZero();snapshot.countDown();await(committed);
            service.resolveTransferDiscrepancy(1L,request("old-snapshot"),202L);
        })));
        await(snapshot);service.resolveTransferDiscrepancy(1L,request("old-snapshot"),202L);committed.countDown();
        assertThat(retry.get(10,TimeUnit.SECONDS)).isNull();assertResolvedOnce();
    }
    @Test void competingDifferentRequestsResolveOnlyOnce() throws Exception
    {
        seedShortage();List<Throwable> outcomes=race("first-key","second-key");
        assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(Objects::nonNull).toList()).allMatch(ServiceException.class::isInstance);
        assertResolvedOnce();
    }
    @Test void differentTransfersCanCreateTheirFirstReceiptsWithoutEmptyIndexGapDeadlock() throws Exception
    {
        seedShortage();
        inTx(()->{
            copyRow("inv_transfer_order","transfer_id",900L,Map.of("transfer_id",901L,"order_no","TF-901"));
            copyRow("inv_transfer_detail","detail_id",1011L,Map.of("detail_id",1012L,"transfer_id",901L,"item_id",1002L,"product_id",1002L));
            copyRow("inv_stock","stock_id",66L,Map.of("stock_id",67L,"item_id",1002L,"product_id",1002L));
            copyRow("inv_transfer_shipment","shipment_id",800L,Map.of("shipment_id",801L,"transfer_id",901L,"shipment_no","SHIP-801"));
            copyRow("inv_transfer_discrepancy","discrepancy_id",1L,Map.of("discrepancy_id",2L,"transfer_id",901L,"shipment_id",801L,"discrepancy_no","DISC-2"));
            copyRow("inv_transfer_reservation","reservation_id",77L,Map.of("reservation_id",78L,"transfer_id",901L,"transfer_detail_id",1012L,"stock_id",67L,"item_id",1002L));
            copyRow("inv_transfer_shipment_detail","shipment_detail_id",111L,Map.of("shipment_detail_id",112L,"shipment_id",801L,"transfer_id",901L,"transfer_detail_id",1012L,"item_id",1002L,"product_id",1002L));
            copyRow("inv_transfer_discrepancy_detail","discrepancy_detail_id",11L,Map.of("discrepancy_detail_id",12L,"discrepancy_id",2L,"transfer_detail_id",1012L,"shipment_detail_id",112L,"item_id",1002L));
        });
        CountDownLatch lookups=new CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger lockingLookups=new java.util.concurrent.atomic.AtomicInteger();
        InvTransferDiscrepancyDispositionMapper real=mapper(InvTransferDiscrepancyDispositionMapper.class);
        var observed=(InvTransferDiscrepancyDispositionMapper)Proxy.newProxyInstance(real.getClass().getClassLoader(),new Class<?>[]{InvTransferDiscrepancyDispositionMapper.class},(p,m,args)->{
            Object value;try{value=m.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            if(m.getName().startsWith("selectByRequestId")) {
                if(m.getName().endsWith("ForUpdate"))lockingLookups.incrementAndGet();
                lookups.countDown();await(lookups);
            }
            return value;
        });
        var target=newService(null,observed);
        Future<Throwable> first=workers.submit(()->attempt(()->target.resolveTransferDiscrepancy(1L,request("first-root"),202L)));
        Future<Throwable> second=workers.submit(()->attempt(()->{
            var next=request("second-root");next.getItems().get(0).setDetailId(12L);
            target.resolveTransferDiscrepancy(2L,next,202L);
        }));
        assertThat(Arrays.asList(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsOnlyNulls();
        assertThat(lockingLookups.get()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inv_transfer_discrepancy where status='RESOLVED'",Integer.class)).isEqualTo(2);
        assertThat(count("inv_transfer_discrepancy_disposition")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from inv_transfer_reservation where reserved_quantity=12 and consumed_quantity=10 and released_quantity=0",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from inv_stock where current_quantity=10 and available_quantity=8 and locked_quantity=2 and total_cost=50",Integer.class)).isEqualTo(2);
        assertThat(count("inv_stock_log")).isZero();
    }
    @Test void insufficientAvailableStockLeavesAllFactsUnchanged()
    {
        seedShortage();jdbc.update("update inv_stock set available_quantity=1,locked_quantity=9 where stock_id=66");
        assertThatThrownBy(()->service.resolveTransferDiscrepancy(1L,request("insufficient"),202L)).isInstanceOf(ServiceException.class).hasMessageContaining("不足");
        assertOpen();assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("1");assertThat(decimal("select locked_quantity from inv_stock")).isEqualByComparingTo("9");
    }
    @Test void failureAfterDispositionSqlRollsBackStockReservationVersionAndDeliveredQuantity()
    {
        seedShortage();service=newService(null,intercept(InvTransferDiscrepancyDispositionMapper.class,"insertDisposition",true));
        assertThatThrownBy(()->service.resolveTransferDiscrepancy(1L,request("fault"),202L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("injected");
        assertOpen();assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("10");assertThat(decimal("select locked_quantity from inv_stock")).isZero();
        assertThat(jdbc.queryForObject("select version from inv_stock",Long.class)).isZero();
        service=newService(null,null);service.resolveTransferDiscrepancy(1L,request("fault"),202L);assertResolvedOnce();
    }
    @Test void missingReservationFailsClosedBeforeTerminalState()
    {
        seedShortage();jdbc.update("delete from inv_transfer_reservation");
        assertThatThrownBy(()->service.resolveTransferDiscrepancy(1L,request("missing"),202L)).isInstanceOf(ServiceException.class).hasMessageContaining("归属");
        assertThat(text("select status from inv_transfer_discrepancy")).isEqualTo("OPEN");assertThat(count("inv_transfer_discrepancy_disposition")).isZero();
        assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("10");
    }

    @Test void outerCommandLedgerReplaysDeletedDraftAndRejectsChangedResource()
    {
        var commands=commands();commands.deleteDraft("delete-command-1",900L,202L);
        commands.deleteDraft("delete-command-1",900L,202L);
        assertThat(count("inv_transfer_order")).isZero();assertThat(count("inv_transfer_detail")).isZero();assertThat(count("inv_transfer_command")).isEqualTo(1);
        assertThat(text("select status from inv_transfer_command")).isEqualTo("SUCCEEDED");
        assertThatThrownBy(()->commands.deleteDraft("delete-command-1",901L,202L)).isInstanceOf(ServiceException.class);
        assertThat(count("inv_transfer_command")).isEqualTo(1);
    }
    @Test void outerResolutionCommandKeepsOneFingerprintAndOneInventoryEffect()
    {
        seedShortage();var commands=commands();commands.resolveDiscrepancy("resolve-command-1",1L,request("inner-request"),202L);
        commands.resolveDiscrepancy("resolve-command-1",1L,request("inner-request"),202L);
        var altered=request("inner-request");altered.setResponsibleParty("STORE");
        assertThatThrownBy(()->commands.resolveDiscrepancy("resolve-command-1",1L,altered,202L)).isInstanceOf(ServiceException.class);
        assertThat(count("inv_transfer_command")).isEqualTo(1);assertThat(text("select status from inv_transfer_command")).isEqualTo("SUCCEEDED");assertResolvedOnce();
    }
    @Test void replacementCanConsumeNewReservationOnceWithActualStockAndCostSql()
    {
        seedShortage();service.resolveTransferDiscrepancy(1L,request("consume-next"),202L);assertResolvedOnce();
        inTx(()->{
            var order=mapper(InvTransferOrderMapper.class).selectInvTransferOrderByIdForUpdate(900L);
            var detail=mapper(InvTransferDetailMapper.class).selectByTransferIdForUpdate(900L).get(0);
            var consumption=proxy(new InvTransferReservationService(mapper(InvTransferReservationMapper.class))).consumeForShipment(order,detail,new BigDecimal("2"),"transfer-it");
            assertThat(consumption.deductedCost()).isEqualByComparingTo("10");
        });
        assertThat(decimal("select reserved_quantity-consumed_quantity-released_quantity from inv_transfer_reservation")).isZero();
        assertThat(decimal("select current_quantity from inv_stock")).isEqualByComparingTo("8");assertThat(decimal("select locked_quantity from inv_stock")).isZero();assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("8");assertThat(decimal("select total_cost from inv_stock")).isEqualByComparingTo("40");
        assertThatThrownBy(()->inTx(()->{
            var order=mapper(InvTransferOrderMapper.class).selectInvTransferOrderByIdForUpdate(900L);var detail=mapper(InvTransferDetailMapper.class).selectByTransferIdForUpdate(900L).get(0);
            proxy(new InvTransferReservationService(mapper(InvTransferReservationMapper.class))).consumeForShipment(order,detail,new BigDecimal("2"),"transfer-it");
        })).isInstanceOf(ServiceException.class).hasMessageContaining("剩余冻结库存不足");
        assertThat(decimal("select total_cost from inv_stock")).isEqualByComparingTo("40");
        // This verifies the production shipment consumption owner, not shipment creation/approval UI.
    }
    @ParameterizedTest @ValueSource(strings={"reserveStock","increaseReservation"})
    void reservationCasFailureCannotCloseDiscrepancyOrLeavePartialFreeze(String method)
    {
        seedShortage();var target=newService(null,null);
        InvTransferServiceImpl original = org.springframework.test.util.AopTestUtils.getTargetObject(target);
        ReflectionTestUtils.setField(original,"transferReservationService",proxy(new InvTransferReservationService(intercept(InvTransferReservationMapper.class,method,false))));
        assertThatThrownBy(()->target.resolveTransferDiscrepancy(1L,request("cas-failure"),202L)).isInstanceOf(ServiceException.class);
        assertOpen();assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("10");assertThat(decimal("select locked_quantity from inv_stock")).isZero();assertThat(jdbc.queryForObject("select version from inv_stock",Long.class)).isZero();
    }
    @Test void originalRemainingReservationIsPreservedAndReplacementAddsItsOwnQuantity()
    {
        seedShortage();jdbc.update("update inv_transfer_reservation set reserved_quantity=15,status='PARTIAL'");jdbc.update("update inv_stock set available_quantity=5,locked_quantity=5");
        service.resolveTransferDiscrepancy(1L,request("extra-not-reuse"),202L);
        assertThat(decimal("select reserved_quantity from inv_transfer_reservation")).isEqualByComparingTo("17");assertThat(decimal("select consumed_quantity from inv_transfer_reservation")).isEqualByComparingTo("10");
        assertThat(decimal("select locked_quantity from inv_stock")).isEqualByComparingTo("7");assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("3");assertThat(decimal("select current_quantity from inv_stock")).isEqualByComparingTo("10");assertThat(decimal("select total_cost from inv_stock")).isEqualByComparingTo("50");
    }
    InvTransferCommandService commands(){return proxy(new InvTransferCommandService(proxy(new InvTransferCommandExecutor(mapper(InvTransferCommandMapper.class),new com.fasterxml.jackson.databind.ObjectMapper())),service,null,null,null,null));}

    List<Throwable> race(String first,String second)throws Exception
    {
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        List<Future<Throwable>> futures=new ArrayList<>();
        for(String key:List.of(first,second))futures.add(workers.submit(()->attempt(()->{ready.countDown();await(go);service.resolveTransferDiscrepancy(1L,request(key),202L);})));await(ready);go.countDown();
        List<Throwable> results=new ArrayList<>();for(var f:futures)results.add(f.get(20,TimeUnit.SECONDS));return results;
    }
    static void seedShortage(){inTx(()->{
        jdbc.update("update inv_transfer_order set status='discrepancy' where transfer_id=900");
        jdbc.update("update inv_transfer_detail set delivered_quantity=10,received_quantity=8 where detail_id=1011");
        seedShipment();seedDiscrepancy();seedReservation();
        jdbc.update("insert into inv_transfer_shipment_detail(shipment_detail_id,shipment_id,transfer_id,transfer_detail_id,product_id,item_type,item_id,shipped_quantity,received_quantity,cost_price) values(111,800,900,1011,1001,'product',1001,10,8,5)");
        jdbc.update("insert into inv_transfer_discrepancy_detail(discrepancy_detail_id,discrepancy_id,transfer_detail_id,shipment_detail_id,item_type,item_id,item_name,shipped_quantity,accepted_quantity,shortage_quantity,resolution_quantity) values(11,1,1011,111,'product',1001,'Item',10,8,2,2)");
    });}
    static void seedShipment(){jdbc.update("insert into inv_transfer_shipment(shipment_id,transfer_id,shipment_no,warehouse_id,warehouse_dept_id,status) values(800,900,'SHIP-800',201,201,'abnormal')");}
    static void seedDiscrepancy(){jdbc.update("insert into inv_transfer_discrepancy(discrepancy_id,transfer_id,shipment_id,discrepancy_no,status,discrepancy_type,shipped_quantity,accepted_quantity,shortage_quantity,version) values(1,900,800,'DISC-1','OPEN','SHORTAGE',10,8,2,0)");}
    static void seedReservation(){jdbc.update("insert into inv_transfer_reservation(reservation_id,transfer_id,transfer_detail_id,reservation_round,stock_id,item_type,item_id,source_location_dept_id,reserved_quantity,consumed_quantity,released_quantity,status,version) values(77,900,1011,1,66,'product',1001,201,10,10,0,'CONSUMED',0)");}
    static void copyRow(String table,String primaryKey,long id,Map<String,Object> replacements)
    {
        Map<String,Object> row=new LinkedHashMap<>(jdbc.queryForMap("select * from "+table+" where "+primaryKey+"=?",id));
        row.putAll(replacements);
        jdbc.update("insert into "+table+" ("+String.join(",",row.keySet())+") values ("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());
    }
    static InvTransferDiscrepancyResolveRequest request(String key)
    {
        var r=new InvTransferDiscrepancyResolveRequest();r.setRequestId(key);r.setVersion(0L);r.setResponsibleParty("LOGISTICS");r.setNote("confirmed shortage");
        var item=new InvTransferDiscrepancyResolutionItem();item.setDetailId(11L);item.setCategory("SHORTAGE");item.setDecision("RESHIP");item.setQuantity(new BigDecimal("2"));item.setNote("send replacement");r.setItems(List.of(item));return r;
    }
    static void assertResolvedOnce()
    {
        assertThat(text("select status from inv_transfer_discrepancy")).isEqualTo("RESOLVED");assertThat(count("inv_transfer_discrepancy_disposition")).isEqualTo(1);
        assertThat(text("select status from inv_transfer_order")).isEqualTo("partial_delivered");assertThat(text("select status from inv_transfer_shipment")).isEqualTo("received");
        assertThat(decimal("select reserved_quantity from inv_transfer_reservation")).isEqualByComparingTo("12");assertThat(decimal("select consumed_quantity from inv_transfer_reservation")).isEqualByComparingTo("10");assertThat(decimal("select released_quantity from inv_transfer_reservation")).isZero();
        assertThat(decimal("select available_quantity from inv_stock")).isEqualByComparingTo("8");assertThat(decimal("select locked_quantity from inv_stock")).isEqualByComparingTo("2");
        assertThat(decimal("select delivered_quantity from inv_transfer_detail")).isEqualByComparingTo("8");assertThat(decimal("select received_quantity from inv_transfer_detail")).isEqualByComparingTo("8");
        assertThat(decimal("select current_quantity from inv_stock")).isEqualByComparingTo("10");assertThat(decimal("select total_cost from inv_stock")).isEqualByComparingTo("50");assertThat(decimal("select cost_price from inv_stock")).isEqualByComparingTo("5");
        assertThat(decimal("select amount from inv_transfer_discrepancy_disposition")).isEqualByComparingTo("10");assertThat(count("inv_stock_log")).isZero();assertThat(count("inv_transfer_status_log")).isEqualTo(1);
    }
    static void assertOpen()
    {
        assertThat(text("select status from inv_transfer_discrepancy")).isEqualTo("OPEN");assertThat(text("select status from inv_transfer_order")).isEqualTo("discrepancy");assertThat(text("select status from inv_transfer_shipment")).isEqualTo("abnormal");
        assertThat(count("inv_transfer_discrepancy_disposition")).isZero();assertThat(count("inv_transfer_status_log")).isZero();assertThat(count("inv_stock_log")).isZero();
        assertThat(decimal("select reserved_quantity from inv_transfer_reservation")).isEqualByComparingTo("10");assertThat(decimal("select consumed_quantity from inv_transfer_reservation")).isEqualByComparingTo("10");
        assertThat(decimal("select delivered_quantity from inv_transfer_detail")).isEqualByComparingTo("10");assertThat(jdbc.queryForObject("select version from inv_transfer_discrepancy",Long.class)).isZero();assertThat(jdbc.queryForObject("select version from inv_transfer_reservation",Long.class)).isZero();
        assertThat(decimal("select current_quantity from inv_stock")).isEqualByComparingTo("10");assertThat(decimal("select total_cost from inv_stock")).isEqualByComparingTo("50");
    }
    InvTransferServiceImpl newService(InvTransferOrderMapper order,InvTransferDiscrepancyDispositionMapper disposition)
    {
        var target=new InvTransferServiceImpl();
        Map<String,Class<?>> fields=Map.of("transferDetailMapper",InvTransferDetailMapper.class,"transferDiscrepancyMapper",InvTransferDiscrepancyMapper.class,"transferShipmentMapper",InvTransferShipmentMapper.class,"transferShipmentDetailMapper",InvTransferShipmentDetailMapper.class,"stockMapper",InvStockMapper.class,"stockLogMapper",InvStockLogMapper.class,"statusLogMapper",InvTransferStatusLogMapper.class);
        fields.forEach((name,type)->ReflectionTestUtils.setField(target,name,session.getMapper(type)));
        ReflectionTestUtils.setField(target,"transferOrderMapper",order==null?mapper(InvTransferOrderMapper.class):order);
        ReflectionTestUtils.setField(target,"transferDiscrepancyDispositionMapper",disposition==null?mapper(InvTransferDiscrepancyDispositionMapper.class):disposition);
        ReflectionTestUtils.setField(target,"transferReservationService",proxy(new InvTransferReservationService(mapper(InvTransferReservationMapper.class))));
        ReflectionTestUtils.setField(target,"transferEvidenceService",mock(InvTransferEvidenceService.class));
        var scope=mock(InvDeptScopeMapper.class);when(scope.countDeptInScope(anyLong(),anyLong())).thenReturn(1);when(scope.countUserShopScope(anyLong(),anyLong())).thenReturn(1);when(scope.selectDeptTypeById(anyLong())).thenReturn("STORE");ReflectionTestUtils.setField(target,"deptScopeMapper",scope);
        return proxy(target);
    }
    @SuppressWarnings("unchecked") static <T>T intercept(Class<T> type,String method,boolean failAfterSql)
    {
        T real=mapper(type);return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)->{
            if(m.getName().equals(method)&&!failAfterSql)return 0;
            Object result;try{result=m.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
            if(m.getName().equals(method))throw new IllegalStateException("injected after real "+method+" SQL");return result;
        });
    }
    @SuppressWarnings("unchecked") static <T>T proxy(T target){ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return(T)factory.getProxy();}
    static <T>T mapper(Class<T> type){return session.getMapper(type);}
    static void identity(){SecurityContextHolder.setUserId("1");SecurityContextHolder.setUserName("transfer-it");}
    static Throwable attempt(Runnable work){identity();try{work.run();return null;}catch(Throwable t){return t;}finally{SecurityContextHolder.remove();}}
    static void inTx(Runnable work){new TransactionTemplate(manager).executeWithoutResult(tx->work.run());}
    static void await(CountDownLatch latch){try{assertThat(latch.await(10,TimeUnit.SECONDS)).as("bounded concurrency barrier").isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    static int count(String table){return jdbc.queryForObject("select count(*) from "+table,Integer.class);}
    static String text(String query){return jdbc.queryForObject(query,String.class);}
    static BigDecimal decimal(String query){return jdbc.queryForObject(query,BigDecimal.class);}
    static void assertParentAndDetail(){assertThat(count("inv_transfer_order")).isEqualTo(1);assertThat(count("inv_transfer_detail")).isEqualTo(1);}
    static void createScalarTable(String table,String primaryKey,Class<?> type)
    {
        Map<String,String> columns=scalarColumns(type);columns.remove(primaryKey);
        var definitions=new ArrayList<String>();definitions.add(primaryKey+" bigint primary key auto_increment");columns.forEach((name,sqlType)->definitions.add(name+" "+sqlType));
        jdbc.execute("create table "+table+"("+String.join(",",definitions)+") engine=InnoDB default charset=utf8mb4");
    }
    static void addMissingScalarColumns(String table,Class<?> type)
    {
        Set<String> existing=new HashSet<>(jdbc.queryForList("select column_name from information_schema.columns where table_schema=database() and table_name=?",String.class,table));
        scalarColumns(type).forEach((name,sqlType)->{if(!existing.contains(name))jdbc.execute("alter table "+table+" add column "+name+" "+sqlType);});
    }
    static Map<String,String> scalarColumns(Class<?> type)
    {
        Map<String,String> result=new LinkedHashMap<>();for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;
            Class<?> t=f.getType();String sql=t==Long.class?"bigint":t==Integer.class?"int":t==BigDecimal.class?"decimal(20,4)":t==Date.class?"datetime":t==String.class?(f.getName().endsWith("Type")||f.getName().equals("status")?"varchar(64)":"text"):null;
            if(sql!=null)result.putIfAbsent(f.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),sql);
        }return result;
    }
}
