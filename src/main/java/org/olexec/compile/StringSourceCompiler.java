package org.olexec.compile;

import org.olexec.controller.RunCodeController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringSourceCompiler {
    private static Map<String, JavaFileObject> fileObjectMap = new ConcurrentHashMap<>();
    private static Logger logger = LoggerFactory.getLogger(RunCodeController.class);
    /**
     * 使用 Pattern 预编译功能
     */
    private static Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s*");

    public static byte[] compile(String source, DiagnosticCollector<JavaFileObject> compileCollector) {
        // 获得运行环境下的 Java 编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileManager javaFileManager =
                new TmpJavaFileManager(compiler.getStandardFileManager(compileCollector, null, null));

        // 从源码字符串中匹配类名
        Matcher matcher = CLASS_PATTERN.matcher(source);
        String className;
        if (matcher.find()) {
            className = matcher.group(1);
        } else {
            // TODO: 21/07/2019  直接抛出异常 返回用户 错误界面 应该 转为输出 放到 结果中
            throw new IllegalArgumentException("No valid class");
        }

/*      把源码字符串构造成JavaFileObject，供编译使用
        JavaFileObject
        File abstraction for tools operating on Java™ programming language source and class files.
        在Java™编程语言源和类文件上运行的工具的文件抽象。
        https://docs.oracle.com/javase/7/docs/api/javax/tools/JavaFileObject.html
        */
        JavaFileObject sourceJavaFileObject = new TmpJavaFileObject(className, source);

        Boolean result = compiler.getTask(null, javaFileManager, compileCollector,
                null, null, Arrays.asList(sourceJavaFileObject)).call();

        JavaFileObject bytesJavaFileObject = fileObjectMap.get(className);
        if (result && bytesJavaFileObject != null) {
            return ((TmpJavaFileObject) bytesJavaFileObject).getCompiledBytes();
        }
        return null;
    }

    /**
     * 管理JavaFileObject对象的工具
     */
    public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        protected TmpJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
            JavaFileObject javaFileObject = fileObjectMap.get(className);
            if (javaFileObject == null) {
                return super.getJavaFileForInput(location, className, kind);
            }
            return javaFileObject;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            JavaFileObject javaFileObject = new TmpJavaFileObject(className, kind);
            fileObjectMap.put(className, javaFileObject);
            return javaFileObject;
        }
    }

    /**
     * 用来封装表示源码与字节码的对象
     * 继承 JavaFileObject ，SimpleJavaFileObject
     * SimpleJavaFileObject为JavaFileObject中的大多数方法提供简单的实现。 此类设计为子类，并用作JavaFileObject实现的基础。 只要遵守JavaFileObject的常规协定，子类就可以覆盖此类的任何方法的实现和规范。
     * https://sky5454.github.io/jdk11api_CN/java.compiler/javax/tools/SimpleJavaFileObject.html
     */
    public static class TmpJavaFileObject extends SimpleJavaFileObject {
        private String source;
        private ByteArrayOutputStream outputStream;

        /**
         * 构造用来存储源代码的JavaFileObject
         * 需要传入源码source，然后调用父类的构造方法创建kind = Kind.SOURCE的JavaFileObject对象
         *
         * Kind.SOURCE.extension : ".java"
         */
        public TmpJavaFileObject(String name, String source) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        /**
         * 构造用来存储字节码的JavaFileObject
         * 需要传入kind，即我们想要构建一个存储什么类型文件的JavaFileObject
         */
        public TmpJavaFileObject(String name, Kind kind) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), kind);
            this.source = null;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            if (source == null) {
                throw new IllegalArgumentException("source == null");
            }
            return source;
        }

        @Override
        public OutputStream openOutputStream() {
            outputStream = new ByteArrayOutputStream();
            return outputStream;
        }

        public byte[] getCompiledBytes() {
            return outputStream.toByteArray();
        }
    }
}
