package org.camelia.studio.gachamelia.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ReflectionUtils {
    private static final Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

    public static <T> List<T> loadClasses(String packageName, Class<T> targetType) {
        List<T> instances = new ArrayList<>();
        String path = packageName.replace('.', '/');

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();

                if (resource.getProtocol().equals("jar")) {
                    processJarFile(resource, path, packageName, targetType, instances);
                } else {
                    processDirectory(new File(resource.toURI()), packageName, targetType, instances);
                }
            }
        } catch (Exception e) {
            logger.error("Erreur lors du scan du package {} : {}", packageName, e.getMessage());
        }

        return instances;
    }

    private static <T> void processJarFile(URL resource, String path, String packageName,
                                           Class<T> targetType, List<T> instances) throws IOException {
        String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(path) && entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6)
                            .replace('/', '.');
                    loadClass(className, targetType, instances);
                }
            }
        }
    }

    private static <T> void processDirectory(File directory, String packageName,
                                             Class<T> targetType, List<T> instances) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file, packageName + "." + file.getName(),
                            targetType, instances);
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + '.' +
                            file.getName().substring(0, file.getName().length() - 6);
                    loadClass(className, targetType, instances);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void loadClass(String className, Class<T> targetType, List<T> instances) {
        try {
            Class<?> clazz = Class.forName(className);

            if (targetType.isAssignableFrom(clazz) && !clazz.isInterface() &&
                    !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                instances.add((T) clazz.getDeclaredConstructor().newInstance());
                logger.debug("Classe chargée : {}", className);
            }
        } catch (Exception e) {
            logger.error("Erreur lors du chargement de la classe {} : {}",
                    className, e.getMessage());
        }
    }
}