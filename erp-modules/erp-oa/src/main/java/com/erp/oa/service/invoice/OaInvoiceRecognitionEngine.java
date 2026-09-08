package com.erp.oa.service.invoice;

import java.nio.file.Path;

public interface OaInvoiceRecognitionEngine
{
    String engine();

    String provider();

    boolean available();

    String unavailableReason();

    OaInvoiceRecognitionResult recognize(Path path, String extension);
}
