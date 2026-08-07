#define PY_SSIZE_T_CLEAN
#include "Python.h"
#ifndef Py_PYTHON_H
#error Python headers needed to compile C extensions, please install development version of Python.
#else

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <dirent.h>
#include <dlfcn.h>
#include <libgen.h>
#include <jni.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <android/log.h>
#include <time.h>
#include <stdarg.h>
#include <string.h>

#include "bootstrap_name.h"

#ifdef BOOTSTRAP_NAME_SDL2
#include "SDL.h"
#include "SDL_opengles2.h"
#endif

#ifdef BOOTSTRAP_NAME_SDL3
#include "SDL3/SDL.h"
#include "SDL3/SDL_main.h"
#endif

#define ENTRYPOINT_MAXLEN 128
#define P4A_MIN_VER 11

// ============================================================
// ★ ★ ★ FILE-LOGGING DEFINTIONEN ★ ★ ★
// ============================================================
#define LOG_TAG "start.c"
#define LOG_FILE_NAME "/sdcard/libmainQt.log"

static FILE* log_file = NULL;

// ============================================================
// ★ ★ ★ FILE-LOGGING FUNKTIONEN ★ ★ ★
// ============================================================
static void init_log_file() {
    if (log_file != NULL) return;
    log_file = fopen(LOG_FILE_NAME, "a");
    if (log_file == NULL) {
        // Fallback: Interner Speicher
        log_file = fopen("/data/local/tmp/libmainQt.log", "a");
    }
    if (log_file != NULL) {
        time_t now = time(NULL);
        char timestamp[64];
        strftime(timestamp, sizeof(timestamp), "%Y-%m-%d %H:%M:%S", localtime(&now));
        fprintf(log_file, "\n========================================================\n");
        fprintf(log_file, "🚀 LOG START: %s\n", timestamp);
        fprintf(log_file, "========================================================\n");
        fflush(log_file);
    }
}

static void close_log_file() {
    if (log_file != NULL) {
        time_t now = time(NULL);
        char timestamp[64];
        strftime(timestamp, sizeof(timestamp), "%Y-%m-%d %H:%M:%S", localtime(&now));
        fprintf(log_file, "\n========================================================\n");
        fprintf(log_file, "🏁 LOG ENDE: %s\n", timestamp);
        fprintf(log_file, "========================================================\n");
        fflush(log_file);
        fclose(log_file);
        log_file = NULL;
    }
}

static void log_to_file(const char *level, const char *tag, const char *msg) {
    init_log_file();
    if (log_file == NULL) return;
    time_t now = time(NULL);
    char timestamp[64];
    strftime(timestamp, sizeof(timestamp), "%H:%M:%S", localtime(&now));
    fprintf(log_file, "[%s] [%s] [%s] %s\n", timestamp, level, tag, msg);
    fflush(log_file);
}

// ============================================================
// ★ ★ ★ LOG-MAKROS (Logcat + File) ★ ★ ★
// ============================================================
#define LOG(n, x) do { \
    __android_log_write(ANDROID_LOG_INFO, (n), (x)); \
    log_to_file("INFO", (n), (x)); \
} while(0)

#define LOGI(...) do { \
    char buffer[1024]; \
    snprintf(buffer, sizeof(buffer), __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", buffer); \
    log_to_file("INFO", LOG_TAG, buffer); \
} while(0)

#define LOGE(...) do { \
    char buffer[1024]; \
    snprintf(buffer, sizeof(buffer), __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", buffer); \
    log_to_file("ERROR", LOG_TAG, buffer); \
} while(0)

#define LOGD(...) do { \
    char buffer[1024]; \
    snprintf(buffer, sizeof(buffer), __VA_ARGS__); \
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "%s", buffer); \
    log_to_file("DEBUG", LOG_TAG, buffer); \
} while(0)

static void LOGP(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_INFO, "python", "%s", buffer);
    log_to_file("INFO", "python", buffer);
}

// ============================================================
// ★ ★ ★ LOG MIT ZEITSTEMPEL ★ ★ ★
// ============================================================
static void LOG_TIMESTAMP(const char *msg) {
    time_t now = time(NULL);
    char timestamp[64];
    strftime(timestamp, sizeof(timestamp), "%H:%M:%S", localtime(&now));
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "⏰ [%s] %s", timestamp, msg);
    LOGI("%s", buffer);
}

