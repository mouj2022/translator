package com.note;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;

import com.note.config.AppConfig;
import com.note.util.FileUtil;
import com.note.util.LogUtil;

public class Main {
    private static final Logger logger = LogUtil.getLogger(Main.class);
    private static final NoteTranslator translator = new NoteTranslator();

    public static void main(String[] args) {
        // 解析命令行参数
        Options options = createOptions();
        CommandLine cmd = parseCommandLine(args, options);

        if (cmd == null) {
            return; // 解析失败
        }

        // 处理帮助命令
        if (cmd.hasOption("h")) {
            printHelp(options);
            return;
        }

        try {
            // 获取输入输出路径（命令行参数优先，否则用默认）
            String inputPath = cmd.getOptionValue("i", AppConfig.getInputPath());
            String outputPath = cmd.getOptionValue("o", AppConfig.getOutputPath());

            File input = new File(inputPath);
            if (input.isFile()) {
                // 处理单个文件
                FileUtil.ensureFolderExists(outputPath);
                translator.translateSingleFile(input, outputPath);
            } else if (input.isDirectory()) {
                // 处理文件夹
                translator.translateBatchFiles(inputPath, outputPath);
            } else {
                logger.error("输入路径不存在: {}", inputPath);
            }
        } catch (Exception e) {
            logger.error("程序运行失败", e);
            System.exit(1);
        }
    }

    /**
     * 创建命令行选项
     */
    private static Options createOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "显示帮助信息");
        options.addOption("i", "input", true, "输入文件或文件夹路径（默认: input/）");
        options.addOption("o", "output", true, "输出文件夹路径（默认: output/）");
        return options;
    }

    /**
     * 解析命令行参数
     */
    private static CommandLine parseCommandLine(String[] args, Options options) {
        try {
            CommandLineParser parser = new DefaultParser();
            return parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("参数解析失败: " + e.getMessage());
            printHelp(options);
            return null;
        }
    }

    /**
     * 打印帮助信息
     */
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("note-translator", "音符数据转译工具（命令行版）", options, "示例:\n" +
                "  转换单个文件: java -jar note-translator.jar -i input/level1.json -o output/\n" +
                "  转换整个文件夹: java -jar note-translator.jar -i input/ -o output/", true);
    }
}