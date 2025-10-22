package org.example.liteworkspace.util;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MapperMatcher {

    /**
     * 根据 SqlSessionConfig 的 mapperLocations 和已有 mapperXmlPaths 取交集
     * 支持 Spring 通配符：
     *   *     -> 匹配文件名任意字符，不跨目录
     *   **    -> 匹配任意目录
     *   classpath*: 或 classpath: 前缀都会去掉再匹配
     */
    public static List<String> matchMapperPaths(List<String> mapperXmlPaths, List<String> mapperLocations) {
        List<Pattern> patterns = new ArrayList<>();

        for (String loc : mapperLocations) {
            String path = loc.replaceFirst("^classpath\\*?:", ""); // 去掉前缀
            path = path.replace("\\", "/"); // Windows 支持

            // 转为正则
            path = path
                    .replace(".", "\\.")       // 转义 .
                    .replace("**/", "(.*/)?") // **/ => 任意目录
                    .replace("**", ".*")       // ** => 任意字符
                    .replace("*", "[^/]*");    // * => 不跨目录任意字符

            patterns.add(Pattern.compile(path));
        }

        return mapperXmlPaths.stream()
                .filter(xmlPath -> {
                    String cleanPath = xmlPath.replace("classpath*:", "")
                            .replace("classpath:", "")
                            .replace("\\", "/");
                    for (Pattern p : patterns) {
                        if (p.matcher(cleanPath).matches()) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
}

