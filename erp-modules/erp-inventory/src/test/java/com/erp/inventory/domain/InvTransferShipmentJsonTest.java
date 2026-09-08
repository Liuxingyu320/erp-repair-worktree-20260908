package com.erp.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("调拨发货批次 JSON ID")
class InvTransferShipmentJsonTest
{
    @Test
    @DisplayName("批次和父调拨 ID 必须按字符串输出以保证浏览器精确定位")
    void shouldSerializeLongIdentifiersAsStrings() throws Exception
    {
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setShipmentId(9_999_999_999_999_999L);
        shipment.setTransferId(9_888_888_888_888_888L);
        shipment.setSealedRevisionId(9_777_777_777_777_777L);

        String json = new ObjectMapper().writeValueAsString(shipment);

        assertThat(json)
                .contains("\"shipmentId\":\"9999999999999999\"")
                .contains("\"transferId\":\"9888888888888888\"")
                .contains("\"sealedRevisionId\":\"9777777777777777\"")
                .doesNotContain("\"shipmentId\":9999999999999999")
                .doesNotContain("\"transferId\":9888888888888888");
    }
}
