package com.erp.oa.service.rule;

import java.math.BigDecimal;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

/** Salary confirmation follows the frozen monetary change, never the display version. */
public final class TransferSalaryChangePolicy
{
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private TransferSalaryChangePolicy() { }

    public static boolean changed(HrEmployeeSigningSnapshot before, HrEmployeeSigningSnapshot after)
    {
        return different(values(before), values(after));
    }

    public static boolean changedForPackage(OaSignTask task, OaSignPackage signPackage)
    {
        if (task == null || signPackage == null || task.getTaskId() == null
                || !Objects.equals(task.getTaskId(), signPackage.getTaskId())
                || task.getPackageId() == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || task.getEmployeeId() == null || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !"TRANSFER".equalsIgnoreCase(task.getScenario())
                || !"TRANSFER".equalsIgnoreCase(signPackage.getScenario()))
            throw unconfirmed();
        try
        {
            HrEmployeeSigningSnapshot before = JSON.readValue(task.getBeforeSnapshotJson(), HrEmployeeSigningSnapshot.class);
            HrEmployeeSigningSnapshot after = JSON.readValue(task.getAfterSnapshotJson(), HrEmployeeSigningSnapshot.class);
            if (before == null || after == null || !Objects.equals(task.getEmployeeId(), before.getEmployeeId())
                    || !Objects.equals(task.getEmployeeId(), after.getEmployeeId())) throw unconfirmed();
            BigDecimal[] packageSalary = { signPackage.getBaseSalary(), signPackage.getPostSalary(),
                    signPackage.getFieldAllowance(), signPackage.getPerformanceSalary(), signPackage.getSalaryTotal() };
            if (different(values(after), packageSalary)) throw unconfirmed();
            return changed(before, after);
        }
        catch (Exception error)
        {
            throw unconfirmed();
        }
    }

    private static BigDecimal[] values(HrEmployeeSigningSnapshot snapshot)
    {
        if (snapshot == null) throw unconfirmed();
        return new BigDecimal[] { snapshot.getBaseSalary(), snapshot.getPostSalary(), snapshot.getFieldAllowance(),
                snapshot.getPerformanceSalary(), snapshot.getSalaryTotal() };
    }

    private static boolean different(BigDecimal[] before, BigDecimal[] after)
    {
        boolean changed = false;
        for (int index = 0; index < before.length; index++)
        {
            if (before[index] == null || after[index] == null || before[index].signum() < 0 || after[index].signum() < 0)
                throw unconfirmed();
            changed |= before[index].compareTo(after[index]) != 0;
        }
        return changed;
    }

    private static ServiceException unconfirmed()
    {
        return new ServiceException("调岗工资冻结快照暂未核实，请人事核对后重试");
    }
}
