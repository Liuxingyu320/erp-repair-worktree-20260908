package com.erp.system.service.support;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.erp.common.core.constant.UserConstants;
import com.erp.common.core.exception.ServiceException;

/**
 * Supplies administrator-issued temporary passwords.
 */
@Component
public class TemporaryPasswordGenerator
{
    static final int PASSWORD_LENGTH = UserConstants.PASSWORD_MAX_LENGTH;

    private static final String DEFAULT_NEW_USER_PASSWORD = "123456";

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "~!@#$%^&*()-=_+";

    private final SecureRandom secureRandom;

    public TemporaryPasswordGenerator()
    {
        this(new SecureRandom());
    }

    TemporaryPasswordGenerator(SecureRandom secureRandom)
    {
        this.secureRandom = secureRandom;
    }

    public String defaultNewUserPassword()
    {
        return DEFAULT_NEW_USER_PASSWORD;
    }

    public String generate(String passwordPolicy)
    {
        String policy = passwordPolicy == null || passwordPolicy.isBlank() ? "0" : passwordPolicy;
        if ("1".equals(policy))
        {
            throw new ServiceException("当前纯数字密码策略无法生成不少于80 bit熵的临时密码，请先升级密码策略");
        }

        String alphabet;
        List<Character> password = new ArrayList<>(PASSWORD_LENGTH);
        switch (policy)
        {
            case "2" -> {
                alphabet = UPPER + LOWER;
                password.add(randomCharacter(UPPER));
                password.add(randomCharacter(LOWER));
            }
            case "3" -> {
                alphabet = UPPER + LOWER + DIGITS;
                password.add(randomCharacter(UPPER + LOWER));
                password.add(randomCharacter(DIGITS));
            }
            case "4" -> {
                alphabet = UPPER + LOWER + DIGITS + SPECIAL;
                password.add(randomCharacter(UPPER));
                password.add(randomCharacter(LOWER));
                password.add(randomCharacter(DIGITS));
                password.add(randomCharacter(SPECIAL));
            }
            default -> alphabet = UPPER + LOWER + DIGITS + SPECIAL;
        }

        while (password.size() < PASSWORD_LENGTH)
        {
            password.add(randomCharacter(alphabet));
        }
        shuffle(password);
        moveLetterToFirstPosition(password);

        StringBuilder result = new StringBuilder(PASSWORD_LENGTH);
        password.forEach(result::append);
        String generated = result.toString();
        if (!UserConstants.isPasswordPolicyValid(generated, policy))
        {
            throw new IllegalStateException("临时密码生成器未满足已配置的密码策略");
        }
        return generated;
    }

    public double minimumEntropyBits(String passwordPolicy)
    {
        String policy = passwordPolicy == null || passwordPolicy.isBlank() ? "0" : passwordPolicy;
        int alphabetSize = switch (policy)
        {
            case "1" -> DIGITS.length();
            case "2" -> (UPPER + LOWER).length();
            case "3" -> (UPPER + LOWER + DIGITS).length();
            default -> (UPPER + LOWER + DIGITS + SPECIAL).length();
        };
        return PASSWORD_LENGTH * (Math.log(alphabetSize) / Math.log(2));
    }

    private Character randomCharacter(String alphabet)
    {
        return alphabet.charAt(secureRandom.nextInt(alphabet.length()));
    }

    private void shuffle(List<Character> password)
    {
        for (int index = password.size() - 1; index > 0; index--)
        {
            int swapIndex = secureRandom.nextInt(index + 1);
            Character value = password.get(index);
            password.set(index, password.get(swapIndex));
            password.set(swapIndex, value);
        }
    }

    private void moveLetterToFirstPosition(List<Character> password)
    {
        for (int index = 0; index < password.size(); index++)
        {
            if (Character.isLetter(password.get(index)))
            {
                Character first = password.get(0);
                password.set(0, password.get(index));
                password.set(index, first);
                return;
            }
        }
    }
}
