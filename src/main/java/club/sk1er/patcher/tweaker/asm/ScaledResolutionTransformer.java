package club.sk1er.patcher.tweaker.asm;

import club.sk1er.patcher.tweaker.transform.PatcherTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ListIterator;

public class ScaledResolutionTransformer implements PatcherTransformer {
    @Override
    public String[] getClassName() {
        return new String[]{"net.minecraft.client.gui.ScaledResolution"};
    }

    @Override
    public void transform(ClassNode classNode, String name) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<init>")) {
                final InsnList instructions = method.instructions;
                final ListIterator<AbstractInsnNode> iterator = instructions.iterator();

                while (iterator.hasNext()) {
                    final AbstractInsnNode next = iterator.next();

                    if (next instanceof FieldInsnNode && next.getOpcode() == Opcodes.GETFIELD) {
                        final String fieldName = mapFieldNameFromNode(next);

                        if (fieldName.equals("guiScale")) {
                            instructions.remove(next.getPrevious().getPrevious());
                            instructions.remove(next.getPrevious());
                            instructions.insertBefore(next.getNext(), new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                getHooksPackage() + "ScaledResolutionHook", "modifyGuiScale", "()I", false
                            ));
                            instructions.remove(next);
                        }
                    }
                }
            }
        }
    }
}
