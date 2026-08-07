#define PY_SSIZE_T_CLEAN
#include "Python.h"
#ifndef Py_PYTHON_H
#error Python headers needed to compile C extensions, please install development version of Python.
#endif

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
// ★ ★ ★ LOG-FUNKTIONEN (mit Datei-Logging) ★ ★ ★
// ============================================================
#define LOG_TAG "start.c"
#define LOG_FILE_NAME "/sdcard/libmain_sdl2.log"

static FILE* log_file = NULL;

static void init_log_file() {
    if (log_file != NULL) return;
    log_file = fopen(LOG_FILE_NAME, "a");
    if (log_file == NULL) {
        log_file = fopen("/data/local/tmp/libmain_sdl2.log", "a");
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
// ★ ★ ★ LOG-MAKROS ★ ★ ★
// ============================================================
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static void LOGP(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_INFO, "python", "%s", buffer);
    log_to_file("INFO", "python", buffer);
}

static void LOG_TO_FILE(const char *tag, const char *msg) {
    __android_log_print(ANDROID_LOG_INFO, tag, "%s", msg);
    log_to_file("INFO", tag, msg);
}

static void LOG_TIMESTAMP(const char *msg) {
    time_t now = time(NULL);
    char timestamp[64];
    strftime(timestamp, sizeof(timestamp), "%H:%M:%S", localtime(&now));
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "⏰ [%s] %s", timestamp, msg);
    LOGD("%s", buffer);
    log_to_file("DEBUG", LOG_TAG, buffer);
}

// ============================================================
// ★ ★ ★ ANDROIDEMBED LOG MODUL ★ ★ ★
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
    {NULL, NULL, 0, NULL}
};

// ============================================================
// ★ ★ ★ PYTHON MODUL INIT ★ ★ ★
// ============================================================
#if PY_MAJOR_VERSION >= 3
PyMODINIT_FUNC PyInit_androidembed(void) {
    static struct PyModuleDef module_def = {
        PyModuleDef_HEAD_INIT,
        "androidembed",
        NULL,
        -1,
        AndroidEmbedMethods
    };
    return PyModule_Create(&module_def);
}
#else
PyMODINIT_FUNC initandroidembed(void) {
    (void)Py_InitModule("androidembed", AndroidEmbedMethods);
}
#endif

// ============================================================
// ★ ★ ★ ORIGINAL START.C FUNKTIONEN ★ ★ ★
// ============================================================

