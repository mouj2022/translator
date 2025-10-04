package com.note;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.note.util.FileUtil;
import com.note.util.NoteLogUtil;
import com.note.util.NoteLogUtil.NoteType;

/**
 * 谱面反向转译核心类（已编译 → 开发态，微调参数集中配置）
 */
public class NoteTranslator {
    // ===================== 【反向编译-开发态微调参数配置区】（直接修改此处即可调整） =====================
    // 时间微调（单位：拍）：正数=延后，负数=提前，默认0（无偏移）
    private static final double DEFAULT_REVERSE_VERTICAL_OFFSET = 0.0;
    // 轨道微调（单位：轨）：正数=右移，负数=左移，默认0（无偏移）
    private static final int DEFAULT_REVERSE_LANE_OFFSET = 3;

    // 当前生效的微调值（可通过set方法动态覆盖默认值）
    private double reverseVerticalOffset = DEFAULT_REVERSE_VERTICAL_OFFSET;
    private int reverseLaneOffset = DEFAULT_REVERSE_LANE_OFFSET;

    // ===================== 核心依赖 =====================
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final NoteLogUtil noteLog;

    // ------------------------------ 构造器：初始化（加载默认微调参数） ------------------------------
    public NoteTranslator() {
        this.noteLog = new NoteLogUtil();
        logOffsetConfig(); // 打印当前微调配置
    }

    // ------------------------------ 打印当前微调参数（方便确认） ------------------------------
    private void logOffsetConfig() {
        noteLog.logTranslateStart("=== 反向编译-开发态微调参数 ===");
        noteLog.logTranslateStart("时间微调（拍）：" + reverseVerticalOffset + "（默认：" + DEFAULT_REVERSE_VERTICAL_OFFSET + "）");
        noteLog.logTranslateStart("轨道微调（轨）：" + reverseLaneOffset + "（默认：" + DEFAULT_REVERSE_LANE_OFFSET + "）");
        noteLog.logTranslateStart("=============================================");
    }

    // ------------------------------ 【可选】动态调整微调参数（也可直接改配置区默认值） ------------------------------
    /**
     * 动态设置开发态谱面的时间和轨道微调
     */
    public void setReverseOffset(double verticalOffset, int laneOffset) {
        this.reverseVerticalOffset = verticalOffset;
        this.reverseLaneOffset = laneOffset;
        noteLog.logTranslateStart("开发态微调已更新 | 时间：" + verticalOffset + "拍 | 轨道：" + laneOffset + "轨");
    }

    // ------------------------------ 核心：单文件反向编译（已→开 + 微调） ------------------------------
    public void translateSingleFile(File inputFile, String outputDir) throws Exception {
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
            noteLog.logTranslateStart("输出文件夹创建：" + outputFolder.getAbsolutePath());
        }

        noteLog.createNewLogFile();
        noteLog.logTranslateStart("反向编译开始 | 输入：" + inputFile.getAbsolutePath());

        if (!FileUtil.isJsonFile(inputFile)) {
            String error = "跳过非JSON：" + inputFile.getName();
            noteLog.logError(error, null);
            System.err.println(error);
            return;
        }

        JsonNode root = OBJECT_MAPPER.readTree(inputFile);
        JsonNode entities = root.get("entities");
        if (entities == null || !entities.isArray()) {
            String error = "格式错误：缺少\"entities\"数组 | " + inputFile.getName();
            noteLog.logError(error, null);
            throw new RuntimeException(error);
        }

        List<JsonNode> originalNotes = new ArrayList<>();
        entities.forEach(originalNotes::add);
        int totalNoteCount = originalNotes.size();
        noteLog.logTranslateStart("读取音符数：" + totalNoteCount + " | 文件：" + inputFile.getName());

        // 音符name映射（双押、滑键关联用）
        Map<String, JsonNode> noteMap = new HashMap<>();
        originalNotes.forEach(note -> {
            if (note.has("name")) {
                noteMap.put(note.get("name").asText(), note);
            }
        });

        List<JsonNode> slideRelated = new ArrayList<>();
        List<ObjectNode> translated = new ArrayList<>();

