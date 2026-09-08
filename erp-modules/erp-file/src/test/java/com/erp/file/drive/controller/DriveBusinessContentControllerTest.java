package com.erp.file.drive.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.exception.DriveException;
import com.erp.file.drive.service.DriveActorResolver;
import com.erp.file.drive.service.DriveContentService;
import com.erp.file.drive.service.DriveFeatureGuard;
import com.erp.system.api.domain.DriveBusinessFile;
import org.junit.jupiter.api.Test;

class DriveBusinessContentControllerTest
{
    @Test
    void bothBusinessFileEndpointsRequireInternalAuthenticatedUser()
            throws Exception
    {
        Method binding = DriveBusinessContentController.class.getMethod(
                "validateBinding", Long.class, String.class);
        Method content = DriveBusinessContentController.class.getMethod(
                "content", Long.class, String.class);

        assertThat(binding.getAnnotation(InnerAuth.class).isUser()).isTrue();
        assertThat(content.getAnnotation(InnerAuth.class).isUser()).isTrue();
    }

    @Test
    void customerPhotoBindingRejectsPdfButHealthCertificateAcceptsIt()
    {
        DriveActor actor = mock(DriveActor.class);
        DriveActorResolver resolver = mock(DriveActorResolver.class);
        DriveContentService service = mock(DriveContentService.class);
        when(resolver.resolve()).thenReturn(actor);
        DriveBusinessFile pdf = new DriveBusinessFile();
        pdf.setNodeId(22L);
        pdf.setContentType("application/pdf");
        when(service.validateBusinessBinding(22L, actor)).thenReturn(pdf);
        DriveBusinessContentController controller = new
                DriveBusinessContentController(enabledGuard(), resolver,
                        service);

        R<DriveBusinessFile> accepted = controller.validateBinding(22L,
                "HEALTH_CERTIFICATE");
        assertThat(accepted.getData()).isSameAs(pdf);
        assertThatThrownBy(() -> controller.validateBinding(22L,
                "CUSTOMER_PHOTO"))
                .isInstanceOf(DriveException.class)
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED);
    }

    private DriveFeatureGuard enabledGuard()
    {
        DriveProperties properties = new DriveProperties();
        properties.setEnabled(true);
        return new DriveFeatureGuard(properties);
    }
}
