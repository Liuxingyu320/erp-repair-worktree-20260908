package com.erp.oa.service.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.erp.oa.config.OaReimbursementProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@DisplayName("百度云发票OCR适配器")
class OaBaiduInvoiceRecognitionEngineTest
{
    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> recognizeBody =
            new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress(
                "127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> respond(exchange, """
                {"access_token":"test-token","expires_in":2592000}
                """));
        server.createContext("/recognize", exchange -> {
            recognizeBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, """
                    {
                      "log_id":"1",
                      "words_result_num":12,
                      "words_result":{
                        "InvoiceType":"电子普通发票",
                        "InvoiceCode":"031002600111",
                        "InvoiceNum":"87654321",
                        "InvoiceDate":"2026年07月30日",
                        "SellerName":"上海云端测试有限公司",
                        "SellerRegisterNum":"91310000999999999X",
                        "PurchaserName":"测试购买方",
                        "PurchaserRegisterNum":"91310000111111111A",
                        "TotalAmount":"94.34",
                        "TotalTax":"5.66",
                        "AmountInFiguers":"100.00",
                        "ServiceType":"交通",
                        "CommodityName":[{"row":"1","word":"客运服务"}]
                      }
                    }
                    """);
        });
        server.start();
        baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("完成OAuth鉴权并映射百度结构化返回")
    void shouldAuthenticateAndMapCloudResult() throws Exception
    {
        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        properties.getOcr().getCloud().setApiKey("api-key");
        properties.getOcr().getCloud().setSecretKey("secret-key");
        properties.getOcr().getCloud().setTokenUrl(baseUrl + "/token");
        properties.getOcr().getCloud().setRecognizeUrl(
                baseUrl + "/recognize");
        OaBaiduInvoiceRecognitionEngine engine =
                new OaBaiduInvoiceRecognitionEngine(properties,
                        new ObjectMapper());
        Path image = tempDir.resolve("invoice.png");
        Files.write(image, new byte[] { 1, 2, 3, 4 });

        OaInvoiceRecognitionResult result =
                engine.recognize(image, "png");

        assertThat(engine.available()).isTrue();
        assertThat(result.getStatus()).isEqualTo("succeeded");
        assertThat(result.getEngine()).isEqualTo("cloud");
        assertThat(result.getProvider()).isEqualTo("baidu");
        assertThat(result.getInvoiceNumber()).isEqualTo("87654321");
        assertThat(result.getInvoiceDate())
                .isEqualTo(Date.valueOf("2026-07-30"));
        assertThat(result.getSellerName())
                .isEqualTo("上海云端测试有限公司");
        assertThat(result.getTotalAmount())
                .isEqualByComparingTo("100.00");
        assertThat(result.getCommoditySummary())
                .isEqualTo("客运服务");
        assertThat(recognizeBody.get())
                .contains("image=", "type=normal", "seal_tag=false");
    }

    @Test
    @DisplayName("缺少密钥时明确返回未配置而不发送文件")
    void shouldReportUnconfiguredWithoutCredentials()
            throws Exception
    {
        OaReimbursementProperties properties =
                new OaReimbursementProperties();
        OaBaiduInvoiceRecognitionEngine engine =
                new OaBaiduInvoiceRecognitionEngine(properties,
                        new ObjectMapper());
        Path image = tempDir.resolve("invoice.png");
        Files.write(image, new byte[] { 1 });

        OaInvoiceRecognitionResult result =
                engine.recognize(image, "png");

        assertThat(engine.available()).isFalse();
        assertThat(result.getStatus()).isEqualTo("unconfigured");
        assertThat(result.getMessage()).contains("密钥未配置");
        assertThat(recognizeBody.get()).isNull();
    }

    private void respond(HttpExchange exchange, String body)
            throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",
                "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
