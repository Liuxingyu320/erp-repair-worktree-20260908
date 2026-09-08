package com.erp.system.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.uuid.IdUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportBatch;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.vo.HrOnboardingImportConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingImportPreviewVo;
import com.erp.system.mapper.HrOnboardingImportMapper;
import com.erp.system.service.IHrOnboardingImportService;
import com.erp.system.service.ISysConfigService;
import com.erp.system.support.HrOnboardingExcelParser;
import com.erp.system.support.HrOnboardingImportPayloadCodec;
import com.erp.system.support.HrSensitiveFieldMasker;
import com.erp.system.exception.HrOnboardingValidationException;

@Service
public class HrOnboardingImportServiceImpl implements IHrOnboardingImportService
{
    private static final List<String> EXPIRABLE = Collections.unmodifiableList(Arrays.asList("PREVIEWED", "FAILED"));
    private static final String RETENTION_KEY = "hr.onboarding.import_retention_days";
    private static final String LEASE_KEY = "hr.onboarding.import_processing_lease_seconds";
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private final HrOnboardingImportMapper mapper;
    private final HrOnboardingExcelParser parser;
    private final HrOnboardingImportRowProcessor rowProcessor;
    private final HrOnboardingImportBatchCoordinator coordinator;
    private final ISysConfigService configService;
    private final HrSensitiveFieldMasker masker;
    private final HrOnboardingImportPayloadCodec payloadCodec;
    private final Clock clock;
    private final LongSupplier currentUserId;
    private final BooleanSupplier currentAdmin;

    @Autowired
    public HrOnboardingImportServiceImpl(HrOnboardingImportMapper mapper, HrOnboardingExcelParser parser,
            HrOnboardingImportRowProcessor rowProcessor,HrOnboardingImportBatchCoordinator coordinator,
            ISysConfigService configService,
            HrSensitiveFieldMasker masker, HrOnboardingImportPayloadCodec payloadCodec)
    {
        this(mapper, parser, rowProcessor, coordinator,configService, masker, payloadCodec, Clock.systemDefaultZone(),
                SecurityUtils::getUserId, SecurityUtils::isAdmin);
    }

    HrOnboardingImportServiceImpl(HrOnboardingImportMapper mapper, HrOnboardingExcelParser parser,
            HrOnboardingImportRowProcessor rowProcessor, ISysConfigService configService,
            HrSensitiveFieldMasker masker, Clock clock, LongSupplier currentUserId, BooleanSupplier currentAdmin)
    {
        this(mapper,parser,rowProcessor,new HrOnboardingImportBatchCoordinator(mapper),configService,masker,
                new HrOnboardingImportPayloadCodec(),clock,currentUserId,currentAdmin);
    }

