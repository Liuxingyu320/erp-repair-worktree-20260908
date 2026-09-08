package com.erp.approval.candidate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractDirectoryCandidateResolver
{
    protected static List<ResolvedApprovalCandidate> candidates(
            List<ApprovalDirectoryUser> users, String sourceType,
            String sourceCode, String reason)
    {
        Map<Long, ResolvedApprovalCandidate> unique = new LinkedHashMap<>();
        if (users != null)
        {
            for (ApprovalDirectoryUser user : users)
            {
                if (user != null && user.getUserId() != null)
                {
                    unique.putIfAbsent(user.getUserId(),
                            new ResolvedApprovalCandidate(user.getUserId(),
                                    safeName(user.getUserName()), user.getDeptId(),
                                    user.getDeptName(), user.getPostSort(),
                                    sourceType, sourceCode, reason));
                }
            }
        }
        return List.copyOf(unique.values());
    }

    protected static String text(Map<String, Object> config, String key)
    {
        Object value = config.get(key);
        if (value == null)
        {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    protected static boolean bool(Map<String, Object> config, String key,
            boolean defaultValue)
    {
        Object value = config.get(key);
        return value == null ? defaultValue
                : Boolean.parseBoolean(value.toString());
    }

    private static String safeName(String value)
    {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.matches("^1\\d{10}$") || name.contains("@"))
        {
            return "姓名未配置";
        }
        return name;
    }
}
