package net.weaver.api.command;

/**
 * The fallback policy of a registered permission node.
 *
 * <p>The policy applies after explicit per-subject decisions have been
 * consulted. An explicit decision always wins over the policy.
 */
public enum PermissionPolicy
{
    /**
     * The node is allowed when no explicit decision applies.
     */
    ALLOW,

    /**
     * The node is denied when no explicit decision applies, unless the
     * registered required op level is positive and the subject's op level
     * reaches it.
     */
    DENY
}