    HrOnboardingImportServiceImpl(HrOnboardingImportMapper mapper, HrOnboardingExcelParser parser,
            HrOnboardingImportRowProcessor rowProcessor,HrOnboardingImportBatchCoordinator coordinator,
            ISysConfigService configService,
            HrSensitiveFieldMasker masker, HrOnboardingImportPayloadCodec payloadCodec, Clock clock,
            LongSupplier currentUserId, BooleanSupplier currentAdmin)
    {
        this.mapper=mapper; this.parser=parser; this.rowProcessor=rowProcessor; this.configService=configService;
        this.coordinator=coordinator;
        this.masker=masker; this.payloadCodec=payloadCodec; this.clock=clock;
        this.currentUserId=currentUserId; this.currentAdmin=currentAdmin;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrOnboardingImportPreviewVo preview(MultipartFile file, String operator)
    {
        int retention = retentionDays();
        deleteExpiredUnconfirmedBatches(Date.from(clock.instant().minus(retention, ChronoUnit.DAYS)), 1000);
        if (file == null || file.isEmpty()) throw new ServiceException("IMPORT_FILE_REQUIRED");
        if(file.getSize()>MAX_UPLOAD_BYTES)throw new ServiceException("IMPORT_FILE_SIZE_LIMIT_EXCEEDED");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException failure) { throw new ServiceException("IMPORT_FILE_INVALID"); }
        if(bytes.length>MAX_UPLOAD_BYTES)throw new ServiceException("IMPORT_FILE_SIZE_LIMIT_EXCEEDED");
        List<HrOnboardingImportRow> rows = parser.parse(new ByteArrayInputStream(bytes));
        HrOnboardingImportBatch batch = new HrOnboardingImportBatch();
        batch.setBatchNo("IMP" + IdUtils.simpleUUID().substring(0, 20).toUpperCase());
        batch.setFileName(safeFileName(file.getOriginalFilename()));
        batch.setFileSize((long) bytes.length); batch.setFileHash(sha256(bytes));
        batch.setStatus(HrOnboardingImportBatch.PREVIEWED); batch.setVersion(0);
        batch.setTotalRows(rows.size()); batch.setImportableRows(count(rows,"IMPORTABLE"));
        batch.setWarningRows(count(rows,"WARNING")); batch.setInvalidRows(count(rows,"INVALID"));
        batch.setDuplicateRows(count(rows,"POSSIBLE_DUPLICATE")); batch.setBindableRows(count(rows,"BINDABLE_ACCOUNT"));
        batch.setSuccessRows(0); batch.setFailureRows(0); batch.setCreatorUserId(currentUserId.getAsLong());
        batch.setCreatorName(operator); batch.setExpiresTime(Date.from(clock.instant().plus(retention, ChronoUnit.DAYS)));
        batch.setCreateBy(operator); batch.setUpdateBy(operator);
        mapper.insertBatch(batch);
        for (HrOnboardingImportRow row : rows)
        {
            row.setBatchId(batch.getBatchId());
            row.setPayloadJson(payloadCodec.encode(row.getPayload(),row.getRawSourceValues()));
            row.setCreateBy(operator); row.setUpdateBy(operator); mapper.insertRow(row);
        }
        return toVo(batch, rows);
    }

    @Override
    public HrOnboardingImportPreviewVo getBatch(Long batchId)
    {
        HrOnboardingImportBatch batch = owned(batchId);
        return toVo(batch, hydratedRows(batchId));
    }

