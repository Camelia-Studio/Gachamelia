package org.camelia.studio.gachamelia.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtils {
    private static final Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

    /**
     * Charge toutes les classes d'un type spécifique depuis un package
     *
     * @param packageName Le package à scanner
     * @param targetType Le type de classe à charger
     * @return Liste des instances des classes trouvées
     */
    public static <T> List<T> loadClasses(String packageName, Class<T> targetType) {
        List<T> instances = new ArrayList<>();

        try {
            // Convertit le nom du package en chemin
            String path = packageName.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            // Récupère toutes les ressources du package
            var resources = classLoader.getResources(path);

            // Parcourt toutes les ressources trouvées
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File directory = new File(resource.toURI());

                // Charge les classes du répertoire et ses sous-répertoires
                scanDirectory(directory, packageName, targetType, instances);
            }

        } catch (Exception e) {
            logger.error("Erreur lors du scan du package {} : {}", packageName, e.getMessage());
        }

        return instances;
    }

    /**
     * Scanne récursivement un répertoire pour trouver les classes
     */
    private static <T> void scanDirectory(File directory, String packageName, Class<T> targetType, List<T> instances) {
        // Vérifie si le répertoire existe
        if (!directory.exists()) {
            return;
        }

        // Récupère tous les fichiers du répertoire
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                // Si c'est un répertoire, on le scanne récursivement
                if (file.isDirectory()) {
                    scanDirectory(
                            file,
                            packageName + "." + file.getName(),
                            targetType,
                            instances
                    );
                }
                // Si c'est un fichier .class, on essaie de le charger
                else if (file.getName().endsWith(".class")) {
                    loadClass(packageName, file.getName(), targetType, instances);
                }
            }
        }
    }

    /**
     * Charge une classe spécifique
     */
    @SuppressWarnings("unchecked")
    private static <T> void loadClass(String packageName, String fileName, Class<T> targetType, List<T> instances) {
        try {
            // Convertit le nom de fichier en nom de classe
            String className = packageName + '.' + fileName.substring(0, fileName.length() - 6);
            Class<?> clazz = Class.forName(className);

            // Vérifie si la classe correspond au type recherché
            if (targetType.isAssignableFrom(clazz) &&
                    !clazz.isInterface() &&
                    !Modifier.isAbstract(clazz.getModifiers())) {

                // Crée une instance de la classe
                T instance = (T) clazz.getDeclaredConstructor().newInstance();
                instances.add(instance);

                logger.debug("Classe chargée : {}", className);
            }

        } catch (Exception e) {
            logger.error("Erreur lors du chargement d'une classe : {}", e.getMessage());
        }
    }
}