package io.github.vizanarkonin.keres.core.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class ClassUtils {
    private static final Logger log = LogManager.getLogger("ClassUtils");

    /**
     * Service method - entry point for classpath scanner. Used to scan the classpath for all classes with KeresScenarioMetaData or keresUserDefinitionMetaData annotation.
     * @param packageName       - Package to scan
     * @param annotationToFind  - Annotation type we expect to see target class have
     * @return                  - List of classes
     */
    public static List<List<Object>> getClassesByAnnotation(String packageName, Class annotationToFind) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = null;
        try {
            resources = classLoader.getResources(path);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        List<List<Object>> classes = new ArrayList<List<Object>>();
        for (File directory : dirs)
            classes.addAll(findClassesWithAnnotation(directory, packageName, annotationToFind));

        return classes;
    }

    /**
     * Service method - used to scan classpath directories for simulation classes (the ones with KeresScenarioMetaData or KeresUserDefinitionMetaData annotation)
     * @param directory         - Directory to scan
     * @param packageName       - Target package name
     * @param annotationToFind  - Annotation type we expect to see target class have
     * @return                  - List of annotated classes
     */
    private static List<List<Object>> findClassesWithAnnotation(File directory, String packageName, Class annotationToFind) {
        List<List<Object>> classes = new ArrayList<List<Object>>();
        if (!directory.exists())
            return classes;
    
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClassesWithAnnotation(file,
                        (!packageName.equals("") ? packageName + "." : packageName) + file.getName(), annotationToFind));
            } else if (file.getName().endsWith(".class"))
                try {
                    Class<?> val = Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                    if (val.isAnnotationPresent(annotationToFind)) {
                        byte[] classContents = Files.readAllBytes(file.toPath());
                        String checksum = DigestUtils.md5Hex(classContents);

                        classes.add(Arrays.asList(val, checksum));
                    }
                } catch (Exception e) {
                    log.trace(e.getMessage());
                    log.trace(ExceptionUtils.getStackTrace(e));
                }
        }
        
        return classes;
    }
}
