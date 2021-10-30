package club.sk1er.patcher.optifine;

import club.sk1er.patcher.commands.OptiFineReflectionDumpCommand;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;
import com.google.gson.Gson;
import gg.essential.api.EssentialAPI;
import kotlin.io.FilesKt;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Type;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

// Scrapes data about OptiFine's reflector and generates a json we can use to optimize it out.
public class OptiFineReflectorScraper {
    private static final byte[] reflectorBytes = "net/optifine/reflect/Reflector".getBytes(StandardCharsets.UTF_8);
    private static final boolean isEnabled = "true".equals(System.getProperty("patcher.scrapeOptiFineReflectionData"));
    private static final ReflectionData data = new ReflectionData();
    private static final Gson gson = new Gson();
    private static final Set<String> knownBrokenReflectors = ImmutableSet.of("ForgeBlock_isBed", "ForgeBlock_getBedDirection", "FMLCommonHandler_handleServerStarting");

    public static class ReflectionData {
        private Set<String> classesToTransform = new HashSet<>();
        private Map<String, MethodData> reflectorMethodData = new HashMap<>();

        public void addClassToTransform(String clazz) {
            classesToTransform.add(clazz);
        }

        public void addReflectorMethodData(String fieldName, String targetClass, String name, String descriptor) {
            if (knownBrokenReflectors.contains(fieldName)) return;
            reflectorMethodData.put(fieldName, new MethodData(targetClass, name, descriptor));
        }

        public Set<String> getClassesToTransform() {
            return classesToTransform;
        }

        public MethodData getReflectorMethodData(String fieldName) {
            return reflectorMethodData.get(fieldName);
        }
    }

    public static class MethodData {

        private final String targetClass;
        private final String name;
        private final String descriptor;

        public MethodData(String targetClass, String name, String descriptor) {
            this.targetClass = targetClass;
            this.name = name;
            this.descriptor = descriptor;
        }

        public String getTargetClass() {
            return targetClass;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }
    }

    public static void scanClassBytesForReflector(byte[] clazz, String className) {
        if (!isEnabled) return;
        if (Bytes.indexOf(clazz, reflectorBytes) != -1) {
            data.addClassToTransform(className);
        }
    }

    public static void registerCommand() {
        if (!isEnabled) return;
        EssentialAPI.getInstance().commandRegistry().registerCommand(new OptiFineReflectionDumpCommand());
    }

    public static void dumpInfo() {
        if (!isEnabled) return;
        gatherReflectorData();
        File output = new File(Minecraft.getMinecraft().mcDataDir, "optifine_reflection_data.json");
        output.delete();
        FilesKt.writeText(output, gson.toJson(data), StandardCharsets.UTF_8);
    }

    public static ReflectionData readData() {
        try (Reader reader = new InputStreamReader(OptiFineReflectorScraper.class.getClassLoader().getResourceAsStream("optifine_reflection_data.json"))) {
            return gson.fromJson(reader, ReflectionData.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void gatherReflectorData() {
        try {
            Class<?> reflector = Class.forName("net.optifine.reflect.Reflector");
            for (Field field : reflector.getFields()) {
                if (field.getType().getName().endsWith("ReflectorMethod")) {
                    handleReflectorMethod(field);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void handleReflectorMethod(Field reflectorMethodField) {
        try {
            Object reflectorMethod = reflectorMethodField.get(null);
            Method targetMethod = callGetter(reflectorMethod, "getTargetMethod");
            String fieldName = reflectorMethodField.getName();
            String targetClass = targetMethod.getDeclaringClass().getName();
            String name = targetMethod.getName();
            String descriptor = Type.getMethodDescriptor(targetMethod);
            data.addReflectorMethodData(fieldName, targetClass, name, descriptor);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static <T> T callGetter(Object obj, String name) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        //noinspection unchecked
        return (T) method.invoke(obj);
    }
}