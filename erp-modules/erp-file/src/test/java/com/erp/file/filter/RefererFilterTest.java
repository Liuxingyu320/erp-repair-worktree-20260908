package com.erp.file.filter;

import java.lang.reflect.Proxy;
import java.util.Enumeration;
import java.util.Vector;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RefererFilterTest
{
    public void testAllowsMissingReferer() throws Exception
    {
        FilterResult result = runFilter("example.com", null);

        assertTrue(result.chainInvoked, "missing Referer should be allowed through");
        assertFalse(result.errorSent, "missing Referer should not send an error");
    }

    public void testAllowsExactAllowedDomain() throws Exception
    {
        FilterResult result = runFilter("example.com", "https://example.com/files/demo.pdf");

        assertTrue(result.chainInvoked, "exact allowed domain should be allowed through");
        assertFalse(result.errorSent, "exact allowed domain should not send an error");
    }

    public void testAllowsSubdomainOfAllowedDomain() throws Exception
    {
        FilterResult result = runFilter("example.com", "https://download.example.com/files/demo.pdf");

        assertTrue(result.chainInvoked, "subdomain of allowed domain should be allowed through");
        assertFalse(result.errorSent, "subdomain of allowed domain should not send an error");
    }

    public void testRejectsHostThatOnlyContainsAllowedDomain() throws Exception
    {
        FilterResult result = runFilter("example.com", "https://badexample.com/files/demo.pdf?next=example.com");

        assertFalse(result.chainInvoked, "host that only contains allowed domain text should be rejected");
        assertForbidden(result);
    }

    public void testTrimsAllowedDomains() throws Exception
    {
        FilterResult result = runFilter("  example.com  ", "https://example.com/files/demo.pdf");

        assertTrue(result.chainInvoked, "trimmed allowed domain should be allowed through");
        assertFalse(result.errorSent, "trimmed allowed domain should not send an error");
    }

    public void testIgnoresEmptyAllowedDomainEntries() throws Exception
    {
        FilterResult result = runFilter("example.com,,static.example.com", "https://evil.com/files/demo.pdf");

        assertFalse(result.chainInvoked, "empty allowed-domain entry should not allow any referer");
        assertForbidden(result);
    }

    public void testRejectsNonEmptyRefererWhenAllowedDomainsMissing() throws Exception
    {
        FilterResult result;
        try
        {
            result = runFilter(null, "https://example.com/files/demo.pdf");
        }
        catch (NullPointerException ex)
        {
            throw new AssertionError("missing allowedDomains should not throw", ex);
        }

        assertFalse(result.chainInvoked, "missing allowedDomains should reject non-empty Referer");
        assertForbidden(result);
    }

    public void testRejectsNonEmptyRefererWhenAllowedDomainsAllBlank() throws Exception
    {
        FilterResult result = runFilter("  , ,  ", "https://example.com/files/demo.pdf");

        assertFalse(result.chainInvoked, "blank allowedDomains should reject non-empty Referer");
        assertForbidden(result);
    }

    private static FilterResult runFilter(String allowedDomains, String referer) throws Exception
    {
        RefererFilter filter = new RefererFilter();
        filter.init(filterConfig(allowedDomains));

        FilterResult result = new FilterResult();
        filter.doFilter(request(referer), response(result), (ServletRequest request, ServletResponse response) -> result.chainInvoked = true);
        return result;
    }

    private static FilterConfig filterConfig(String allowedDomains)
    {
        return new FilterConfig()
        {
            @Override
            public String getFilterName()
            {
                return "refererFilter";
            }

            @Override
            public ServletContext getServletContext()
            {
                return null;
            }

            @Override
            public String getInitParameter(String name)
            {
                return "allowedDomains".equals(name) ? allowedDomains : null;
            }

            @Override
            public Enumeration<String> getInitParameterNames()
            {
                Vector<String> names = new Vector<String>();
                names.add("allowedDomains");
                return names.elements();
            }
        };
    }

    private static HttpServletRequest request(String referer)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName()))
                    {
                        return "Referer".equalsIgnoreCase((String) args[0]) ? referer : null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static HttpServletResponse response(FilterResult result)
    {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("sendError".equals(method.getName()))
                    {
                        result.errorSent = true;
                        result.status = (Integer) args[0];
                        result.message = args.length > 1 ? (String) args[1] : null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive())
        {
            return null;
        }
        if (boolean.class.equals(returnType))
        {
            return false;
        }
        if (char.class.equals(returnType))
        {
            return '\0';
        }
        return 0;
    }

    private static void assertTrue(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message)
    {
        assertTrue(!condition, message);
    }

    private static void assertForbidden(FilterResult result)
    {
        assertTrue(result.errorSent, "rejected Referer should send an error");
        if (HttpServletResponse.SC_FORBIDDEN != result.status)
        {
            throw new AssertionError("expected status 403 but was " + result.status);
        }
    }

    private static class FilterResult
    {
        private boolean chainInvoked;
        private boolean errorSent;
        private int status;
        private String message;
    }
}
