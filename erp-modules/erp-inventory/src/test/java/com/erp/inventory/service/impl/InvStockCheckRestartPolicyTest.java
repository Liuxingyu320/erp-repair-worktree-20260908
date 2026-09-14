package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class InvStockCheckRestartPolicyTest
{
    static InvStockCheckDetail row(long id, String count)
    {
        InvStockCheckDetail row = new InvStockCheckDetail(); row.setDetailId(id); row.setCheckId(1L);
        row.setItemType("gift"); row.setItemId(id); row.setBookQty(new BigDecimal("10.125"));
        row.setActualQty(count == null ? null : new BigDecimal(count)); return row;
    }
    static InvStockCheck restarted(InvStockCheckDetail row)
    {
        InvStockCheck check = new InvStockCheck();
        check.setRestartReferenceSnapshot(InvStockCheckRestartPolicy.appendRound(check, List.of(row), Set.of(row.getDetailId()), "counter")); return check;
    }
    @Test void firstRoundAcceptsMissingOrZeroAndRejectsUnknownTokens()
    {
        InvStockCheck check = new InvStockCheck(); InvStockCheckDetail row = row(1,"8");
        InvStockCheckRestartPolicy.validateInputs(check,List.of(row),List.of(row));
        row.setSnapshotVersion("0"); InvStockCheckRestartPolicy.validateInputs(check,List.of(row),List.of(row));
        row.setSnapshotVersion("fake"); assertThatThrownBy(() -> InvStockCheckRestartPolicy.validateInputs(check,List.of(row),List.of(row))).isInstanceOf(ServiceException.class);
    }
    @Test void everyRowMustMatchBeforeAValidPrefixCanBeWritten()
    {
        InvStockCheckDetail first = row(1,"8"), second = row(2,"9"); InvStockCheck check = restarted(first);
        first.setSnapshotVersion(InvStockCheckRestartPolicy.version(check));
        assertThatThrownBy(() -> InvStockCheckRestartPolicy.validateInputs(check,List.of(first,second),List.of(first,second))).hasMessageContaining("重新");
        second.setSnapshotVersion(first.getSnapshotVersion()); InvStockCheckRestartPolicy.validateInputs(check,List.of(first,second),List.of(first,second));
        assertThatThrownBy(() -> InvStockCheckRestartPolicy.validateInputs(check,List.of(first,first),List.of(first,second))).hasMessageContaining("重复");
        assertThatThrownBy(() -> InvStockCheckRestartPolicy.validateInputs(check,List.of(row(3,"8")),List.of(first))).hasMessageContaining("不存在");
    }
    @Test void subsequentRoundsPreserveExactOriginalValuesAndInvalidateOlderTokens()
    {
        InvStockCheckDetail row = row(Long.MAX_VALUE,"8.375"); row.setRecountQty(new BigDecimal("9.125"));
        InvStockCheck check = restarted(row); String first = InvStockCheckRestartPolicy.version(check), history = check.getRestartReferenceSnapshot();
        row.setActualQty(new BigDecimal("7.125")); check.setRestartReferenceSnapshot(InvStockCheckRestartPolicy.appendRound(check,List.of(row),Set.of(row.getDetailId()),"next"));
        var rounds=JSON.parseObject(check.getRestartReferenceSnapshot()).getJSONArray("rounds");
        assertThat(rounds).hasSize(2); assertThat(rounds.getJSONObject(0)).isEqualTo(JSON.parseObject(history).getJSONArray("rounds").getJSONObject(0));
        assertThat(rounds.getJSONObject(0).getJSONArray("details").getJSONObject(0).getString("detailId")).isEqualTo(String.valueOf(Long.MAX_VALUE));
        row.setSnapshotVersion(first); assertThatThrownBy(() -> InvStockCheckRestartPolicy.validateInputs(check,List.of(row),List.of(row))).hasMessageContaining("重新");
        row.setActualQty(null); InvStockCheckRestartPolicy.decorate(check,List.of(row));
        assertThat(row.getPreviousActualQty()).isEqualByComparingTo("7.125"); assertThat(row.getPreviousBookQty()).isEqualByComparingTo("10.125"); assertThat(row.isNeedsSnapshotReview()).isTrue();
        row.setActualQty(BigDecimal.ZERO); InvStockCheckRestartPolicy.decorate(check,List.of(row)); assertThat(row.isNeedsSnapshotReview()).isFalse();
    }
    @Test void invalidationEvidenceRetainsThePreviouslyChangedRow()
    {
        InvStockCheck check=new InvStockCheck(); check.setLastInvalidDetailSnapshot("[{\"detailId\":\"9223372036854775807\",\"bookQuantity\":10,\"currentQuantity\":9}]");
        assertThat(InvStockCheckRestartPolicy.invalidatedIds(check)).containsExactly(Long.MAX_VALUE);
        check.setLastInvalidDetailSnapshot("bad-json"); assertThatThrownBy(() -> InvStockCheckRestartPolicy.invalidatedIds(check)).hasMessageContaining("失效记录");
    }
    @Test void corruptPrivateHistoryCannotFallBackToVersionZero()
    {
        for(String raw:List.of("{}","null","{\"snapshotVersion\":\"0\",\"rounds\":[]}","broken"))
        { InvStockCheck check=new InvStockCheck();check.setRestartReferenceSnapshot(raw);assertThatThrownBy(() -> InvStockCheckRestartPolicy.version(check)).hasMessageContaining("重启记录"); }
    }
    @Test void privateHistoryNeverSerializesOrAcceptsClientReplacement() throws Exception
    {
        InvStockCheck check=restarted(row(1,"8")); ObjectMapper mapper=new ObjectMapper();
        assertThat(mapper.writeValueAsString(check)).doesNotContain("restartReferenceSnapshot", "10.125");
        assertThat(JSON.toJSONString(check)).doesNotContain("restartReferenceSnapshot", "10.125");
        assertThat(mapper.readValue("{\"restartReferenceSnapshot\":\"forged\"}",InvStockCheck.class).getRestartReferenceSnapshot()).isNull();
        assertThat(JSON.parseObject("{\"restartReferenceSnapshot\":\"forged\"}",InvStockCheck.class).getRestartReferenceSnapshot()).isNull();
        InvStockCheckDetail row=mapper.readValue("{\"snapshotVersion\":\"round\",\"previousBookQty\":100,\"previousActualQty\":200,\"previousRecountQty\":300,\"needsSnapshotReview\":true}",InvStockCheckDetail.class);
        assertThat(row.getSnapshotVersion()).isEqualTo("round"); assertThat(row.getPreviousBookQty()).isNull();assertThat(row.getPreviousActualQty()).isNull();assertThat(row.getPreviousRecountQty()).isNull();assertThat(row.isNeedsSnapshotReview()).isFalse();
    }
}
