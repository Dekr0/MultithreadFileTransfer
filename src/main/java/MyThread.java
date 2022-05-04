public class MyThread extends Thread {

    private int id;

    public MyThread(int id) {
        this.id = id;
    }

    public void run() {
        System.out.println(id);
    }

}
