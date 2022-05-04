import java.util.concurrent.CountDownLatch;

public class LatchTest {

    public static void main(String[] args) {
        int n = 10;
        
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            new Thread(new Worker(doneSignal, startSignal, i)).start();
        }

        try {
            System.out.println("Main thread is doing some work");
            Thread.sleep(5000);
            startSignal.countDown();
            System.out.println("Main thread is waiting for workers");
            doneSignal.await();
            System.out.println("All workers done");
        } catch (InterruptedException exception) {

        }
    }
}


class Worker implements Runnable {

    private CountDownLatch doneSignal;
    private CountDownLatch startSignal;
    private int id;


    public Worker(CountDownLatch doneSignal, CountDownLatch startSignal, int id) {
        this.doneSignal = doneSignal;
        this.startSignal = startSignal;
        this.id = id;
    }


    @Override
    public void run() {
        String header = "Thread " + Integer.toString(id);
        System.out.println(header + " : ready\n");

        try{
            startSignal.await();
            System.out.println(header + " : doing work\n");
            Thread.sleep(5000);
            System.out.println(header + " : done work\n");
            doneSignal.countDown();
        } catch (InterruptedException exception) {

        }
    }
}
