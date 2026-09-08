package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrSensitiveAccessLog;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.HrSensitiveExportArtifact;
import com.erp.system.mapper.HrSensitiveAccessLogMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.support.HrEmployeeFieldRegistry;
import com.erp.system.support.HrSensitiveFieldMasker;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@ExtendWith(MockitoExtension.class)
class HrSensitiveFieldRevealTest
{
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserProfileMapper profileMapper;
    @Mock private HrSensitiveAccessLogMapper auditMapper;
    @Mock private SysUserProfileDerivationService derivationService;
    @Mock private HrSensitiveWorkbookGenerator workbookGenerator;
    @Mock private HrSensitiveAuditService exportAuditService;
    private HrEmployeeProfileServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new HrEmployeeProfileServiceImpl(new HrEmployeeAccessService(userMapper), userMapper,
                profileMapper, auditMapper, derivationService, new HrEmployeeFieldRegistry(),
                new HrSensitiveFieldMasker(),null,null,null,null,exportAuditService,workbookGenerator);
    }

    @Test
    void revealScopesFirstAcceptsOnlyWhitelistAndAuditsWithoutTheSensitiveValue() throws Exception
    {
        SysUser employee = employee();
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee));
        when(auditMapper.insertSensitiveAccessLog(any())).thenReturn(1);

        var revealed = service.reveal(42L, "idNumber", 9L, "审计员", "10.0.0.8");

        assertThat(revealed.getFieldKey()).isEqualTo("idNumber");
        assertThat(revealed.getValue()).isEqualTo("350000199001010000");
        ArgumentCaptor<HrEmployeeQuery> scoped = ArgumentCaptor.forClass(HrEmployeeQuery.class);
        verify(userMapper).selectHrEmployeeList(scoped.capture());
        assertThat(scoped.getValue().getUserId()).isEqualTo(42L);
        ArgumentCaptor<HrSensitiveAccessLog> audit = ArgumentCaptor.forClass(HrSensitiveAccessLog.class);
        verify(auditMapper).insertSensitiveAccessLog(audit.capture());
        assertThat(audit.getValue().getOperatorUserId()).isEqualTo(9L);
        assertThat(audit.getValue().getOperatorName()).isEqualTo("审计员");
        assertThat(audit.getValue().getEmployeeUserId()).isEqualTo(42L);
        assertThat(audit.getValue().getFieldKey()).isEqualTo("idNumber");
        assertThat(audit.getValue().getRequestIp()).isEqualTo("10.0.0.8");
        assertThat(audit.getValue().getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getValue().toString()).doesNotContain("350000199001010000");

        Method method = HrEmployeeProfileServiceImpl.class.getMethod("reveal", Long.class, String.class,
                Long.class, String.class, String.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void revealRejectsUnknownFieldsAndAuditFailureAbortsTheOperation()
    {
        assertThatThrownBy(() -> service.reveal(42L, "password", 9L, "审计员", "10.0.0.8"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("敏感字段");

        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee()));
        when(auditMapper.insertSensitiveAccessLog(any())).thenReturn(0);
        assertThatThrownBy(() -> service.reveal(42L, "bankAccount", 9L, "审计员", "10.0.0.8"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("审计");
    }

    @Test
    void sensitiveExportGeneratesArtifactBeforeSuccessAuditAndUsesExplicitFieldSet()
    {
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee()));
        when(workbookGenerator.generate(any(),any())).thenReturn(new byte[]{1,2,3});
        HrEmployeeQuery query = new HrEmployeeQuery();
        query.setKeyword("测试");

        HrSensitiveExportArtifact artifact = service.exportSensitive(query, Set.of("idNumber", "bankAccount"),
                9L, "审计员", "10.0.0.8");

        assertThat(artifact.getContent()).containsExactly(1,2,3);
        assertThat(artifact.getExportScope()).contains("idNumber", "bankAccount", "keywordPresent=true", "rowCount=1")
                .doesNotContain("测试");
        ArgumentCaptor<List<Map<String,Object>>> rows=ArgumentCaptor.forClass(List.class);
        var order=inOrder(workbookGenerator,exportAuditService);
        order.verify(workbookGenerator).generate(org.mockito.ArgumentMatchers.eq(
                List.of("employeeNo","employeeName","idNumber","bankAccount")),rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row->assertThat(row.keySet())
                .containsExactly("employeeNo","employeeName","idNumber","bankAccount"));
        order.verify(exportAuditService).recordGenerated(any(),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("审计员"),org.mockito.ArgumentMatchers.eq("10.0.0.8"));
    }

    @Test
    void generationFailureRecordsRequiresNewFailureAndNeverWritesFalseSuccess()
    {
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee()));
        doThrow(new IllegalStateException("workbook failed")).when(workbookGenerator).generate(any(),any());

        assertThatThrownBy(()->service.exportSensitive(new HrEmployeeQuery(),Set.of("idNumber"),
                9L,"审计员","10.0.0.8")).isInstanceOf(IllegalStateException.class).hasMessage("workbook failed");

        verify(exportAuditService).recordFailure(any(),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("审计员"),org.mockito.ArgumentMatchers.eq("10.0.0.8"),
                org.mockito.ArgumentMatchers.eq("GENERATION_FAILED"));
        verify(exportAuditService,never()).recordGenerated(any(),any(),any(),any());
    }

    @Test
    void sensitiveExportAppliesDatabaseRowCapBeforeMaterializingWorkbook()
    {
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.nCopies(10001,employee()));
        assertThatThrownBy(()->service.exportSensitive(new HrEmployeeQuery(),Set.of("idNumber"),
                9L,"审计员","10.0.0.8")).isInstanceOf(ServiceException.class).hasMessageContaining("10000");
        ArgumentCaptor<HrEmployeeQuery> limited=ArgumentCaptor.forClass(HrEmployeeQuery.class);
        verify(userMapper).selectHrEmployeeList(limited.capture());
        assertThat(limited.getValue().getMaxRows()).isEqualTo(10001);
        verify(workbookGenerator,never()).generate(any(),any());
    }

    @Test
    void failureAuditUsesIndependentRequiresNewBoundary() throws Exception
    {
        Transactional tx=HrSensitiveAuditService.class.getMethod("recordFailure",String.class,Long.class,
                String.class,String.class,String.class).getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
    }

    @Test
    void failureAuditPersistsOnlySafeWorkflowMetadata()
    {
        when(auditMapper.insertSensitiveAccessLog(any())).thenReturn(1);
        new HrSensitiveAuditService(auditMapper).recordFailure("fields=idNumber;rowCount=1",9L,
                "审计员","10.0.0.8","DELIVERY_FAILED");
        ArgumentCaptor<HrSensitiveAccessLog> audit=ArgumentCaptor.forClass(HrSensitiveAccessLog.class);
        verify(auditMapper).insertSensitiveAccessLog(audit.capture());
        assertThat(audit.getValue().getResult()).isEqualTo("FAILURE");
        assertThat(audit.getValue().getResultMessage()).isEqualTo("DELIVERY_FAILED");
        assertThat(audit.getValue().getExportScope()).isEqualTo("fields=idNumber;rowCount=1");
        assertThat(audit.getValue().toString()).doesNotContain("350000199001010000","6222020200001234567");
    }

    @Test
    void successAuditAccuratelyMeansGeneratedRatherThanDelivered()
    {
        when(auditMapper.insertSensitiveAccessLog(any())).thenReturn(1);
        new HrSensitiveAuditService(auditMapper).recordGenerated("fields=idNumber;rowCount=1",9L,
                "审计员","10.0.0.8");
        ArgumentCaptor<HrSensitiveAccessLog> audit=ArgumentCaptor.forClass(HrSensitiveAccessLog.class);
        verify(auditMapper).insertSensitiveAccessLog(audit.capture());
        assertThat(audit.getValue().getResult()).isEqualTo("SUCCESS");
        assertThat(audit.getValue().getResultMessage()).isEqualTo("GENERATED");
    }

    @Test
    void auditInsertFailureRollsBackTheTransactionalRevealBoundary()
    {
        when(userMapper.selectHrEmployeeList(any())).thenReturn(Collections.singletonList(employee()));
        when(auditMapper.insertSensitiveAccessLog(any())).thenReturn(0);
        RecordingTransactionManager transactions=new RecordingTransactionManager();
        AspectJProxyFactory factory=new AspectJProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor((TransactionManager)transactions,
                new AnnotationTransactionAttributeSource()));
        HrEmployeeProfileServiceImpl proxy=factory.getProxy();

        assertThatThrownBy(()->proxy.reveal(42L,"idNumber",9L,"审计员","10.0.0.8"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("审计");
        assertThat(transactions.rolledBack).isTrue();
        assertThat(transactions.committed).isFalse();
    }

    private SysUser employee()
    {
        SysUser user = new SysUser();
        user.setUserId(42L);
        user.setNickName("测试员工");
        SysUserProfile profile = new SysUserProfile();
        profile.setUserId(42L);
        profile.setEmployeeNo("E042");
        profile.setIdNumber("350000199001010000");
        profile.setBankAccount("6222020200001234567");
        user.setProfile(profile);
        return user;
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager
    {
        private static final long serialVersionUID=1L;
        private boolean rolledBack;
        private boolean committed;
        @Override protected Object doGetTransaction(){return new Object();}
        @Override protected void doBegin(Object transaction,TransactionDefinition definition){ }
        @Override protected void doCommit(DefaultTransactionStatus status){committed=true;}
        @Override protected void doRollback(DefaultTransactionStatus status){rolledBack=true;}
    }
}
