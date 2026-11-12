package net.weaver.core.command;

import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionPolicy;
import net.weaver.api.command.PermissionRegistration;
import net.weaver.api.command.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceTest
{
    private final Object source = new Object();

    @BeforeEach
    void setUp()
    {
        PermissionService.reset();
    }

    @AfterEach
    void tearDown()
    {
        PermissionService.reset();
    }

    @Test
    void unregisteredNodeIsDenied()
    {
        PermissionNode node = PermissionNode.parse("demo:command/test");

        assertFalse(PermissionService.isAllowed(source, node));
    }

    @Test
    void allowPolicyAllowsAndDenyPolicyWithoutOpLevelDenies()
    {
        PermissionNode allowed = PermissionNode.parse("demo:command/allowed");
        PermissionNode denied = PermissionNode.parse("demo:command/denied");
        PermissionService.register(allowed, PermissionPolicy.ALLOW, 0);
        PermissionService.register(denied, PermissionPolicy.DENY, 0);

        assertTrue(PermissionService.isAllowed(source, allowed));
        assertFalse(PermissionService.isAllowed(source, denied));
    }

    @Test
    void opLevelFallbackMatchesTheRegisteredRequirement()
    {
        PermissionNode node = PermissionNode.parse("demo:command/op");
        FakePermissionResolver resolver = new FakePermissionResolver();
        PermissionService.install(resolver);
        PermissionService.register(node, PermissionPolicy.DENY, 2);

        resolver.setOpLevel(0);
        assertFalse(PermissionService.isAllowed(source, node));
        resolver.setOpLevel(1);
        assertFalse(PermissionService.isAllowed(source, node));
        resolver.setOpLevel(2);
        assertTrue(PermissionService.isAllowed(source, node));
        resolver.setOpLevel(4);
        assertTrue(PermissionService.isAllowed(source, node));
    }

    @Test
    void explicitDenyOverridesAllowPolicyAndOpLevel()
    {
        PermissionNode node = PermissionNode.parse("demo:command/explicit");
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.setOpLevel(4);
        PermissionService.install(resolver);
        PermissionService.register(node, PermissionPolicy.ALLOW, 0);

        assertTrue(PermissionService.isAllowed(source, node));
        PermissionService.deny(source, node);
        assertFalse(PermissionService.isAllowed(source, node));
        PermissionService.clear(source, node);
        assertTrue(PermissionService.isAllowed(source, node));
    }

    @Test
    void explicitAllowOverridesDenyPolicy()
    {
        PermissionNode node = PermissionNode.parse("demo:command/override");
        PermissionService.register(node, PermissionPolicy.DENY, 4);

        assertFalse(PermissionService.isAllowed(source, node));
        PermissionService.allow(source, node);
        assertTrue(PermissionService.isAllowed(source, node));
        PermissionService.clear(source, node);
        assertFalse(PermissionService.isAllowed(source, node));
    }

    @Test
    void clearRestoresTheRegisteredFallback()
    {
        PermissionNode node = PermissionNode.parse("demo:command/restore");
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.setOpLevel(2);
        PermissionService.install(resolver);
        PermissionService.register(node, PermissionPolicy.DENY, 2);

        assertTrue(PermissionService.isAllowed(source, node));
        PermissionService.deny(source, node);
        assertFalse(PermissionService.isAllowed(source, node));
        PermissionService.clear(source, node);
        assertTrue(PermissionService.isAllowed(source, node));
    }

    @Test
    void ancestorDecisionAppliesToDescendants()
    {
        PermissionNode parent = PermissionNode.parse("demo:command/parent");
        PermissionNode child = PermissionNode.parse("demo:command/parent/child");
        PermissionService.register(child, PermissionPolicy.DENY, 0);

        PermissionService.allow(source, parent);
        assertTrue(PermissionService.isAllowed(source, child));
        PermissionService.deny(source, parent);
        assertFalse(PermissionService.isAllowed(source, child));
    }

    @Test
    void wildcardDecisionAppliesToUnregisteredDescendants()
    {
        PermissionNode child = PermissionNode.parse("demo:command/parent/child");
        PermissionNode wildcard = PermissionNode.wildcard("demo");

        PermissionService.allow(source, wildcard);
        assertTrue(PermissionService.isAllowed(source, child));
        PermissionService.deny(source, wildcard);
        assertFalse(PermissionService.isAllowed(source, child));
    }

    @Test
    void resolverSubjectIsUsedForExplicitDecisions()
    {
        PermissionNode node = PermissionNode.parse("demo:command/subject");
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.useSubject("alice");
        PermissionService.install(resolver);
        Object firstSource = new Object();
        Object secondSource = new Object();

        PermissionService.allow(firstSource, node);

        assertTrue(PermissionService.isAllowed(firstSource, node));
        assertTrue(PermissionService.isAllowed(secondSource, node));
    }

    @Test
    void registrationsAreReferenceCounted()
    {
        PermissionNode node = PermissionNode.parse("demo:command/refcount");
        PermissionRegistration first = PermissionService.register(node, PermissionPolicy.ALLOW, 0);
        PermissionRegistration second = PermissionService.register(node, PermissionPolicy.ALLOW, 0);

        assertTrue(first.isActive());
        assertTrue(second.isActive());
        first.unregister();
        assertFalse(first.isActive());
        assertTrue(PermissionService.isAllowed(source, node));
        second.unregister();
        assertFalse(second.isActive());
        assertFalse(PermissionService.isAllowed(source, node));
        first.unregister();
        assertFalse(PermissionService.isAllowed(source, node));
    }

    @Test
    void conflictingRegistrationIsRejected()
    {
        PermissionNode node = PermissionNode.parse("demo:command/conflict");
        PermissionService.register(node, PermissionPolicy.ALLOW, 0);

        assertThrows(IllegalArgumentException.class,
                () -> PermissionService.register(node, PermissionPolicy.DENY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PermissionService.register(node, PermissionPolicy.ALLOW, 2));
    }

    @Test
    void unregisteredNodeIsDeniedEvenAtHighOpLevel()
    {
        PermissionNode node = PermissionNode.parse("demo:command/unregistered");
        FakePermissionResolver resolver = new FakePermissionResolver();
        resolver.setOpLevel(4);
        PermissionService.install(resolver);

        assertFalse(PermissionService.isAllowed(source, node));
    }
}