        for (int i = 0; i < totalNoteCount; i++) {
            JsonNode original = originalNotes.get(i);
            String archetype = original.get("archetype").asText();

            if (archetype.equals("Initialization") || archetype.equals("Stage")) {
                // 记录元数据日志（保持日志完整，不遗漏信息）
                noteLog.logNoteTranslated(
                        i + 1, totalNoteCount, 
                        NoteType.OTHER, // 元数据标记为“其他类型”
                        archetype, // 名称用元数据类型（如Initialization）
                        getBaseBeat(original), // 基础beat（元数据无beat时返回0，不影响）
                        getBaseLane(original), // 基础lane（元数据无lane时返回0，不影响）
                        getBaseBeat(original) + reverseVerticalOffset, // 微调后beat
                        getBaseLane(original) + reverseLaneOffset, // 微调后lane
                        noteLog.getRefs(original) // 关联信息（元数据通常为空）
        );
        continue; // 跳过后续所有处理，不生成开发态节点
    }

            String noteName = original.has("name") ? original.get("name").asText() : "";
            NoteType noteType = noteLog.getNoteType(archetype);
            String refs = noteLog.getRefs(original);

            // 提取已编译原始值 + 应用开发态微调
            double baseBeat = getBaseBeat(original);
            int baseLane = getBaseLane(original);
            double finalBeat = baseBeat + reverseVerticalOffset;
            int finalLane = baseLane + reverseLaneOffset;

            // ===================== 分类型转换为开发态格式 =====================
            // 1. BPM变更 → 开发态BPM
            if (archetype.equals("#BPM_CHANGE")) {
                JsonNode data = original.get("data");
                double beat = getFieldValueByName(data, "#BEAT");
                double bpm = getFieldValueByName(data, "#BPM");
    
                // 防止无效BPM（比如BPM=0或负数）
                if (bpm <= 0 || beat < 0) {
                    noteLog.logError("BPM无效！BPM=" + bpm + " | 节拍=" + beat + " | 索引：" + i, null);
                    continue; // 跳过这个无效BPM
                }
    
                ObjectNode bpmNote = OBJECT_MAPPER.createObjectNode();
                bpmNote.put("type", "BPM");
                bpmNote.put("bpm", bpm);
                bpmNote.put("beat", beat + reverseVerticalOffset); // 应用时间微调
                translated.add(bpmNote);

                noteLog.logNoteTranslated(
                        i + 1, totalNoteCount, NoteType.OTHER, "BPM",
                        beat, 0, 
                        beat + reverseVerticalOffset, 0, 
                        noteLog.getRefs(original)
                );
                continue;
            }

            // 2. 单键（Tap/Flick → Single）
            if (archetype.equals("TapNote") || archetype.equals("FlickNote")) {
                ObjectNode single = OBJECT_MAPPER.createObjectNode();
                single.put("type", "Single");
                if (archetype.equals("FlickNote")) single.put("flick", true);
                single.put("beat", finalBeat);
                single.put("lane", finalLane);
                translated.add(single);

                noteLog.logNoteTranslated(
                        i + 1, totalNoteCount,
                        archetype.equals("TapNote") ? NoteType.BLUE : NoteType.PINK,
                        noteName, baseBeat, baseLane, finalBeat, finalLane, refs
                );
                continue;
            }

            // 3. 双押（SimLine → 两个Single）
            if (archetype.equals("SimLine")) {
                handleSimLine(original, noteMap, translated, noteLog, i, totalNoteCount);
                continue;
            }

            // 4. 滑键（所有Slide子元素 → 后续整合为Slide）
            if (archetype.startsWith("Slide") || archetype.contains("Connector")) {
                slideRelated.add(original);
                noteLog.logNoteTranslated(
                        i + 1, totalNoteCount, noteType, noteName,
                        baseBeat, baseLane, finalBeat, finalLane, refs
                );
                continue;
            }

            // 5. 其他类型（如IgnoredNote）
            ObjectNode defaultNote = OBJECT_MAPPER.createObjectNode();
            defaultNote.put("type", mapToDevType(archetype));
            defaultNote.put("beat", finalBeat);
            defaultNote.put("lane", finalLane);
            if (!noteName.isEmpty()) defaultNote.put("name", noteName);
            if (!refs.isEmpty()) {
                for (String pair : refs.split(",")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) defaultNote.put(kv[0] + "Ref", kv[1]);
                }
            }
            translated.add(defaultNote);

