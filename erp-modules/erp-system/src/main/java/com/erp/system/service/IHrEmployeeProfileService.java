package com.erp.system.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.system.domain.vo.*;

public interface IHrEmployeeProfileService
{
    List<HrEmployeeListVo> list(HrEmployeeQuery query);
    HrEmployeeProfileVo get(Long userId);
    HrEmployeeSummaryVo summary(HrEmployeeQuery query);
    Map<String, Object> formOptions();
    HrEmployeeProfileVo derivedPreview(Map<String, Object> values);
    HrEmployeeProfileVo initializeProfile(Long userId,String operator);
    HrEmployeeProfileVo update(Long userId, Map<String, Object> patch, String operator);
    HrEmployeeProfileVo updateLegacy(Map<String,Object> input,String operator);
    HrEmployeeProfileVo completeness(Long userId);
    List<HrEmployeeListVo> completenessEmployees(HrEmployeeQuery query);
    List<Map<String, Object>> completenessDepartments(HrEmployeeQuery query);
    HrSensitiveRevealVo reveal(Long userId, String fieldKey, Long operatorUserId, String operatorName, String requestIp);
    HrSensitiveExportArtifact exportSensitive(HrEmployeeQuery query, Set<String> requestedFields,
            Long operatorUserId, String operatorName, String requestIp);
    void recordSensitiveExportDeliveryFailure(String exportScope,Long operatorUserId,String operatorName,String requestIp);
}
