package com.erp.system.service.credential;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import com.erp.common.core.constant.UserConstants;

/**
 * 按系统密码策略生成一次性密码。
 */
@Component("credentialTemporaryPasswordGenerator")
public class CredentialTemporaryPasswordGenerator
{
    static final int PASSWORD_LENGTH = 16;

    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] LETTERS_AND_DIGITS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final char[] SPECIAL = "!@#$%^&*()-=_+".toCharArray();
    private static final char[] STRONG_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*()-=_+".toCharArray();

    private final SecureRandom secureRandom;

    public CredentialTemporaryPasswordGenerator()
    {
        this(new SecureRandom());
    }

    CredentialTemporaryPasswordGenerator(SecureRandom secureRandom)
    {
        this.secureRandom = secureRandom;
    }

    public String generate(String policyType)
    {
        String type = policyType == null || policyType.isEmpty() ? "0" : policyType;
        char[] password = new char[PASSWORD_LENGTH];
        int position = 0;
        char[] fillAlphabet;

        if ("1".equals(type))
        {
            fillAlphabet = DIGITS;
        }
        else if ("2".equals(type))
        {
            password[position++] = pick(UPPER);
            password[position++] = pick(LOWER);
            fillAlphabet = LETTERS;
        }
        else if ("3".equals(type))
        {
            password[position++] = pick(UPPER);
            password[position++] = pick(LOWER);
            password[position++] = pick(DIGITS);
            fillAlphabet = LETTERS_AND_DIGITS;
        }
        else
        {
            password[position++] = pick(UPPER);
            password[position++] = pick(LOWER);
            password[position++] = pick(DIGITS);
            password[position++] = pick(SPECIAL);
            fillAlphabet = STRONG_ALPHABET;
        }

        while (position < password.length)
        {
            password[position++] = pick(fillAlphabet);
        }
        shuffle(password);
        moveLetterOrDigitToFront(password);
        String value = new String(password);
        String validationError = UserConstants.getPasswordPolicyError(value, type);
        if (validationError != null)
        {
            throw new IllegalStateException("一次性密码生成结果不符合当前策略");
        }
        return value;
    }

    private char pick(char[] alphabet)
    {
        return alphabet[secureRandom.nextInt(alphabet.length)];
    }

    private void shuffle(char[] value)
    {
        for (int i = value.length - 1; i > 0; i--)
        {
            int target = secureRandom.nextInt(i + 1);
            char current = value[i];
            value[i] = value[target];
            value[target] = current;
        }
    }

    private void moveLetterOrDigitToFront(char[] value)
    {
        if (value.length == 0 || Character.isLetterOrDigit(value[0]))
        {
            return;
        }
        for (int index = 1; index < value.length; index++)
        {
            if (Character.isLetterOrDigit(value[index]))
            {
                char first = value[0];
                value[0] = value[index];
                value[index] = first;
                return;
            }
        }
    }
}
