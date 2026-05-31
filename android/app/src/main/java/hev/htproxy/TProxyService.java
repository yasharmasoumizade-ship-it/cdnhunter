package hev.htproxy;

import java.io.File;
import java.io.FileWriter;

/**
 * JNI bridge to hev-socks5-tunnel native library.
 * MUST be Java class (not Kotlin) for JNI_OnLoad RegisterNatives to work.
 * Package: hev.htproxy, Class: TProxyService — matches native PKGNAME/CLSNAME.
 */
public class TProxyService {

    private static boolean loaded = false;
    private static boolean running = false;

    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
        }
    }

    public static boolean isLoaded() { return loaded; }
    public static boolean isRunning() { return running; }

    public static void startService(String configPath, int tunFd) {
        if (!loaded || running) return;
        TProxyStartService(configPath, tunFd);
        running = true;
    }

    public static void stopService() {
        if (!running) return;
        try { TProxyStopService(); } catch (Exception e) {}
        running = false;
    }

    public static long[] stats() {
        if (!running) return new long[]{0, 0, 0, 0};
        try {
            long[] s = TProxyGetStats();
            return s != null ? s : new long[]{0, 0, 0, 0};
        } catch (Exception e) {
            return new long[]{0, 0, 0, 0};
        }
    }

    public static String writeConfig(File dir, String socksAddr, int socksPort) {
        String config = "tunnel:\n" +
                "  name: tun0\n" +
                "  mtu: 8500\n\n" +
                "socks5:\n" +
                "  address: " + socksAddr + "\n" +
                "  port: " + socksPort + "\n" +
                "  udp: udp\n";
        File configFile = new File(dir, "tun2socks.yml");
        try {
            FileWriter writer = new FileWriter(configFile);
            writer.write(config);
            writer.close();
        } catch (Exception e) {
            return null;
        }
        return configFile.getAbsolutePath();
    }

    // Native methods — names MUST match hev-jni.c registration
    private static native void TProxyStartService(String configPath, int fd);
    private static native void TProxyStopService();
    private static native long[] TProxyGetStats();
}
