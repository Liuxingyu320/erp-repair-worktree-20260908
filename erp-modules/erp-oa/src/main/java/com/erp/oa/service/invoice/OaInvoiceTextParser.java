package com.erp.oa.service.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OaInvoiceTextParser
{
    private static final Pattern INVOICE_CODE = Pattern.compile(
            "(?:发\\s*票\\s*代\\s*码|票\\s*据\\s*代\\s*码)"
                    + "\\s*[:：]?\\s*([0-9]{10,20})");
    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?:数\\s*电\\s*票\\s*号\\s*码|发\\s*票\\s*号\\s*码"
                    + "|票\\s*据\\s*号\\s*码)"
                    + "\\s*[:：]?\\s*([0-9]{8,30})");
    private static final Pattern DATE = Pattern.compile(
            "开\\s*票\\s*日\\s*期\\s*[:：]?\\s*"
                    + "(20\\d{2}\\s*[年./-]\\s*\\d{1,2}"
                    + "\\s*[月./-]\\s*\\d{1,2}\\s*日?)");
    private static final Pattern TOTAL = Pattern.compile(
            "(?:价\\s*税\\s*合\\s*计[\\s\\S]{0,24}?"
                    + "(?:小\\s*写)?|(?:小\\s*写))"
                    + "\\s*[（(]?\\s*[¥￥]?\\s*"
                    + "([0-9][0-9,]*\\.\\d{1,2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_AND_TAX = Pattern.compile(
            "(?:合\\s*计|金\\s*额\\s*合\\s*计)"
                    + "\\s*[¥￥]?\\s*([0-9][0-9,]*\\.\\d{1,2})"
                    + "\\s*[¥￥]?\\s*([0-9][0-9,]*\\.\\d{1,2})");
    private static final Pattern TAX = Pattern.compile(
            "(?:合\\s*计\\s*税\\s*额|税\\s*额\\s*合\\s*计)"
                    + "\\s*[:：]?\\s*[¥￥]?\\s*"
                    + "([0-9][0-9,]*\\.\\d{1,2})");
    private static final Pattern CHECK_CODE = Pattern.compile(
            "校\\s*验\\s*码\\s*[:：]?\\s*([0-9*]{6,24})");
    private static final Pattern SELLER_NAME = partyPattern("销\\s*售\\s*方",
            "名\\s*称", 200);
    private static final Pattern SELLER_TAX = partyPattern("销\\s*售\\s*方",
            "(?:纳\\s*税\\s*人\\s*识\\s*别\\s*号"
                    + "|统\\s*一\\s*社\\s*会\\s*信\\s*用\\s*代\\s*码)",
            40);
    private static final Pattern PURCHASER_NAME = partyPattern(
            "购\\s*买\\s*方", "名\\s*称", 200);
    private static final Pattern PURCHASER_TAX = partyPattern(
            "购\\s*买\\s*方",
            "(?:纳\\s*税\\s*人\\s*识\\s*别\\s*号"
                    + "|统\\s*一\\s*社\\s*会\\s*信\\s*用\\s*代\\s*码)",
            40);
    private static final List<String> INVOICE_TYPES = List.of(
            "增值税电子专用发票", "增值税电子普通发票",
            "增值税专用发票", "增值税普通发票",
            "电子发票（专用发票）", "电子发票（普通发票）",
            "电子发票(专用发票)", "电子发票(普通发票)",
            "通行费电子普通发票", "区块链电子发票", "机动车销售统一发票",
            "二手车销售统一发票", "普通发票", "专用发票");

    public OaInvoiceRecognitionResult parse(String rawText, String provider)
    {
        String text = normalize(rawText);
        OaInvoiceRecognitionResult result = new OaInvoiceRecognitionResult();
        result.setEngine("local");
        result.setProvider(provider);
        result.setRawText(limit(text, 100_000));
        result.setInvoiceType(invoiceType(text));
        result.setInvoiceCode(first(INVOICE_CODE, text, 1, 32));
        result.setInvoiceNumber(first(INVOICE_NUMBER, text, 1, 40));
        result.setInvoiceDate(parseDate(first(DATE, text, 1, 32)));
        result.setSellerName(cleanParty(first(SELLER_NAME, text, 1, 200)));
        result.setSellerTaxNo(alphaNumeric(first(SELLER_TAX, text, 1, 40)));
        result.setPurchaserName(cleanParty(
                first(PURCHASER_NAME, text, 1, 200)));
        result.setPurchaserTaxNo(alphaNumeric(
                first(PURCHASER_TAX, text, 1, 40)));
        result.setCheckCode(first(CHECK_CODE, text, 1, 40));
        result.setTotalAmount(money(first(TOTAL, text, 1, 40)));

        Matcher amountTax = AMOUNT_AND_TAX.matcher(text);
        if (amountTax.find())
        {
            result.setAmountWithoutTax(money(amountTax.group(1)));
            result.setTaxAmount(money(amountTax.group(2)));
        }
        else
        {
            result.setTaxAmount(money(first(TAX, text, 1, 40)));
        }
        if (result.getTotalAmount() == null
                && result.getAmountWithoutTax() != null
                && result.getTaxAmount() != null)
        {
            result.setTotalAmount(result.getAmountWithoutTax()
                    .add(result.getTaxAmount())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        result.setCommoditySummary(commoditySummary(text));
        int fields = result.recognizedFieldCount();
        result.setStatus(fields >= 4 ? "succeeded"
                : fields > 0 ? "partial" : "failed");
        result.setConfidence(fields == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.min(0.92, 0.38 + fields * 0.09))
                        .setScale(4, RoundingMode.HALF_UP));
        result.setMessage(fields == 0
                ? "未能从发票中提取关键字段，可人工修正或改用云端识别"
                : fields >= 4 ? "本地识别完成"
                        : "本地识别完成，但部分字段需要人工核对");
        return result;
    }

    private static Pattern partyPattern(String party, String field,
            int maximum)
    {
        return Pattern.compile(party + "[\\s\\S]{0,180}?" + field
                + "\\s*[:：]?\\s*([^\\n\\r]{1," + maximum + "}?)"
                + "(?=\\s*(?:纳\\s*税|统\\s*一\\s*社\\s*会"
                + "|地\\s*址|电\\s*话|开\\s*户|银\\s*行|$))",
                Pattern.CASE_INSENSITIVE);
    }

    private String invoiceType(String text)
    {
        String compact = text.replaceAll("\\s+", "");
        for (String type : INVOICE_TYPES)
        {
            if (compact.contains(type.replaceAll("\\s+", "")))
            {
                return type;
            }
        }
        return null;
    }

    private String commoditySummary(String text)
    {
        Pattern pattern = Pattern.compile(
                "(?:货\\s*物\\s*或\\s*应\\s*税\\s*劳\\s*务"
                        + "|项\\s*目\\s*名\\s*称|商\\s*品\\s*名\\s*称)"
                        + "[^\\n\\r]*[\\n\\r]+\\s*([^\\n\\r]{2,200})");
        String value = first(pattern, text, 1, 500);
        if (value == null)
        {
            return null;
        }
        return value.replaceAll("\\s{2,}", " ").trim();
    }

    private String first(Pattern pattern, String text, int group,
            int maximum)
    {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
        {
            return null;
        }
        return limit(matcher.group(group).trim(), maximum);
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
        List<DateTimeFormatter> formats = new ArrayList<>();
        formats.add(DateTimeFormatter.ofPattern("yyyy-M-d",
                Locale.ROOT));
        formats.add(DateTimeFormatter.ISO_LOCAL_DATE);
        for (DateTimeFormatter format : formats)
        {
            try
            {
                return Date.valueOf(LocalDate.parse(normalized, format));
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next accepted invoice date format.
            }
        }
        return null;
    }

    private String cleanParty(String value)
    {
        if (value == null)
        {
            return null;
        }
        String result = value.replaceAll("^[：:|]+", "")
                .replaceAll("[|]+$", "").trim();
        return result.isEmpty() ? null : result;
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

    private String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replace("\u200B", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
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
