// Py4JGateway.java - mit vollständigem Lifecycle-Tracking
package org.gateway.android;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.meinname.ssh.Globals;

import py4j.GatewayServer;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.Socket;

public class Py4JGateway {

    private static final String TAG = "Py4JGateway";
    
    // ★ ★ ★ GATEWAY STATUS ★ ★ ★
    private static boolean isGatewayRunning = false;
    private static boolean isGatewayInitialized = false;
    private static long gatewayStartTime = 0;
    private static long lastActivityTime = 0;
    private static int connectionCount = 0;
    private static String lastError = "Kein Fehler";
    private static String lastAction = "Keine Aktion";
    private static String lastStateChange = "Noch nicht gestartet";
    private static long lastStateChangeTime = 0;
    
    // ★ ★ ★ LIFECYCLE-TRACKING ★ ★ ★
    private static boolean isWatchdogRunning = false;
    private static int restartCount = 0;
    private static long lastRestartTime = 0;
    private static String shutdownReason = "Noch nicht gestoppt";
    private static String shutdownStackTrace = "";
    private static boolean isShuttingDown = false;
    
    // ★ ★ ★ GATEWAY-SERVER INSTANZ ★ ★ ★
    private static GatewayServer gatewayServer = null;
    
    // ★ ★ ★ ACTIVITY-REFERENZ ★ ★ ★
    private static WeakReference<Activity> mActivityRef = null;
    private static String currentActivityName = "None";

    // ─── ALLE ACTIVITY-CLASS NAMEN ★ ★ ★
    private static final String[] ACTIVITY_CLASSES = {
        "org.kivy.android.PythonActivity",
        "org.kivy.android.PythonQActivity",
        "com.meinname.loginapp3.MyCustomActivity",
        "com.rk.terminal.ui.activities.terminal.MainActivity"
    };
    
    private static final String[] ACTIVITY_NAMES = {
        "1. PythonActivity (SDL2/Kivy)",
        "2. PythonQActivity (Qt/PySide6)",
        "3. MyCustomActivity (Login/Start)",
        "4. ReTerminal (Shell)"
    };

