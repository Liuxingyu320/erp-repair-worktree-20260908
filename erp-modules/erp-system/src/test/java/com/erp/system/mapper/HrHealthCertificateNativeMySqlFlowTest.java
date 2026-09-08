package com.erp.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Native transaction flow for draft, review, renewal and optimistic locking. */
@EnabledIfEnvironmentVariable(named = "ERP_HR_NATIVE_TEST_URL",
        matches = "jdbc:mysql:.*")
class HrHealthCertificateNativeMySqlFlowTest
{
    @Test
    void draftSubmitApproveAndRenewalKeepExactlyOneCurrentCertificate()
            throws Exception
    {
        String url = System.getenv("ERP_HR_NATIVE_TEST_URL");
        String username = System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_USERNAME", "root");
        String password = System.getenv().getOrDefault(
                "ERP_HR_NATIVE_TEST_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection(url, username,
                password))
        {
            connection.setAutoCommit(false);
            try
            {
                HrNativeMySqlTestSupport.requireIsolatedDatabase(connection);
                long userId = firstEmployeeProfileUserId(connection);
                String marker = "native-hr-" + UUID.randomUUID();

                long firstId = insertDraft(connection, userId, marker + "-1",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2027, 6, 30));
                assertThat(status(connection, firstId)).isEqualTo("DRAFT:0:");
                assertThat(submit(connection, firstId, userId, 0L)).isEqualTo(1);
                assertThat(submit(connection, firstId, userId, 0L)).isZero();
                assertThat(approve(connection, firstId, 1L)).isEqualTo(1);
                assertThat(status(connection, firstId))
                        .isEqualTo("APPROVED:2:Y");
                assertThat(currentCount(connection, userId)).isEqualTo(1);

                long renewalId = insertDraft(connection, userId,
                        marker + "-2", LocalDate.of(2027, 6, 1),
                        LocalDate.of(2028, 5, 31));
                assertThat(submit(connection, renewalId, userId, 0L))
                        .isEqualTo(1);

                Savepoint beforeReview = connection.setSavepoint();
                clearCurrent(connection, userId);
                assertThat(approve(connection, renewalId, 1L)).isEqualTo(1);
                assertThat(currentCount(connection, userId)).isEqualTo(1);
                assertThat(status(connection, firstId)).isEqualTo("APPROVED:3:");
                assertThat(status(connection, renewalId))
                        .isEqualTo("APPROVED:2:Y");
                connection.rollback(beforeReview);
                assertThat(status(connection, firstId))
                        .isEqualTo("APPROVED:2:Y");
                assertThat(status(connection, renewalId))
                        .isEqualTo("PENDING_REVIEW:1:");

                Savepoint duplicateCurrent = connection.setSavepoint();
                assertThatThrownBy(() -> forceCurrent(connection, renewalId))
                        .isInstanceOf(SQLIntegrityConstraintViolationException.class);
                connection.rollback(duplicateCurrent);

                clearCurrent(connection, userId);
                assertThat(approve(connection, renewalId, 1L)).isEqualTo(1);
                assertThat(currentCount(connection, userId)).isEqualTo(1);
            }
            finally
            {
                connection.rollback();
            }
        }
    }

    private long firstEmployeeProfileUserId(Connection connection)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "select p.user_id from sys_user_profile p "
                        + "inner join sys_user u on u.user_id=p.user_id "
                        + "where u.del_flag='0' and u.status='0' "
                        + "order by p.user_id limit 1");
                ResultSet rows = statement.executeQuery())
        {
            assertThat(rows.next()).as("隔离库至少需要一名启用且有档案的员工")
                    .isTrue();
            return rows.getLong(1);
        }
    }

    private long insertDraft(Connection connection, long userId,
            String certificateNo, LocalDate issuedDate, LocalDate expiresOn)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into hr_employee_health_certificate "
                        + "(user_id,certificate_no,issued_date,valid_from,expires_on,"
                        + "review_status,current_flag,version,del_flag,create_by) "
                        + "values (?,?,?,?,?,'DRAFT',null,0,'0','native-hr-test')",
                PreparedStatement.RETURN_GENERATED_KEYS))
        {
            statement.setLong(1, userId);
            statement.setString(2, certificateNo);
            statement.setObject(3, issuedDate);
            statement.setObject(4, issuedDate);
            statement.setObject(5, expiresOn);
            assertThat(statement.executeUpdate()).isEqualTo(1);
            try (ResultSet keys = statement.getGeneratedKeys())
            {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private int submit(Connection connection, long certificateId, long userId,
            long version) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "update hr_employee_health_certificate "
                        + "set review_status='PENDING_REVIEW',version=version+1 "
                        + "where certificate_id=? and user_id=? "
                        + "and review_status in ('DRAFT','REJECTED') "
                        + "and version=? and del_flag='0'"))
        {
            statement.setLong(1, certificateId);
            statement.setLong(2, userId);
            statement.setLong(3, version);
            return statement.executeUpdate();
        }
    }

    private int approve(Connection connection, long certificateId,
            long version) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "update hr_employee_health_certificate "
                        + "set review_status='APPROVED',current_flag='Y',"
                        + "reviewed_by_user_id=1,reviewed_by_name='native-test',"
                        + "reviewed_time=now(),version=version+1 "
                        + "where certificate_id=? and review_status='PENDING_REVIEW' "
                        + "and version=? and del_flag='0'"))
        {
            statement.setLong(1, certificateId);
            statement.setLong(2, version);
            return statement.executeUpdate();
        }
    }

    private void clearCurrent(Connection connection, long userId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "update hr_employee_health_certificate "
                        + "set current_flag=null,version=version+1 "
                        + "where user_id=? and current_flag='Y' and del_flag='0'"))
        {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private void forceCurrent(Connection connection, long certificateId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "update hr_employee_health_certificate set current_flag='Y' "
                        + "where certificate_id=?"))
        {
            statement.setLong(1, certificateId);
            statement.executeUpdate();
        }
    }

    private int currentCount(Connection connection, long userId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from hr_employee_health_certificate "
                        + "where user_id=? and current_flag='Y' and del_flag='0'"))
        {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery())
            {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    private String status(Connection connection, long certificateId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "select review_status,version,coalesce(current_flag,'') "
                        + "from hr_employee_health_certificate where certificate_id=?"))
        {
            statement.setLong(1, certificateId);
            try (ResultSet rows = statement.executeQuery())
            {
                assertThat(rows.next()).isTrue();
                return rows.getString(1) + ":" + rows.getLong(2) + ":"
                        + rows.getString(3);
            }
        }
    }
}