// ============================================================
// ★ ★ ★ PYCONFIG DUMP FUNKTION ★ ★ ★
// ============================================================
// ============================================================
// ★ PYCONFIG DUMP FÜR PYTHON 3.11+ (KORRIGIERT) ★
// ============================================================
// ============================================================
// ★ PYCONFIG DUMP (NUR WICHTIGE FELDER) ★
// ============================================================
static void dump_pyconfig(PyConfig *config) {
    LOGP("====== PYCONFIG (WICHTIG) ======");
    
    // ★★★★★ 1. KRITISCHE PFADE ★★★★★
    LOGP("home=%ls", config->home ? config->home : L"(null)");
    LOGP("program_name=%ls", config->program_name ? config->program_name : L"(null)");
    LOGP("executable=%ls", config->executable ? config->executable : L"(null)");
    LOGP("prefix=%ls", config->prefix ? config->prefix : L"(null)");
    LOGP("exec_prefix=%ls", config->exec_prefix ? config->exec_prefix : L"(null)");
    LOGP("base_prefix=%ls", config->base_prefix ? config->base_prefix : L"(null)");
    LOGP("base_exec_prefix=%ls", config->base_exec_prefix ? config->base_exec_prefix : L"(null)");
    LOGP("pythonpath_env=%ls", config->pythonpath_env ? config->pythonpath_env : L"(null)");
    
    // ★★★★★ 2. MODUL-SUCHPFADE (WICHTIG!) ★★★★★
    if (config->module_search_paths.length > 0) {
        LOGP("module_search_paths_count=%zu", config->module_search_paths.length);
        for (size_t i = 0; i < config->module_search_paths.length && i < 10; i++) {
            if (config->module_search_paths.items[i]) {
                LOGP("  path[%zu]=%ls", i, config->module_search_paths.items[i]);
            } else {
                LOGP("  path[%zu]=(null)", i);
            }
        }
    } else {
        LOGP("module_search_paths_count=0");
    }
    
    // ★★★★★ 3. ARGV ★★★★★
    if (config->argv.length > 0) {
        LOGP("argv_count=%zu", config->argv.length);
        for (size_t i = 0; i < config->argv.length && i < 10; i++) {
            if (config->argv.items[i]) {
                LOGP("  argv[%zu]=%ls", i, config->argv.items[i]);
            } else {
                LOGP("  argv[%zu]=(null)", i);
            }
        }
    } else {
        LOGP("argv_count=0");
    }
    
    // ★★★★★ 4. WICHTIGE FLAGS ★★★★★
    LOGP("isolated=%d (0=normal, 1=isoliert)", config->isolated);
    LOGP("use_environment=%d (1=Env-Vars verwenden)", config->use_environment);
    LOGP("site_import=%d (1=site-packages laden)", config->site_import);
    LOGP("install_signal_handlers=%d (1=Signal-Handler)", config->install_signal_handlers);
    LOGP("verbose=%d (Ausführliche Ausgaben)", config->verbose);
    
    LOGP("====== ENDE PYCONFIG (WICHTIG) ======");
}

// ============================================================
// ★ ★ ★ ORIGINAL START.C CODE ★ ★ ★
// ============================================================
static PyObject *androidembed_log(PyObject *self, PyObject *args) {
    char *logstr = NULL;
    if (!PyArg_ParseTuple(args, "s", &logstr)) {
        return NULL;
    }
    const char *name = getenv("PYTHON_NAME");
    if (name == NULL) name = "python";
    __android_log_print(ANDROID_LOG_INFO, name, "%s", logstr);
    log_to_file("PYTHON", name, logstr);
    Py_RETURN_NONE;
}

static PyMethodDef AndroidEmbedMethods[] = {
    {"log", androidembed_log, METH_VARARGS, "Log on android platform"},
    {NULL, NULL, 0, NULL}};

#if PY_MAJOR_VERSION >= 3
static struct PyModuleDef androidembed = {PyModuleDef_HEAD_INIT, "androidembed",
                                          "", -1, AndroidEmbedMethods};

PyMODINIT_FUNC initandroidembed(void) {
    return PyModule_Create(&androidembed);
}
#else
PyMODINIT_FUNC initandroidembed(void) {
    (void)Py_InitModule("androidembed", AndroidEmbedMethods);
}
#endif

int dir_exists(char *filename) {
    struct stat st;
    if (stat(filename, &st) == 0) {
        if (S_ISDIR(st.st_mode))
            return 1;
    }
    return 0;
}

int file_exists(const char *filename) {
    return access(filename, F_OK) == 0;
}

static void get_dirname(const char *path, char *dir, size_t size) {
    strncpy(dir, path, size - 1);
    dir[size - 1] = '\0';
    char *last_slash = strrchr(dir, '/');
    if (last_slash) *last_slash = '\0';
    else dir[0] = '\0';
}

