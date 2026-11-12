package net.weaver.core.dev;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Which methods of a class changed between two versions, by their code (line
 * numbers and frames ignored). The hot reload uses it to find changes that a
 * redefinition would accept but never run: a static initializer, or a mod's
 * startup methods.
 */
final class MethodPrints
{
    /** The lifecycle methods Weaver calls once, at startup. */
    static final Set<String> STARTUP = Set.of("onInitialize", "onRegister", "onCommonSetup", "onClientSetup",
            "onServerSetup", "onLoadComplete");

    private MethodPrints()
    {
    }

    /** The names of methods added, removed, or whose code differs. */
    static Set<String> changed(byte[] before, byte[] after)
    {
        Map<String, String> a = prints(before);
        Map<String, String> b = prints(after);
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : b.entrySet())
        {
            if (!entry.getValue().equals(a.get(entry.getKey())))
            {
                out.add(entry.getKey().substring(0, entry.getKey().indexOf('(')));
            }
        }
        for (String key : a.keySet())
        {
            if (!b.containsKey(key))
            {
                out.add(key.substring(0, key.indexOf('(')));
            }
        }
        return out;
    }

    /** Whether the class is a mod entry point (its startup methods run once). */
    static boolean isModEntry(byte[] bytes)
    {
        List<String> interfaces = List.of(new ClassReader(bytes).getInterfaces());
        return interfaces.contains("net/weaver/api/WeaverMod") || interfaces.contains("net/weaver/api/game/GameWeaverMod");
    }

    /**
     * Why a changed class needs a restart even though it can be redefined, or null:
     * its static initializer changed, or a mod's startup method changed.
     */
    static String restartReason(byte[] before, byte[] after)
    {
        if (before == null)
        {
            return null;
        }
        Set<String> changed = changed(before, after);
        if (changed.contains("<clinit>"))
        {
            return "static initializer changed (it runs once, when the class loads)";
        }
        if (isModEntry(after))
        {
            for (String name : changed)
            {
                if (STARTUP.contains(name))
                {
                    return name + " changed (it runs once, at startup)";
                }
            }
        }
        return null;
    }

    static Map<String, String> prints(byte[] bytes)
    {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Map<String, String> out = new HashMap<>();
        for (MethodNode method : node.methods)
        {
            out.put(method.name + method.desc, print(method));
        }
        return out;
    }

    private static String print(MethodNode method)
    {
        Map<LabelNode, Integer> labels = new HashMap<>();
        for (AbstractInsnNode insn : method.instructions)
        {
            if (insn instanceof LabelNode label)
            {
                labels.put(label, labels.size());
            }
        }
        StringBuilder out = new StringBuilder().append(method.access).append(';');
        for (AbstractInsnNode insn : method.instructions)
        {
            if (insn instanceof LabelNode label)
            {
                out.append('L').append(labels.get(label)).append(';');
                continue;
            }
            if (insn.getOpcode() < 0)
            {
                continue;
            }
            out.append(insn.getOpcode());
            switch (insn)
            {
                case VarInsnNode v -> out.append(' ').append(v.var);
                case IntInsnNode i -> out.append(' ').append(i.operand);
                case LdcInsnNode l -> out.append(' ').append(l.cst.getClass().getSimpleName()).append(':').append(l.cst);
                case TypeInsnNode t -> out.append(' ').append(t.desc);
                case FieldInsnNode f -> out.append(' ').append(f.owner).append('.').append(f.name).append(f.desc);
                case MethodInsnNode m -> out.append(' ').append(m.owner).append('.').append(m.name).append(m.desc);
                case InvokeDynamicInsnNode d -> out.append(' ').append(d.name).append(d.desc).append(d.bsm)
                        .append(java.util.Arrays.toString(d.bsmArgs));
                case JumpInsnNode j -> out.append(" L").append(labels.get(j.label));
                case IincInsnNode i -> out.append(' ').append(i.var).append(',').append(i.incr);
                case TableSwitchInsnNode t -> {
                    out.append(' ').append(t.min).append('-').append(t.max).append(" L").append(labels.get(t.dflt));
                    t.labels.forEach(l -> out.append(",L").append(labels.get(l)));
                }
                case LookupSwitchInsnNode s -> {
                    out.append(' ').append(s.keys).append(" L").append(labels.get(s.dflt));
                    s.labels.forEach(l -> out.append(",L").append(labels.get(l)));
                }
                case MultiANewArrayInsnNode a -> out.append(' ').append(a.desc).append(',').append(a.dims);
                default -> {
                }
            }
            out.append(';');
        }
        if (method.tryCatchBlocks != null)
        {
            method.tryCatchBlocks.forEach(t -> out.append("T").append(labels.get(t.start)).append(',').append(labels.get(t.end))
                    .append(',').append(labels.get(t.handler)).append(',').append(t.type).append(';'));
        }
        return out.toString();
    }

    /** Unused marker to keep the ASM API level explicit. */
    static final int API = Opcodes.ASM9;
}
