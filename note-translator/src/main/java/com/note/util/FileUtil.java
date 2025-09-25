package com.note.util;

import java.io.File;

public class FileUtil {
    /**
     * 确保文件夹存在，不存在则创建
     */
    public static void ensureFolderExists(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                throw new RuntimeException("无法创建文件夹: " + path);
            }
        }
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public static String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        return lastDotIndex == -1 ? name : name.substring(0, lastDotIndex);
    }

    /**
     * 判断文件是否为JSON
     */
    public static boolean isJsonFile(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".json");
    }
}