static void get_exe_name(const char *filename, char *out, size_t size) {
    size_t len = strlen(filename);
    if (len < 7) {
        strncpy(out, filename, size - 1);
        out[size - 1] = '\0';
        return;
    }
    const char *start = filename;
    if (strncmp(filename, "lib", 3) == 0) start += 3;
    size_t start_len = strlen(start);
    if (start_len > 6) {
        size_t copy_len = start_len - 6;
        if (copy_len >= size) copy_len = size - 1;
        strncpy(out, start, copy_len);
        out[copy_len] = '\0';
    } else {
        strncpy(out, start, size - 1);
        out[size - 1] = '\0';
    }
}

char *setup_symlinks() {
    LOG_TIMESTAMP("📁 START: setup_symlinks()");
    LOG("start.c", "📁 START: Creating .bin symlinks");

    Dl_info info;
    char lib_path[512];
    char *interpreter = NULL;

    if (!(dladdr((void*)setup_symlinks, &info) && info.dli_fname)) {
        LOGE("❌ setup_symlinks: failed to get libdir");
        LOG("start.c", "❌ failed to get libdir");
        return interpreter;
    }

    strncpy(lib_path, info.dli_fname, sizeof(lib_path) - 1);
    lib_path[sizeof(lib_path) - 1] = '\0';

    char native_lib_dir[512];
    get_dirname(lib_path, native_lib_dir, sizeof(native_lib_dir));
    if (native_lib_dir[0] == '\0') {
        LOGE("❌ setup_symlinks: could not determine lib directory");
        LOG("start.c", "❌ could not determine lib directory");
        return interpreter;
    }

    const char *files_dir_env = getenv("ANDROID_APP_PATH");
    if (files_dir_env == NULL) {
        LOGE("❌ setup_symlinks: ANDROID_APP_PATH is NULL");
        LOG("start.c", "❌ ANDROID_APP_PATH is NULL");
        return interpreter;
    }

    char bin_dir[512];
    snprintf(bin_dir, sizeof(bin_dir), "%s/.bin", files_dir_env);
    if (mkdir(bin_dir, 0755) != 0 && errno != EEXIST) {
        LOGE("❌ Failed to create .bin directory: %s", strerror(errno));
        LOG("start.c", "❌ Failed to create .bin directory");
        return interpreter;
    }

    DIR *dir = opendir(native_lib_dir);
    if (!dir) {
        LOGE("❌ Failed to open native lib dir");
        LOG("start.c", "❌ Failed to open native lib dir");
        return interpreter;
    }

    struct dirent *entry;
    int symlink_count = 0;
    LOG("start.c", "🔍 Scanning for bin.so files");

    while ((entry = readdir(dir)) != NULL) {
        const char *name = entry->d_name;
        size_t len = strlen(name);

        if (len < 7) continue;
        if (strcmp(name + len - 6, "bin.so") != 0) continue;

        char exe_name[128];
        get_exe_name(name, exe_name, sizeof(exe_name));

        char src[512], dst[512];
        snprintf(src, sizeof(src), "%s/%s", native_lib_dir, name);
        snprintf(dst, sizeof(dst), "%s/%s", bin_dir, exe_name);

        if (strcmp(exe_name, "python") == 0) {
            interpreter = strdup(dst);
        }

        struct stat st;
        if (lstat(dst, &st) == 0) continue;
        if (symlink(src, dst) == 0) {
            LOGP("symlink: %s -> %s", name, exe_name);
            symlink_count++;
        } else {
            LOGE("❌ Symlink failed: %s -> %s", name, exe_name);
        }
    }

    closedir(dir);
    LOGP("✅ Created %d symlinks", symlink_count);

    const char *old_path = getenv("PATH");
    char new_path[1024];
    if (old_path && strlen(old_path) > 0) {
        snprintf(new_path, sizeof(new_path), "%s:%s", old_path, bin_dir);
    } else {
        snprintf(new_path, sizeof(new_path), "%s", bin_dir);
    }
    setenv("PATH", new_path, 1);

    setenv("LD_LIBRARY_PATH", native_lib_dir, 1);

    LOG_TIMESTAMP("✅ END: setup_symlinks() completed");
    LOG("start.c", "✅ setup_symlinks completed");
    return interpreter;
}

// ============================================================
// ★ PID IN UMWELTVARIABLE SPEICHERN ★
// ============================================================
static void save_python_pid_to_env() {
    pid_t pid = getpid();
    char pid_str[32];
    snprintf(pid_str, sizeof(pid_str), "%d", pid);
    
    setenv("PID_PYTHON", pid_str, 1);
    LOGP("📌 Python PID in Umgebungsvariable gespeichert: %s", pid_str);
}

