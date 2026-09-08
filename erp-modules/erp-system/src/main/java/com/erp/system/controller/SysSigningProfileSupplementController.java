package com.erp.system.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;
import com.erp.system.service.ISysSigningProfileSupplementService;

/** Internal-only endpoint used after HR approves an OA signing data request. */
@RestController
@RequestMapping("/user/sign-profile")
public class SysSigningProfileSupplementController
{
    private final ISysSigningProfileSupplementService supplementService;

    public SysSigningProfileSupplementController(ISysSigningProfileSupplementService supplementService)
    {
        this.supplementService = supplementService;
    }

    @InnerAuth
    @PostMapping("/supplement")
    public R<ReviewedSignProfileSupplementResult> supplement(
            @RequestBody ReviewedSignProfileSupplement request)
    {
        return R.ok(supplementService.supplement(request));
    }
}
