package net.weaver.core.command;

import net.weaver.api.command.PermissionResolver;

import java.util.Objects;

public final class FakePermissionResolver implements PermissionResolver
{
    private Object subject;
    private boolean subjectOverride;
    private int opLevel;

    public void useSubject(Object subject)
    {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.subjectOverride = true;
    }

    public void useIdentitySubject()
    {
        this.subject = null;
        this.subjectOverride = false;
    }

    public void setOpLevel(int opLevel)
    {
        this.opLevel = opLevel;
    }

    @Override
    public Object subject(Object source)
    {
        return subjectOverride ? subject : source;
    }

    @Override
    public int opLevel(Object source)
    {
        return opLevel;
    }
}
