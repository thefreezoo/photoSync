package com.example.pho;

import android.content.Context;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final String CRASH_DIR = "pho_crash";
    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;

    private CrashHandler() {
    }

    public static CrashHandler getInstance() {
        if (instance == null) {
            synchronized (CrashHandler.class) {
                if (instance == null) {
                    instance = new CrashHandler();
                }
            }
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context;
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            // 记录崩溃日志
            saveCrashInfoToFile(ex);
        } catch (Exception e) {
            Log.e(TAG, "Error saving crash log", e);
        }

        // 调用默认处理器
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            // 如果没有默认处理器，直接退出进程
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private void saveCrashInfoToFile(Throwable ex) throws IOException {
        String time = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
        String fileName = "crash-" + time + ".txt";

        // 创建崩溃日志目录
        File crashDir = new File(Environment.getExternalStorageDirectory(), CRASH_DIR);
        if (!crashDir.exists()) {
            crashDir.mkdirs();
        }

        // 创建日志文件
        File crashFile = new File(crashDir, fileName);

        // 写入崩溃信息
        try (FileWriter writer = new FileWriter(crashFile);
             PrintWriter printWriter = new PrintWriter(writer)) {

            // 记录时间
            printWriter.println("Crash time: " + time);

            // 记录设备信息
            printWriter.println("Device info:");
            printWriter.println("Android version: " + android.os.Build.VERSION.RELEASE);
            printWriter.println("Device model: " + android.os.Build.MODEL);
            printWriter.println("Manufacturer: " + android.os.Build.MANUFACTURER);

            // 记录崩溃堆栈
            printWriter.println("\nCrash stack trace:");
            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            printWriter.println(stringWriter.toString());

            Log.i(TAG, "Crash log saved to: " + crashFile.getAbsolutePath());
        }
    }
}