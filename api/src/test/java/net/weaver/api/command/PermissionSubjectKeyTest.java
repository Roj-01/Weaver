package net.weaver.api.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionSubjectKeyTest
{
    @Test
    void defaultSubjectKeyPersistsStringsUuidsAndNumbers()
    {
        PermissionResolver resolver = PermissionResolver.simple();
        UUID id = UUID.randomUUID();

        assertEquals("player", resolver.subjectKey("player"));
        assertEquals(id.toString(), resolver.subjectKey(id));
        assertEquals("42", resolver.subjectKey(42));
        assertEquals("3.5", resolver.subjectKey(3.5D));
        assertNull(resolver.subjectKey(new Object()));
        assertNull(resolver.subjectKey(null));
    }
}
