package net.weaver.patched;

/**
 * Attachment contract added to the core game classes by the Weaver patcher.
 *
 * <p>Attachments are side-band data owned by the Weaver runtime. They are not
 * serialized by the game and do not change the game's own class layout.</p>
 */
public interface WeaverAttachmentHolder {
    Object weaver$getAttachment(String id);

    Object weaver$setAttachment(String id, Object value);

    Object weaver$removeAttachment(String id);

    boolean weaver$hasAttachment(String id);
}
