package com.erp.system.service.impl;

import org.springframework.stereotype.Service;
import com.erp.system.domain.vo.HrHealthCertificateCapabilityVo;
import com.erp.system.service.BusinessFeatureGate;

/** Interprets the health-certificate setting strictly as a new-intake switch. */
@Service
public class HrHealthCertificateFeatureService
{
    public static final String FEATURE_KEY=BusinessFeatureGate.HEALTH_CERTIFICATE;
    private final BusinessFeatureGate featureGate;

    public HrHealthCertificateFeatureService(BusinessFeatureGate featureGate)
    {
        this.featureGate=featureGate;
    }

    public HrHealthCertificateCapabilityVo capability()
    {
        BusinessFeatureGate.Decision decision=featureGate.inspect(FEATURE_KEY);
        HrHealthCertificateCapabilityVo result=new HrHealthCertificateCapabilityVo();
        if(decision.isEnabled())
        {
            result.setIntakeEnabled(true);
            result.setReason("健康证新受理已开放");
        }
        else if(decision.isUnavailable())
        {
            result.setReason("健康证受理配置暂时不可用，系统已按关闭处理；历史查询、审核和提醒仍可使用");
        }
        else if(decision.isInvalid())
        {
            result.setReason("健康证新受理参数值无效，系统已按关闭处理");
        }
        else
        {
            result.setReason("健康证新受理当前处于维护关闭状态，历史查询、审核和提醒仍可使用");
        }
        return result;
    }

    public void requireIntakeEnabled()
    {
        featureGate.requireEnabled(FEATURE_KEY);
    }
}
