package org.example.liteworkspace.cache;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CacheVersionChecker {

    private final Map<String, Long> fileTimestamps = new HashMap<>();

    /**
     * 注册文件的最后修改时间
     */
    public void registerFile(VirtualFile file) {
        fileTimestamps.put(file.getPath(), file.getTimeStamp());
    }

    /**
     * 判断文件是否被修改
     */
    public boolean isFileModified(VirtualFile file) {
        long previous = fileTimestamps.getOrDefault(file.getPath(), -1L);
        return file.getTimeStamp() != previous;
    }

    /**
     * 从磁盘路径注册文件
     */
    public void registerFile(File file) {
        if (!file.exists()) return;
        fileTimestamps.put(file.getAbsolutePath(), file.lastModified());
    }

    /**
     * 磁盘文件是否被修改
     */
    public boolean isFileModified(File file) {
        long previous = fileTimestamps.getOrDefault(file.getAbsolutePath(), -1L);
        return file.exists() && file.lastModified() != previous;
    }

    /**
     * 注册一批文件
     */
    public void registerFiles(Iterable<File> files) {
        for (File file : files) {
            registerFile(file);
        }
    }

    /**
     * 注册一批虚拟文件
     */
    public void registerVirtualFiles(Iterable<VirtualFile> files) {
        for (VirtualFile file : files) {
            registerFile(file);
        }
    }

    public Map<String, Long> snapshot() {
        return new HashMap<>(fileTimestamps);
    }

    public void restore(Map<String, Long> snapshot) {
        fileTimestamps.clear();
        fileTimestamps.putAll(snapshot);
    }
}
