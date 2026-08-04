// sshclient/main/src/main/java/com/meinname/ssh/Globals.java
package com.meinname.ssh;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Globals {
    
    private static final String TAG = "Globals";
    
    private static Context appContext;
    
    
    // ============================================================
    // KONFIGURATIONSVARIABLEN
    // ============================================================
    public static int DIALOG_DELAY;
    public static String APP_VERSION;
    public static boolean DEBUG;
    public static int TIMEOUT;
    public static int MAX_RETRIES;
    public static String PYTHON_HOME;
    public static String LOG_DIR;
    public static String ROOTFS_TYPE;
    public static String ROOTFS_FILE;
    public static String ROOTFS_DIR;
    public static String INIT_SCRIPT;
    public static String INIT_HOST_SCRIPT;
    public static int SSHD_PORT;
    public static boolean SSHD_ENABLED;
    public static int FTP_PORT;
    public static boolean FTP_ENABLED;
    
    public static int GATEWAY_PORT;
    public static boolean GATEWAY_ENABLED;
    
    // ==================================
    // ★ ★ ★ STATISCHE CONFIG ★ ★ ★
    // ==================================
    
    private static Properties configProps; 
    
    // ============================================================
    // WORKING MODE
    // ============================================================
    public static final int WORKING_MODE_ALPINE = 0;
    public static final int WORKING_MODE_UBUNTU = 1;
    public static final int WORKING_MODE_ANDROID = 2;
    public static int WORKING_MODE;
    
    // ============================================================
    // PROPERTIES SPEICHERN (für setAlpine/setUbuntu)
    // ============================================================
    private static Properties modeProps;
    private static Properties alpineProps;
    private static Properties ubuntuProps;
    private static Properties defaultProps;
    private static Properties appProps;
    private static Properties pathsProps;  // ★ ★ ★ NEU ★ ★ ★
    
    // ============================================================
    // SHOWLOG
    // ============================================================
    public static void showLog(String tag, String message) {
        try {
            File logFile = new File(Environment.getExternalStorageDirectory(), "main_core.log");
            logFile.getParentFile().mkdirs();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(timestamp + " - " + tag + ": " + message + "\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.d(tag, message);
        }
    }
    
    // ============================================================
    // INI PARSER
    // ============================================================
    private static Map<String, Properties> parseIni(InputStream inputStream) {
        Map<String, Properties> sections = new HashMap<>();
        String currentSection = "DEFAULT";
        Properties currentProps = new Properties();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue;
                
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (!currentProps.isEmpty()) sections.put(currentSection, currentProps);
                    currentSection = trimmed.substring(1, trimmed.length() - 1);
                    currentProps = new Properties();
                } else {
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        currentProps.setProperty(trimmed.substring(0, eq).trim(), 
                                                 trimmed.substring(eq + 1).trim());
                    }
                }
            }
            if (!currentProps.isEmpty()) sections.put(currentSection, currentProps);
        } catch (Exception e) {
            showLog(TAG, "❌ Parse error: " + e.getMessage());
        }
        return sections;
    }
    
    // ============================================================
    // PROPERTY HELPER
    // ============================================================
    private static String get(Properties p1, Properties p2, String key, String def) {
        String v = p1.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = p2.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
    
    private static String get(Properties p1, Properties p2, Properties p3, String key, String def) {
        String v = p1.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = p2.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = p3.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        return def;
    }
    
    // ============================================================
    // SET METHODEN (NUR AUS CONFIG.INI)
    // ============================================================
    public static void setAlpine() {
        WORKING_MODE = WORKING_MODE_ALPINE;
        
        // ★ ★ ★ KORREKT: alpineProps STATT configProps ★ ★ ★
        if (alpineProps != null) {
            ROOTFS_TYPE = alpineProps.getProperty("ROOTFS_TYPE", "alpine");
            ROOTFS_FILE = alpineProps.getProperty("ROOTFS_FILE", "alpine.tar.gz");
            ROOTFS_DIR = alpineProps.getProperty("ROOTFS_DIR", "alpine");
            INIT_SCRIPT = alpineProps.getProperty("INIT_SCRIPT", "alpine/init.sh");
            INIT_HOST_SCRIPT = alpineProps.getProperty("INIT_HOST_SCRIPT", "alpine/init-host.sh");
            SSHD_PORT = Integer.parseInt(alpineProps.getProperty("SSHD_PORT", "2222"));
            SSHD_ENABLED = Boolean.parseBoolean(alpineProps.getProperty("SSHD_ENABLED", "true"));
            FTP_PORT = Integer.parseInt(alpineProps.getProperty("FTP_PORT", "2135"));
            FTP_ENABLED = Boolean.parseBoolean(alpineProps.getProperty("FTP_ENABLED", "true"));
        } else {
            // Fallback
            showLog(TAG, "⚠️ Alpine-Config nicht gefunden – verwende Fallback");
            ROOTFS_TYPE = "alpine";
            ROOTFS_FILE = "alpine.tar.gz";
            ROOTFS_DIR = "alpine";
            INIT_SCRIPT = "alpine/init.sh";
            INIT_HOST_SCRIPT = "alpine/init-host.sh";
            SSHD_PORT = 2222;
            SSHD_ENABLED = true;
            FTP_PORT = 2135;
            FTP_ENABLED = true;
        }
        
        showLog(TAG, "✅ Alpine geladen: SSHD_PORT=" + SSHD_PORT);
    }
    
    public static void setUbuntu() {
        WORKING_MODE = WORKING_MODE_UBUNTU;
        
        // ★ ★ ★ KORREKT: ubuntuProps STATT configProps ★ ★ ★
        if (ubuntuProps != null) {
            ROOTFS_TYPE = ubuntuProps.getProperty("ROOTFS_TYPE", "ubuntu");
            ROOTFS_FILE = ubuntuProps.getProperty("ROOTFS_FILE", "ubuntu.tar.gz");
            ROOTFS_DIR = ubuntuProps.getProperty("ROOTFS_DIR", "ubuntu");
            INIT_SCRIPT = ubuntuProps.getProperty("INIT_SCRIPT", "ubuntu/init.sh");
            INIT_HOST_SCRIPT = ubuntuProps.getProperty("INIT_HOST_SCRIPT", "ubuntu/init-host.sh");
            SSHD_PORT = Integer.parseInt(ubuntuProps.getProperty("SSHD_PORT", "2233"));
            SSHD_ENABLED = Boolean.parseBoolean(ubuntuProps.getProperty("SSHD_ENABLED", "true"));
            FTP_PORT = Integer.parseInt(ubuntuProps.getProperty("FTP_PORT", "2140"));
            FTP_ENABLED = Boolean.parseBoolean(ubuntuProps.getProperty("FTP_ENABLED", "true"));
        } else {
            // Fallback
            showLog(TAG, "⚠️ Ubuntu-Config nicht gefunden – verwende Fallback");
            ROOTFS_TYPE = "ubuntu";
            ROOTFS_FILE = "ubuntu.tar.gz";
            ROOTFS_DIR = "ubuntu";
            INIT_SCRIPT = "ubuntu/init.sh";
            INIT_HOST_SCRIPT = "ubuntu/init-host.sh";
            SSHD_PORT = 2233;
            SSHD_ENABLED = true;
            FTP_PORT = 2140;
            FTP_ENABLED = true;
        }
        
        showLog(TAG, "✅ Ubuntu geladen: SSHD_PORT=" + SSHD_PORT);
    }

    
    // ============================================================
    // INIT
    // ============================================================
    public static void init(Context context) {
        InputStream is = null;
        try {
            File cfg = new File(context.getFilesDir(), "config.ini");
            is = cfg.exists() ? new FileInputStream(cfg) : context.getAssets().open("config.ini");
            
            Map<String, Properties> sections = parseIni(is);
            is.close();
            
            showLog(TAG, "✅ Config.ini geladen");
            
            // Properties speichern
            defaultProps = sections.getOrDefault("DEFAULT", new Properties());
            appProps = sections.getOrDefault("APP", new Properties());
            alpineProps = sections.getOrDefault("ALPINE", new Properties());
            ubuntuProps = sections.getOrDefault("UBUNTU", new Properties());
            pathsProps = sections.getOrDefault("PATHS", new Properties());
            
            // Working Mode aus APP-Sektion lesen
            WORKING_MODE = getWorkingModeFromString(
                appProps.getProperty("WORKING_MODE", defaultProps.getProperty("WORKING_MODE", "alpine"))
            );
            
            // ============================================================
            // ALLE ALLGEMEINEN WERTE LADEN
            // ============================================================
            DIALOG_DELAY = Integer.parseInt(get(appProps, defaultProps, "DIALOG_DELAY", "3000"));
            APP_VERSION = get(appProps, defaultProps, "APP_VERSION", "1.2.1");
            DEBUG = Boolean.parseBoolean(get(appProps, defaultProps, "DEBUG", "true"));
            TIMEOUT = Integer.parseInt(get(appProps, defaultProps, "TIMEOUT", "30"));
            MAX_RETRIES = Integer.parseInt(get(appProps, defaultProps, "MAX_RETRIES", "3"));
            
            GATEWAY_ENABLED = Boolean.parseBoolean(appProps.getProperty("GATEWAY_ENABLED", "false"));
            
            GATEWAY_PORT = Integer.parseInt(appProps.getProperty("GATEWAY_PORT", "25000"));
            
            // ★ ★ ★ PATHS ★ ★ ★
            PYTHON_HOME = get(pathsProps, appProps, defaultProps, "PYTHON_HOME", "");
            LOG_DIR = get(pathsProps, appProps, defaultProps, "LOG_DIR", "/sdcard/logs");
            
            // ★ ★ ★ JE NACH WORKING MODE: Alpine oder Ubuntu laden ★ ★ ★
            if (isAlpine()) {
                setAlpine();
            } else if (isUbuntu()) {
                setUbuntu();
            } else {
                // Fallback: Alpine
                setAlpine();
            }
            
        } catch (Exception e) {
            showLog(TAG, "❌ Config error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { if (is != null) is.close(); } catch (IOException e) {}
        }
    }
    
    // ============================================================
    // HELPERS
    // ============================================================
    private static int getWorkingModeFromString(String mode) {
        if (mode.equals("ubuntu")) return WORKING_MODE_UBUNTU;
        if (mode.equals("android")) return WORKING_MODE_ANDROID;
        return WORKING_MODE_ALPINE;
    }
    
    public static String getWorkingModeName() {
        if (WORKING_MODE == WORKING_MODE_UBUNTU) return "Ubuntu Linux";
        if (WORKING_MODE == WORKING_MODE_ANDROID) return "Android Shell";
        return "Alpine Linux";
    }
    
    public static String getWorkingModeShortName() {
        if (WORKING_MODE == WORKING_MODE_UBUNTU) return "ubuntu";
        if (WORKING_MODE == WORKING_MODE_ANDROID) return "android";
        return "alpine";
    }
    
    public static String getRootfsDisplayName() {
        return getWorkingModeName();
    }
    
    public static boolean isAlpine() { return WORKING_MODE == WORKING_MODE_ALPINE; }
    public static boolean isUbuntu() { return WORKING_MODE == WORKING_MODE_UBUNTU; }
    public static boolean isAndroid() { return WORKING_MODE == WORKING_MODE_ANDROID; }
}