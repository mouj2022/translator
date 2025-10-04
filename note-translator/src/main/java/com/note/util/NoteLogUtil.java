package com.note.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * 谱面转译专用日志工具（每次转译生成独立时间命名日志）
 * 功能：1. 每转译1个文件/文件夹生成1个日志文档 2. 日志名含时间戳 3. 音符信息代号化缩写
 */
public class NoteLogUtil {

    // 音符类型枚举（代号化，精简日志篇幅）
    public enum NoteType {
        BLUE("T", "蓝键(TapNote)"),
        PINK("F", "粉键(FlickNote)"),
        SLIDE("S", "滑键(Slide系列)"),
        CONNECTOR("C", "滑键连接器"),
        LONG("L", "长音符(LongNote)"),
        EFFECT("E", "特效音符(EffectNote)"),
        OTHER("O", "其他/非音符类型");

        public final String code;
        public final String desc;

        NoteType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    private LoggerContext loggerContext;
    private Logger noteLogger;
    private String currentLogFilePath;

    public NoteLogUtil() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        createNewLogFile(); // 初始化时创建首个日志
    }

    /**
     * 创建新的时间命名日志文件
     */
    public void createNewLogFile() {
        // 生成带时间戳的日志名（格式：note_translate_yyyyMMddHHmmssSSS.log）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC+8")); // 东八区时间
        String logFileName = "note_translate_" + sdf.format(new Date()) + ".log";
        String logDir = "logs";
        new File(logDir).mkdirs(); // 确保logs文件夹存在
        currentLogFilePath = logDir + "/" + logFileName;

        // 配置Logback输出到新文件
        noteLogger = loggerContext.getLogger("NoteTranslator");
        noteLogger.detachAndStopAllAppenders(); // 移除旧的Appender

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setFile(currentLogFilePath);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%level] %msg%n"); // 日志格式：时间 [级别] 内容
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();
        noteLogger.addAppender(fileAppender);
        noteLogger.setLevel(ch.qos.logback.classic.Level.INFO);
        noteLogger.setAdditive(false); // 避免重复输出到根日志

        logTranslateStart("==================================== 新日志文档创建 ====================================");
        logTranslateStart("日志文档路径：" + currentLogFilePath);
        logTranslateStart("创建时间：" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "（UTC+8）");
        logTranslateStart("=======================================================================================");
    }

    /**
     * 记录转译开始（含目标信息）
     */
    public void logTranslateStart(String targetInfo) {
        noteLogger.info("==================================== 转译开始 ====================================");
        noteLogger.info(targetInfo);
        noteLogger.info("开始时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "（UTC+8）");
        noteLogger.info("=======================================================================================");
    }

    /**
     * 记录转译完成（含统计信息）
     */
    public void logTranslateComplete(int totalNoteCount, String completeInfo) {
        noteLogger.info("==================================== 转译完成 ====================================");
        noteLogger.info(completeInfo);
        noteLogger.info("总音符数：" + totalNoteCount);
        noteLogger.info("完成时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "（UTC+8）");
        noteLogger.info("=======================================================================================");
    }

    /**
     * 记录错误信息
     */
    public void logError(String errorMsg, Exception e) {
        noteLogger.error("[错误] " + errorMsg);
        if (e != null) {
            noteLogger.error("异常详情：", e);
        }
    }

    /**
     * 记录单音符转译信息（核心：过滤非音符元数据 + 显示节拍/轨道）
     */
    public void logNoteTranslated(
            int index, int total, NoteType type, String name,
            double originalBeat, int originalLane,
            double translatedBeat, int translatedLane, String refs) {
        // 过滤非音符元数据（如Initialization/Stage/BPM_CHANGE等，避免报“未定义音符”）
        if (type == NoteType.OTHER && (name.isEmpty() || refs.isEmpty())) {
            return; // 非音符且无有效信息，跳过日志
        }
        // 生成音符日志（含原始/偏移后的节拍、轨道）
        noteLogger.info("[{}/{}] 类型={} | 名称={} | 原始(B={},L={}) | 偏移(B={},L={}) | 关联={}",
                index, total, type.code, name,
                originalBeat, originalLane,
                translatedBeat, translatedLane,
                refs);
    }

    /**
     * 根据archetype获取音符类型（新增：识别更多类型 + 兼容非音符）
     */
    public NoteType getNoteType(String archetype) {
        if (archetype == null) return NoteType.OTHER;
        return switch (archetype) {
            case "TapNote" -> NoteType.BLUE;
            case "FlickNote" -> NoteType.PINK;
            case "SlideStartNote", "SlideTickNote", "SlideEndNote" -> NoteType.SLIDE;
            case "StraightSlideConnector", "CurvedSlideConnector" -> NoteType.CONNECTOR;
            case "LongNote" -> NoteType.LONG;
            case "EffectNote" -> NoteType.EFFECT;
            // 非音符元数据直接标记为OTHER
            case "Initialization", "Stage", "BPM_CHANGE" -> NoteType.OTHER;
            default -> NoteType.OTHER;
        };
    }

    /**
     * 辅助：从音符节点中提取原始节拍
     */
    public double getOriginalBeat(JsonNode noteNode) {
        return noteNode.has("beat") ? noteNode.get("beat").asDouble() : 0.0;
    }

    /**
     * 辅助：从音符节点中提取原始轨道
     */
    public int getOriginalLane(JsonNode noteNode) {
        return noteNode.has("lane") ? noteNode.get("lane").asInt() : 0;
    }

    /**
     * 辅助：从音符节点中提取关联信息（refs）
     */
    public String getRefs(JsonNode noteNode) {
        return noteNode.has("refs") ? noteNode.get("refs").asText() : "";
    }
}