package com.erp.system.api.factory;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.erp.common.core.domain.R;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SignCandidateUser;
import com.erp.system.api.domain.SignCandidateUserQuery;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

/**
 * 用户服务降级处理
 * 
 * @author erp
 */
@Component
public class RemoteUserFallbackFactory implements FallbackFactory<RemoteUserService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteUserFallbackFactory.class);

    @Override
    public RemoteUserService create(Throwable throwable)
    {
        log.error("用户服务调用失败:{}", throwable.getMessage());
        return new RemoteUserService()
        {
            @Override
            public R<LoginUser> getUserInfo(String username, String source)
            {
                return R.fail("获取用户失败:" + throwable.getMessage());
            }

            @Override
            public R<Boolean> registerUserInfo(SysUser sysUser, String source)
            {
                return R.fail("注册用户失败:" + throwable.getMessage());
            }

            @Override
            public R<List<SignCandidateUser>> listSignCandidates(SignCandidateUserQuery query, String source)
            {
                return R.fail("获取签约候选员工失败:" + throwable.getMessage());
            }

            @Override
            public R<ReviewedSignProfileSupplementResult> supplementSigningProfile(
                    ReviewedSignProfileSupplement request, String source)
            {
                return R.fail("同步审核后签约个人事实失败:" + throwable.getMessage());
            }

            @Override
            public R<Boolean> recordUserLogin(SysUser sysUser, String source)
            {
                return R.fail("记录用户登录信息失败:" + throwable.getMessage());
            }
        };
    }
}
