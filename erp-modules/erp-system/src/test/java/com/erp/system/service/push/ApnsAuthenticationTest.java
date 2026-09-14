package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import com.erp.system.config.PushNotificationProperties;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;

class ApnsAuthenticationTest
{
    @TempDir Path temporary;

    @Test
    void certificateModeRequiresNoTeamWideSigningKeyAndUsesOnlyCertificate() throws Exception
    {
        PushNotificationProperties.Apns apns = new PushNotificationProperties.Apns();
        apns.setEnabled(true);
        apns.setAuthMode("certificate");
        apns.setCertificatePath("/run/secrets/erp-push.p12");
        apns.setCertificatePassword("test-only-password");
        assertThat(apns.isComplete()).isTrue();
        ApnsClientBuilder builder = mock(ApnsClientBuilder.class);

        ApnsPushDeliveryClient.configureAuthentication(builder, apns);

        verify(builder).setClientCredentials(new File("/run/secrets/erp-push.p12"), "test-only-password");
        verify(builder, never()).setSigningKey(any());
    }

    @Test
    void unencryptedCertificateUsesEmptyPasswordAndMissingSelectedCredentialDoesNotFallback() throws Exception
    {
        PushNotificationProperties.Apns apns = new PushNotificationProperties.Apns();
        apns.setEnabled(true);
        apns.setAuthMode("certificate");
        apns.setTeamId("TEAM");
        apns.setKeyId("KEY");
        apns.setPrivateKeyPath("/ignored.p8");
        assertThat(apns.isComplete()).isFalse();
        apns.setCertificatePath("/run/secrets/erp-push.p12");
        apns.setCertificatePassword(null);
        ApnsClientBuilder builder = mock(ApnsClientBuilder.class);
        ApnsPushDeliveryClient.configureAuthentication(builder, apns);
        verify(builder).setClientCredentials(new File("/run/secrets/erp-push.p12"), "");
        apns.setAuthMode("unexpected");
        assertThat(apns.isComplete()).isFalse();
    }

    @Test
    void tokenModeLoadsRealPkcs8KeyAndNeverChoosesCertificateImplicitly() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String body = Base64.getMimeEncoder(64, new byte[] { '\n' })
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        Path keyPath = temporary.resolve("test-only.p8");
        Files.writeString(keyPath, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
        PushNotificationProperties.Apns apns = new PushNotificationProperties.Apns();
        apns.setEnabled(true);
        apns.setTeamId("TEAM123456");
        apns.setKeyId("KEY1234567");
        apns.setPrivateKeyPath(keyPath.toString());
        apns.setCertificatePath("/ignored.p12");
        ApnsClientBuilder builder = mock(ApnsClientBuilder.class);

        ApnsPushDeliveryClient.configureAuthentication(builder, apns);

        assertThat(apns.isComplete()).isTrue();
        ArgumentCaptor<ApnsSigningKey> key = ArgumentCaptor.forClass(ApnsSigningKey.class);
        verify(builder).setSigningKey(key.capture());
        assertThat(key.getValue().getTeamId()).isEqualTo("TEAM123456");
        assertThat(key.getValue().getKeyId()).isEqualTo("KEY1234567");
        verify(builder, never()).setClientCredentials(any(File.class), anyString());
    }
}
