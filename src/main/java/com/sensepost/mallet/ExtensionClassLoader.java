package com.sensepost.mallet;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class ExtensionClassLoader extends ClassLoader {

    private static ClassLoader loader;

    private final static String DIRECTORY = "./libext";

    private static FilenameFilter jarFilter = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            return new File(dir, name).isFile() && name.endsWith(".jar");
        }
    };

    static {
        reload();
    }

    public static synchronized void reload() {
        File libDir = new File(DIRECTORY);
        ClassLoader parent = ExtensionClassLoader.class.getClassLoader();
        if (!libDir.isDirectory()) {
            System.err.format("%s is not a directory, using default classloader", libDir);
            loader = parent;
            return;
        }
        File[] jars = libDir.listFiles(jarFilter);
        URL[] urls = new URL[jars.length];
        try {
            for (int i = 0; i < jars.length; i++)
                urls[i] = jars[i].toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }
        loader = URLClassLoader.newInstance(urls, parent);
    }

    public static ClassLoader getExtensionClassLoader() {
        return loader;
    }

}
