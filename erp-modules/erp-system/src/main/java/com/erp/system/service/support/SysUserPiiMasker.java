package com.erp.system.service.support;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SysUserPiiMasker
{
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    public String maskAccount(String account)
    {
        return MOBILE.matcher(value(account)).matches() ? maskPhone(account) : account;
    }

    public String maskPhone(String phone)
    {
        String value = value(phone);
        if (value.length() < 7) return value.isEmpty() ? value : "***";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    public String maskEmail(String email)
    {
        String value = value(email);
        int at = value.indexOf('@');
        if (at <= 0) return value.isEmpty() ? value : "***";
        return value.substring(0, 1) + "***" + value.substring(at);
    }

    private static String value(String value) { return value == null ? "" : value; }
}

