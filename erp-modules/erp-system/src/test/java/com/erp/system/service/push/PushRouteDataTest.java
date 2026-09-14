package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;

class PushRouteDataTest
{
    @Test
    void healthReminderPreservesEmployeeAndCertificateButCannotOverrideRecipient()
    {
        UserNotificationCommand command = command("HR_HEALTH_CERT_DUE", """
                {"userId":42,"certificateId":19,"recipientUserId":999,"url":"https://invalid.example",
                 "employeeId":999,"salary":10000}
                """);
        assertThat(PushDeliveryClient.whitelistedRouteData(command)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "routeType", "HR_HEALTH_CERT_DUE", "recipientUserId", "88",
                "employeeId", "42", "certificateId", "19", "businessKey", "event:1"));
    }

    @Test
    void genericMessageAllowsOptionalNotificationIdAndNeverForwardsArbitraryPath()
    {
        assertThat(PushDeliveryClient.whitelistedRouteData(command("USER_NOTIFICATION", null)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("routeType", "USER_NOTIFICATION",
                        "recipientUserId", "88", "businessKey", "event:1"));
        assertThat(PushDeliveryClient.whitelistedRouteData(command("USER_NOTIFICATION",
                "{\"notificationId\":91,\"path\":\"/admin\"}")))
                .containsEntry("notificationId", "91").doesNotContainKey("path");
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "1.5", "\"01\"", "null", "true", "9223372036854775808" })
    void malformedRouteIdsNeverReachADevice(String id)
    {
        assertThatThrownBy(() -> PushDeliveryClient.whitelistedRouteData(
                command("USER_NOTIFICATION", "{\"notificationId\":" + id + "}")))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void unknownRouteAndMissingRecipientFailClosed()
    {
        assertThatThrownBy(() -> PushDeliveryClient.whitelistedRouteData(command("EXTERNAL_URL", "{}")))
                .isInstanceOf(ServiceException.class);
        UserNotificationCommand command = command("USER_NOTIFICATION", null);
        command.setRecipientUserId(null);
        assertThatThrownBy(() -> PushDeliveryClient.whitelistedRouteData(command))
                .isInstanceOf(ServiceException.class);
    }

    private static UserNotificationCommand command(String type, String params)
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setRecipientUserId(88L);
        command.setBusinessKey("event:1");
        command.setRouteType(type);
        command.setRouteParams(params);
        return command;
    }
}
