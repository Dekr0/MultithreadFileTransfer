import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ThreadLogger {
    private static Logger logger = LogManager.getLogger(ThreadLogger.class);

    public static void debug(String msg) {
        logger.debug(msg);
    }

    public static void error(String msg) {
        logger.error(msg);
    }

    public static void main(String[] args) {
        error("Test");
    }
}
