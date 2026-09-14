package com.erp.common.security.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SessionRetentionDigestTest
{
    @Test void digestIsStableOpaqueAndBoundToOwnerAndInternalSession()
    {
        String digest = SessionRetentionDigest.fromUserKey(42L, "original-internal-session");
        assertTrue(digest.matches("[a-f0-9]{64}"));
        assertEquals(digest, SessionRetentionDigest.fromUserKey(42L, "original-internal-session"));
        assertNotEquals(digest, SessionRetentionDigest.fromUserKey(43L, "original-internal-session"));
        assertNotEquals(digest, SessionRetentionDigest.fromUserKey(42L, "another-internal-session"));
        assertTrue(SessionRetentionDigest.matches(42L, "original-internal-session", digest));
        assertFalse(SessionRetentionDigest.matches(43L, "original-internal-session", digest));
    }
    @Test void absentRetentionDoesNotInventAnIdentifier()
    {
        assertNull(SessionRetentionDigest.fromUserKey(42L, null));
        assertNull(SessionRetentionDigest.fromUserKey(42L, " "));
        assertFalse(SessionRetentionDigest.matches(42L, "existing-session", null));
        SessionRetentionDigest.validate(null);
    }
    @Test void malformedDigestsAndMissingOwnersAreRejectedWithoutEchoingInput()
    {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SessionRetentionDigest.validate("sensitive-input"));
        assertFalse(error.getMessage().contains("sensitive-input"));
        assertThrows(IllegalArgumentException.class, () -> SessionRetentionDigest.fromUserKey(null, "session"));
        assertThrows(IllegalArgumentException.class, () -> SessionRetentionDigest.fromUserKey(0L, "session"));
    }
}
