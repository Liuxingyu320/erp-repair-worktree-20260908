package com.erp.system.domain.vo;

import java.util.LinkedHashSet;
import java.util.Set;

public class HrSensitiveExportRequest
{
    private HrEmployeeQuery filter = new HrEmployeeQuery();
    private Set<String> requestedFields = new LinkedHashSet<>();
    public HrEmployeeQuery getFilter(){return filter;} public void setFilter(HrEmployeeQuery v){filter=v;}
    public Set<String> getRequestedFields(){return requestedFields;} public void setRequestedFields(Set<String> v){requestedFields=v;}
}
