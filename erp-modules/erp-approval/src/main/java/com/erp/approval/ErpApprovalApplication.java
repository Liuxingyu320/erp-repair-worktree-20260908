package com.erp.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.erp.common.security.annotation.EnableCustomConfig;
import com.erp.common.security.annotation.EnableRyFeignClients;

@EnableCustomConfig
@EnableRyFeignClients
@EnableScheduling
@SpringBootApplication
public class ErpApprovalApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(ErpApprovalApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  统一审批中心启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
