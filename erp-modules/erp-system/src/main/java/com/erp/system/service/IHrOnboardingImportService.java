package com.erp.system.service;

import java.util.Date;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingImportPreviewVo;

public interface IHrOnboardingImportService
{
    HrOnboardingImportPreviewVo preview(MultipartFile file, String operator);
    HrOnboardingImportPreviewVo getBatch(Long batchId);
    HrOnboardingImportPreviewVo confirmBatch(Long batchId, HrOnboardingImportConfirmRequest request, String operator);
    void writeTemplate(HttpServletResponse response);
    void writeErrorRows(Long batchId, HttpServletResponse response);
    void deleteExpiredUnconfirmedBatches(Date cutoff, int limit);
}
