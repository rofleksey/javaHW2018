package ru.ifmo.rain.borisov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class Implementor implements JarImpler {


    private static final String FILE_NAME_SUFFIX = "Impl.java";
    private static final String CLASS_NAME_SUFFIX = "Impl";
    private static final String TMP_DIR = "tmp" + File.separator;
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private Class<?> classToImplement;
    private Writer out;
    private String pathOut;

    /**
     * Checks if token can be implemented
     *
     * @param token interface to check
     * @throws ImplerException if token is not an interface, or is either final or primitive
     */
    private static void checkClass(Class<?> token) throws ImplerException {
        int mod = token.getModifiers();
        if (!token.isInterface()) {
            throw new ImplerException("Input class is not an interface");
        }
        if (Modifier.isFinal(mod)) {
            throw new ImplerException("Input class is final");
        }
        if (token.isPrimitive()) {
            throw new ImplerException("Input class is primitive");
        }
    }

    /**
     * Implements class
     *
     * @param token interface to implement
     * @param root  path to write new interface
     * @throws ImplerException if token is not an interface, or is either final or primitive
     */
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkClass(token);
        Path p = root.toAbsolutePath();
        try {
            pathOut = token.getPackage() != null ? (p.toString() + File.separator + token.getPackage().getName().replace(".", File.separator)) : p.toString();
            p = Paths.get(pathOut + File.separator + token.getSimpleName() + FILE_NAME_SUFFIX);
            Files.createDirectories(p.getParent());
            try (Writer out = new FilterWriter(new FileWriter(p.toFile())) {
                @Override
                public void write(String str, int off, int len) throws IOException {
                    for (int i = off; i < off + len; i++) {
                        char c = str.charAt(i);
                        if (c >= 128) {
                            out.write("\\u" + String.format("%04X", (int) c));
                            System.out.println(c);
                        } else {
                            out.write(c);
                        }
                    }
                }
                @Override
                public void write(int c) throws IOException {
                    if (c >= 128) {
                        out.write("\\u" + String.format("%04X", (int) c));
                        System.out.println(c);
                    } else {
                        out.write(c);
                    }
                }
            }) {
                this.classToImplement = token;
                this.out = out;
                writeClass();
                out.close();
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Writes class to file
     *
     * @throws IOException On file I/O error
     */
    private void writeClass() throws IOException {
        List<Method> methods = Arrays.asList(classToImplement.getMethods());
        if (classToImplement.getPackage() != null) {
            out.write("package " + classToImplement.getPackage().getName() + ";" + LINE_SEPARATOR);
        }
        out.write(LINE_SEPARATOR);
        out.write("public class " + classToImplement.getSimpleName() + CLASS_NAME_SUFFIX + " implements " + classToImplement.getCanonicalName() + " {" + LINE_SEPARATOR);
        for (Method method : methods) {
            writeMethod(method);
        }
        out.write("}");
    }


    /**
     * Writes target method to file with default return value
     *
     * @param method method to write
     * @throws IOException On file I/O error
     * @see java.lang.reflect.Method
     */
    private void writeMethod(Method method) throws IOException {
        int modifiers = method.getModifiers();
        if (Modifier.isFinal(modifiers) || Modifier.isNative(modifiers) || Modifier.isPrivate(modifiers) || !Modifier.isAbstract(modifiers)) {
            return;
        }
        modifiers ^= Modifier.ABSTRACT;
        if (Modifier.isTransient(modifiers)) {
            modifiers ^= Modifier.TRANSIENT;
        }
        out.write(LINE_SEPARATOR + "    " + "@Override");
        Annotation[] an = method.getAnnotations();
        writeAnnotations(an);
        out.write(LINE_SEPARATOR + "    ");
        Class<?>[] args = method.getParameterTypes();
        out.write(Modifier.toString(modifiers));
        Class<?> returnType = method.getReturnType();
        out.write(" " + returnType.getCanonicalName());
        out.write(" " + method.getName());
        writeArgs(args);
        writeExceptions(method.getExceptionTypes());
        out.write("{" + LINE_SEPARATOR);
        out.write("    " + "    " + "return ");
        if (returnType.isPrimitive()) {
            if (returnType.equals(boolean.class)) {
                out.write("false");
            } else if (!returnType.equals(void.class)) {
                out.write("0");
            }
        } else {
            out.write("null");
        }
        out.write(";" + LINE_SEPARATOR);
        out.write("    " + "}" + LINE_SEPARATOR);
    }

    /**
     * Writes arguments of method
     *
     * @param args array of types of arguments
     * @throws IOException on file I/O error
     */
    private void writeArgs(Class<?>[] args) throws IOException {
        out.write("(");
        for (int i = 0; i < args.length; ++i) {
            out.write(args[i].getCanonicalName() + " arg" + Integer.toString(i));
            if (i < args.length - 1) {
                out.write(", ");
            }
        }
        out.write(")");
    }

    /**
     * Writes annotations of method
     *
     * @param arr array of annotations
     * @throws IOException on file I/O error
     * @see java.lang.annotation.Annotation
     */
    private void writeAnnotations(Annotation[] arr) throws IOException {
        for (Annotation a : arr) {
            out.write(LINE_SEPARATOR);
            out.write(a.toString());
        }
        out.write(LINE_SEPARATOR);
    }

    /**
     * Writes exceptions of method
     *
     * @param exceptions array of types of exceptions
     * @throws IOException on file I/O error
     */
    private void writeExceptions(Class<?>[] exceptions) throws IOException {
        if (exceptions.length == 0) {
            return;
        }
        out.write(" throws ");
        for (int i = 0; i < exceptions.length; ++i) {
            out.write(exceptions[i].getCanonicalName());
            if (i < exceptions.length - 1) {
                out.write(", ");
            }
        }
    }

    /**
     * Main function of Implementor. If "-jar" parameter is present, this method generates jar file, otherwise - javafile
     *
     * @param args "-jar" class outputFile.jar or class outputFile.java
     */
    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Invalid arguments");
            return;
        }
        try {
            Implementor implementor = new Implementor();
            boolean isJar = "-jar".equals(args[0]);
            String name = args[(isJar ? 1 : 0)];
            String filename = args[(isJar ? 2 : 1)];
            Path root = Paths.get(filename);
            Class<?> token = Class.forName(name);
            if (isJar) {
                implementor.implementJar(token, root);
            } else {
                implementor.implement(token, root);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found: " + e.getMessage());
        } catch (ImplerException e) {
            System.err.println("Implementation error: " + e.getMessage());
        }
    }

    /**
     * Implements class, compiles and creates jar file for this class
     *
     * @param token class to implement
     * @param jar   output path
     * @throws ImplerException if token is not an interface, or is either final or primitive
     */
    public void implementJar(Class<?> token, Path jar) throws ImplerException {
        //System.out.println(token.getName());
        try {
            //checkClass(token);
            implement(token, Paths.get(TMP_DIR));
            String fileAbsPath = pathOut;
            if (token.getPackage() != null) {
                fileAbsPath += (token.getPackage().getName().isEmpty()) ? "" : "/";
            }
            fileAbsPath += token.getSimpleName();
            compileFile(fileAbsPath + "Impl.java");
            makeJar(jar, fileAbsPath + "Impl.class");
            recDelete(new File(TMP_DIR));
        } catch (IOException e) {
            System.err.println("Error occurred while working with files: ");
        } finally {
            if (Files.exists(Paths.get(TMP_DIR))) {
                recDelete(new File(TMP_DIR));
            }
        }
    }

    /**
     * Compiles class with default java compiler
     *
     * @param path path to class for compile
     * @throws IOException on I/O error
     */
    private static void compileFile(String path) throws IOException {
        File f = new File(path);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"));
        File[] files = {f};
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(files));
        compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();
        fileManager.close();
    }

    /**
     * Makes jar file
     *
     * @param jarPath   path for new jar file
     * @param classPath path to class in jar file
     * @throws IOException on I/O error
     */
    private static void makeJar(Path jarPath, String classPath) throws IOException {
        if (!Files.exists(jarPath)) {
            if (jarPath.getParent() != null) {
                Files.createDirectories(jarPath.getParent());
            }
            Files.createFile(jarPath);
        }
        String newClassPath = classPath.substring(classPath.indexOf(TMP_DIR) + 4);
        try (FileOutputStream fileOut = new FileOutputStream(jarPath.toFile())) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, newClassPath);
            try (JarOutputStream jarOut = new JarOutputStream(fileOut, manifest)) {
                jarOut.putNextEntry(new ZipEntry(newClassPath));
                Files.copy(Paths.get(classPath), jarOut);
                jarOut.closeEntry();
            }
        }
    }

    /**
     * Removes files and dirs recursively
     *
     * @param file folder or dir to delete
     */
    private void recDelete(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    recDelete(f);
                }
            }
        }
        file.delete();
    }
}