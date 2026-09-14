package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.oa.domain.OaLaborContract;

/** Real temporary files; transaction callbacks are explicit local fixtures, not a DB integration test. */
class OaLaborContractArchiveAttemptTest
{
    @TempDir Path root;
    static final List<String> NAMES=List.of("signature.png","archive.docx","archive.pdf","certificate.pdf");
    OaLaborContract contract(){var c=new OaLaborContract();c.setContractId(42L);return c;}
    OaLaborContractDocumentService service(){var s=new OaLaborContractDocumentService();ReflectionTestUtils.setField(s,"localFilePath",root.toString());return s;}
    Path dir(){return root.resolve("labor-contract/42");}
    void write(String name,String content){try{Files.writeString(dir().resolve(name),content);}catch(Exception e){throw new RuntimeException(e);}}
    @AfterEach void clear(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}

    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void eachPartialGenerationFailureDeletesOnlyThisAttemptsFourFiles(int completed) throws Exception
    {
        Files.createDirectories(dir());Files.writeString(dir().resolve("preview.pdf"),"frozen preview");
        IllegalStateException failure=new IllegalStateException("injected stage "+completed);
        assertThatThrownBy(()->service().withSignedArchiveAttempt(contract(),()->{
            for(int i=0;i<completed;i++)write(NAMES.get(i),"owned result");throw failure;
        })).isSameAs(failure);
        for(String name:NAMES)assertThat(dir().resolve(name)).doesNotExist();
        assertThat(Files.readString(dir().resolve("preview.pdf"))).isEqualTo("frozen preview");
    }

    @Test void existingHistoryIsNeverOverwrittenOrDeletedEvenAfterEarlierReservations() throws Exception
    {
        Files.createDirectories(dir());Files.writeString(dir().resolve("archive.pdf"),"historic immutable PDF");
        assertThatThrownBy(()->service().withSignedArchiveAttempt(contract(),()->"must not run")).hasMessageContaining("既有归档");
        assertThat(Files.readString(dir().resolve("archive.pdf"))).isEqualTo("historic immutable PDF");
        assertThat(dir().resolve("signature.png")).doesNotExist();assertThat(dir().resolve("archive.docx")).doesNotExist();
    }

    @Test void committedFilesRemainAndRolledBackFilesAreRemoved() throws Exception
    {
        TransactionSynchronizationManager.initSynchronization();
        service().withSignedArchiveAttempt(contract(),()->{for(String n:NAMES)write(n,"signed");return "ok";});
        var first=TransactionSynchronizationManager.getSynchronizations();
        first.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        for(String name:NAMES)assertThat(dir().resolve(name)).doesNotExist();
        TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.initSynchronization();
        service().withSignedArchiveAttempt(contract(),()->{for(String n:NAMES)write(n,"next attempt");return "ok";});
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        // A delayed duplicate callback from the removed attempt cannot erase a later attempt.
        first.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        for(String name:NAMES)assertThat(Files.readString(dir().resolve(name))).isEqualTo("next attempt");
    }

    @Test void eventFailureAfterGenerationCleansImmediatelyAndRetainsOriginalException()
    {
        TransactionSynchronizationManager.initSynchronization();IllegalStateException eventFailure=new IllegalStateException("event insert failed");
        assertThatThrownBy(()->service().withSignedArchiveAttempt(contract(),()->{
            for(String n:NAMES)write(n,"generated");throw eventFailure;
        })).isSameAs(eventFailure);
        for(String name:NAMES)assertThat(dir().resolve(name)).doesNotExist();
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test void replacementFileIsNeverDeletedWhenOwnershipNoLongerMatches() throws Exception
    {
        Files.createDirectories(dir());Path target=dir().resolve("archive.pdf");
        OaLaborContractArchiveAttempt attempt=new OaLaborContractArchiveAttempt(42L);attempt.reserve(target);
        Path replacement=Files.createTempFile(dir(),"replacement-",".pdf");Files.writeString(replacement,"different owner");
        Files.move(replacement,target,StandardCopyOption.REPLACE_EXISTING);
        RuntimeException failure=new RuntimeException("original");attempt.cleanup(failure);
        assertThat(Files.readString(target)).isEqualTo("different owner");assertThat(failure.getSuppressed()).hasSize(1);
    }

    @Test void linkedContractDirectoryIsRejectedWithoutWritingOutsideStorage() throws Exception
    {
        Path external=Files.createTempDirectory(root,"elsewhere");Files.createDirectories(root.resolve("labor-contract"));
        Files.createSymbolicLink(dir(),external);
        assertThatThrownBy(()->service().withSignedArchiveAttempt(contract(),()->"never")).hasMessageContaining("链接目录");
        try(var files=Files.list(external)){assertThat(files.count()).isZero();}
    }
}
