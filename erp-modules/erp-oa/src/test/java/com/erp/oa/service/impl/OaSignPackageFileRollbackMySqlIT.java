package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;
import com.erp.oa.domain.vo.StagedSignFile;

/** Actual PDF/storage/lifecycle + MySQL transaction; receipt tables stand in for business update/event failure. */
class OaSignPackageFileRollbackMySqlIT
{
    static MySQLContainer mysql;static JdbcTemplate jdbc;static TransactionTemplate transaction;
    @TempDir Path root;Path review;OaSignFileProperties properties;
    @BeforeAll static void start()
    {
        String version=System.getProperty("hr.pdf.mysql.version","5.7.44");assertThat(version).isIn("5.7.44","8.0.36");
        mysql=new MySQLContainer("mysql:"+version).withDatabaseName("sign_pdf_rollback_it").withUsername("pdf_it").withPassword(UUID.randomUUID().toString()).withReuse(false).withTmpFs(Map.of("/var/lib/mysql","rw,size=1g")).withEnv("MYSQL_INITDB_SKIP_TZINFO","1");
        try{mysql.start();var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(source);transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
            jdbc.execute("create table receipt(id int primary key,state varchar(30)) engine=InnoDB default charset=utf8mb4");jdbc.execute("create table event_receipt(id int primary key) engine=InnoDB default charset=utf8mb4");
            assertThat(jdbc.queryForObject("select version()",String.class)).startsWith(version);System.out.printf("HR PDF isolated image=%s container=%s port=%s%n",version,mysql.getContainerId(),mysql.getMappedPort(3306));
        }catch(Throwable error){mysql.stop();throw error;}
    }
    @AfterAll static void stop(){if(mysql!=null){mysql.stop();assertThat(mysql.isRunning()).isFalse();}}
    @BeforeEach void fixture() throws Exception
    {
        jdbc.update("delete from receipt");jdbc.update("delete from event_receipt");jdbc.update("insert into receipt values(1,'pending')");jdbc.update("insert into event_receipt values(1)");
        properties=new OaSignFileProperties();properties.getStorage().setRootPath(root.resolve("archive").toString());properties.getStorage().setTempPath(root.resolve("staging").toString());
        review=root.resolve("review.pdf");try(PDDocument pdf=new PDDocument()){pdf.addPage(new PDPage());pdf.save(review.toFile());}
    }
    OaSignPackage pkg(long id){var p=new OaSignPackage();p.setPackageId(id);p.setPackageNo("fixture");p.setDocumentVersion("SP-"+id+"-V1");p.setEmployeeNameSnapshot("fixture");return p;}
    OaSignPackageDocument doc(long id,long pkg){var d=new OaSignPackageDocument();d.setDocumentId(id);d.setPackageId(pkg);d.setDocumentVersion("SP-"+pkg+"-V1");d.setDocumentName("fixture");d.setEmployeeSignRequired("N");d.setCompanySealRequired("N");return d;}
    long fileCount(Path path) throws Exception {if(!Files.exists(path))return 0;try(var files=Files.walk(path)){return files.filter(Files::isRegularFile).count();}}
    Path file(SignedPdfResult result){return root.resolve("archive").resolve(result.getArchiveRelativePath());}

    @ParameterizedTest @ValueSource(strings={"later-pdf","conditional-sql","event-insert","promotion-after-move"})
    void failuresRollbackRealDatabaseAndOnlyThisAttemptsActualPdfFiles(String stage) throws Exception
    {
        OaSignFileStorageService storage=new OaSignFileStorageService(properties){
            @Override public StagedSignFile promote(StagedSignFile staged){var promoted=super.promote(staged);if(stage.equals("promotion-after-move"))throw new ServiceException("injected after move");return promoted;}
        };
        var pdf=new OaSignedPdfService(storage);var lifecycle=new OaSignPackageFileLifecycle(storage,mock(OaSignDocumentService.class),pdf);
        List<SignedPdfResult> generated=new ArrayList<>();
        assertThatThrownBy(()->transaction.execute(status->{
            try{
                for(long id=1;id<=2;id++){
                    var result=pdf.generateFinalPdfWithoutMarks(10L,pkg(100L),doc(id,100L),stage.equals("later-pdf")&&id==2?root.resolve("missing.pdf"):review);
                    generated.add(result);lifecycle.registerSignedPdfRollbackCleanup(List.of(result));
                }
                jdbc.update("update receipt set state='signed' where id=1");
                if(stage.equals("conditional-sql")){int changed=jdbc.update("update receipt set state='signed' where id=1 and state='pending'");if(changed!=1)throw new ServiceException("injected condition mismatch");}
                if(stage.equals("event-insert"))jdbc.update("insert into event_receipt values(1)");
                return null;
            }catch(RuntimeException failure){lifecycle.discardSignedPdfResults(generated,failure);throw failure;}
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select state from receipt where id=1",String.class)).isEqualTo("pending");
        assertThat(jdbc.queryForObject("select count(*) from event_receipt",Integer.class)).isEqualTo(1);
        assertThat(fileCount(root.resolve("archive"))).isZero();assertThat(fileCount(root.resolve("staging"))).isZero();assertThat(review).isRegularFile();
    }

    @Test void committedArchiveSurvivesLaterTransactionRollback() throws Exception
    {
        var storage=new OaSignFileStorageService(properties);var pdf=new OaSignedPdfService(storage);var lifecycle=new OaSignPackageFileLifecycle(storage,mock(OaSignDocumentService.class),pdf);
        SignedPdfResult committed=transaction.execute(status->{var r=pdf.generateFinalPdfWithoutMarks(10L,pkg(100L),doc(1,100L),review);lifecycle.registerSignedPdfRollbackCleanup(List.of(r));return r;});
        byte[] before=Files.readAllBytes(file(committed));
        assertThatThrownBy(()->transaction.execute(status->{var later=pdf.generateFinalPdfWithoutMarks(10L,pkg(100L),doc(2,100L),review);lifecycle.registerSignedPdfRollbackCleanup(List.of(later));throw new ServiceException("later transaction failed");})).hasMessageContaining("later");
        assertThat(Files.readAllBytes(file(committed))).isEqualTo(before);assertThat(fileCount(root.resolve("archive"))).isEqualTo(1);assertThat(fileCount(root.resolve("staging"))).isZero();
    }

    @Test void cleanupRefusesHashMismatchButStillRollsBackDatabaseAndKeepsOriginalFailure() throws Exception
    {
        var storage=new OaSignFileStorageService(properties);var pdf=new OaSignedPdfService(storage);var lifecycle=new OaSignPackageFileLifecycle(storage,mock(OaSignDocumentService.class),pdf);
        var original=new ServiceException("original event failure");var retained=new AtomicReference<SignedPdfResult>();
        assertThatThrownBy(()->transaction.execute(status->{
            var r=pdf.generateFinalPdfWithoutMarks(10L,pkg(100L),doc(1,100L),review);retained.set(r);lifecycle.registerSignedPdfRollbackCleanup(List.of(r));
            jdbc.update("update receipt set state='signed' where id=1");try{Files.writeString(file(r),"different bytes; do not delete");}catch(Exception e){throw new RuntimeException(e);}
            lifecycle.discardSignedPdfResults(List.of(r),original);throw original;
        })).isSameAs(original);
        assertThat(original.getSuppressed()).isNotEmpty();assertThat(Files.readString(file(retained.get()))).isEqualTo("different bytes; do not delete");
        assertThat(jdbc.queryForObject("select state from receipt where id=1",String.class)).isEqualTo("pending");
    }
}
