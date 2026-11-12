package diag;

/** فئات الفريمات بحسب مصدر الحزمة. */
public enum Category {
    /** أكواد اللعبة الرسمية: net.minecraft / com.mojang */
    VANILLA("اللعبة"),
    /** المحمّل وبناه: Fabric/Forge/NeoForge/Quilt + launchwrapper */
    LOADER("المحمّل"),
    /** بنى Mixin نفسها (org.spongepowered.asm) */
    MIXIN_INFRA("بنى Mixin"),
    /** جافا والمكتبات المرافقة المعروفة (LWJGL, Netty, ...) */
    PLATFORM("المنصة/مكتبات"),
    /** أي حزمة غير رسمية لم نصنّفها = مود */
    MOD("مود");

    public final String ar;

    Category(String ar) {
        this.ar = ar;
    }
}