    @Override
    public HrOnboardingImportPreviewVo confirmBatch(Long batchId, HrOnboardingImportConfirmRequest request,
            String operator)
    {
        HrOnboardingImportBatch before = owned(batchId);
        if(isCompleted(before.getStatus()))return getBatch(batchId);
        List<HrOnboardingImportConfirmRequest.RowDecision> requested=request==null?null:request.getRows();
        boolean newlyClaimed=HrOnboardingImportBatch.PREVIEWED.equals(before.getStatus());
        int leaseVersion;
        if(HrOnboardingImportBatch.PREVIEWED.equals(before.getStatus()))
        {
            if(request==null||request.getVersion()==null)throw new ServiceException("IMPORT_VERSION_REQUIRED");
            validateDecisions(requested);
            Map<Long,HrOnboardingImportRow> candidates=hydratedRows(batchId).stream()
                    .collect(Collectors.toMap(HrOnboardingImportRow::getRowId,value->value));
            List<HrOnboardingImportConfirmRequest.RowDecision> durable=new ArrayList<>();
            if(requested!=null)for(HrOnboardingImportConfirmRequest.RowDecision decision:requested)
            {
                if(!candidates.containsKey(decision.getRowId()))throw new ServiceException("ROW_NOT_IN_BATCH");
                durable.add(decision);
            }
            try{coordinator.claimAndRecord(batchId,request.getVersion(),before.getCreatorUserId(),durable,operator);}
            catch(ServiceException claimFailure)
            {
                if(!"IMPORT_VERSION_CONFLICT".equals(claimFailure.getMessage()))throw claimFailure;
                HrOnboardingImportBatch latest=owned(batchId);
                if(HrOnboardingImportBatch.PROCESSING.equals(latest.getStatus())||isCompleted(latest.getStatus()))
                    return getBatch(batchId);
                throw claimFailure;
            }
            leaseVersion=request.getVersion()+1;
        }
        else if(HrOnboardingImportBatch.PROCESSING.equals(before.getStatus()))
        {
            Date cutoff=Date.from(clock.instant().minus(leaseSeconds(),ChronoUnit.SECONDS));
            if(before.getUpdateTime()!=null&&!before.getUpdateTime().before(cutoff))return getBatch(batchId);
            coordinator.resume(batchId,before.getVersion(),before.getCreatorUserId(),cutoff,operator);
            leaseVersion=before.getVersion()+1;
        }
        else return getBatch(batchId);
        Map<Long,HrOnboardingImportRow> stored = hydratedRows(batchId).stream()
                .collect(Collectors.toMap(HrOnboardingImportRow::getRowId, value -> value));
        Map<Long,HrOnboardingImportConfirmRequest.RowDecision> durableDecisions=new LinkedHashMap<>();
        if(newlyClaimed&&requested!=null)for(HrOnboardingImportConfirmRequest.RowDecision decision:requested)
            if(stored.containsKey(decision.getRowId()))durableDecisions.put(decision.getRowId(),decision);
        for(HrOnboardingImportRow row:stored.values())if(row.getDecision()!=null)
            durableDecisions.putIfAbsent(row.getRowId(),decision(row));
        int success=(int)stored.values().stream().filter(r->"SUCCESS".equals(r.getRowStatus())).count();
        int failure=(int)stored.values().stream().filter(r->"FAILED".equals(r.getRowStatus())).count();
        List<Map<String,Object>> errors = new ArrayList<>();
        for (HrOnboardingImportConfirmRequest.RowDecision decision : durableDecisions.values())
        {
            HrOnboardingImportRow staged = stored.get(decision.getRowId());
            if(staged==null||!"PREVIEWED".equals(staged.getRowStatus()))continue;
            if ("INVALID".equals(staged.getCategory()))
            {
                failure++; recordFailure(staged,"ROW_NOT_CONFIRMABLE",operator,errors); continue;
            }
            coordinator.heartbeat(batchId,leaseVersion,before.getCreatorUserId(),operator);
            try { rowProcessor.process(decision.getRowId(), decision, operator,leaseVersion); success++; }
            catch (RuntimeException rowFailure)
            {
                if("IMPORT_PROCESSING_LEASE_LOST".equals(rowFailure.getMessage()))throw rowFailure;
                failure++; recordFailure(staged,failureCode(rowFailure),operator,errors);
            }
        }
        String status = failure == 0 ? HrOnboardingImportBatch.COMPLETED : HrOnboardingImportBatch.COMPLETED_WITH_ERRORS;
        coordinator.heartbeat(batchId,leaseVersion,before.getCreatorUserId(),operator);
        coordinator.finish(batchId,leaseVersion,success,failure,status,currentUserId.getAsLong(),operator);
        HrOnboardingImportPreviewVo result = getBatch(batchId);
        result.setSuccessRows(success); result.setFailureRows(failure); result.setErrors(errors);
        return result;
    }

    private HrOnboardingImportConfirmRequest.RowDecision decision(HrOnboardingImportRow row)
    {
        HrOnboardingImportConfirmRequest.RowDecision decision=new HrOnboardingImportConfirmRequest.RowDecision();
        decision.setRowId(row.getRowId());decision.setDecision(row.getDecision());decision.setBindUserId(row.getBindUserId());
        return decision;
    }

    private boolean isCompleted(String status)
    {return HrOnboardingImportBatch.COMPLETED.equals(status)||HrOnboardingImportBatch.COMPLETED_WITH_ERRORS.equals(status);}

    private void validateDecisions(List<HrOnboardingImportConfirmRequest.RowDecision> decisions)
    {
        if (decisions == null) return;
        Set<Long> rowIds = new HashSet<>();
        for (HrOnboardingImportConfirmRequest.RowDecision decision : decisions)
        {
            if (decision == null || decision.getRowId() == null) throw new ServiceException("ROW_DECISION_INVALID");
            if (!rowIds.add(decision.getRowId())) throw new ServiceException("DUPLICATE_ROW_DECISION");
        }
    }

    private void recordFailure(HrOnboardingImportRow staged,String code,String operator,List<Map<String,Object>> errors)
    {
        String message=safeMessage(code);
        if(mapper.markRowFailure(staged.getRowId(),staged.getBatchId(),code,message,currentUserId.getAsLong(),operator)!=1)
            throw new ServiceException("IMPORT_ROW_FAILURE_UPDATE_FAILED");
        addFailure(code,message,staged.getRowId(),staged,errors);
    }

