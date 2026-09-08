package com.erp.oa.attendance.support;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties.Address;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer.Coordinate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Controlled server-side adapter for Amap Web Service reverse geocoding. */
public final class AmapAttendanceAddressResolver
        implements AttendanceAddressResolver
{
    private static final String ENDPOINT =
            "https://restapi.amap.com/v3/geocode/regeo";
    private static final String ENDPOINT_HOST = "restapi.amap.com";
    private static final String ENDPOINT_PATH = "/v3/geocode/regeo";
    private static final int MAX_CANDIDATES = 20;
    private static final Pattern KEY = Pattern.compile(
            "^[A-Za-z0-9_-]{8,128}$");
    private static final Pattern COORDINATE_ONLY = Pattern.compile(
            "^[+-]?\\d{1,3}(?:\\.\\d+)?\\s*[,\uff0c]\\s*"
                    + "[+-]?\\d{1,3}(?:\\.\\d+)?$");

    private final Address properties;
    private final AttendanceCoordinateTransformer coordinates;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    public AmapAttendanceAddressResolver(Address properties,
            AttendanceCoordinateTransformer coordinates,
            ObjectMapper objectMapper)
    {
        this(properties, coordinates, objectMapper,
                new FixedHttpsTransport(properties));
    }

    AmapAttendanceAddressResolver(Address properties,
            AttendanceCoordinateTransformer coordinates,
            ObjectMapper objectMapper, Transport transport)
    {
        this.properties = requireConfiguration(properties);
        this.coordinates = coordinates;
        this.objectMapper = objectMapper;
        this.transport = transport;
        if (coordinates == null || objectMapper == null || transport == null)
            throw new IllegalStateException(
                    "AMAP_ATTENDANCE_RESOLVER_DEPENDENCY_MISSING");
    }

    @Override
    public ResolvedAddress resolve(BigDecimal latitude, BigDecimal longitude,
            String coordinateSystem)
    {
        Coordinate amap = coordinates.transform(latitude, longitude,
                coordinateSystem, "GCJ02");
        HttpResult result;
        try
        {
            result = transport.get(requestUri(amap));
        }
        catch (IOException | RuntimeException exception)
        {
            throw resolutionFailed();
        }
        if (result == null || result.statusCode() != 200
                || !jsonContentType(result.contentType()))
            throw resolutionFailed();
        return parse(result.body());
    }

    private URI requestUri(Coordinate coordinate)
    {
        String location = coordinate.longitude().setScale(6,
                java.math.RoundingMode.HALF_UP).toPlainString()
                + "," + coordinate.latitude().setScale(6,
                        java.math.RoundingMode.HALF_UP).toPlainString();
        return URI.create(ENDPOINT + "?key="
                + properties.getAmapWebServiceKey()
                + "&location=" + location
                + "&output=JSON&extensions=all&radius="
                + properties.getRadiusMeters() + "&homeorcorp=2");
    }

    private ResolvedAddress parse(String body)
    {
        JsonNode root;
        try
        {
            root = objectMapper.readTree(body == null ? "" : body);
        }
        catch (IOException | RuntimeException exception)
        {
            throw resolutionFailed();
        }
        if (root == null || !root.isObject()
                || !"1".equals(strictText(root.get("status")))
                || !"OK".equalsIgnoreCase(strictText(root.get("info"))))
            throw resolutionFailed();
        JsonNode regeocode = root.get("regeocode");
        if (regeocode == null || !regeocode.isObject())
            throw resolutionFailed();
        String base = addressText(regeocode.get("formatted_address"),
                4, 255);
        String detail = preciseDetail(regeocode, base);
        String formatted = detail == null ? base
                : base + "\uff08" + detail + "\uff09";
        if (formatted.length() > 255)
            formatted = base;
        return new ResolvedAddress(formatted, "AMAP");
    }

    private String preciseDetail(JsonNode regeocode, String base)
    {
        List<String> candidates = new ArrayList<>();
        JsonNode component = regeocode.path("addressComponent");
        addCandidate(candidates, component.path("building").get("name"));
        addCandidate(candidates, nearest(regeocode.get("aois")));
        addCandidate(candidates, nearest(regeocode.get("pois")));
        String baseKey = comparisonKey(base);
        for (String candidate : candidates)
        {
            String candidateKey = comparisonKey(candidate);
            if (candidateKey.isBlank()) continue;
            if (baseKey.contains(candidateKey)
                    || candidateKey.contains(baseKey))
                return null;
            return candidate;
        }
        return null;
    }

    private JsonNode nearest(JsonNode values)
    {
        if (values == null || !values.isArray()) return null;
        List<JsonNode> candidates = new ArrayList<>();
        int count = 0;
        for (JsonNode value : values)
        {
            if (count++ >= MAX_CANDIDATES) break;
            if (value != null && value.isObject()
                    && value.get("name") != null)
                candidates.add(value);
        }
        return candidates.stream().min(Comparator.comparing(
                this::distance)).map(value -> value.get("name"))
                .orElse(null);
    }

    private BigDecimal distance(JsonNode value)
    {
        String text = strictText(value == null ? null
                : value.get("distance"));
        try
        {
            BigDecimal distance = new BigDecimal(text);
            return distance.signum() < 0 ? BigDecimal.valueOf(Long.MAX_VALUE)
                    : distance;
        }
        catch (RuntimeException exception)
        {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
    }

    private void addCandidate(List<String> values, JsonNode node)
    {
        String candidate = optionalDetail(node);
        if (candidate == null) return;
        String key = comparisonKey(candidate);
        boolean duplicate = values.stream()
                .anyMatch(value -> comparisonKey(value).equals(key));
        if (!duplicate) values.add(candidate);
    }

    private String addressText(JsonNode node, int minimum, int maximum)
    {
        String value = strictText(node);
        if (value == null || hasControlCharacter(value))
            throw addressInvalid();
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() < minimum || normalized.length() > maximum
                || COORDINATE_ONLY.matcher(normalized).matches())
            throw addressInvalid();
        return normalized;
    }

    private String optionalDetail(JsonNode node)
    {
        String value = strictText(node);
        if (value == null || hasControlCharacter(value)) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() < 2 || normalized.length() > 80
                || COORDINATE_ONLY.matcher(normalized).matches())
            return null;
        return normalized;
    }

    private String strictText(JsonNode node)
    {
        if (node == null || !node.isTextual()) return null;
        return node.textValue();
    }

    private String comparisonKey(String value)
    {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value,
                Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private boolean hasControlCharacter(String value)
    {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private boolean jsonContentType(String contentType)
    {
        if (contentType == null || contentType.isBlank()) return true;
        return contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private static Address requireConfiguration(Address properties)
    {
        if (properties == null
                || !KEY.matcher(nullToEmpty(
                        properties.getAmapWebServiceKey())).matches())
            throw new IllegalStateException(
                    "AMAP_ATTENDANCE_KEY_REQUIRED");
        if (properties.getConnectTimeoutMillis() < 100
                || properties.getConnectTimeoutMillis() > 5_000
                || properties.getReadTimeoutMillis() < 200
                || properties.getReadTimeoutMillis() > 10_000
                || properties.getRadiusMeters() < 0
                || properties.getRadiusMeters() > 3_000
                || properties.getMaxResponseBytes() < 1_024
                || properties.getMaxResponseBytes() > 1_048_576)
            throw new IllegalStateException(
                    "AMAP_ATTENDANCE_CONFIGURATION_INVALID");
        return properties;
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private ServiceException resolutionFailed()
    {
        return new ServiceException(
                "ATTENDANCE_ADDRESS_RESOLUTION_FAILED");
    }

    private ServiceException addressInvalid()
    {
        return new ServiceException("ATTENDANCE_ADDRESS_INVALID");
    }

    @FunctionalInterface
    interface Transport
    {
        HttpResult get(URI uri) throws IOException;
    }

    record HttpResult(int statusCode, String contentType, String body) { }

    private static final class FixedHttpsTransport implements Transport
    {
        private final int connectTimeoutMillis;
        private final int readTimeoutMillis;
        private final int maxResponseBytes;

        private FixedHttpsTransport(Address properties)
        {
            Address valid = requireConfiguration(properties);
            connectTimeoutMillis = valid.getConnectTimeoutMillis();
            readTimeoutMillis = valid.getReadTimeoutMillis();
            maxResponseBytes = valid.getMaxResponseBytes();
        }

        @Override
        public HttpResult get(URI uri) throws IOException
        {
            requireFixedEndpoint(uri);
            HttpsURLConnection connection = (HttpsURLConnection)
                    uri.toURL().openConnection();
            try
            {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(connectTimeoutMillis);
                connection.setReadTimeout(readTimeoutMillis);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Accept-Encoding", "identity");
                int status = connection.getResponseCode();
                String contentType = connection.getContentType();
                if (status != 200)
                    return new HttpResult(status, contentType, "");
                long declared = connection.getContentLengthLong();
                if (declared > maxResponseBytes)
                    throw new IOException("AMAP_RESPONSE_TOO_LARGE");
                try (InputStream input = connection.getInputStream())
                {
                    byte[] bytes = input.readNBytes(maxResponseBytes + 1);
                    if (bytes.length > maxResponseBytes)
                        throw new IOException("AMAP_RESPONSE_TOO_LARGE");
                    return new HttpResult(status, contentType,
                            new String(bytes, StandardCharsets.UTF_8));
                }
            }
            finally
            {
                connection.disconnect();
            }
        }

        private void requireFixedEndpoint(URI uri) throws IOException
        {
            if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                    || !ENDPOINT_HOST.equalsIgnoreCase(uri.getHost())
                    || !ENDPOINT_PATH.equals(uri.getPath())
                    || uri.getPort() != -1 || uri.getUserInfo() != null
                    || uri.getFragment() != null)
                throw new IOException("AMAP_ENDPOINT_REJECTED");
        }
    }
}
