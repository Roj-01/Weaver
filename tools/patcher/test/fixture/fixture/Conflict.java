package fixture;

/**
 * Fixture used to prove the patcher fails closed when a target already has a
 * member the patch set wants to add.
 */
public class Conflict {
    public Object weaver$getAttachment(String id) {
        return null;
    }
}
