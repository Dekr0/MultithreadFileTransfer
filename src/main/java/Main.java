import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
    Area to research :
        Multithreading
            Thread pool
        HTTP Request
            get content length
        Multithreading FTP
            Split downloading
            Merging
        Android Dev

*/

public class Main {

    private static final int MAX_AVAILABLE = 3;
    private static final Logger logger = LogManager.getLogger(Main.class);
    private static String filename;
    private static String urlPath;
    private static long downloadSize = -1;


    public static void main(String[] args) {
        urlPath = args[0];

        downloadSize = getFileSize();
        if (downloadSize <= 0) {
            downloadSize = getFileSizeHeader();
        }

        if (downloadSize <= 0) return;

        filename = getFileName();

        DownloadThread.filename = filename;
        DownloadThread.urlPath = urlPath;
        DownloadThread.totalFileSize = downloadSize;

        startDownload();
    }


    /**
     * Create random access file
     * @return
     * @throws IOException
     */
    public static void createDownloadFile() throws IOException {
        RandomAccessFile downloadFile = new RandomAccessFile(new File(filename), "rw");

        downloadFile.setLength(downloadSize);
        downloadFile.close();
    }


    public static void deleteDownloadFile() {
        File downloadFile = new File(filename);
        if (!downloadFile.delete()) {
            System.out.println("Failed to delete file");
        }
    }


    public static String getFileName() {
        String filename = "out";

        try {
            URL url = new URL(urlPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("HEAD");

            String tmpFilename = url.getFile();
            filename = tmpFilename.substring(tmpFilename.lastIndexOf('/') + 1);

            if (isValidName(filename)) {
                filename = getHeaderFields(conn, 0, "Content-Disposition");

                if (filename != null) {
                    filename = filename.substring(filename.indexOf("filename=")
                            + "filename=".length() + 1).replaceAll("\"", "");

                    if (isValidName(filename)) {
                        filename = "out";
                    }
                } else {
                    filename = "out";
                }
            }
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }

        return filename;
    }


    /**
     * Get file size via sending a GET request
     * @return
     */
    public static long getFileSize() {
        long size = -1;

        try {
            URL url = new URL(urlPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != 200) return size;

            size = conn.getContentLengthLong();
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }

        return size;
    }


    /**
     * Get file size via sending a HEAD request if sending GET request does not
     * work
     * @return
     */
    public static long getFileSizeHeader() {
        long size = -1;

        try {
            URL url = new URL(urlPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("HEAD");

            int code = conn.getResponseCode();
            if (code != 200) return size;

            String fieldValue = getHeaderFields(conn,0, "Content-Length");
            if (fieldValue != null) {
                size = Long.parseLong(fieldValue);
            }
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }

        return size;
    }


    public static String getHeaderFields(HttpURLConnection conn, int index, String fieldName) {
        Map<String, List<String>> headerFields = conn.getHeaderFields();
        List<String> fieldValues = headerFields.get(fieldName);

        if (fieldValues.size() == 0) {
            return null;
        }

        return fieldValues.get(index);
    }


    public static boolean isValidName(String text) {
        Pattern pattern = Pattern.compile(
                "# Match a valid Windows filename (unspecified file system).          \n" +
                        "^                                # Anchor to start of string.        \n" +
                        "(?!                              # Assert filename is not: CON, PRN, \n" +
                        "  (?:                            # AUX, NUL, COM1, COM2, COM3, COM4, \n" +
                        "    CON|PRN|AUX|NUL|             # COM5, COM6, COM7, COM8, COM9,     \n" +
                        "    COM[1-9]|LPT[1-9]            # LPT1, LPT2, LPT3, LPT4, LPT5,     \n" +
                        "  )                              # LPT6, LPT7, LPT8, and LPT9...     \n" +
                        "  (?:\\.[^.]*)?                  # followed by optional extension    \n" +
                        "  $                              # and end of string                 \n" +
                        ")                                # End negative lookahead assertion. \n" +
                        "[^<>:\"/\\\\|?*\\x00-\\x1F]*     # Zero or more valid filename chars.\n" +
                        "[^<>:\"/\\\\|?*\\x00-\\x1F\\ .]  # Last char is not a space or dot.  \n" +
                        "$                                # Anchor to end of string.            ",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.COMMENTS);
        Matcher matcher = pattern.matcher(text);

        return !matcher.matches();
    }


    public static void startDownload() {
        int threadCount = DownloadThreadManager.MAX_THREAD_COUNT;
        long rangeSize = downloadSize / threadCount;
        long start = 0, end = 0;

        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        Semaphore sem = new Semaphore(MAX_AVAILABLE, true);

        ThreadPoolExecutor threadPool = DownloadThreadManager.getThreadPool();

        try {
            createDownloadFile();
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
            return;
        }

        for (int i = 0; i < threadCount; ++i) {
            if (downloadSize < end + rangeSize ||
                    (end + rangeSize < downloadSize && i == threadCount - 1)) {
                threadPool.submit(new DownloadThread(doneSignal, i, start, downloadSize, sem));
                break;
            }

            end = start + rangeSize;
            threadPool.submit(new DownloadThread(doneSignal, i, start, end, sem));
            start = end + 1;
        }

        try {
            doneSignal.await();
        } catch (InterruptedException exception) {
            logger.error(exception.getMessage());
        }

        threadPool.shutdown();

        if (DownloadThread.processError) {
            deleteDownloadFile();
        }
    }
}
