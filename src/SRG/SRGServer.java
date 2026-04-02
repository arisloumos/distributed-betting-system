package SRG;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.HashUtils;
import Common.Config;

/**
 * SRG (Secure Random Generator) Server.
 * Υλοποιεί έναν κεντρικό παραγωγό τυχαίων αριθμών που εξυπηρετεί τους Workers.
 * Χρησιμοποιεί το μοντέλο Producer-Consumer με ενδιάμεσο Buffer.
 */
public class SRGServer {
    // Φόρτωση ρυθμίσεων από το system.conf
    private static final int PORT = Config.getInt("SRG_PORT", 5555);
    private static final int BUFFER_SIZE = 50; 
    
    // Ο κοινόχρηστος Buffer για τους τυχαίους αριθμούς
    private static final LinkedList<Integer> buffer = new LinkedList<>();

    public static void main(String[] args) {
        // Εκκίνηση του Producer Thread που γεμίζει τον buffer στο παρασκήνιο
        Thread producer = new Thread(new Producer());
        producer.setDaemon(true); // Τερματίζει αυτόματα αν κλείσει ο server
        producer.start();

        System.out.println("[SRG] Server started on port " + PORT);
        System.out.println("[SRG] Buffer size set to: " + BUFFER_SIZE);

        // Κύριος βρόχος αποδοχής συνδέσεων από Workers
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket workerSocket = serverSocket.accept();
                // Πολυνηματική εξυπηρέτηση κάθε αιτήματος
                new Thread(new WorkerHandler(workerSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("[SRG] Server Error: " + e.getMessage());
        }
    }

    /**
     * Producer: Παράγει συνεχώς τυχαίους αριθμούς.
     * Χρησιμοποιεί wait() αν ο buffer γεμίσει και notifyAll() όταν προσθέτει στοιχεία.
     */
    static class Producer implements Runnable {
        private Random random = new Random();

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (buffer) {
                        // Αν ο buffer είναι γεμάτος, το thread μπαίνει σε κατάσταση αναμονής
                        while (buffer.size() >= BUFFER_SIZE) {
                            buffer.wait();
                        }
                        
                        int num = random.nextInt(1000000); 
                        buffer.add(num);
                        
                        // Ειδοποίηση των καταναλωτών (WorkerHandlers) ότι υπάρχει διαθέσιμος αριθμός
                        buffer.notifyAll();
                    }
                    // Μικρή παύση για εξοικονόμηση πόρων συστήματος
                    Thread.sleep(50); 
                }
            } catch (InterruptedException e) {
                System.err.println("[SRG] Producer interrupted.");
            }
        }
    }

    /**
     * WorkerHandler: Εξυπηρετεί το αίτημα ενός Worker για τυχαίο αριθμό.
     * Καταναλώνει έναν αριθμό από τον buffer και τον στέλνει μαζί με το SHA-256 hash.
     */
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
                // Λήψη του Secret S από τον Worker για το hashing
                String secretS = in.readUTF();
                
                int randomNumber;
                synchronized (buffer) {
                    // Αν ο buffer είναι άδειος, το thread περιμένει τον Producer
                    while (buffer.isEmpty()) {
                        System.out.println("[SRG] Buffer empty, waiting for producer...");
                        buffer.wait();
                    }
                    // Λήψη του πρώτου διαθέσιμου αριθμού (FIFO)
                    randomNumber = buffer.removeFirst();
                    
                    // Ειδοποίηση του Producer ότι άδειασε θέση στον buffer
                    buffer.notifyAll();
                }

                // Δημιουργία ασφαλούς Hash: SHA256(αριθμός + secret)
                String hash = HashUtils.sha256(randomNumber + secretS);

                // Αποστολή δεδομένων στον Worker
                out.writeInt(randomNumber);
                out.writeUTF(hash);
                out.flush();
                
                System.out.println("[SRG] Dispatched number: " + randomNumber + " (Hash generated)");
                
                socket.close();
            } catch (Exception e) {
                System.err.println("[SRG] Error handling worker request: " + e.getMessage());
            }
        }
    }
}