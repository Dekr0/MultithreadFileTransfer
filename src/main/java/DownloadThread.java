import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class DownloadThread implements Runnable {

    static public String Filename;
    static public String URLPath;
    static public long TotalFileSize;
    static public boolean Error = false;

    private final int id;
    private final long start;
    private final long end;
    private final CountDownLatch doneSignal;
    private final Semaphore sem;

    public DownloadThread(CountDownLatch doneSignal, int id, long start, long end, Semaphore sem) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.doneSignal = doneSignal;
        this.sem = sem;
    }


    public void run() {
        try {
            String msg;

            URL url = new URL(URLPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            int code = -1;
            while (code != 206) {
                // send GET request and retrieve the bytes from "start" to "end" of the file

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));

                code = conn.getResponseCode();

                msg = String.format("Thread %d: Response code - %d", id, code);
                System.out.println(msg);

                Thread.sleep(1000);
            }

            long length = conn.getContentLengthLong();
            msg = String.format("Thread %d: Content length = %d, %d byte ~ %d byte",
                    id, length, start, end);
            System.out.println(msg);

            ReadableByteChannel downloadChannel = Channels.newChannel(conn.getInputStream());

            RandomAccessFile downloadFile = new RandomAccessFile(new File(Filename), "rw");
            FileChannel writeChannel = downloadFile.getChannel();

            writeChannel.transferFrom(downloadChannel, start, end - start);

            downloadChannel.close();
            downloadFile.close();
            writeChannel.close();

        } catch (IOException ioException) {
            System.err.println(ioException);
            Error = true;
        } catch (InterruptedException interrException) {
            System.out.println(interrException);
            Error = true;
        } catch (Exception e) {
            System.out.println(id + e.toString());
            Error = true;
        } finally {
            doneSignal.countDown();
        }
    }

}