// ============================================================
// ★ SICHERER PYTHON FINALIZE ★
// ============================================================
static void safe_finalize_python() {
    LOGP("🔄 safe_finalize_python() aufgerufen...");
    
    // 1. Prüfen ob Python initialisiert ist
    if (!Py_IsInitialized()) {
        LOGP("ℹ️ Python ist nicht initialisiert – überspringe Finalize");
        return;
    }
    
    LOGP("⚠️ Python läuft noch – finalisiere...");
    
    // 2. Thread-State prüfen
    PyThreadState *tstate = PyThreadState_Get();
    if (tstate == NULL) {
        LOGP("⚠️ Kein Thread-State vorhanden – überspringe Finalize");
        return;
    }
    
    // 3. Python finalisieren
    #if PY_MAJOR_VERSION < 3
        Py_Finalize();
        LOGP("✅ Py_Finalize() erfolgreich");
    #else
        int result = Py_FinalizeEx();
        if (result != 0) {
            LOGP("⚠️ Py_FinalizeEx() gab Fehler: %d", result);
        } else {
            LOGP("✅ Py_FinalizeEx() erfolgreich");
        }
    #endif
    
    // 4. Prüfen ob Python wirklich weg ist
    if (Py_IsInitialized()) {
        LOGP("⚠️ Python läuft immer noch! HARTEN STOP...");
        _exit(0);
    }
    
    LOGP("✅ safe_finalize_python() abgeschlossen");
}

// ============================================================
// ★ ★ ★ Reset Python ★ ★ ★
// ============================================================
static void reset_python() {
    LOGP("🔄 Python wird zurückgesetzt...");
    
    // 1. Prüfen ob Python läuft
    if (Py_IsInitialized()) {
        LOGP("⚠️ Python läuft noch – wird finalisiert...");
        // Schließt zumindest noch die Log-Datei, falls offen
        close_log_file();
        
        // Zwingt das Betriebssystem, den Prozess sofort zu beenden.
        // Beim nächsten Start von außen startet alles garantiert frisch.
        _exit(0); 
        
        // 2. Python finalisieren
        //Py_Finalize();
        //LOGP("✅ Python finalisiert");
        
        // 3. Kurz warten (damit alles aufgeräumt wird)
        //usleep(100000); // 100ms warten
    } else {
        LOGP("ℹ️ Python läuft nicht – kein Reset nötig");
    }
    
    LOGP("✅ Python bereit für Neustart");
}

