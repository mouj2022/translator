package com.note.config;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Properties props = new Properties();
    private static String inputPath;
    private static String outputPath;

    static {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            props.load(is);
            // 默认路径（可被命令行参数覆盖）
            inputPath = props.getProperty("default.input.path", "input");
            outputPath = props.getProperty("default.output.path", "output");
        } catch (Exception e) {
            throw new RuntimeException("加载配置文件失败", e);
        }
    }

    public static String getInputPath() {
        return inputPath;
    }

    public static void setInputPath(String path) {
        inputPath = path;
    }

    public static String getOutputPath() {
        return outputPath;
    }

    public static void setOutputPath(String path) {
        outputPath = path;
    }
}