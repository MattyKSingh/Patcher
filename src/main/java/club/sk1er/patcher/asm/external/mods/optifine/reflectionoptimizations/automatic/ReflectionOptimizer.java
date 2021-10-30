package club.sk1er.patcher.asm.external.mods.optifine.reflectionoptimizations.automatic;

import club.sk1er.patcher.optifine.OptiFineReflectorScraper;
import club.sk1er.patcher.tweaker.transform.PatcherTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;

public class ReflectionOptimizer implements PatcherTransformer {
    private static final String reflectorClass = "net/optifine/reflect/Reflector";
    private final OptiFineReflectorScraper.ReflectionData data = OptiFineReflectorScraper.readData();

    @Override
    public String[] getClassName() {
        return data.getClassesToTransform().toArray(new String[0]);
    }

    @Override
    public void transform(ClassNode classNode, String name) {
        if (data == null) return;

        for (MethodNode methodNode : classNode.methods) {
            AbstractInsnNode insn = methodNode.instructions.getFirst();
            if (insn == null) continue;
            while (insn.getNext() != null) {
                insn = insn.getNext();
                if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                    if (fieldInsnNode.owner.equals(reflectorClass) && fieldInsnNode.desc.equals("Lnet/optifine/reflect/ReflectorMethod;")) {
                        if (insn.getNext() instanceof MethodInsnNode) {
                            MethodInsnNode next = (MethodInsnNode) insn.getNext();
                            if (next.name.equals("exists")) continue;
                        }
                        insn = transformMethodCall(methodNode.instructions, fieldInsnNode);
                    }
                }
            }
        }
    }

    private AbstractInsnNode transformMethodCall(InsnList insns, FieldInsnNode getReflectorNode) {
        OptiFineReflectorScraper.MethodData methodData = data.getReflectorMethodData(getReflectorNode.name);
        if (methodData == null) return getReflectorNode;
        Type returnType = Type.getReturnType(methodData.getDescriptor());
        Type[] parameterTypes = Type.getArgumentTypes(methodData.getDescriptor());
        if (Arrays.stream(parameterTypes).anyMatch(it -> it.getDescriptor().length() == 1)) {
            // TODO: Handle primitive unboxing
            return getReflectorNode;
        }
        int numberOfArrayStoresSeen = 0;
        AbstractInsnNode insn = getReflectorNode.getNext();
        while (insn.getNext() != null) {
            if (isReflectorCall(insn)) {
                if (insn.getNext().getOpcode() == Opcodes.POP && returnType == Type.VOID_TYPE) {
                    insns.remove(insn.getNext());
                }
                MethodInsnNode methodInsnNode = ((MethodInsnNode) insn);
                boolean isStatic = Type.getArgumentTypes(methodInsnNode.desc).length == 2;
                int opcode = isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
                MethodInsnNode newCall = new MethodInsnNode(opcode, methodData.getTargetClass().replace('.', '/'), methodData.getName(), methodData.getDescriptor());
                insns.set(insn, newCall);
                insns.remove(getReflectorNode);
                return newCall;
            }

            if (insn.getOpcode() == Opcodes.ANEWARRAY) {
                AbstractInsnNode lengthNode = insn.getPrevious();
                insns.remove(lengthNode);
                if (lengthNode.getOpcode() != Opcodes.ICONST_0) {
                    insns.remove(insn.getNext()); // DUP
                    insns.remove(insn.getNext()); // index of first item
                }
                AbstractInsnNode thisInsn = insn;
                insn = insn.getNext();
                insns.remove(thisInsn);
                continue;
            } else if (insn.getOpcode() == Opcodes.AASTORE) {
                if (insn.getNext().getOpcode() == Opcodes.DUP) {
                    insns.remove(insn.getNext()); // DUP
                    insns.remove(insn.getNext()); // index of next item
                }
                AbstractInsnNode thisInsn = insn;
                insn = insn.getNext();
                // Cast to the desired type in case it isn't known by this point
                insns.set(thisInsn, new TypeInsnNode(Opcodes.CHECKCAST, parameterTypes[numberOfArrayStoresSeen].getInternalName()));
                numberOfArrayStoresSeen++;
                continue;
            }
            insn = insn.getNext();
        }
        return null;
    }

    private boolean isReflectorCall(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
            return methodInsnNode.owner.equals(reflectorClass) && methodInsnNode.name.startsWith("call");
        }
        return false;
    }
}