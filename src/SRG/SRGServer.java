package SRG;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.HashUtils;

public class SRGServer {
    private static final int PORT = 5555; // Το port που ακούει ο SRG
    private static final int BUFFER_SIZE = 50; // Μέγεθος του Buffer
    private static final LinkedList<Integer> buffer = new LinkedList<>();

    public static void main(String[] args) {
        // 1. Ξεκινάμε το Producer Thread (παράγει αριθμούς)
        Thread producer = new Thread(new Producer());
        producer.start();

        System.out.println("SRG Server started on port " + PORT);

        // 2. Ξεκινάμε τον TCP Server για να ακούει τους Workers
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket workerSocket = serverSocket.accept();
                // Κάθε Worker εξυπηρετείται σε δικό του Thread (Πολυνηματικότητα)
                new Thread(new WorkerHandler(workerSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- PRODUCER: Γεμίζει το buffer με τυχαίους αριθμούς ---
    static class Producer implements Runnable {
        private Random random = new Random();

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (buffer) {
                        // Αν ο buffer είναι γεμάτος, περίμενε (wait)
                        while (buffer.size() >= BUFFER_SIZE) {
                            buffer.wait();
                        }
                        
                        int num = random.nextInt(1000000); // Τυχαίος αριθμός
                        buffer.add(num);
                        // System.out.println("Produced: " + num); // Για debug
                        
                        // Ειδοποίησε τους καταναλωτές ότι υπάρχει αριθμός
                        buffer.notifyAll();
                    }
                    Thread.sleep(100); // Μικρή καθυστέρηση για να μην τρώει όλη τη CPU
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // --- WORKER HANDLER: Στέλνει αριθμούς στους Workers ---
    static class WorkerHandler implements Runnable {
        private Socket socket;

        public WorkerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())
            ) {
                // Ο Worker στέλνει το Secret S (hashKey) του παιχνιδιού
                String secretS = in.readUTF();
                
                int randomNumber;
                synchronized (buffer) {
                    // Αν ο buffer είναι άδειος, περίμενε (wait)
                    while (buffer.isEmpty()) {
                        buffer.wait();
                    }
                    randomNumber = buffer.removeFirst();
                    // Ειδοποίησε τον Producer ότι άδειασε θέση
                    buffer.notifyAll();
                }

                // Υπολογισμός Hash: SHA256(αριθμός + secret)
                String hash = HashUtils.sha256(randomNumber + secretS);

                // Αποστολή στον Worker: Πρώτα ο αριθμός, μετά το hash
                out.writeInt(randomNumber);
                out.writeUTF(hash);
                
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}