int dir_exists(char *filename) {
    struct stat st;
    if (stat(filename, &st) == 0) {
        if (S_ISDIR(st.st_mode)) return 1;
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
    LOG_TO_FILE("start.c", "📁 START: Creating .bin symlinks");
    const char *files_dir_env = getenv("ANDROID_APP_PATH");
    if (files_dir_env == NULL) {
        LOGE("❌ setup_symlinks: ANDROID_APP_PATH is NULL");
        LOG_TO_FILE("start.c", "❌ ANDROID_APP_PATH is NULL");
        return NULL;
    }
    Dl_info info;
    char lib_path[512];
    char *interpreter = NULL;
    if (!(dladdr((void*)setup_symlinks, &info) && info.dli_fname)) {
        LOGE("❌ setup_symlinks: failed to get libdir");
        LOG_TO_FILE("start.c", "❌ failed to get libdir");
        return interpreter;
    }
    strncpy(lib_path, info.dli_fname, sizeof(lib_path) - 1);
    lib_path[sizeof(lib_path) - 1] = '\0';
    char native_lib_dir[512];
    get_dirname(lib_path, native_lib_dir, sizeof(native_lib_dir));
    if (native_lib_dir[0] == '\0') {
        LOGE("❌ setup_symlinks: could not determine lib directory");
        LOG_TO_FILE("start.c", "❌ could not determine lib directory");
        return interpreter;
    }
    char bin_dir[512];
    snprintf(bin_dir, sizeof(bin_dir), "%s/.bin", files_dir_env);
    if (mkdir(bin_dir, 0755) != 0 && errno != EEXIST) {
        LOGE("❌ Failed to create .bin directory: %s", strerror(errno));
        LOG_TO_FILE("start.c", "❌ Failed to create .bin directory");
        return interpreter;
    }
    DIR *dir = opendir(native_lib_dir);
    if (!dir) {
        LOGE("❌ Failed to open native lib dir");
        LOG_TO_FILE("start.c", "❌ Failed to open native lib dir");
        return interpreter;
    }
    struct dirent *entry;
    int symlink_count = 0;
    LOG_TO_FILE("start.c", "🔍 Scanning for bin.so files");
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
    LOG_TO_FILE("start.c", "✅ setup_symlinks completed");
    return interpreter;
}

// ============================================================
// ★ ★ ★ Reset Python ★ ★ ★
// ============================================================
static void reset_python() {
    LOGP("🔄 Python wird zurückgesetzt...");
    
    // 1. Prüfen ob Python läuft
    if (Py_IsInitialized()) {
        LOGP("⚠️ Python läuft noch – wird finalisiert...");
        
        // 2. Python finalisieren
        Py_Finalize();
        LOGP("✅ Python finalisiert");
        
        // 3. Kurz warten (damit alles aufgeräumt wird)
        usleep(100000); // 100ms warten
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
    LOG_TO_FILE("start.c", "🐍 Starting Python initialization");

    // ★ ★ ★ ALLE UMWELTVARIABLEN LOGGEN ★ ★ ★
    LOGP("🔍 ENV: ANDROID_UNPACK = %s", getenv("ANDROID_UNPACK"));
    LOGP("🔍 ENV: ANDROID_APP_PATH = %s", getenv("ANDROID_APP_PATH"));
    LOGP("🔍 ENV: PYTHONHOME = %s", getenv("PYTHONHOME"));
    LOGP("🔍 ENV: PYTHONPATH = %s", getenv("PYTHONPATH"));
    LOGP("🔍 ENV: LD_LIBRARY_PATH = %s", getenv("LD_LIBRARY_PATH"));

    char *env_argument = NULL;
    char *env_entrypoint = NULL;
    char *env_logname = NULL;
    char entrypoint[ENTRYPOINT_MAXLEN];
    int ret = 0;
    FILE *fd;

    LOGP("Initializing Python for Android");

    setenv("P4A_BOOTSTRAP", bootstrap_name, 1);
    env_argument = getenv("ANDROID_ARGUMENT");
    if (env_argument == NULL) {
        LOGE("❌ ANDROID_ARGUMENT is NULL!");
        LOG_TO_FILE("start.c", "❌ ANDROID_ARGUMENT is NULL");
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
        LOG_TO_FILE("start.c", "✅ p4a_env_vars.txt found");
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
                    if (strlen(env_value) > 0 && env_value[strlen(env_value)-1] == '\n') {
                        env_value[strlen(env_value)-1] = '\0';
                        if (strlen(env_value) > 0 && env_value[strlen(env_value)-1] == '\r') {
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
        LOG_TO_FILE("start.c", "⚠️ no p4a_env_vars.txt found");
    }

    LOGP("Changing directory to '%s'", env_argument);
    chdir(env_argument);
    LOG_TO_FILE("start.c", "📁 Changed directory");

    char *interpreter = setup_symlinks();
    if (interpreter != NULL) {
        LOGP("✅ Python interpreter path: %s", interpreter);
        LOG_TO_FILE("start.c", "✅ Python interpreter ready");
    } else {
        LOGE("❌ Python interpreter NOT found!");
        LOG_TO_FILE("start.c", "❌ Python interpreter NOT found");
    }

#if PY_MAJOR_VERSION < 3
    Py_NoSiteFlag = 1;
#endif

#if PY_MAJOR_VERSION >= 3
    PyImport_AppendInittab("androidembed", PyInit_androidembed);
#endif

    LOGP("Preparing to initialize python");
    LOG_TO_FILE("start.c", "📁 Preparing Python init");

    char python_bundle_dir[256];
    snprintf(python_bundle_dir, 256,
             "%s/_python_bundle", getenv("ANDROID_UNPACK"));
    LOGP("🔍 python_bundle_dir = %s", python_bundle_dir);
    LOG_TO_FILE("start.c", "🔍 python_bundle_dir");

#if PY_MAJOR_VERSION >= 3
    #if PY_MINOR_VERSION >= P4A_MIN_VER
        PyConfig config;
        PyConfig_InitPythonConfig(&config);
        config.program_name = L"android_python";
        LOGP("🔍 PyConfig initialisiert (Python 3.11+)");
    #else
        Py_SetProgramName(L"android_python");
        LOGP("🔍 Py_SetProgramName (Python < 3.11)");
    #endif
#else
    Py_SetProgramName("android_python");
#endif

    if (dir_exists(python_bundle_dir)) {
        LOGP("✅ _python_bundle dir exists");
        LOG_TO_FILE("start.c", "✅ _python_bundle dir exists");
        #if PY_MAJOR_VERSION >= 3
            #if PY_MINOR_VERSION >= P4A_MIN_VER
                wchar_t wchar_zip_path[256];
                wchar_t wchar_modules_path[256];
                swprintf(wchar_zip_path, 256, L"%s/stdlib.zip", python_bundle_dir);
                swprintf(wchar_modules_path, 256, L"%s/modules", python_bundle_dir);
                LOGP("🔍 stdlib.zip: %ls", wchar_zip_path);
                LOGP("🔍 modules: %ls", wchar_modules_path);
                config.module_search_paths_set = 1;
                PyWideStringList_Append(&config.module_search_paths, wchar_zip_path);
                PyWideStringList_Append(&config.module_search_paths, wchar_modules_path);
                LOGP("✅ module_search_paths gesetzt (Anzahl: %d)", config.module_search_paths.length);
            #else
                char paths[512];
                snprintf(paths, 512, "%s/stdlib.zip:%s/modules", python_bundle_dir, python_bundle_dir);
                wchar_t *wchar_paths = Py_DecodeLocale(paths, NULL);
                Py_SetPath(wchar_paths);
                LOGP("✅ Py_SetPath: %s", paths);
            #endif
        #endif
        LOGP("set wchar paths...");
    } else {
        LOGP("⚠️ _python_bundle does not exist!");
        LOG_TO_FILE("start.c", "⚠️ _python_bundle does not exist");
    }

#if PY_MAJOR_VERSION >= 3 && PY_MINOR_VERSION >= P4A_MIN_VER
    LOGP("🔍 Rufe Py_InitializeFromConfig() auf...");
    PyStatus status = Py_InitializeFromConfig(&config);
    if (PyStatus_Exception(status)) {
        LOGE("❌ Python initialization failed: %s", status.err_msg);
        LOGP("Python initialization failed:");
        LOGP(status.err_msg);
        LOG_TO_FILE("start.c", "❌ Python initialization failed");
    } else {
        LOGP("✅ Python initialized via Py_InitializeFromConfig()");
        LOG_TO_FILE("start.c", "✅ Python initialized via Py_InitializeFromConfig()");
    }
#else
    Py_Initialize();
    LOGP("Python initialized using legacy Py_Initialize().");
    LOG_TO_FILE("start.c", "✅ Python initialized");
#endif

    LOGP("Initialized python");
    LOG_TO_FILE("start.c", "✅ Python initialized");

    #if PY_VERSION_HEX < 0x03090000
        LOGP("Initializing threads (required for Python < 3.9)");
        PyEval_InitThreads();
    #endif

    PyRun_SimpleString(
        "import androidembed\n"
        "androidembed.log('testing python print redirection')"
    );

    PyRun_SimpleString("import io, sys, posix\n");

    char add_site_packages_dir[256];

    if (dir_exists(python_bundle_dir)) {
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
        LOG_TO_FILE("start.c", "✅ sys.path configured");
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

    // ★ ★ ★ ENTRYPOINT PRÜFUNG MIT LOGS ★ ★ ★
    LOGP("🔍 ENTRYPOINT: %s", env_entrypoint);
    LOG_TO_FILE("start.c", "🔍 ENTRYPOINT value");

    char *dot = strrchr(env_entrypoint, '.');
    char *ext = ".pyc";
    if (dot <= 0) {
        LOGE("❌ Invalid entrypoint, abort.");
        LOG_TO_FILE("start.c", "❌ Invalid entrypoint, abort.");
        close_log_file();
        return -1;
    }
    if (strlen(env_entrypoint) > ENTRYPOINT_MAXLEN - 2) {
        LOGE("❌ Entrypoint path is too long, try increasing ENTRYPOINT_MAXLEN.");
        LOG_TO_FILE("start.c", "❌ Entrypoint path too long");
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
                LOG_TO_FILE("start.c", "❌ Entrypoint not found");
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
                LOG_TO_FILE("start.c", "❌ Entrypoint not found (.py)");
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
        LOG_TO_FILE("start.c", "❌ Invalid entrypoint extension");
        close_log_file();
        return -1;
    }

    LOGP("✅ FINAL ENTRYPOINT: %s", entrypoint);
    LOG_TO_FILE("start.c", "✅ FINAL ENTRYPOINT");

    fd = fopen(entrypoint, "r");
    if (fd == NULL) {
        LOGE("❌ Open the entrypoint failed: %s", entrypoint);
        LOG_TO_FILE("start.c", "❌ Open entrypoint failed");
        close_log_file();
        return -1;
    }

    LOGP("✅ Running Python script: %s", entrypoint);
    LOG_TO_FILE("start.c", "✅ Running Python script");

    //start python mit entrypoint (main pyc)
    ret = PyRun_SimpleFile(fd, entrypoint );
    
    //start python mit entrypoint (main pyc)
    /*
    ret = PyRun_SimpleString(
    "import sys\n"
    "import subprocess\n"
    "subprocess.check_call([sys.executable, '-m', 'pip', 'list'])\n"
);
    LOGP("📌 Return PyRun_SimpleString: %d", ret);
    */
    fclose(fd);

    if (PyErr_Occurred() != NULL) {
        ret = 1;
        PyErr_Print();
        PyObject *f = PySys_GetObject("stdout");
        if (f != NULL && PyFile_WriteString("\n", f))
            PyErr_Clear();
    }

    LOGP("✅ Python for android ended with code: %d", ret);
    LOG_TO_FILE("start.c", "✅ Python for android ended");

#if PY_MAJOR_VERSION < 3
    Py_Finalize();
    LOGP("Unexpectedly reached Py_FinalizeEx(), but was successful.");
#else
    if (Py_FinalizeEx() != 0) {
        LOGP("Unexpectedly reached Py_FinalizeEx(), and got error!");
    }
#endif

    close_log_file();
    exit(ret);
    return ret;
}

// ============================================================
// ★ ★ ★ JNI-FUNKTIONEN ★ ★ ★
// ============================================================
JNIEXPORT void JNICALL Java_org_kivysdl_android_PythonService_nativeStart(
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
    main(1, argv);
}

#if defined(BOOTSTRAP_NAME_WEBVIEW) || defined(BOOTSTRAP_NAME_SERVICEONLY)
void Java_org_kivysdl_android_PythonActivity_nativeSetenv(
    JNIEnv* env, jclass cls,
    jstring name, jstring value) {
    const char *utfname = (*env)->GetStringUTFChars(env, name, NULL);
    const char *utfvalue = (*env)->GetStringUTFChars(env, value, NULL);
    setenv(utfname, utfvalue, 1);
    (*env)->ReleaseStringUTFChars(env, name, utfname);
    (*env)->ReleaseStringUTFChars(env, value, utfvalue);
}

void Java_org_kivysdl_android_PythonActivity_nativeInit(JNIEnv* env, jclass cls, jobject obj) {
    char *argv[2];
    argv[0] = "Python_app";
    argv[1] = NULL;
    main(1, argv);
}
#endif

// ============================================================
// ★ ★ ★ JNI_OnLoad ★ ★ ★
// ============================================================
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGP("✅ JNI_OnLoad called");
    log_to_file("INFO", "start.c", "✅ JNI_OnLoad called");
    return JNI_VERSION_1_6;
}