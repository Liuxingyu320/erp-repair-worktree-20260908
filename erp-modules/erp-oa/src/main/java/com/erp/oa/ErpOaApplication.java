package com.erp.oa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.erp.common.security.annotation.EnableCustomConfig;
import com.erp.common.security.annotation.EnableRyFeignClients;

@EnableCustomConfig
@EnableRyFeignClients
@EnableScheduling
@SpringBootApplication
public class ErpOaApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(ErpOaApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  OA模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