    private void addFailure(String code,String message,Long rowId,HrOnboardingImportRow staged,
            List<Map<String,Object>> errors)
    {
        Map<String,Object> error = new LinkedHashMap<>(); error.put("rowId", rowId);
        error.put("sourceRowNumber", staged == null ? null : staged.getSourceRowNumber()); error.put("code", code);
        error.put("message",message);
        errors.add(error);
    }

    private String failureCode(RuntimeException failure)
    {
        if(failure instanceof HrOnboardingValidationException)
            return ((HrOnboardingValidationException)failure).getErrorCode();
        String value=failure.getMessage();
        if(value!=null && Arrays.asList("STALE_CONFLICT","STALE_BIND_CANDIDATE","OUT_OF_SCOPE",
                "ROW_NOT_CONFIRMABLE","ROW_INVALID","ROW_DECISION_INVALID","IMPORT_DECISION_REQUIRED",
                "CONTINUE_DECISION_REQUIRED","BIND_DECISION_REQUIRED","BIND_USER_REQUIRED",
                "ROW_PAYLOAD_INVALID","IMPORT_PAYLOAD_INVALID","ONBOARDING_VALIDATION_FAILED",
                "IMPORT_CREATE_FAILED","IMPORT_ROW_DECISION_UPDATE_FAILED",
                "IMPORT_ROW_SUCCESS_UPDATE_FAILED","IMPORT_PROCESSING_LEASE_LOST").contains(value))return value;
        return "ROW_PROCESSING_FAILED";
    }

