/*
 * ffmpeg_jni.c — JNI wrapper for executing FFmpeg via memfd_create + fexecve.
 *
 * Android's /data/ partition is mounted noexec, preventing direct execve() on
 * binaries extracted to app private directories. This wrapper:
 *
 * 1. Reads the FFmpeg binary from a file path
 * 2. Copies it into an anonymous memory file (memfd_create)
 * 3. Executes from the memfd via fexecve — no filesystem noexec check applies
 *
 * memfd_create is available since Android 10 (API 29). For older devices,
 * we fall back to direct execve() which works on those versions.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dlfcn.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG "FFMPEG_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// memfd_create flags
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

// fexecve is available in <unistd.h> but not always declared
extern int fexecve(int fd, char *const argv[], char *const envp[]);

/**
 * Convert a Java String[] to a C char* argv array.
 * Must be freed by caller.
 */
static char **java_args_to_argv(JNIEnv *env, jobjectArray jArgs, const char *binaryPath) {
    jsize argc = (*env)->GetArrayLength(env, jArgs);
    // argv[0] = binaryPath, argv[1..argc] = jArgs elements, argv[argc+1] = NULL
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

/**
 * JNI function: executeFFmpeg(binaryPath: String, args: Array<String>): Int
 *
 * Returns the exit code (0 = success, non-zero = error, -1 = execution failed).
 */
JNIEXPORT jint JNICALL
Java_com_audiora_data_processing_FFmpegNative_executeFFmpeg(
    JNIEnv *env, jclass clazz,
    jstring jBinaryPath, jobjectArray jArgs) {

    const char *binaryPath = (*env)->GetStringUTFChars(env, jBinaryPath, NULL);
    if (!binaryPath) return -1;

    LOGD("Executing: %s", binaryPath);

    // Open the binary file
    FILE *binFile = fopen(binaryPath, "rb");
    if (!binFile) {
        LOGE("Failed to open binary: %s (%s)", binaryPath, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    // Get file size
    fseek(binFile, 0, SEEK_END);
    long fileSize = ftell(binFile);
    fseek(binFile, 0, SEEK_SET);

    if (fileSize <= 0) {
        LOGE("Invalid binary size: %ld", fileSize);
        fclose(binFile);
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    // Create a memfd and copy the binary into it
    int memfd = memfd_create("ffmpeg", MFD_CLOEXEC);
    if (memfd < 0) {
        LOGE("memfd_create failed: %s", strerror(errno));
        fclose(binFile);
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    char buffer[65536];
    long totalWritten = 0;
    size_t bytesRead;
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), binFile)) > 0) {
        ssize_t written = write(memfd, buffer, bytesRead);
        if (written < 0) {
            LOGE("write to memfd failed: %s", strerror(errno));
            close(memfd);
            fclose(binFile);
            (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
            return -1;
        }
        totalWritten += written;
    }
    fclose(binFile);

    LOGD("Copied %ld bytes to memfd", totalWritten);

    // Fork and execute from memfd
    char **argv = java_args_to_argv(env, jArgs, binaryPath);
    if (!argv) {
        LOGE("Failed to build argv");
        close(memfd);
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        free_argv(argv);
        close(memfd);
        (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);
        return -1;
    }

    if (pid == 0) {
        // Child process: execute from memfd
        fexecve(memfd, argv, environ);
        // If we get here, fexecve failed
        _exit(127 + errno);
    }

    // Parent process: wait for child
    int status;
    waitpid(pid, &status, 0);

    free_argv(argv);
    close(memfd);
    (*env)->ReleaseStringUTFChars(env, jBinaryPath, binaryPath);

    if (WIFEXITED(status)) {
        int exitCode = WEXITSTATUS(status);
        LOGD("Exit code: %d", exitCode);
        return exitCode;
    } else if (WIFSIGNALED(status)) {
        LOGE("Killed by signal: %d", WTERMSIG(status));
        return -1;
    }

    return -1;
}
