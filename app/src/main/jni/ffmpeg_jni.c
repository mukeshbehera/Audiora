/*
 * ffmpeg_jni.c — JNI wrapper for executing FFmpeg via memfd_create + fexecve.
 *
 * Uses memfd_create + fexecve (API 29+) to bypass noexec mount restrictions.
 * Falls back to direct execve() for older devices where noexec is not an issue.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG "FFMPEG_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Typedef for memfd_create (API 29+)
typedef int (*memfd_create_fn)(const char *name, unsigned int flags);
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

// fexecve declaration
extern int fexecve(int fd, char *const argv[], char *const envp[]);

static char **java_args_to_argv(JNIEnv *env, jobjectArray jArgs, const char *binaryPath) {
    jsize argc = (*env)->GetArrayLength(env, jArgs);
    char **argv = malloc(sizeof(char *) * (argc + 2));
    if (!argv) return NULL;
    argv[0] = strdup(binaryPath);
    if (!argv[0]) { free(argv); return NULL; }
    for (int i = 0; i < argc; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, jArgs, i);
        const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
        argv[i + 1] = strdup(utf ? utf : "");
        (*env)->ReleaseStringUTFChars(env, js, utf);
        (*env)->DeleteLocalRef(env, js);
    }
    argv[argc + 1] = NULL;
    return argv;
}

static void free_argv(char **argv) {
    if (!argv) return;
    for (int i = 0; argv[i]; i++) free(argv[i]);
    free(argv);
}

JNIEXPORT jint JNICALL
Java_com_audiora_data_processing_FFmpegNative_executeFFmpeg(
    JNIEnv *env, jclass clazz,
    jstring jBinaryPath, jobjectArray jArgs) {

    const char *binaryPath = (*env)->GetStringUTFChars(env, jBinaryPath, NULL);
    const char *argsFile = NULL;
    if (!binaryPath) return -1;

    LOGD("Executing: %s", binaryPath);

    // Build argv
    char **argv = java_args_to_argv(env, jArgs, binaryPath);
    if (!argv) { (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath); return -1; }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        free_argv(argv);
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    if (pid == 0) {
        // Child process
        // Try memfd_create + fexecve (API 29+) to bypass noexec
        // Dynamically resolve memfd_create since it's only in API 29+
        memfd_create_fn memfd_create_func = (memfd_create_fn)dlsym(RTLD_DEFAULT, "memfd_create");
        if (memfd_create_func) {
            // API 29+ — use memfd + fexecve (bypasses noexec)
            FILE *binFile = fopen(binaryPath, "rb");
            if (binFile) {
                int memfd = memfd_create_func("ffmpeg", MFD_CLOEXEC);
                if (memfd >= 0) {
                    char buf[65536];
                    size_t n;
                    while ((n = fread(buf, 1, sizeof(buf), binFile)) > 0) write(memfd, buf, n);
                    fclose(binFile);
                    lseek(memfd, 0, SEEK_SET);
                    fexecve(memfd, argv, environ);
                    close(memfd);
                } else {
                    fclose(binFile);
                }
            }
        }

        // Fallback: direct execve (may fail on noexec mounts, but works on older Android)
        execve(binaryPath, argv, environ);
        _exit(127 + errno);
    }

    // Parent: wait
    int status;
    waitpid(pid, &status, 0);
    free_argv(argv);
    (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        LOGE("Killed by signal: %d", WTERMSIG(status));
    }
    return -1;
}
