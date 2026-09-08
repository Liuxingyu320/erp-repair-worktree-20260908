package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.erp.system.domain.SysSalaryScheme;
import com.erp.system.domain.SysSalarySchemeItem;

/**
 * 薪资修订中的纯业务快照。刻意不包含用户、角色绑定和个人工资结果。
 */
public class SysSalarySchemeSnapshot
{
    private String schemeName;
    private String socialType;
    private String effectiveDate;
    private String status;
    private String remark;
    private List<Item> items = new ArrayList<>();

    public static SysSalarySchemeSnapshot from(SysSalaryScheme scheme, List<SysSalarySchemeItem> sourceItems)
    {
        SysSalarySchemeSnapshot snapshot = new SysSalarySchemeSnapshot();
        snapshot.setSchemeName(scheme.getSchemeName());
        snapshot.setSocialType(scheme.getSocialType());
        snapshot.setEffectiveDate(scheme.getEffectiveDate());
        snapshot.setStatus(scheme.getStatus());
        snapshot.setRemark(scheme.getRemark());
        if (sourceItems != null)
        {
            for (SysSalarySchemeItem source : sourceItems)
            {
                snapshot.getItems().add(Item.from(source));
            }
        }
        return snapshot;
    }

    public SysSalaryScheme toScheme(Long schemeId, Integer expectedVersion)
    {
        SysSalaryScheme scheme = new SysSalaryScheme();
        scheme.setSchemeId(schemeId);
        scheme.setSchemeName(schemeName);
        scheme.setSocialType(socialType);
        scheme.setEffectiveDate(effectiveDate);
        scheme.setStatus(status);
        scheme.setRemark(remark);
        scheme.setVersion(expectedVersion);
        List<SysSalarySchemeItem> restoredItems = new ArrayList<>();
        for (Item item : items)
        {
            restoredItems.add(item.toDomain(schemeId));
        }
        scheme.setItems(restoredItems);
        return scheme;
    }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items == null ? new ArrayList<>() : items; }

    public static class Item
    {
        private String postName;
        private String gradeName;
        private String regionName;
        private BigDecimal baseSalary;
        private BigDecimal managementAllowance;
        private BigDecimal overtimePay;
        private BigDecimal rewardAllowance;
        private BigDecimal fullAttendanceBonus;
        private BigDecimal socialSubsidy;
        private BigDecimal commuteSubsidy;
        private Integer itemSort;
        private String remark;

        static Item from(SysSalarySchemeItem source)
        {
            Item item = new Item();
            item.setPostName(source.getPostName());
            item.setGradeName(source.getGradeName());
            item.setRegionName(source.getRegionName());
            item.setBaseSalary(source.getBaseSalary());
            item.setManagementAllowance(source.getManagementAllowance());
            item.setOvertimePay(source.getOvertimePay());
            item.setRewardAllowance(source.getRewardAllowance());
            item.setFullAttendanceBonus(source.getFullAttendanceBonus());
            item.setSocialSubsidy(source.getSocialSubsidy());
            item.setCommuteSubsidy(source.getCommuteSubsidy());
            item.setItemSort(source.getItemSort());
            item.setRemark(source.getRemark());
            return item;
        }

        SysSalarySchemeItem toDomain(Long schemeId)
        {
            SysSalarySchemeItem item = new SysSalarySchemeItem();
            item.setSchemeId(schemeId);
            item.setPostName(postName);
            item.setGradeName(gradeName);
            item.setRegionName(regionName);
            item.setBaseSalary(baseSalary);
            item.setManagementAllowance(managementAllowance);
            item.setOvertimePay(overtimePay);
            item.setRewardAllowance(rewardAllowance);
            item.setFullAttendanceBonus(fullAttendanceBonus);
            item.setSocialSubsidy(socialSubsidy);
            item.setCommuteSubsidy(commuteSubsidy);
            item.setItemSort(itemSort);
            item.setRemark(remark);
            return item;
        }

        public String getPostName() { return postName; }
        public void setPostName(String postName) { this.postName = postName; }
        public String getGradeName() { return gradeName; }
        public void setGradeName(String gradeName) { this.gradeName = gradeName; }
        public String getRegionName() { return regionName; }
        public void setRegionName(String regionName) { this.regionName = regionName; }
        public BigDecimal getBaseSalary() { return baseSalary; }
        public void setBaseSalary(BigDecimal value) { this.baseSalary = value; }
        public BigDecimal getManagementAllowance() { return managementAllowance; }
        public void setManagementAllowance(BigDecimal value) { this.managementAllowance = value; }
        public BigDecimal getOvertimePay() { return overtimePay; }
        public void setOvertimePay(BigDecimal value) { this.overtimePay = value; }
        public BigDecimal getRewardAllowance() { return rewardAllowance; }
        public void setRewardAllowance(BigDecimal value) { this.rewardAllowance = value; }
        public BigDecimal getFullAttendanceBonus() { return fullAttendanceBonus; }
        public void setFullAttendanceBonus(BigDecimal value) { this.fullAttendanceBonus = value; }
        public BigDecimal getSocialSubsidy() { return socialSubsidy; }
        public void setSocialSubsidy(BigDecimal value) { this.socialSubsidy = value; }
        public BigDecimal getCommuteSubsidy() { return commuteSubsidy; }
        public void setCommuteSubsidy(BigDecimal value) { this.commuteSubsidy = value; }
        public Integer getItemSort() { return itemSort; }
        public void setItemSort(Integer itemSort) { this.itemSort = itemSort; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