// ============================================================
// ★ ★ ★ MAIN ★ ★ ★
// ============================================================
int main(int argc, char *argv[]) {

    LOGP("🚀 Python wird (neu) gestartet...");
    
    // 1. Reset
    reset_python();
    
    init_log_file();
    LOG_TIMESTAMP("🚀 START: main() - Python for Android initializing");
    LOG("start.c", "🐍 Starting Python initialization");

    char *env_argument = NULL;
    char *env_entrypoint = NULL;
    char *env_logname = NULL;
    char entrypoint[ENTRYPOINT_MAXLEN];
    int ret = 0;
    FILE *fd;

    LOGP("Initializing Python for Android");

    // Set a couple of built-in environment vars:
    setenv("P4A_BOOTSTRAP", bootstrap_name, 1);
    env_argument = getenv("ANDROID_ARGUMENT");
    if (env_argument == NULL) {
        LOGE("❌ ANDROID_ARGUMENT is NULL!");
        LOG("start.c", "❌ ANDROID_ARGUMENT is NULL");
        close_log_file();
        return -1;
    }
    setenv("ANDROID_APP_PATH", env_argument, 1);
    env_entrypoint = getenv("ANDROID_ENTRYPOINT");
    env_logname = getenv("PYTHON_NAME");
    if (!getenv("ANDROID_UNPACK")) {
        setenv("ANDROID_UNPACK", env_argument, 1);
    }
    if (env_logname == NULL) {
        env_logname = "python";
        setenv("PYTHON_NAME", "python", 1);
    }

    LOGP("Setting additional env vars from p4a_env_vars.txt");
    char env_file_path[256];
    snprintf(env_file_path, sizeof(env_file_path),
             "%s/p4a_env_vars.txt", getenv("ANDROID_UNPACK"));
    FILE *env_file_fd = fopen(env_file_path, "r");
    if (env_file_fd) {
        LOG("start.c", "✅ p4a_env_vars.txt found");
        char* line = NULL;
        size_t len = 0;
        while (getline(&line, &len, env_file_fd) != -1) {
            if (strlen(line) > 0) {
                char *eqsubstr = strstr(line, "=");
                if (eqsubstr) {
                    size_t eq_pos = eqsubstr - line;

                    char env_name[256];
                    strncpy(env_name, line, sizeof(env_name));
                    env_name[eq_pos] = '\0';

                    char env_value[256];
                    strncpy(env_value, (char*)(line + eq_pos + 1), sizeof(env_value));
                    if (strlen(env_value) > 0 &&
                        env_value[strlen(env_value)-1] == '\n') {
                        env_value[strlen(env_value)-1] = '\0';
                        if (strlen(env_value) > 0 &&
                            env_value[strlen(env_value)-1] == '\r') {
                            env_value[strlen(env_value)-1] = '\0';
                        }
                    }
                    setenv(env_name, env_value, 1);
                    LOGD("🔧 %s = %s", env_name, env_value);
                }
            }
        }
        fclose(env_file_fd);
    } else {
        LOGP("⚠️ no p4a_env_vars.txt found!");
        LOG("start.c", "⚠️ no p4a_env_vars.txt found");
    }

    LOGP("Changing directory to '%s'", env_argument);
    chdir(env_argument);
    LOG("start.c", "📁 Changed directory");

    char *interpreter = setup_symlinks();
    if (interpreter != NULL) {
        LOGP("✅ Python interpreter path: %s", interpreter);
        LOG("start.c", "✅ Python interpreter ready");
    } else {
        LOGE("❌ Python interpreter NOT found!");
        LOG("start.c", "❌ Python interpreter NOT found");
    }

#if PY_MAJOR_VERSION < 3
    Py_NoSiteFlag = 1;
#endif

#if PY_MAJOR_VERSION >= 3
    PyImport_AppendInittab("androidembed", initandroidembed);
#endif

    LOGP("Preparing to initialize python");
    LOG("start.c", "📁 Preparing Python init");

    const char *unpack_dir = getenv("ANDROID_UNPACK");
    char python_bundle_dir[512];
    char python_home[512];
    char python_path[1024];
    
    LOGP("🔍 ANDROID_UNPACK = %s", unpack_dir);
    LOG("start.c", "🔍 ANDROID_UNPACK value");

    if (unpack_dir == NULL) {
        LOGE("❌ ANDROID_UNPACK is NULL!");
        LOG("start.c", "❌ ANDROID_UNPACK is NULL");
        close_log_file();
        return -1;
    }

    snprintf(python_bundle_dir, sizeof(python_bundle_dir),
             "%s/_python_bundle", unpack_dir);
    snprintf(python_home, sizeof(python_home), "%s", python_bundle_dir);
    snprintf(python_path, sizeof(python_path), "%s/stdlib.zip:%s/modules", 
             python_bundle_dir, python_bundle_dir);

    setenv("PYTHONHOME", python_home, 1);
    setenv("PYTHONPATH", python_path, 1);
    setenv("PYTHONUTF8", "1", 1);

    LOGP("🐍 PYTHONHOME = %s (SDL2-Stil)", python_home);
    LOGP("🐍 PYTHONPATH = %s (SDL2-Stil)", python_path);
    LOG("start.c", "✅ PYTHONHOME und PYTHONPATH gesetzt (SDL2-Stil)");

    // ============================================================
    // ★ ★ ★ PYTHON INIT ★ ★ ★
    // ============================================================
    #if PY_MAJOR_VERSION >= 3 && PY_MINOR_VERSION >= P4A_MIN_VER
        PyConfig config;
        PyConfig_InitPythonConfig(&config);
        config.program_name = L"android_python";

        // ★ ★ ★ config.home SETZEN ★ ★ ★
        wchar_t *wchar_home = Py_DecodeLocale(python_home, NULL);
        if (wchar_home != NULL) {
            config.home = wchar_home;
            config.prefix = wchar_home;
            config.exec_prefix = wchar_home;
            LOGP("✅ config.home = %s (SDL2-Stil)", python_home);
        } else {
            LOGE("❌ Py_DecodeLocale failed for PYTHONHOME");
            LOG("start.c", "❌ Py_DecodeLocale failed");
            close_log_file();
            return -1;
        }

        // ★ ★ ★ ZUSÄTZLICHE CONFIG-FELDER ★ ★ ★
        config.isolated = 0;
        config.site_import = 1;
        config.install_signal_handlers = 1;
        config.use_environment = 1;
        config.filesystem_encoding = NULL;
        config.filesystem_errors = NULL;
        LOGP("✅ Zusätzliche config-Felder gesetzt");

        // ★ ★ ★ config.module_search_paths SETZEN ★ ★ ★
        config.module_search_paths_set = 1;

        // stdlib.zip
        char stdlib_path[512];
        snprintf(stdlib_path, sizeof(stdlib_path), "%s/stdlib.zip", python_bundle_dir);
        wchar_t *wchar_stdlib = Py_DecodeLocale(stdlib_path, NULL);
        if (wchar_stdlib != NULL) {
            PyWideStringList_Append(&config.module_search_paths, wchar_stdlib);
            LOGP("✅ stdlib.zip: %s", stdlib_path);
        }

        // modules
        char modules_path[512];
        snprintf(modules_path, sizeof(modules_path), "%s/modules", python_bundle_dir);
        wchar_t *wchar_modules = Py_DecodeLocale(modules_path, NULL);
        if (wchar_modules != NULL) {
            PyWideStringList_Append(&config.module_search_paths, wchar_modules);
            LOGP("✅ modules: %s", modules_path);
        }

        // site-packages
        char site_packages_path[512];
        snprintf(site_packages_path, sizeof(site_packages_path), "%s/site-packages", python_bundle_dir);
        wchar_t *wchar_site = Py_DecodeLocale(site_packages_path, NULL);
        if (wchar_site != NULL) {
            PyWideStringList_Append(&config.module_search_paths, wchar_site);
            LOGP("✅ site-packages: %s", site_packages_path);
        }

        // ★ ★ ★ DEBUG: config vor Initialize ausgeben ★ ★ ★
        LOGP("🔍 config.isolated = %d", config.isolated);
        LOGP("🔍 config.site_import = %d", config.site_import);
        LOGP("🔍 config.use_environment = %d", config.use_environment);
        LOGP("🔍 config.module_search_paths count = %d", 
              config.module_search_paths.length);

        // ★ ★ ★ KOMPLETTE CONFIG AUSGEBEN ★ ★ ★
        dump_pyconfig(&config);

        PyStatus status = Py_InitializeFromConfig(&config);
        if (PyStatus_Exception(status)) {
            LOGE("❌ Python initialization failed: %s", status.err_msg);
            LOGP("Python initialization failed:");
            LOGP(status.err_msg);
            LOG("start.c", "❌ Python initialization failed");
            // Fallback: Py_Initialize()
            Py_Initialize();
            LOGP("✅ Fallback: Py_Initialize() used");
            LOG("start.c", "✅ Fallback: Py_Initialize() used");
        } else {
            LOGP("✅ Python initialized via Py_InitializeFromConfig()");
            LOG("start.c", "✅ Python initialized via Py_InitializeFromConfig()");
            
            // ★ ★ ★ 2. NACH DEM START: NEUE PID SPEICHERN ★ ★ ★
            save_python_pid_to_env();
    
        }
    #else
        Py_Initialize();
        LOGP("Python initialized using legacy Py_Initialize().");
        LOG("start.c", "✅ Python initialized (legacy)");
    #endif

    LOGP("Initialized python");
    LOG("start.c", "✅ Python initialized");

    #if PY_VERSION_HEX < 0x03090000
        LOGP("Initializing threads (required for Python < 3.9)");
        PyEval_InitThreads();
    #endif

#if PY_MAJOR_VERSION < 3
    initandroidembed();
#endif

    PyRun_SimpleString(
        "import androidembed\n"
        "androidembed.log('testing python print redirection')"
    );

    PyRun_SimpleString("import io, sys, posix\n");

    char add_site_packages_dir[256];

    if (dir_exists(python_bundle_dir)) {
        LOGP("✅ _python_bundle dir exists");
        LOG("start.c", "✅ _python_bundle dir exists");
        
        snprintf(add_site_packages_dir, 256,
                 "sys.path.append('%s/site-packages')",
                 python_bundle_dir);

        PyRun_SimpleString("import sys, os\n"
                          "from os.path import realpath, join, dirname");

        char buf_exec[512];
        char buf_argv[512];
        snprintf(buf_exec, sizeof(buf_exec), "sys.executable = '%s'\n", interpreter);
        snprintf(buf_argv, sizeof(buf_argv), "sys.argv = ['%s']\n", interpreter);
        PyRun_SimpleString(buf_exec);
        PyRun_SimpleString(buf_argv);

        PyRun_SimpleString(add_site_packages_dir);
        PyRun_SimpleString("sys.path = ['.'] + sys.path");
        PyRun_SimpleString("os.environ['PYTHONPATH'] = ':'.join(sys.path)");
        LOG("start.c", "✅ sys.path configured");
    } else {
        LOGE("❌ _python_bundle does NOT exist!");
        LOG("start.c", "❌ _python_bundle does NOT exist");
        close_log_file();
        return -1;
    }

    PyRun_SimpleString(
        "class LogFile(io.IOBase):\n"
        "    def __init__(self):\n"
        "        self.__buffer = ''\n"
        "    def readable(self):\n"
        "        return False\n"
        "    def writable(self):\n"
        "        return True\n"
        "    def write(self, s):\n"
        "        s = self.__buffer + s\n"
        "        lines = s.split('\\n')\n"
        "        for l in lines[:-1]:\n"
        "            androidembed.log(l.replace('\\x00', ''))\n"
        "        self.__buffer = lines[-1]\n"
        "sys.stdout = sys.stderr = LogFile()\n"
        "print('Android kivy bootstrap done. __name__ is', __name__)");

#if PY_MAJOR_VERSION < 3
    PyRun_SimpleString("import site; print site.getsitepackages()\n");
#endif

    // ============================================================
    // ★ ★ ★ ENTRYPOINT PRÜFEN ★ ★ ★
    // ============================================================
    LOGP("🔍 ENTRYPOINT: %s", env_entrypoint);
    LOG("start.c", "🔍 ENTRYPOINT value");

    char *dot = strrchr(env_entrypoint, '.');
    char *ext = ".pyc";
    if (dot <= 0) {
        LOGE("❌ Invalid entrypoint, abort.");
        LOG("start.c", "❌ Invalid entrypoint, abort.");
        close_log_file();
        return -1;
    }
    if (strlen(env_entrypoint) > ENTRYPOINT_MAXLEN - 2) {
        LOGE("❌ Entrypoint path is too long, try increasing ENTRYPOINT_MAXLEN.");
        LOG("start.c", "❌ Entrypoint path too long");
        close_log_file();
        return -1;
    }
    if (!strcmp(dot, ext)) {
        if (!file_exists(env_entrypoint)) {
            strcpy(entrypoint, env_entrypoint);
            entrypoint[strlen(env_entrypoint) - 1] = '\0';
            LOGP("📄 Fallback to: %s", entrypoint);
            if (!file_exists(entrypoint)) {
                LOGE("❌ Entrypoint not found (.pyc, fallback on .py), abort");
                LOG("start.c", "❌ Entrypoint not found");
                close_log_file();
                return -1;
            }
        } else {
            strcpy(entrypoint, env_entrypoint);
            LOGP("📄 Entrypoint (pyc): %s", entrypoint);
        }
    } else if (!strcmp(dot, ".py")) {
        strcpy(entrypoint, env_entrypoint);
        entrypoint[strlen(env_entrypoint) + 1] = '\0';
        entrypoint[strlen(env_entrypoint)] = 'c';
        if (!file_exists(entrypoint)) {
            if (!file_exists(env_entrypoint)) {
                LOGE("❌ Entrypoint not found (.py), abort.");
                LOG("start.c", "❌ Entrypoint not found (.py)");
                close_log_file();
                return -1;
            }
            strcpy(entrypoint, env_entrypoint);
            LOGP("📄 Entrypoint (py): %s", entrypoint);
        } else {
            LOGP("📄 Entrypoint (pyc): %s", entrypoint);
        }
    } else {
        LOGE("❌ Entrypoint have an invalid extension (must be .py or .pyc), abort.");
        LOG("start.c", "❌ Invalid entrypoint extension");
        close_log_file();
        return -1;
    }

    LOGP("✅ FINAL ENTRYPOINT: %s", entrypoint);
    LOG("start.c", "✅ FINAL ENTRYPOINT");

    // ============================================================
    // ★ ★ ★ ENTRYPOINT AUSFÜHREN (MIT FEHLER-DETAILS) ★ ★ ★
    // ============================================================
    fd = fopen(entrypoint, "r");
    if (fd == NULL) {
        LOGE("❌ Open the entrypoint failed: %s", entrypoint);
        LOG("start.c", "❌ Open entrypoint failed");
        close_log_file();
        return -1;
    }

    LOGP("✅ Running Python script: %s", entrypoint);
    LOG("start.c", "✅ Running Python script");

    ret = PyRun_SimpleFile(fd, entrypoint);
    fclose(fd);

    // ★ ★ ★ FEHLER DETAILS AUSGEBEN ★ ★ ★
    if (PyErr_Occurred() != NULL) {
        ret = 1;
        
        // 1. Fehler in Logcat ausgeben
        PyErr_Print();
        
        // 2. Fehler in die Log-Datei schreiben
        PyObject *pType, *pValue, *pTraceback;
        PyErr_Fetch(&pType, &pValue, &pTraceback);
        
        if (pValue != NULL) {
            PyObject *pStr = PyObject_Str(pValue);
            if (pStr != NULL) {
                const char *error_msg = PyUnicode_AsUTF8(pStr);
                if (error_msg != NULL) {
                    LOGP("❌ Python Fehler: %s", error_msg);
                    LOG("start.c", error_msg);
                }
                Py_DECREF(pStr);
            }
            Py_XDECREF(pType);
            Py_XDECREF(pValue);
            Py_XDECREF(pTraceback);
        } else {
            LOGP("❌ Python Fehler (keine Details)");
            LOG("start.c", "❌ Python Fehler (keine Details)");
        }
        
        // 3. stdout leeren
        PyObject *f = PySys_GetObject("stdout");
        if (f != NULL && PyFile_WriteString("\n", f)) {
            PyErr_Clear();
        }
    } else {
        LOGP("✅ Python script executed successfully");
        LOG("start.c", "✅ Python script executed successfully");
    }

    LOGP("✅ Python for android ended with code: %d", ret);
    LOG("start.c", "✅ Python for android ended");

/*
#if PY_MAJOR_VERSION < 3
    Py_Finalize();
    LOGP("Unexpectedly reached Py_FinalizeEx(), but was successful.");
#else
    if (Py_FinalizeEx() != 0) {
        LOGP("Unexpectedly reached Py_FinalizeEx(), and got error!");
    }
#endif
*/

    // ★ ★ ★ SICHERER FINALIZE AM ENDE ★ ★ ★
    safe_finalize_python();
    
    close_log_file();
    exit(ret);
    return ret;
}


