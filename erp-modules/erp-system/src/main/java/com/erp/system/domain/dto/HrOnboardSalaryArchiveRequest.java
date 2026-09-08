package com.erp.system.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record HrOnboardSalaryArchiveRequest(
        @NotNull @Positive Long batchId,
        @NotEmpty @Size(max = 100) List<@Valid Row> rows)
{
    public record Row(@NotNull @Positive Long rowId,
            @NotNull @PositiveOrZero Long version) { }
}