            noteLog.logNoteTranslated(
                    i + 1, totalNoteCount, noteType, noteName,
                    baseBeat, baseLane, finalBeat, finalLane, refs
            );
        }

        // 整合滑键为开发态Slide（带connections数组）
        List<ObjectNode> slideNotes = generateSlideObjects(slideRelated, noteMap, noteLog);
        translated.addAll(slideNotes);

        // 输出开发态谱面
        File outputFile = new File(outputDir + File.separator + inputFile.getName());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputFile, translated);

        noteLog.logTranslateComplete(
                translated.size(), "反向编译完成 | 输出：" + outputFile.getAbsolutePath()
        );
        System.out.printf("[完成] 反向编译 | %s → %s | 音符数：%d%n",
                inputFile.getName(), outputFile.getName(), translated.size());
    }

    // ------------------------------ 辅助：提取已编译谱面的基础beat ------------------------------
    private double getBaseBeat(JsonNode compiledNote) {
        JsonNode data = compiledNote.get("data");
        return getFieldValueByName(data, "#BEAT");
    }

    // ------------------------------ 辅助：提取已编译谱面的基础lane ------------------------------
    private int getBaseLane(JsonNode compiledNote) {
        JsonNode data = compiledNote.get("data");
        double laneValue = getFieldValueByName(data, "lane");
        return (int) laneValue; // lane是整数，转一下
    }

    // ------------------------------ 辅助：处理双押（SimLine → 两个Single） ------------------------------
    private void handleSimLine(JsonNode simLine, Map<String, JsonNode> noteMap, 
                              List<ObjectNode> translated, NoteLogUtil noteLog,
                              int index, int total) {
        JsonNode data = simLine.get("data");
        if (!data.has("a") || !data.has("b")) {
            noteLog.logError("SimLine缺少a/b关联 | 索引：" + index, null);
            return;
        }
        String leftRef = data.get("a").get("ref").asText();
        String rightRef = data.get("b").get("ref").asText();

        JsonNode leftNote = noteMap.get(leftRef);
        JsonNode rightNote = noteMap.get(rightRef);
        if (leftNote == null || rightNote == null) {
            noteLog.logError("双押关联音符不存在 | 左=" + leftRef + " 右=" + rightRef, null);
            return;
        }

        // 提取基础值 + 应用开发态微调
        double leftBase = getBaseBeat(leftNote);
        int leftBaseLane = getBaseLane(leftNote);
        double rightBase = getBaseBeat(rightNote);
        int rightBaseLane = getBaseLane(rightNote);
        double leftFinal = leftBase + reverseVerticalOffset;
        int leftFinalLane = leftBaseLane + reverseLaneOffset;
        double rightFinal = rightBase + reverseVerticalOffset;
        int rightFinalLane = rightBaseLane + reverseLaneOffset;

        // 生成左单键
        ObjectNode leftSingle = OBJECT_MAPPER.createObjectNode();
        leftSingle.put("type", "Single");
        if (leftNote.get("archetype").asText().equals("FlickNote")) leftSingle.put("flick", true);
        leftSingle.put("beat", leftFinal);
        leftSingle.put("lane", leftFinalLane);
        translated.add(leftSingle);

        // 生成右单键
        ObjectNode rightSingle = OBJECT_MAPPER.createObjectNode();
        rightSingle.put("type", "Single");
        if (rightNote.get("archetype").asText().equals("FlickNote")) rightSingle.put("flick", true);
        rightSingle.put("beat", rightFinal);
        rightSingle.put("lane", rightFinalLane);
        translated.add(rightSingle);

        // 日志记录
        noteLog.logNoteTranslated(
                index + 1, total, NoteType.OTHER, "双押左",
                leftBase, leftBaseLane, leftFinal, leftFinalLane, "关联右=" + rightRef
        );
        noteLog.logNoteTranslated(
                index + 1, total, NoteType.OTHER, "双押右",
                rightBase, rightBaseLane, rightFinal, rightFinalLane, "关联左=" + leftRef
        );
    }

    // ------------------------------ 辅助：整合滑键为开发态Slide（带connections） ------------------------------
    private List<ObjectNode> generateSlideObjects(List<JsonNode> slideRelated, 
                                                 Map<String, JsonNode> noteMap, 
                                                 NoteLogUtil noteLog) {
        List<ObjectNode> slideNotes = new ArrayList<>();
        // 按起始节点分组
        Map<String, List<JsonNode>> slideGroups = new HashMap<>();
        for (JsonNode note : slideRelated) {
            String name = note.has("name") ? note.get("name").asText() : "";
            JsonNode data = note.get("data");
            String firstRef = (data != null && data.has("first")) ? data.get("first").get("ref").asText() : "";
            String groupKey = firstRef.isEmpty() ? name : firstRef;
            slideGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(note);
        }

        // 处理每个Slide组
        for (Map.Entry<String, List<JsonNode>> entry : slideGroups.entrySet()) {
            List<JsonNode> slideNodes = entry.getValue();
            // 按beat排序（保证滑动顺序）
            slideNodes.sort(Comparator.comparingDouble(this::getBaseBeat));

            // 构建connections（开发态 + 微调）
            ArrayNode connections = OBJECT_MAPPER.createArrayNode();
            for (JsonNode node : slideNodes) {
                ObjectNode conn = OBJECT_MAPPER.createObjectNode();
                conn.put("beat", getBaseBeat(node) + reverseVerticalOffset);
                conn.put("lane", getBaseLane(node) + reverseLaneOffset);
                connections.add(conn);
            }

            // 生成开发态Slide
            ObjectNode slide = OBJECT_MAPPER.createObjectNode();
            slide.put("type", "Slide");
            slide.set("connections", connections);
            slideNotes.add(slide);

            // 日志记录
            noteLog.logTranslateStart("生成Slide | 起始beat：" + connections.get(0).get("beat").asDouble() 
                                     + " | 节点数：" + connections.size());
        }
        return slideNotes;
    }

    // ------------------------------ 辅助：已编译archetype → 开发态type映射 ------------------------------
    private String mapToDevType(String archetype) {
        return switch (archetype) {
            case "TapNote", "FlickNote" -> "Single";
            case "SlideStartNote", "SlideTickNote", "SlideEndNote", 
                 "StraightSlideConnector", "CurvedSlideConnector" -> "Slide";
            case "SimLine" -> "SimLine";
            case "IgnoredNote" -> "Ignored";
            case "#BPM_CHANGE" -> "BPM";
            // 新增：识别谱面元数据类型，标记为"Meta"（或直接跳过）
            case "Initialization", "Stage" -> "Meta"; 
            default -> {
                noteLog.logError("未定义开发态类型 | archetype：" + archetype, null);
                yield archetype;
            }
        };
    }

    /**
 * 从data数组里，根据name找对应的value（核心：遍历数组匹配name）
 */
    private double getFieldValueByName(JsonNode dataArray, String targetName) {
        if (dataArray == null || !dataArray.isArray()) {
            return 0.0;
        }
        for (JsonNode item : dataArray) {
            if (item.has("name") && item.get("name").asText().equals(targetName) && item.has("value")) {
                return item.get("value").asDouble();
            }
        }
        return 0.0;
    }

    // ------------------------------ 批量反向编译（复用单文件逻辑） ------------------------------
    public void translateBatchFiles(String inputDir, String outputDir) throws Exception {
        File inputFolder = new File(inputDir);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            String error = "输入路径无效：" + inputDir;
            noteLog.logError(error, null);
            throw new RuntimeException(error);
        }

        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) outputFolder.mkdirs();

        noteLog.createNewLogFile();
        noteLog.logTranslateStart("批量反向编译开始 | 输入：" + inputFolder.getAbsolutePath());

        File[] jsonFiles = inputFolder.listFiles(file -> file.isFile() && FileUtil.isJsonFile(file));
        if (jsonFiles == null || jsonFiles.length == 0) {
            String warn = "无JSON文件：" + inputDir;
            noteLog.logError(warn, null);
            System.out.println(warn);
            return;
        }

        int totalFile = jsonFiles.length;
        int success = 0, fail = 0, totalNote = 0;

        noteLog.logTranslateStart("待处理文件数：" + totalFile);
        System.out.printf("批量反向编译开始 | 共%d个文件%n", totalFile);

        for (int i = 0; i < totalFile; i++) {
            File file = jsonFiles[i];
            System.out.printf("进度：%d/%d | 处理：%s...%n", i + 1, totalFile, file.getName());

            try {
                translateSingleFile(file, outputDir);
                success++;
                JsonNode root = OBJECT_MAPPER.readTree(file);
                JsonNode entities = root.get("entities");
                if (entities != null && entities.isArray()) totalNote += entities.size();
            } catch (Exception e) {
                fail++;
                noteLog.logError("文件失败：" + file.getName() + " | 原因：" + e.getMessage(), e);
                System.err.println("失败：" + file.getName() + " → " + e.getMessage());
            }
        }

        noteLog.logTranslateComplete(
                totalNote, "批量反向编译完成 | 成功：" + success + " 失败：" + fail + " 总音符：" + totalNote
        );
        System.out.printf("批量结束 | 成功：%d 失败：%d 总音符：%d%n", success, fail, totalNote);
        System.out.println("日志路径：logs/（时间命名）");
    }
}