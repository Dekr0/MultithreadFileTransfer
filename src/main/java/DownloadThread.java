import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class DownloadThread implements Runnable {

    static public String filename;
    static public String urlPath;
    static public long totalFileSize;
    static public boolean processError = false;

    private final int id;
    private final long start;
    private final long end;
    private final CountDownLatch doneSignal;
    private final Logger logger;
    private final Semaphore sem;

    public DownloadThread(CountDownLatch doneSignal, int id, long start, long end, Semaphore sem) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.doneSignal = doneSignal;
        this.logger = LogManager.getLogger();
        this.sem = sem;
    }


    public void run() {
        try {
            URL url = new URL(urlPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            int code = -1;
            while (code != 206) {
                // send GET request and retrieve the bytes from "start" to "end" of the file

                sem.acquire();
                code = get(conn);
                sem.release();

                Thread.sleep(1000);
            }

            processError = download(conn);
        } catch (IOException | InterruptedException exception) {
            logger.error(exception.getMessage());
            processError = true;
        }

        doneSignal.countDown();
    }

    private int get(HttpURLConnection conn) {
        int code = -1;

        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));

            code = conn.getResponseCode();

            logger.debug(String.format("Thread %d: Response code - %d", id, code));

        } catch (IOException exception) {
            logger.error(exception.getMessage());
        }
            
        return code;
    }

    private boolean download(HttpURLConnection conn) {
        boolean error = false;

        long length = conn.getContentLengthLong();

        logger.debug(String.format("Thread %d: Content length = %d, %d byte ~ %d byte",
                id, length, start, end));

        try (InputStream in = conn.getInputStream()) {
            try (RandomAccessFile downloadFile = new RandomAccessFile(new File(filename), "rw")) {
                ReadableByteChannel downloadChannel = Channels.newChannel(in);
                FileChannel writeChannel = downloadFile.getChannel();

                writeChannel.transferFrom(downloadChannel, start, end - start);

                downloadChannel.close();
                writeChannel.close();
            }
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
            error = true;
        }

        return error;
    }
}
