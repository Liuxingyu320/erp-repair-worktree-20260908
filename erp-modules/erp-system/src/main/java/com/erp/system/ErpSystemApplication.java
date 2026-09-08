package com.erp.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.erp.common.security.annotation.EnableCustomConfig;
import com.erp.common.security.annotation.EnableRyFeignClients;

/**
 * 系统模块
 *
 * @author erp
 */
@EnableCustomConfig
@EnableRyFeignClients
@EnableScheduling
@SpringBootApplication
public class ErpSystemApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(ErpSystemApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  系统模块启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}