// ============================================================
// ★ ★ ★ JNI-FUNKTIONEN ★ ★ ★
// ============================================================
JNIEXPORT void JNICALL Java_org_kivy_android_PythonService_nativeStart(
    JNIEnv *env,
    jobject thiz,
    jstring j_android_private,
    jstring j_android_argument,
    jstring j_service_entrypoint,
    jstring j_python_name,
    jstring j_python_home,
    jstring j_python_path,
    jstring j_arg) {
    jboolean iscopy;
    const char *android_private =
        (*env)->GetStringUTFChars(env, j_android_private, &iscopy);
    const char *android_argument =
        (*env)->GetStringUTFChars(env, j_android_argument, &iscopy);
    const char *service_entrypoint =
        (*env)->GetStringUTFChars(env, j_service_entrypoint, &iscopy);
    const char *python_name =
        (*env)->GetStringUTFChars(env, j_python_name, &iscopy);
    const char *python_home =
        (*env)->GetStringUTFChars(env, j_python_home, &iscopy);
    const char *python_path =
        (*env)->GetStringUTFChars(env, j_python_path, &iscopy);
    const char *arg = (*env)->GetStringUTFChars(env, j_arg, &iscopy);

    setenv("ANDROID_PRIVATE", android_private, 1);
    setenv("ANDROID_ARGUMENT", android_argument, 1);
    setenv("ANDROID_APP_PATH", android_argument, 1);
    setenv("ANDROID_ENTRYPOINT", service_entrypoint, 1);
    setenv("PYTHONOPTIMIZE", "2", 1);
    setenv("PYTHON_NAME", python_name, 1);
    setenv("PYTHONHOME", python_home, 1);
    setenv("PYTHONPATH", python_path, 1);
    setenv("PYTHON_SERVICE_ARGUMENT", arg, 1);
    setenv("P4A_BOOTSTRAP", bootstrap_name, 1);

    char *argv[] = {"."};
    /* ANDROID_ARGUMENT points to service subdir,
     * so main() will run main.py from this dir
     */
    main(1, argv);
}

#if defined(BOOTSTRAP_NAME_WEBVIEW) || defined(BOOTSTRAP_NAME_SERVICEONLY)
void Java_org_kivy_android_PythonActivity_nativeSetenv(
    JNIEnv* env, jclass cls,
    jstring name, jstring value)
{
    const char *utfname = (*env)->GetStringUTFChars(env, name, NULL);
    const char *utfvalue = (*env)->GetStringUTFChars(env, value, NULL);

    setenv(utfname, utfvalue, 1);

    (*env)->ReleaseStringUTFChars(env, name, utfname);
    (*env)->ReleaseStringUTFChars(env, value, utfvalue);
}

void Java_org_kivy_android_PythonActivity_nativeInit(JNIEnv* env, jclass cls, jobject obj)
{
    char *argv[2];
    argv[0] = "Python_app";
    argv[1] = NULL;
    main(1, argv);
}
#endif

#endif