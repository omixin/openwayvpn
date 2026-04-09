package hev.sockstun;

public final class TProxyService {
    private static native void TProxyStartService(String configPath, int fd);
    private static native void TProxyStopService();
    private static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private TProxyService() {
    }

    public static void start(String configPath, int fd) {
        TProxyStartService(configPath, fd);
    }

    public static void stop() {
        TProxyStopService();
    }

    public static long[] getStats() {
        return TProxyGetStats();
    }
}