    // ─── ★ ★ ★ LIFECYCLE: STATE CHANGE LOG ★ ★ ★ ──────────────
    private static void logStateChange(String newState, String reason) {
        lastStateChange = newState;
        lastStateChangeTime = System.currentTimeMillis();
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .format(new java.util.Date(lastStateChangeTime));
        Globals.showLog(TAG, "🔄 STATE CHANGE: " + newState + " | Reason: " + reason + " | Time: " + timestamp);
        Log.d(TAG, "🔄 STATE CHANGE: " + newState + " | Reason: " + reason);
        
        // ★ ★ ★ STACKTRACE AUFNEHMEN ★ ★ ★
        if (reason.contains("destroy") || reason.contains("shutdown") || reason.contains("stop")) {
            StringBuilder sb = new StringBuilder();
            sb.append("📚 Stacktrace für State Change:\n");
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(15, stack.length); i++) {
                sb.append("  at ").append(stack[i].toString()).append("\n");
            }
            Globals.showLog(TAG, sb.toString());
            shutdownStackTrace = sb.toString();
        }
    }

    // ─── ★ ★ ★ DEBUG: PORT PRÜFEN ★ ★ ★ ──────────────────────
    public static boolean isPortOpen() {
        try {
            Socket socket = new Socket("127.0.0.1", Globals.GATEWAY_PORT);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ─── ★ ★ ★ DEBUG: LETZTE AKTIVITÄT ★ ★ ★ ──────────────
    public static String getLastActivity() {
        if (lastActivityTime == 0) {
            return "Keine Aktivität";
        }
        long diff = (System.currentTimeMillis() - lastActivityTime) / 1000;
        return "Letzte Aktivität: " + lastAction + " vor " + diff + "s";
    }
    
    // ─── ★ ★ ★ DEBUG: LIFECYCLE STATUS ★ ★ ★ ──────────────
    public static String getLifecycleStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 LIFECYCLE STATUS:\n");
        sb.append("  State: ").append(lastStateChange).append("\n");
        sb.append("  Time: ").append(new java.text.SimpleDateFormat("HH:mm:ss.SSS")
            .format(new java.util.Date(lastStateChangeTime))).append("\n");
        sb.append("  Running: ").append(isGatewayRunning).append("\n");
        sb.append("  Initialized: ").append(isGatewayInitialized).append("\n");
        sb.append("  Watchdog: ").append(isWatchdogRunning).append("\n");
        sb.append("  Restarts: ").append(restartCount).append("\n");
        sb.append("  Last Restart: ").append(lastRestartTime > 0 ? 
            new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(lastRestartTime)) : "Nie").append("\n");
        sb.append("  Shutdown Reason: ").append(shutdownReason).append("\n");
        if (!shutdownStackTrace.isEmpty()) {
            sb.append("  Stacktrace:\n").append(shutdownStackTrace);
        }
        return sb.toString();
    }
    
    // ─── ★ ★ ★ DEBUG: FEHLER-LOG ★ ★ ★ ──────────────────────
    public static void logError(String error) {
        lastError = error;
        Globals.showLog(TAG, "❌ ERROR: " + error);
        Log.e(TAG, "❌ ERROR: " + error);
        
        // ★ ★ ★ STACKTRACE AUFNEHMEN ★ ★ ★
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            Globals.showLog(TAG, "  at " + stackTrace[i].toString());
        }
    }
    
    // ─── ★ ★ ★ DEBUG: AKTIVITÄT LOGGEN ★ ★ ★ ──────────────
    public static void logAction(String action) {
        lastAction = action;
        lastActivityTime = System.currentTimeMillis();
        connectionCount++;
        Globals.showLog(TAG, "🔍 ACTION: " + action + " (#" + connectionCount + ")");
    }

    // ─── ★ ★ ★ NEU: ALLE 4 ACTIVITIES PRÜFEN ★ ★ ★ ──────────
    public String checkAllActivities() {
        Globals.showLog(TAG, "🔍 checkAllActivities() aufgerufen");
        logAction("checkAllActivities");
        
        StringBuilder result = new StringBuilder();
        result.append("📊 ACTIVITY-STATUS:\n");
        result.append("========================================\n");
        
        // 1. Gateway-Status
        result.append("🔌 Gateway: ");
        if (isGatewayRunning) {
            result.append("✅ LÄUFT (Port " + Globals.GATEWAY_PORT + ")\n");
            result.append("   Port offen: " + (isPortOpen() ? "✅ JA" : "❌ NEIN") + "\n");
        } else {
            result.append("❌ NICHT GESTARTET!\n");
        }
        
        result.append("   Letzte Aktion: " + lastAction + "\n");
        result.append("   Verbindungen: " + connectionCount + "\n");
        if (lastError != null && !lastError.equals("Kein Fehler")) {
            result.append("   Letzter Fehler: " + lastError + "\n");
        }
        result.append("   State: " + lastStateChange + "\n");
        result.append("   Restarts: " + restartCount + "\n");
        result.append("   Shutdown Reason: " + shutdownReason + "\n");
        
        // 2. Gespeicherte Activity
        result.append("\n📌 Gespeicherte Activity: ");
        if (mActivityRef != null) {
            Activity act = mActivityRef.get();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                result.append("✅ " + act.getClass().getSimpleName() + " (gültig)\n");
            } else {
                result.append("⚠️ " + currentActivityName + " (ungültig oder zerstört)\n");
            }
        } else {
            result.append("❌ KEINE\n");
        }
        
        // 3. Alle 4 Activities prüfen
        result.append("\n📋 Alle 4 Activities:\n");
        result.append("----------------------------------------\n");
        
        int foundCount = 0;
        int validCount = 0;
        String activeActivity = "Keine";
        
        for (int i = 0; i < ACTIVITY_CLASSES.length; i++) {
            String className = ACTIVITY_CLASSES[i];
            String displayName = ACTIVITY_NAMES[i];
            
            result.append(displayName + ": ");
            
            try {
                Class<?> clazz = Class.forName(className);
                foundCount++;
                
                // Versuche getInstance()
                try {
                    Method getInstance = clazz.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    
                    if (instance != null && instance instanceof Activity) {
                        Activity activity = (Activity) instance;
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            result.append("✅ AKTIV (gültig)\n");
                            validCount++;
                            activeActivity = activity.getClass().getSimpleName();
                        } else {
                            result.append("⚠️ INSTANZ EXISTIERT, aber Activity beendet/zerstört\n");
                        }
                    } else {
                        result.append("⚠️ INSTANZ EXISTIERT, aber null oder keine Activity\n");
                    }
                } catch (NoSuchMethodException e) {
                    result.append("⚠️ KEINE getInstance() Methode\n");
                } catch (Exception e) {
                    result.append("⚠️ Fehler beim Aufruf: " + e.getMessage() + "\n");
                }
                
            } catch (ClassNotFoundException e) {
                result.append("❌ KLASSE NICHT GEFUNDEN (nicht geladen)\n");
            } catch (Exception e) {
                result.append("❌ Fehler: " + e.getMessage() + "\n");
            }
        }
        
        // 4. Zusammenfassung
        result.append("\n========================================\n");
        result.append("📊 ZUSAMMENFASSUNG:\n");
        result.append("   Gefundene Klassen: " + foundCount + "/4\n");
        result.append("   Gültige Activities: " + validCount + "/4\n");
        result.append("   Aktive Activity: " + activeActivity + "\n");
        result.append("   " + getLastActivity() + "\n");
        result.append("   Lifecycle: " + lastStateChange + "\n");
        
        if (validCount > 0) {
            result.append("   ✅ Mindestens eine Activity ist aktiv!\n");
        } else {
            result.append("   ❌ KEINE Activity aktiv! Bitte starte eine Activity.\n");
        }
        
        // 5. Gateway-Status
        result.append("\n🔍 Gateway-Status: " + getGatewayStatus());
        result.append("\n📋 Lifecycle-Details:\n" + getLifecycleStatus());
        
        Globals.showLog(TAG, result.toString());
        return result.toString();
    }

    // ─── SELBST-PRÜFUNG ──────────────────────────────────────
    public static boolean isGatewayActive() {
        if (!isGatewayRunning) {
            logError("Gateway läuft nicht!");
            logStateChange("GATEWAY_STOPPED", "isGatewayRunning = false");
            return false;
        }
        if (mActivityRef == null) {
            logError("Keine Activity-Referenz!");
            logStateChange("NO_ACTIVITY", "mActivityRef = null");
            return false;
        }
        Activity activity = mActivityRef.get();
        if (activity == null) {
            logError("Activity wurde eingesammelt (GC)!");
            logStateChange("ACTIVITY_GC", "WeakReference wurde null");
            return false;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            logError("Activity ist beendet oder zerstört: " + activity.getClass().getSimpleName());
            logStateChange("ACTIVITY_DESTROYED", activity.getClass().getSimpleName() + " isFinishing=" + activity.isFinishing() + ", isDestroyed=" + activity.isDestroyed());
            return false;
        }
        return true;
    }
    
    // ─── GATEWAY STATUS ──────────────────────────────────────
    public static String getGatewayStatus() {
        if (!isGatewayRunning) {
            return "❌ Gateway NICHT gestartet (Fehler: " + lastError + ")";
        }
        if (mActivityRef == null || mActivityRef.get() == null) {
            return "⚠️ Gateway läuft, aber KEINE Activity-Referenz!";
        }
        Activity activity = mActivityRef.get();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return "⚠️ Gateway läuft, aber Activity ist zerstört!";
        }
        return "✅ Gateway AKTIV (Activity: " + activity.getClass().getSimpleName() + ", Uptime: " + getUptime() + "s, Port: " + (isPortOpen() ? "✅ offen" : "❌ geschlossen") + ")";
    }
    
    // ─── UPTIME ──────────────────────────────────────────────
    public static long getUptime() {
        if (gatewayStartTime == 0) return 0;
        return (System.currentTimeMillis() - gatewayStartTime) / 1000;
    }

    // ─── AKTUELLE ACTIVITY HOLEN ──────────────────────────────
    private static Activity getValidActivity() {
        logAction("getValidActivity");
        
        if (!isGatewayRunning) {
            logError("Gateway läuft nicht!");
            logStateChange("GET_ACTIVITY_FAILED", "Gateway nicht gestartet");
            return null;
        }
        
        if (mActivityRef != null) {
            Activity activity = mActivityRef.get();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                return activity;
            } else {
                mActivityRef = null;
                logError("Gespeicherte Activity ungültig!");
                logStateChange("ACTIVITY_INVALID", "Gespeicherte Activity wurde zerstört");
            }
        }

        for (String className : ACTIVITY_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                try {
                    Method getInstance = clazz.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    
                    if (instance != null && instance instanceof Activity) {
                        Activity activity = (Activity) instance;
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            mActivityRef = new WeakReference<>(activity);
                            currentActivityName = activity.getClass().getSimpleName();
                            logAction("Activity gefunden: " + currentActivityName);
                            return activity;
                        }
                    }
                } catch (Exception e) {
                    // Ignorieren
                }
            } catch (Exception e) {
                // Ignorieren
            }
        }

        logError("Keine gültige Activity gefunden!");
        logStateChange("NO_VALID_ACTIVITY", "Alle Activities sind null oder zerstört");
        return null;
    }

    // ─── JAVA-METHODEN (VON PYTHON AUFRUFBAR) ──────────────────
    public String holeAndroidStatus() {
        logAction("holeAndroidStatus");
        return "✅ " + getGatewayStatus();
    }

    public String verarbeiteText(String eingabe) {
        logAction("verarbeiteText: " + (eingabe != null ? eingabe.substring(0, Math.min(20, eingabe.length())) : "null"));
        Globals.showLog(TAG, "📩 Text von Python empfangen: " + eingabe);
        
        if (eingabe == null || eingabe.trim().isEmpty()) {
            return "⚠️ Java hat einen leeren Text erhalten!";
        }
        
        return "✅ Java meldet: Text erfolgreich verarbeitet (Länge: " + eingabe.length() + " Zeichen).";
    }

    public String startPythonActivity() {
        logAction("startPythonActivity");
        return startActivity("org.kivy.android.PythonActivity", "main.pyc");
    }

    public String startPythonQActivity() {
        logAction("startPythonQActivity");
        return startActivity("org.kivy.android.PythonQActivity", "main2.pyc");
    }
    
    public String startTerminal() {
        logAction("startTerminal");
        return startActivity("com.rk.terminal.ui.activities.terminal.MainActivity", "");
    }

    private String startActivity(String className, String entrypoint) {
        logAction("startActivity: " + className);
        
        if (!isGatewayRunning) {
            String error = "❌ Gateway läuft NICHT! startServer() wurde nicht aufgerufen.";
            logError(error);
            logStateChange("START_ACTIVITY_FAILED", "Gateway nicht gestartet");
            return error;
        }
        
        Activity activity = getValidActivity();
        
        if (activity == null) {
            String error = "❌ Keine Activity-Referenz! Bitte rufe setActivity() auf.";
            logError(error);
            logStateChange("START_ACTIVITY_FAILED", "Keine gültige Activity");
            return error;
        }
        
        try {
            Class<?> activityClass = Class.forName(className);
            Intent intent = new Intent(activity, activityClass);
            
            if (entrypoint != null && !entrypoint.isEmpty()) {
                intent.putExtra("ANDROID_ENTRYPOINT", entrypoint);
                intent.putExtra("PYTHON_ENTRYPOINT", entrypoint);
                intent.putExtra("entrypoint", entrypoint);
            }
            
            activity.startActivity(intent);
            logAction("Activity gestartet: " + className);
            logStateChange("ACTIVITY_STARTED", className);
            return "✅ " + className + " gestartet";
            
        } catch (ClassNotFoundException e) {
            String error = "❌ Klasse nicht gefunden: " + className;
            logError(error);
            logStateChange("START_ACTIVITY_FAILED", "ClassNotFoundException: " + className);
            return error;
        } catch (Exception e) {
            String error = "❌ Fehler: " + e.getMessage();
            logError(error);
            logStateChange("START_ACTIVITY_FAILED", "Exception: " + e.getMessage());
            return error;
        }
    }

    // ─── ACTIVITY-REFERENZ SETZEN ──────────────────────────────
    public static void setActivity(Activity activity) {
        logAction("setActivity: " + (activity != null ? activity.getClass().getSimpleName() : "null"));
        
        if (activity != null) {
            mActivityRef = new WeakReference<>(activity);
            currentActivityName = activity.getClass().getSimpleName();
            Globals.showLog(TAG, "✅ Activity-Referenz GESETZT: " + currentActivityName);
            logStateChange("ACTIVITY_SET", currentActivityName);
        } else {
            mActivityRef = null;
            currentActivityName = "None";
            logError("Activity-Referenz auf null gesetzt!");
            logStateChange("ACTIVITY_SET_NULL", "setActivity(null) aufgerufen");
        }
    }

    // ─── ★ ★ ★ WATCHDOG: PORT ÜBERWACHEN ★ ★ ★ ──────────────
    public static void startWatchdog() {
        if (isWatchdogRunning) {
            Globals.showLog(TAG, "⚠️ Watchdog läuft bereits!");
            return;
        }
        
        isWatchdogRunning = true;
        logStateChange("WATCHDOG_STARTED", "Watchdog Thread gestartet");
        Globals.showLog(TAG, "🔍 Watchdog Thread gestartet!");
        
        new Thread(() -> {
            while (isWatchdogRunning) {
                try {
                    Thread.sleep(5000); // Alle 5 Sekunden prüfen
                    
                    // ★ ★ ★ PRÜFE OB GATEWAY NOCH LÄUFT ★ ★ ★
                    if (!isGatewayRunning) {
                        logStateChange("WATCHDOG_DETECTED_STOP", "isGatewayRunning = false");
                        Globals.showLog(TAG, "⚠️ Watchdog: Gateway läuft nicht! Starte neu...");
                        restartServer();
                        continue;
                    }
                    
                    // ★ ★ ★ PRÜFE OB PORT OFFEN IST ★ ★ ★
                    if (!isPortOpen()) {
                        logStateChange("WATCHDOG_DETECTED_PORT_CLOSED", "Port " + Globals.GATEWAY_PORT + " ist geschlossen!");
                        logError("🚨 PORT GESCHLOSSEN! Gateway läuft aber Port ist zu!");
                        logError("Letzte Aktion: " + lastAction);
                        logError("Letzte Activity: " + currentActivityName);
                        logError("State: " + lastStateChange);
                        
                        Globals.showLog(TAG, "🔄 Watchdog: Versuche Gateway neu zu starten...");
                        restartServer();
                    }
                    
                } catch (Exception e) {
                    Globals.showLog(TAG, "⚠️ Watchdog Fehler: " + e.getMessage());
                    logError("Watchdog Exception: " + e.getMessage());
                }
            }
            Globals.showLog(TAG, "⚠️ Watchdog beendet (isWatchdogRunning = false)");
            logStateChange("WATCHDOG_STOPPED", "isWatchdogRunning = false");
        }).start();
    }
    
    // ─── ★ ★ ★ GATEWAY NEU STARTEN ★ ★ ★ ────────────────────
    public static void restartServer() {
        restartCount++;
        lastRestartTime = System.currentTimeMillis();
        logAction("restartServer (#" + restartCount + ")");
        logStateChange("RESTARTING", "Restart #" + restartCount);
        Globals.showLog(TAG, "🔄 Gateway-Neustart #" + restartCount + " wird durchgeführt...");
        
        // Alten Server stoppen
        if (gatewayServer != null) {
            try {
                gatewayServer.shutdown();
                Globals.showLog(TAG, "✅ Alter Gateway-Server gestoppt");
                logStateChange("OLD_SERVER_STOPPED", "gatewayServer.shutdown() erfolgreich");
            } catch (Exception e) {
                Globals.showLog(TAG, "⚠️ Fehler beim Stoppen: " + e.getMessage());
                logError("Fehler beim Stoppen: " + e.getMessage());
            }
            gatewayServer = null;
        }
        
        isGatewayRunning = false;
        
        // Neu starten
        Globals.showLog(TAG, "🔄 startServer() wird aufgerufen...");
        startServer();
    }

    // ─── SERVER STARTEN ──────────────────────────────────────
    public static void startServer() {
        logAction("startServer");
        logStateChange("STARTING", "Server wird gestartet...");
        Globals.showLog(TAG, "🚀 Gateway-Server wird gestartet...");
        
        isGatewayInitialized = true;
        gatewayStartTime = System.currentTimeMillis();
        lastError = "Kein Fehler";
        shutdownReason = "Noch nicht gestoppt";
        
        new Thread(() -> {
            try {
                Py4JGateway gatewayInstance = new Py4JGateway();
                gatewayServer = new GatewayServer(gatewayInstance, Globals.GATEWAY_PORT);
                gatewayServer.start();
                
                isGatewayRunning = true;
                logStateChange("RUNNING", "Server erfolgreich gestartet auf Port " + Globals.GATEWAY_PORT);
                
                Globals.showLog(TAG, "✅ Gateway Server auf Port " + Globals.GATEWAY_PORT + " gestartet!");
                Log.d(TAG, "✅ Gateway Server auf Port " + Globals.GATEWAY_PORT + " gestartet!");
                
                // ★ ★ ★ WATCHDOG STARTEN ★ ★ ★
                if (!isWatchdogRunning) {
                    startWatchdog();
                    Globals.showLog(TAG, "✅ Watchdog gestartet");
                }
                
            } catch (Exception e) {
                isGatewayRunning = false;
                String error = "❌ Fehler beim Starten des Gateway Servers: " + e.getMessage();
                logError(error);
                logStateChange("START_FAILED", error);
                Log.e(TAG, error, e);
                shutdownReason = "Start-Fehler: " + e.getMessage();
            }
        }).start();
    }
    
    // ─── ★ ★ ★ SERVER STOPPEN (NUR BEIM APP-EXIT) ★ ★ ★ ──
    public static void stopServer() {
        isShuttingDown = true;
        logAction("stopServer");
        logStateChange("STOPPING", "stopServer() aufgerufen");
        Globals.showLog(TAG, "🛑 Gateway-Server wird gestoppt...");
        
        // ★ ★ ★ STACKTRACE AUFNEHMEN ★ ★ ★
        shutdownReason = "stopServer() aufgerufen";
        StringBuilder sb = new StringBuilder();
        sb.append("📚 stopServer() Stacktrace:\n");
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(15, stack.length); i++) {
            sb.append("  at ").append(stack[i].toString()).append("\n");
        }
        shutdownStackTrace = sb.toString();
        Globals.showLog(TAG, sb.toString());
        
        isWatchdogRunning = false;
        isGatewayRunning = false;
        isGatewayInitialized = false;
        gatewayStartTime = 0;
        
        if (gatewayServer != null) {
            try {
                gatewayServer.shutdown();
                Globals.showLog(TAG, "✅ Gateway-Server gestoppt");
                logStateChange("STOPPED", "gatewayServer.shutdown() erfolgreich");
            } catch (Exception e) {
                Globals.showLog(TAG, "⚠️ Fehler beim Stoppen: " + e.getMessage());
                logError("Fehler beim Stoppen: " + e.getMessage());
            }
            gatewayServer = null;
        }
        
        mActivityRef = null;
        currentActivityName = "None";
        isShuttingDown = false;
    }
    
    // ─── ★ ★ ★ NEU: LIFECYCLE STATUS ABFRAGEN ★ ★ ★ ──────
    public static String getLifecycleStatusString() {
        return getLifecycleStatus();
    }
}