package com.erp.system.service;

import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;

/** Applies only HR-reviewed personal facts supplied by the OA signing flow. */
public interface ISysSigningProfileSupplementService
{
    ReviewedSignProfileSupplementResult supplement(ReviewedSignProfileSupplement request);
}
