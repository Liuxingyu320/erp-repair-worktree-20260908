package com.erp.oa.service.invoice;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.erp.oa.config.OaReimbursementProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OaBaiduInvoiceRecognitionEngine
        implements OaInvoiceRecognitionEngine
{
    private final OaReimbursementProperties.Cloud properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public OaBaiduInvoiceRecognitionEngine(
            OaReimbursementProperties reimbursementProperties,
            ObjectMapper objectMapper)
    {
        this.properties = reimbursementProperties.getOcr().getCloud();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String engine()
    {
        return "cloud";
    }

    @Override
    public String provider()
    {
        return "baidu";
    }

    @Override
    public boolean available()
    {
        return properties.isEnabled()
                && "baidu".equalsIgnoreCase(properties.getProvider())
                && notBlank(properties.getApiKey())
                && notBlank(properties.getSecretKey());
    }

    @Override
    public String unavailableReason()
    {
        if (!properties.isEnabled())
        {
            return "云端识别已关闭";
        }
        if (!"baidu".equalsIgnoreCase(properties.getProvider()))
        {
            return "暂不支持配置的云OCR提供方";
        }
        return available() ? "" : "百度云OCR密钥未配置";
    }

    @Override
    public OaInvoiceRecognitionResult recognize(Path path, String extension)
    {
        if (!available())
        {
            return failure("unconfigured", unavailableReason());
        }
        if (path == null || !Files.isRegularFile(path))
        {
            return failure("failed", "发票文件不存在");
        }
        try
        {
            return call(path, extension, false);
        }
        catch (Exception exception)
        {
            return failure("failed", "云端识别失败："
                    + safeMessage(exception));
        }
    }

    private OaInvoiceRecognitionResult call(Path path, String extension,
            boolean tokenRetried) throws Exception
    {
        String token = token();
        String parameter = fileParameter(extension);
        String base64 = Base64.getEncoder().encodeToString(
                Files.readAllBytes(path));
        String body = form(parameter, base64)
                + ("pdf_file".equals(parameter)
                        ? "&pdf_file_num=1" : "")
                + ("ofd_file".equals(parameter)
                        ? "&ofd_file_num=1" : "")
                + "&type=normal&seal_tag=false";
        long encodedBytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (encodedBytes > properties.getMaxEncodedBytes())
        {
            return failure("failed",
                    "文件编码后超过云端8MB限制，已保留附件，可改用本地识别");
        }
        URI endpoint = URI.create(properties.getRecognizeUrl()
                + (properties.getRecognizeUrl().contains("?") ? "&" : "?")
                + "access_token=" + encode(token));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout())
                .header("Content-Type",
                        "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body,
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request);
        JsonNode root = parseResponse(response, "云端发票识别");
        int errorCode = root.path("error_code").asInt(0);
        if ((errorCode == 110 || errorCode == 111) && !tokenRetried)
        {
            invalidateToken();
            return call(path, extension, true);
        }
        if (errorCode != 0)
        {
            return failure("failed", cloudError(root));
        }
        return toResult(root);
    }

    private OaInvoiceRecognitionResult toResult(JsonNode root)
    {
        JsonNode words = root.path("words_result");
        OaInvoiceRecognitionResult result = new OaInvoiceRecognitionResult();
        result.setRequestedEngine("cloud");
        result.setEngine(engine());
        result.setProvider(provider());
        result.setRawPayload(limit(root.toString(), 500_000));
        result.setInvoiceType(text(words, "InvoiceType"));
        result.setInvoiceCode(firstText(words,
                "InvoiceCode", "InvoiceCodeConfirm"));
        result.setInvoiceNumber(firstText(words,
                "InvoiceNum", "InvoiceNumDigit", "InvoiceNumConfirm"));
        result.setInvoiceDate(parseDate(text(words, "InvoiceDate")));
        result.setSellerName(text(words, "SellerName"));
        result.setSellerTaxNo(alphaNumeric(
                text(words, "SellerRegisterNum")));
        result.setPurchaserName(text(words, "PurchaserName"));
        result.setPurchaserTaxNo(alphaNumeric(
                text(words, "PurchaserRegisterNum")));
        result.setAmountWithoutTax(money(text(words, "TotalAmount")));
        result.setTaxAmount(money(text(words, "TotalTax")));
        result.setTotalAmount(money(firstText(words,
                "AmountInFiguers", "AmountInFigures")));
        if (result.getTotalAmount() == null
                && result.getAmountWithoutTax() != null
                && result.getTaxAmount() != null)
        {
            result.setTotalAmount(result.getAmountWithoutTax()
                    .add(result.getTaxAmount())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        result.setCheckCode(text(words, "CheckCode"));
        result.setServiceType(text(words, "ServiceType"));
        result.setCommoditySummary(arrayWords(words.path(
                "CommodityName")));
        int fields = result.recognizedFieldCount();
        result.setStatus(fields >= 4 ? "succeeded"
                : fields > 0 ? "partial" : "failed");
        result.setMessage(fields >= 4 ? "百度云OCR识别完成"
                : fields > 0 ? "百度云OCR已返回部分字段，请人工核对"
                        : "云端未能提取发票关键字段");
        return result;
    }

    private synchronized String token() throws Exception
    {
        if (accessToken != null
                && Instant.now().isBefore(accessTokenExpiresAt))
        {
            return accessToken;
        }
        String body = form("grant_type", "client_credentials")
                + "&" + form("client_id", properties.getApiKey())
                + "&" + form("client_secret", properties.getSecretKey());
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(properties.getTokenUrl()))
                .timeout(timeout())
                .header("Content-Type",
                        "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body,
                        StandardCharsets.UTF_8))
                .build();
        JsonNode root = parseResponse(send(request), "云端OCR鉴权");
        String token = root.path("access_token").asText("");
        if (token.isBlank())
        {
            throw new IOException(cloudError(root));
        }
        long expiresIn = Math.max(300L,
                root.path("expires_in").asLong(2_592_000L));
        accessToken = token;
        accessTokenExpiresAt = Instant.now().plusSeconds(
                Math.max(60L, expiresIn - 300L));
        return accessToken;
    }

    private void invalidateToken()
    {
        accessToken = null;
        accessTokenExpiresAt = Instant.EPOCH;
    }

    private HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException
    {
        try
        {
            return httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8));
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private JsonNode parseResponse(HttpResponse<String> response,
            String operation) throws IOException
    {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException(operation + "返回HTTP "
                    + response.statusCode());
        }
        try
        {
            return objectMapper.readTree(response.body());
        }
        catch (RuntimeException exception)
        {
            throw new IOException(operation + "返回格式无效", exception);
        }
    }

    private String cloudError(JsonNode root)
    {
        String message = firstText(root, "error_msg", "error_description");
        int code = root.path("error_code").asInt(0);
        return (message == null ? "云端服务返回错误" : message)
                + (code == 0 ? "" : "（" + code + "）");
    }

    private String fileParameter(String extension)
    {
        String value = extension == null ? ""
                : extension.toLowerCase(Locale.ROOT).replace(".", "");
        return switch (value)
        {
            case "pdf" -> "pdf_file";
            case "ofd" -> "ofd_file";
            case "png", "jpg", "jpeg" -> "image";
            default -> throw new IllegalArgumentException(
                    "云端识别不支持该发票格式");
        };
    }

    private String text(JsonNode parent, String field)
    {
        if (parent == null || parent.isMissingNode())
        {
            return null;
        }
        JsonNode value = parent.path(field);
        String result;
        if (value.isTextual() || value.isNumber())
        {
            result = value.asText();
        }
        else if (value.isObject())
        {
            result = value.path("words").asText(
                    value.path("word").asText(""));
        }
        else
        {
            result = "";
        }
        result = result == null ? "" : result.trim();
        return result.isEmpty() ? null : limit(result, 500);
    }

    private String firstText(JsonNode parent, String... fields)
    {
        for (String field : fields)
        {
            String value = text(parent, field);
            if (value != null)
            {
                return value;
            }
        }
        return null;
    }

    private String arrayWords(JsonNode values)
    {
        if (values == null || !values.isArray())
        {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (JsonNode value : values)
        {
            String word = value.path("word").asText("").trim();
            if (!word.isEmpty() && !words.contains(word))
            {
                words.add(word);
            }
            if (words.size() >= 10)
            {
                break;
            }
        }
        String result = String.join("；", words);
        return result.isEmpty() ? null : limit(result, 500);
    }

    private BigDecimal money(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            BigDecimal result = new BigDecimal(value.replace(",", "")
                    .replace("¥", "").replace("￥", "").trim());
            return result.signum() < 0 ? null
                    : result.setScale(2, RoundingMode.HALF_UP);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private Date parseDate(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "")
                .replace('年', '-').replace('月', '-')
                .replace("日", "").replace('.', '-').replace('/', '-');
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT),
                DateTimeFormatter.ISO_LOCAL_DATE))
        {
            try
            {
                return Date.valueOf(LocalDate.parse(normalized,
                        formatter));
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next format.
            }
        }
        return null;
    }

    private String alphaNumeric(String value)
    {
        if (value == null)
        {
            return null;
        }
        String result = value.replaceAll("[^0-9A-Za-z]", "")
                .toUpperCase(Locale.ROOT);
        return result.isEmpty() ? null : result;
    }

    private Duration timeout()
    {
        return Duration.ofSeconds(Math.max(5,
                Math.min(120, properties.getTimeoutSeconds())));
    }

    private String form(String name, String value)
    {
        return encode(name) + "=" + encode(value == null ? "" : value);
    }

    private String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private OaInvoiceRecognitionResult failure(String status,
            String message)
    {
        return OaInvoiceRecognitionResult.failure("cloud", engine(),
                provider(), status, limit(message, 500));
    }

    private String safeMessage(Exception exception)
    {
        String value = exception.getMessage();
        return value == null || value.isBlank()
                ? exception.getClass().getSimpleName()
                : limit(value, 240);
    }

    private boolean notBlank(String value)
    {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximum)
    {
        if (value == null || value.length() <= maximum)
        {
            return value;
        }
        return value.substring(0, maximum);
    }
}
