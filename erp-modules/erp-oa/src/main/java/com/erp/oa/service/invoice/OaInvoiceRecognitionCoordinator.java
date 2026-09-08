package com.erp.oa.service.invoice;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.erp.oa.config.OaReimbursementProperties;
import com.erp.oa.domain.OaReimbursementInvoice;

@Service
public class OaInvoiceRecognitionCoordinator
{
    private final OaReimbursementProperties properties;
    private final OaBaiduInvoiceRecognitionEngine cloud;
    private final OaLocalInvoiceRecognitionEngine local;

    public OaInvoiceRecognitionCoordinator(
            OaReimbursementProperties properties,
            OaBaiduInvoiceRecognitionEngine cloud,
            OaLocalInvoiceRecognitionEngine local)
    {
        this.properties = properties;
        this.cloud = cloud;
        this.local = local;
    }

    public OaInvoiceRecognitionResult recognize(Path path,
            String extension, String requestedEngine)
    {
        String requested = normalizeEngine(requestedEngine);
        OaInvoiceRecognitionResult result;
        if ("cloud".equals(requested))
        {
            result = cloud.recognize(path, extension);
        }
        else if ("local".equals(requested))
        {
            result = local.recognize(path, extension);
        }
        else
        {
            result = recognizeAutomatically(path, extension);
        }
        result.setRequestedEngine(requested);
        return result;
    }

    public OaInvoiceRecognitionResult pending()
    {
        return OaInvoiceRecognitionResult.failure("auto", null, null,
                "pending", "发票已上传，等待识别");
    }

    public boolean autoRecognize()
    {
        return properties.getOcr().isAutoRecognize();
    }

    public Map<String, Object> availability()
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("autoRecognize", autoRecognize());
        value.put("defaultEngine", "auto");
        value.put("cloudConfigured", cloud.available());
        value.put("cloudProvider", cloud.provider());
        value.put("cloudMessage", cloud.available()
                ? "百度云OCR已配置" : cloud.unavailableReason());
        value.put("localAvailable", local.available());
        value.put("localImageOcrAvailable",
                local.imageOcrAvailable());
        value.put("localProvider", local.provider());
        value.put("localMessage", local.unavailableReason());
        return value;
    }

    public void apply(OaReimbursementInvoice invoice,
            OaInvoiceRecognitionResult result)
    {
        invoice.setRecognitionStatus(result.getStatus());
        invoice.setRequestedEngine(result.getRequestedEngine());
        invoice.setRecognitionEngine(result.getEngine());
        invoice.setRecognitionProvider(result.getProvider());
        invoice.setRecognitionMessage(result.getMessage());
        invoice.setInvoiceType(result.getInvoiceType());
        invoice.setInvoiceCode(result.getInvoiceCode());
        invoice.setInvoiceNumber(result.getInvoiceNumber());
        invoice.setInvoiceDate(result.getInvoiceDate());
        invoice.setSellerName(result.getSellerName());
        invoice.setSellerTaxNo(result.getSellerTaxNo());
        invoice.setPurchaserName(result.getPurchaserName());
        invoice.setPurchaserTaxNo(result.getPurchaserTaxNo());
        invoice.setAmountWithoutTax(result.getAmountWithoutTax());
        invoice.setTaxAmount(result.getTaxAmount());
        invoice.setInvoiceTotalAmount(result.getTotalAmount());
        invoice.setCheckCode(result.getCheckCode());
        invoice.setServiceType(result.getServiceType());
        invoice.setCommoditySummary(result.getCommoditySummary());
        invoice.setRecognitionConfidence(result.getConfidence());
        invoice.setRecognitionRawText(result.getRawText());
        invoice.setRecognitionRawPayload(result.getRawPayload());
    }

    private OaInvoiceRecognitionResult recognizeAutomatically(
            Path path, String extension)
    {
        OaInvoiceRecognitionResult cloudResult = null;
        if (cloud.available())
        {
            cloudResult = cloud.recognize(path, extension);
            if (cloudResult.hasRecognizedFields())
            {
                return cloudResult;
            }
        }
        OaInvoiceRecognitionResult localResult =
                local.recognize(path, extension);
        if (cloudResult != null)
        {
            String cloudMessage = cloudResult.getMessage();
            String localMessage = localResult.getMessage();
            localResult.setMessage("云端识别未成功，已自动切换本地"
                    + (localMessage == null ? ""
                            : "；" + localMessage)
                    + (cloudMessage == null ? ""
                            : "（云端：" + cloudMessage + "）"));
        }
        else if (!cloud.available()
                && localResult.getMessage() != null)
        {
            localResult.setMessage("云端未配置，已使用本地识别；"
                    + localResult.getMessage());
        }
        return localResult;
    }

    private String normalizeEngine(String value)
    {
        String normalized = value == null ? "auto"
                : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized)
        {
            case "cloud", "local" -> normalized;
            default -> "auto";
        };
    }
}