    private String safeMessage(String code)
    {
        Map<String,String> values=new LinkedHashMap<>();
        values.put("STALE_CONFLICT","冲突信息已变化，请刷新预检结果");
        values.put("STALE_BIND_CANDIDATE","绑定候选已变化，请重新选择");
        values.put("OUT_OF_SCOPE","目标组织或人员已不可访问");
        values.put("ROW_NOT_IN_BATCH","行不属于当前导入批次");
        values.put("ROW_NOT_CONFIRMABLE","该行当前不可确认导入");
        values.put("ONBOARDING_VALIDATION_FAILED","入职资料未通过创建校验");
        values.put("BIND_USER_REQUIRED","请选择要绑定的已有账号");
        values.put("CONTINUE_DECISION_REQUIRED","请明确确认继续导入");
        values.put("IMPORT_DECISION_REQUIRED","该行需要选择直接导入");
        values.put("BIND_DECISION_REQUIRED","该行需要选择绑定已有账号");
        values.put("ROW_DECISION_INVALID","行确认选择无效");
        values.put("ROW_INVALID","该行预检未通过");
        values.put("ROW_PAYLOAD_INVALID","暂存行资料无法读取");
        values.put("IMPORT_PAYLOAD_INVALID","暂存行资料无法创建入职单");
        values.put("IMPORT_CREATE_FAILED","创建入职单失败");
        values.put("IMPORT_ROW_DECISION_UPDATE_FAILED","保存导入选择失败");
        values.put("IMPORT_ROW_SUCCESS_UPDATE_FAILED","保存导入结果失败");
        return values.getOrDefault(code,"该行未导入，请检查预检结果后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExpiredUnconfirmedBatches(Date cutoff, int limit)
    {
        int bounded = Math.max(1, Math.min(1000, limit));
        mapper.deleteRowsForExpiredBatches(cutoff, bounded, EXPIRABLE);
        mapper.deleteExpiredBatches(cutoff, bounded, EXPIRABLE);
    }

    @Override
    public void writeTemplate(HttpServletResponse response)
    {
        try (XSSFWorkbook workbook = parser.createTemplate()) { prepare(response,"入职导入模板.xlsx"); workbook.write(response.getOutputStream()); }
        catch (IOException failure) { throw new ServiceException("IMPORT_TEMPLATE_EXPORT_FAILED"); }
    }

    @Override
    public void writeErrorRows(Long batchId, HttpServletResponse response)
    {
        HrOnboardingImportPreviewVo preview = getBatch(batchId);
        try (XSSFWorkbook workbook = new XSSFWorkbook())
        {
            Sheet sheet = workbook.createSheet("错误行"); Row header=sheet.createRow(0);
            String[] columns={"源行号","姓名","手机号(脱敏)","证件号(脱敏)","银行卡号(脱敏)",
                    "户口所在地(脱敏)","现居住地址(脱敏)","紧急电话(脱敏)","所属公司","1级部门",
                    "2级部门","3级部门","门店","岗位","人员类别","预计入职日期原值","分类",
                    "预检错误码","结果码","失败原因"};
            for(int i=0;i<columns.length;i++) header.createCell(i, CellType.STRING).setCellValue(columns[i]);
            int index=1;
            for(HrOnboardingImportPreviewVo.RowVo item:preview.getRows())
            {
                if (item.getErrorCodes()==null && !"FAILED".equals(item.getRowStatus())) continue;
                Row row=sheet.createRow(index++); row.createCell(0).setCellValue(item.getSourceRowNumber());
                row.createCell(1).setCellValue(nullToEmpty(item.getEmployeeName()));
                row.createCell(2).setCellValue(nullToEmpty(item.getPhoneNumberMasked()));
                row.createCell(3).setCellValue(nullToEmpty(item.getIdNumberMasked()));
                row.createCell(4).setCellValue(nullToEmpty(item.getBankAccountMasked()));
                row.createCell(5).setCellValue(nullToEmpty(item.getRegisteredResidenceMasked()));
                row.createCell(6).setCellValue(nullToEmpty(item.getCurrentAddressMasked()));
                row.createCell(7).setCellValue(nullToEmpty(item.getEmergencyContactPhoneMasked()));
                row.createCell(8).setCellValue(nullToEmpty(item.getCompanyName()));
                row.createCell(9).setCellValue(nullToEmpty(item.getDeptLevel1Name()));
                row.createCell(10).setCellValue(nullToEmpty(item.getDeptLevel2Name()));
                row.createCell(11).setCellValue(nullToEmpty(item.getDeptLevel3Name()));
                row.createCell(12).setCellValue(nullToEmpty(item.getStoreName()));
                row.createCell(13).setCellValue(nullToEmpty(item.getPositionName()));
                row.createCell(14).setCellValue(nullToEmpty(item.getEmployeeCategory()));
                row.createCell(15).setCellValue(nullToEmpty(item.getExpectedEntryDateText()));
                row.createCell(16).setCellValue(nullToEmpty(item.getCategory()));
                row.createCell(17).setCellValue(nullToEmpty(item.getErrorCodes()));
                row.createCell(18).setCellValue(nullToEmpty(item.getResultCode()));
                row.createCell(19).setCellValue(nullToEmpty(item.getResultMessage()));
            }
            prepare(response,"入职导入错误行.xlsx"); workbook.write(response.getOutputStream());
        }
        catch(IOException failure){throw new ServiceException("IMPORT_ERROR_EXPORT_FAILED");}
    }

    private HrOnboardingImportBatch owned(Long batchId)
    {
        HrOnboardingImportBatch batch=mapper.selectBatchById(batchId);
        if(batch==null || (!currentAdmin.getAsBoolean() && !Long.valueOf(currentUserId.getAsLong()).equals(batch.getCreatorUserId())))
            throw new ServiceException("无权访问该导入批次或记录不存在", HttpStatus.FORBIDDEN);
        return batch;
    }

    private List<HrOnboardingImportRow> hydratedRows(Long batchId)
    {
        List<HrOnboardingImportRow> rows=mapper.selectRowsByBatchId(batchId);
        if(rows==null)return Collections.emptyList();
        for(HrOnboardingImportRow row:rows) if(row.getPayload()==null && row.getPayloadJson()!=null)
            payloadCodec.hydrate(row);
        return rows;
    }

    private HrOnboardingImportPreviewVo toVo(HrOnboardingImportBatch batch,List<HrOnboardingImportRow> rows)
    {
        HrOnboardingImportPreviewVo vo=new HrOnboardingImportPreviewVo();
        vo.setBatchId(batch.getBatchId());vo.setBatchNo(batch.getBatchNo());vo.setFileName(batch.getFileName());
        vo.setStatus(batch.getStatus());vo.setVersion(batch.getVersion());vo.setTotalRows(batch.getTotalRows());
        vo.setImportableRows(batch.getImportableRows());vo.setWarningRows(batch.getWarningRows());
        vo.setInvalidRows(batch.getInvalidRows());vo.setDuplicateRows(batch.getDuplicateRows());
        vo.setBindableRows(batch.getBindableRows());vo.setSuccessRows(batch.getSuccessRows());vo.setFailureRows(batch.getFailureRows());
        vo.setRows(rows.stream().map(this::safeRow).collect(Collectors.toList()));
        List<Map<String,Object>> errors=new ArrayList<>();
        for(HrOnboardingImportRow row:rows)if("FAILED".equals(row.getRowStatus()))
            addFailure(row.getResultCode()==null?"ROW_PROCESSING_FAILED":row.getResultCode(),
                    row.getResultMessage()==null?safeMessage(row.getResultCode()):row.getResultMessage(),
                    row.getRowId(),row,errors);
        vo.setErrors(errors);
        return vo;
    }

    private HrOnboardingImportPreviewVo.RowVo safeRow(HrOnboardingImportRow row)
    {
        HrOnboardingImportPreviewVo.RowVo vo=new HrOnboardingImportPreviewVo.RowVo(); HrOnboarding p=row.getPayload();
        vo.setRowId(row.getRowId());vo.setSourceRowNumber(row.getSourceRowNumber());vo.setCategory(row.getCategory());
        vo.setRowStatus(row.getRowStatus());vo.setWarningCodes(row.getWarningCodes());vo.setErrorCodes(row.getErrorCodes());
        vo.setCandidateSummary(row.getCandidateSummary());vo.setResultCode(row.getResultCode());
        vo.setCandidateUserId(row.getCandidateUserId());vo.setCandidateOnboardingId(row.getCandidateOnboardingId());
        vo.setResultMessage(row.getResultMessage());vo.setResultOnboardingId(row.getResultOnboardingId());
        if(p!=null){vo.setEmployeeName(p.getEmployeeName());vo.setPhoneNumberMasked(masker.maskPhone(p.getPhoneNumber()));
            vo.setIdNumberMasked(masker.maskIdNumber(p.getIdNumber()));vo.setBankAccountMasked(masker.maskBankAccount(p.getBankAccount()));
            vo.setRegisteredResidenceMasked(masker.maskAddress(p.getRegisteredResidence()));vo.setCurrentAddressMasked(masker.maskAddress(p.getCurrentAddress()));
            vo.setEmergencyContactPhoneMasked(masker.maskPhone(p.getEmergencyContactPhone()));
            vo.setCompanyName(p.getCompanyName());vo.setDeptLevel1Name(p.getDeptLevel1Name());
            vo.setDeptLevel2Name(p.getDeptLevel2Name());vo.setDeptLevel3Name(p.getDeptLevel3Name());
            vo.setStoreName(p.getStoreName());vo.setPositionName(p.getPositionName());
            vo.setEmployeeCategory(p.getEmployeeCategory());
            String rawDate=row.getRawSourceValues().get("expectedEntryDate");
            if(rawDate==null&&p.getExpectedEntryDate()!=null)rawDate=new java.text.SimpleDateFormat("yyyy-MM-dd").format(p.getExpectedEntryDate());
            vo.setExpectedEntryDateText(rawDate);}
        return vo;
    }

    private int retentionDays(){try{return Math.max(1,Integer.parseInt(configService.selectConfigByKey(RETENTION_KEY)));}catch(Exception ignored){return 30;}}
    private int leaseSeconds(){try{return Math.max(30,Math.min(3600,Integer.parseInt(configService.selectConfigByKey(LEASE_KEY))));}catch(Exception ignored){return 120;}}
    private int count(List<HrOnboardingImportRow> rows,String category){return (int)rows.stream().filter(r->category.equals(r.getCategory())).count();}
    private String safeFileName(String name){String value=name==null?"import.xlsx":name.replace('\\','/');value=value.substring(value.lastIndexOf('/')+1);return value.length()>255?value.substring(value.length()-255):value;}
    private String sha256(byte[] value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b));return out.toString();}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private void prepare(HttpServletResponse response,String fileName){response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setCharacterEncoding("UTF-8");response.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(fileName,StandardCharsets.UTF_8));}
    private String nullToEmpty(String value){return value==null?"":value;}
}
