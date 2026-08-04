package com.meinname.ssh;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.jcraft.jsch.*;

// ★ ★ ★ GLOBALS IMPORT ★ ★ ★
import com.meinname.ssh.Globals;

public class SSHClient {
    
    // Verbindungsparameter
    private String host;
    private int port;
    private String username;
    private String password;
    
    // JSch Objekte
    private JSch jsch;
    private Session session;
    private ChannelShell channel;
    private ChannelSftp sftp;
    
    private boolean isConnected;
    private BlockingQueue<String> outputQueue;
    
    // ============================================================
    // ★ ★ ★ SHOWLOG ★ ★ ★
    // ============================================================
    private static void showLog(String title, String message) {
        Globals.showLog("SSHClient", "[" + title + "] " + message);
    }
    
    // Konstruktor
    public SSHClient() {
        this("localhost", 2222, "test", "alpine");
    }
    
    public SSHClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.jsch = new JSch();
        this.isConnected = false;
        this.outputQueue = new LinkedBlockingQueue<>();
        
        showLog("Constructor", "📝 SSHClient initialisiert: " + username + "@" + host + ":" + port);
    }
    
    // connect() - Baut die SSH-Verbindung auf
    public boolean connect() {
        showLog("connect", "🔗 Verbinde zu " + username + "@" + host + ":" + port + "...");
        
        try {
            // Session erstellen
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            
            // HostKey-Überprüfung deaktivieren (wie StrictHostKeyChecking=no)
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            
            // Timeout setzen (10 Sekunden)
            session.setTimeout(10000);
            
            // Verbinden
            session.connect();
            
            isConnected = true;
            showLog("connect", "✅ SSH verbunden: " + username + "@" + host + ":" + port);
            return true;
            
        } catch (JSchException e) {
            showLog("connect", "❌ SSH Fehler: " + e.getMessage());
            close();
            return false;
        }
    }
    
    // cmd() - Führt einen Einzelbefehl aus (wie exec_command)
    public CommandResult cmd(String command) {
        return cmd(command, 30);
    }
    
    public CommandResult cmd(String command, int timeout) {
        showLog("cmd", "📝 Führe Befehl aus: " + command);
        
        if (!isConnected) {
            showLog("cmd", "❌ Keine SSH-Verbindung!");
            throw new IllegalStateException("Keine SSH-Verbindung. Rufe zuerst connect() auf.");
        }
        
        try {
            ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
            execChannel.setCommand(command);
            
            // Streams für Ausgabe
            ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
            
            execChannel.setOutputStream(stdoutStream);
            execChannel.setExtOutputStream(stderrStream);
            
            execChannel.connect(timeout);
            
            // Warten bis Kanal geschlossen wird
            while (!execChannel.isClosed()) {
                Thread.sleep(100);
            }
            
            int exitCode = execChannel.getExitStatus();
            execChannel.disconnect();
            
            String stdout = stdoutStream.toString("UTF-8");
            String stderr = stderrStream.toString("UTF-8");
            
            showLog("cmd", "📊 Exit Code: " + exitCode);
            if (!stdout.isEmpty()) {
                showLog("cmd", "📤 Stdout: " + stdout.substring(0, Math.min(stdout.length(), 200)) + (stdout.length() > 200 ? "..." : ""));
            }
            if (!stderr.isEmpty()) {
                showLog("cmd", "⚠️ Stderr: " + stderr.substring(0, Math.min(stderr.length(), 200)) + (stderr.length() > 200 ? "..." : ""));
            }
            
            return new CommandResult(
                stdout,
                stderr,
                exitCode,
                exitCode == 0
            );
            
        } catch (Exception e) {
            showLog("cmd", "❌ Fehler: " + e.getMessage());
            return new CommandResult("", e.getMessage(), -1, false);
        }
    }
    
    // cmd_write() - Sendet Befehl an dauerhafte Shell
    public String cmdWrite(String command) {
        return cmdWrite(command, true, 30);
    }
    
    public String cmdWrite(String command, boolean waitForPrompt, int timeout) {
        showLog("cmdWrite", "📝 Schreib Befehl in Shell: " + command);
        
        if (!isConnected) {
            showLog("cmdWrite", "❌ Keine SSH-Verbindung!");
            throw new IllegalStateException("Keine SSH-Verbindung. Rufe zuerst connect() auf.");
        }
        
        try {
            if (channel == null || channel.isClosed()) {
                channel = (ChannelShell) session.openChannel("shell");
                channel.setPtyType("xterm");
                channel.connect();
                Thread.sleep(500);
                clearBuffer();
                showLog("cmdWrite", "🔧 Neue Shell geöffnet");
            }
            
            // Befehl senden
            PrintWriter writer = new PrintWriter(channel.getOutputStream());
            writer.println(command);
            writer.flush();
            Thread.sleep(300);
            
            // Ausgabe lesen
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeout * 1000L) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) {
                        output.append(line).append("\n");
                    }
                } else if (output.length() > 0) {
                    break;
                }
                Thread.sleep(100);
            }
            
            String result = cleanOutput(output.toString(), command);
            showLog("cmdWrite", "📤 Ausgabe: " + result.substring(0, Math.min(result.length(), 200)) + (result.length() > 200 ? "..." : ""));
            return result;
            
        } catch (Exception e) {
            showLog("cmdWrite", "❌ Fehler: " + e.getMessage());
            return "Fehler: " + e.getMessage();
        }
    }
    
    private void clearBuffer() {
        try {
            if (channel != null && channel.getInputStream().available() > 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                while (reader.ready()) {
                    reader.read();
                }
            }
        } catch (IOException e) {
            // Ignorieren
        }
    }
    
    private String cleanOutput(String output, String command) {
        String[] lines = output.split("\n");
        StringBuilder cleaned = new StringBuilder();
        boolean skipCmd = true;
        
        for (String line : lines) {
            if (skipCmd && line.contains(command)) {
                skipCmd = false;
                continue;
            }
            if (!skipCmd) {
                cleaned.append(line).append("\n");
            }
        }
        
        return cleaned.toString().trim();
    }
    
    // cmd_interactive() - Für interaktive Befehle (sudo, passwd)
    public String cmdInteractive(String command, Map<String, String> responses) {
        return cmdInteractive(command, responses, 15);
    }
    
    public String cmdInteractive(String command, Map<String, String> responses, int timeout) {
        showLog("cmdInteractive", "📝 Interaktiver Befehl: " + command + " mit " + responses.size() + " Antworten");
        
        if (!isConnected) {
            showLog("cmdInteractive", "❌ Keine SSH-Verbindung!");
            throw new IllegalStateException("Keine SSH-Verbindung.");
        }
        
        try {
            if (channel == null || channel.isClosed()) {
                channel = (ChannelShell) session.openChannel("shell");
                channel.setPtyType("xterm");
                channel.connect();
                Thread.sleep(500);
                clearBuffer();
                showLog("cmdInteractive", "🔧 Neue Shell geöffnet");
            }
            
            // Befehl senden
            PrintWriter writer = new PrintWriter(channel.getOutputStream());
            writer.println(command);
            writer.flush();
            
            // Ausgabe lesen und auf Prompts reagieren
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            
            Map<String, String> remainingResponses = new HashMap<>(responses);
            long startTime = System.currentTimeMillis();
            int responseCount = 0;
            
            while (System.currentTimeMillis() - startTime < timeout * 1000L) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) {
                        output.append(line).append("\n");
                        
                        // Nach Prompts suchen
                        for (Map.Entry<String, String> entry : new HashMap<>(remainingResponses).entrySet()) {
                            if (line.toLowerCase().contains(entry.getKey().toLowerCase())) {
                                writer.println(entry.getValue());
                                writer.flush();
                                remainingResponses.remove(entry.getKey());
                                responseCount++;
                                showLog("cmdInteractive", "✅ Antwort gesendet: " + entry.getKey() + " → " + entry.getValue());
                                break;
                            }
                        }
                    }
                }
                Thread.sleep(100);
            }
            
            showLog("cmdInteractive", "📤 " + responseCount + " Antworten gesendet");
            return output.toString();
            
        } catch (Exception e) {
            showLog("cmdInteractive", "❌ Fehler: " + e.getMessage());
            return "Fehler: " + e.getMessage();
        }
    }
    
    // cmd_async() - Asynchroner Befehl
    public Thread cmdAsync(String command, Consumer<CommandResult> callback) {
        showLog("cmdAsync", "📝 Asynchroner Befehl: " + command);
        
        Thread thread = new Thread(() -> {
            CommandResult result = cmd(command);
            if (callback != null) {
                callback.accept(result);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    
    // upload_file() - SFTP Upload
    public boolean uploadFile(String localPath, String remotePath) {
        showLog("uploadFile", "📤 Upload: " + localPath + " → " + remotePath);
        
        try {
            if (sftp == null || !sftp.isConnected()) {
                sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                showLog("uploadFile", "🔗 SFTP verbunden");
            }
            
            try (FileInputStream fis = new FileInputStream(localPath)) {
                sftp.put(fis, remotePath, ChannelSftp.OVERWRITE);
                showLog("uploadFile", "✅ Upload erfolgreich");
                return true;
            }
            
        } catch (Exception e) {
            showLog("uploadFile", "❌ Upload Fehler: " + e.getMessage());
            return false;
        }
    }
    
    // download_file() - SFTP Download
    public boolean downloadFile(String remotePath, String localPath) {
        showLog("downloadFile", "📥 Download: " + remotePath + " → " + localPath);
        
        try {
            if (sftp == null || !sftp.isConnected()) {
                sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                showLog("downloadFile", "🔗 SFTP verbunden");
            }
            
            try (FileOutputStream fos = new FileOutputStream(localPath)) {
                sftp.get(remotePath, fos);
                showLog("downloadFile", "✅ Download erfolgreich");
                return true;
            }
            
        } catch (Exception e) {
            showLog("downloadFile", "❌ Download Fehler: " + e.getMessage());
            return false;
        }
    }
    
    // cmd_python() - Python-Code ausführen
    public CommandResult cmdPython(String pythonCode) {
        showLog("cmdPython", "🐍 Python Code: " + pythonCode.substring(0, Math.min(pythonCode.length(), 50)) + "...");
        return cmd("python3 -c \"" + pythonCode + "\"");
    }
    
    // close() - Verbindung schließen
    public void close() {
        showLog("close", "🔌 Schließe SSH-Verbindung...");
        
        try {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
                sftp = null;
                showLog("close", "✅ SFTP getrennt");
            }
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
                channel = null;
                showLog("close", "✅ Shell getrennt");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                session = null;
                showLog("close", "✅ Session getrennt");
            }
        } catch (Exception e) {
            showLog("close", "⚠️ Fehler beim Trennen: " + e.getMessage());
        }
        isConnected = false;
        showLog("close", "🔌 SSH-Verbindung geschlossen");
    }
    
    // Für try-with-resources (AutoCloseable)
    public void autoClose() {
        close();
    }
    
    // Getter
    public boolean isConnected() {
        return isConnected;
    }
    
    // ============================================================
    // HILFSKLASSEN
    // ============================================================
    
    // CommandResult (wie Python-Dict)
    public static class CommandResult {
        private final String stdout;
        private final String stderr;
        private final int exitCode;
        private final boolean success;
        
        public CommandResult(String stdout, String stderr, int exitCode, boolean success) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.success = success;
        }
        
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public int getExitCode() { return exitCode; }
        public boolean isSuccess() { return success; }
        
        @Override
        public String toString() {
            return "CommandResult{stdout='" + stdout + "', stderr='" + stderr + "', exitCode=" + exitCode + ", success=" + success + "}";
        }
    }
}