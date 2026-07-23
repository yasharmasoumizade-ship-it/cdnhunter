/*
 * JNI bridge between hev.htproxy.TProxyService (Java) and hev-socks5-tunnel's
 * pure C API (src/hev-main.h). Current upstream hev-socks5-tunnel ships NO
 * Java/JNI wrapper of its own — only hev_socks5_tunnel_main_from_str(),
 * hev_socks5_tunnel_quit(), and hev_socks5_tunnel_stats(). Any Android app
 * using this library has to provide its own thin JNI glue, which is what
 * this file is. Compiled together with the library by the CI workflow.
 *
 * Exported JNI symbols MUST match:
 *   package hev.htproxy;
 *   public class TProxyService {
 *       private static native void TProxyStartService(String configPath, int fd);
 *       private static native void TProxyStopService();
 *       private static native long[] TProxyGetStats();
 *   }
 */
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "hev-main.h"

static pthread_t tunnel_thread;
static int tunnel_running = 0;

typedef struct {
    char *config_path;
    int tun_fd;
} StartArgs;

static void *
tunnel_thread_entry(void *arg)
{
    StartArgs *args = (StartArgs *) arg;
    /* Blocks until hev_socks5_tunnel_quit() is called or an error occurs. */
    hev_socks5_tunnel_main_from_file(args->config_path, args->tun_fd);
    free(args->config_path);
    free(args);
    tunnel_running = 0;
    return NULL;
}

JNIEXPORT void JNICALL
Java_hev_htproxy_TProxyService_TProxyStartService(JNIEnv *env, jclass clazz,
                                                    jstring configPath, jint fd)
{
    (void) clazz;
    if (tunnel_running) return;

    const char *path = (*env)->GetStringUTFChars(env, configPath, NULL);
    if (!path) return;

    StartArgs *args = (StartArgs *) malloc(sizeof(StartArgs));
    args->config_path = strdup(path);
    args->tun_fd = fd;
    (*env)->ReleaseStringUTFChars(env, configPath, path);

    tunnel_running = 1;
    pthread_create(&tunnel_thread, NULL, tunnel_thread_entry, args);
}

JNIEXPORT void JNICALL
Java_hev_htproxy_TProxyService_TProxyStopService(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    if (!tunnel_running) return;
    hev_socks5_tunnel_quit();
    pthread_join(tunnel_thread, NULL);
    tunnel_running = 0;
}

JNIEXPORT jlongArray JNICALL
Java_hev_htproxy_TProxyService_TProxyGetStats(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    size_t tx_packets = 0, tx_bytes = 0, rx_packets = 0, rx_bytes = 0;
    if (tunnel_running) {
        hev_socks5_tunnel_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
    }

    jlongArray result = (*env)->NewLongArray(env, 4);
    if (!result) return NULL;
    jlong buf[4] = {
        (jlong) tx_packets, (jlong) tx_bytes,
        (jlong) rx_packets, (jlong) rx_bytes,
    };
    (*env)->SetLongArrayRegion(env, result, 0, 4, buf);
    return result;
}
