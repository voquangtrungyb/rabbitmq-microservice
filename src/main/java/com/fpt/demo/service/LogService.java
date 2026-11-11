package com.fpt.demo.service;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LogService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_LOGS = 100;

    private final List<String> logEntries = new CopyOnWriteArrayList<>();

    public void addLog(String source, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String logLine = String.format("[%s] [%s] %s", timestamp, source, message);
        logEntries.add(logLine);

        // Giữ tối đa MAX_LOGS mục gần nhất
        while (logEntries.size() > MAX_LOGS) {
            logEntries.remove(0);
        }
    }

    public List<String> getLogs() {
        return new ArrayList<>(logEntries);
    }

    public void clearLogs() {
        logEntries.clear();
    }
}

