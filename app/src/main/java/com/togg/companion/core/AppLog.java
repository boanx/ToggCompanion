package com.togg.companion.core;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded private diagnostics. Never pass request/response bodies or key material. */
public final class AppLog {
    private AppLog() { }
    private static final int LIMIT = 192 * 1024;
    public static synchronized void event(Context context, String category, String message) {
        try {
            File file = new File(context.getFilesDir(), "application.log");
            String safe = sanitize(message);
            if (safe.length() > 8192) safe = safe.substring(0, 8192) + " [truncated]";
            if (file.length() > LIMIT) {
                Files.move(file.toPath(), new File(context.getFilesDir(), "application.previous.log").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " " + category.replaceAll("[^A-Za-z0-9_-]", "") + " " + safe + "\n";
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file, true)) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }
    public static String userMessage(Context context, String category, String technical, String fallback) {
        event(context, category, technical);
        if (technical == null || technical.trim().isEmpty()) return fallback;
        if (technical.matches("(?is).*(HTTP|GATT|CERT_|MTU|base64|truId|UUID|sertifika|certificate|BLE|TRANSPORT_|FETCHING_|PREPARING_|[0-9a-f]{8}-[0-9a-f-]{27,}|https?://).*")) return fallback;
        return technical;
    }
    static String sanitize(String message) {
        if (message == null) return "";
        return message.replaceAll("https?://\\S+", "[url]")
                .replaceAll("(?i)Bearer\\s+\\S+", "Bearer [redacted]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email]")
                .replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[id]")
                .replaceAll("\\b[A-HJ-NPR-Z0-9]{17}\\b", "[vin]")
                .replaceAll("[A-Za-z0-9_+/=-]{32,}(?:\\.[A-Za-z0-9_+/=-]+)*", "[secret]")
                .replaceAll("-?\\d{1,3}\\.\\d{3,}\\s*,\\s*-?\\d{1,3}\\.\\d{3,}", "[location]");
    }
    public static synchronized void share(Activity activity) {
        try {
            File directory = new File(activity.getCacheDir(), "shared-logs");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException();
            File export = new File(directory, "ToggCompanion.log");
            StringBuilder text = new StringBuilder("Togg Companion / Android ")
                    .append(android.os.Build.VERSION.SDK_INT).append('\n');
            for (String name : new String[]{"application.previous.log", "application.log"}) {
                File file = new File(activity.getFilesDir(), name);
                if (file.isFile()) text.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
            Files.write(export.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".logs", export);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "Togg Companion uygulama günlüğü")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Uygulama günlüğü", uri));
            activity.startActivity(Intent.createChooser(intent, "Uygulama günlüğünü paylaş"));
        } catch (Exception unavailable) {
            android.widget.Toast.makeText(activity, "Günlük paylaşılamadı. Yeniden deneyin.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
