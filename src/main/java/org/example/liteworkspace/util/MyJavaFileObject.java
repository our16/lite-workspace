package org.example.liteworkspace.util;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自定义 JavaFileObject，用于从磁盘上的 .java 文件加载源码内容，
 * 以便 JavaCompiler 可以编译它。
 * 通常可以不用这个，直接用 fileManager.getJavaFileObjectsFromFiles() 更简单。
 */
public class MyJavaFileObject extends SimpleJavaFileObject {

    private final Path path;  // 源码文件路径

    /**
     * 构造方法
     *
     * @param path      .java 文件的 Path 对象
     * @param fileName  文件名（通常用全限定类名 + ".java" 形式，但这里只是兼容）
     */
    public MyJavaFileObject(Path path, String fileName) {
        // 调用父类构造，指定这是一个 SOURCE 类型的 JavaFileObject，
        // URI 格式为：file:///path/to/MyClass.java
        super(URI.create("file:///" + path.toAbsolutePath().toString().replace("\\", "/")), Kind.SOURCE);
        this.path = path;
    }

    /**
     * 重写 getCharContent 方法，返回源码内容（供编译器读取）
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        // 从磁盘文件读取源码内容
        return Files.readString(path);
    }
}
