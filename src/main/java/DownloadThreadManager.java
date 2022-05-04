import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class DownloadThreadManager {
    static public final int MAX_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    static private ThreadPoolExecutor THREAD_POOL = null;


    static public ThreadPoolExecutor getThreadPool() {
        if (THREAD_POOL == null) {
            THREAD_POOL = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREAD_COUNT);
        }

        return THREAD_POOL;
    }
}
