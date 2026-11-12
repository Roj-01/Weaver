public final class EditBoxProbe {

    private EditBoxProbe() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("net.minecraft.client.gui.components.EditBox",
                false, ClassLoader.getSystemClassLoader());
        System.out.println("EDITBOX_LOADED=" + type.getName());
    }
}
