package com.note;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.note.util.FileUtil;
import com.note.util.LogUtil;

public class NoteTranslator {
    private static final Logger logger = LogUtil.getLogger(NoteTranslator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 转换单个JSON文件
     */
    public void translateFile(File inputFile, String outputDir) throws Exception {
        if (!FileUtil.isJsonFile(inputFile)) {
            logger.error("跳过非JSON文件: {}", inputFile.getAbsolutePath());
            return;
        }

        // 读取原始数据
        JsonNode rootNode = objectMapper.readTree(inputFile);
        JsonNode notesNode = rootNode.get("entities");
        if (notesNode == null || !notesNode.isArray()) {
            throw new RuntimeException("文件格式错误，缺少\"entities\"数组: " + inputFile.getName());
        }

        // 转换音符
        List<JsonNode> originalNotes = new ArrayList<>();
        notesNode.forEach(originalNotes::add);
        List<ObjectNode> translatedNotes = translateNotes(originalNotes);

        // 输出到文件
        String outputFileName = inputFile.getName();
        File outputFile = new File(outputDir + File.separator + outputFileName);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, translatedNotes);
        logger.info("转换完成: {} -> {}", inputFile.getName(), outputFile.getAbsolutePath());
    }

    /**
     * 批量转换文件夹中的所有JSON文件
     */
    public void translateFolder(String inputDir, String outputDir) throws Exception {
        File inputFolder = new File(inputDir);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            throw new RuntimeException("输入文件夹不存在或不是目录: " + inputDir);
        }

        // 确保输出文件夹存在
        FileUtil.ensureFolderExists(outputDir);

        // 遍历所有JSON文件
        File[] files = inputFolder.listFiles();
        if (files == null || files.length == 0) {
            logger.info("输入文件夹中没有文件: {}", inputDir);
            return;
        }

        for (File file : files) {
            try {
                translateFile(file, outputDir);
            } catch (Exception e) {
                logger.error("处理文件失败: " + file.getName(), e);
            }
        }
    }

    /**
     * 转换单个音符列表
     */
    private List<ObjectNode> translateNotes(List<JsonNode> originalNotes) {
        List<ObjectNode> result = new ArrayList<>();
        for (JsonNode note : originalNotes) {
            ObjectNode translated = objectMapper.createObjectNode();

            // 转换类型
            String archetype = note.get("archetype").asText();
            translated.put("type", getTranslatedType(archetype));

            // 保留名称
            if (note.has("name")) {
                translated.put("name", note.get("name").asText());
            }

            // 处理数据字段
            JsonNode dataNode = note.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode data : dataNode) {
                    String name = data.get("name").asText();
                    if ("#BEAT".equals(name)) {
                        translated.put("beat", data.get("value").asDouble());
                    } else if ("lane".equals(name)) {
                        translated.put("lane", data.get("value").asInt());
                    } else if (data.has("ref")) {
                        translated.put(name + "Ref", data.get("ref").asText());
                    } else if (data.has("value")) {
                        translated.put(name, data.get("value").asInt());
                    }
                }
            }

            result.add(translated);
        }
        return result;
    }

    /**
     * 转换音符类型
     */
    private String getTranslatedType(String archetype) {
        switch (archetype) {
            case "TapNote": return "Single";
            case "FlickNote": return "Flick";
            case "SlideStartNote": return "SlideStart";
            case "SlideTickNote": return "SlideTick";
            case "SlideEndNote": return "SlideEnd";
            case "SlideEndFlickNote": return "SlideEndFlick";
            case "StraightSlideConnector": return "StraightSlideConnector";
            case "CurvedSlideConnector": return "CurvedSlideConnector";
            case "IgnoredNote": return "Ignored";
            default: return archetype;
        }
    }
}