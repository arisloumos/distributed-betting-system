package gr.aueb.bettingclientapp.SRG;

import java.io.*;
import java.net.*;
import java.util.*;

import gr.aueb.bettingclientapp.Common.Config;
import gr.aueb.bettingclientapp.Common.HashUtils;

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
    private static final Map<String, LinkedList<Integer>> gameBuffers = new HashMap<>();

    public static void main(String[] args) {
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
     * Producer: Παράγει συνεχώς τυχαίους αριθμούς για ΕΝΑ συγκεκριμένο παιχνίδι.
     * Χρησιμοποιεί wait() αν ο buffer γεμίσει και notifyAll() όταν προσθέτει στοιχεία.
     */
    static class Producer implements Runnable {
        private final LinkedList<Integer> myBuffer;
        private final String secretS;
        private Random random = new Random();

        public Producer(LinkedList<Integer> buffer, String secret) {
            this.myBuffer = buffer;
            this.secretS = secret;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (myBuffer) {
                        while (myBuffer.size() >= BUFFER_SIZE) {
                            myBuffer.wait();
                        }
                        int num = random.nextInt(1000000); 
                        myBuffer.add(num);
                        myBuffer.notifyAll();
                    }
                    Thread.sleep(50); 
                }
            } catch (InterruptedException e) {
                System.err.println("[SRG] Producer for " + secretS + " interrupted.");
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
                LinkedList<Integer> myBuffer;

                // Εύρεση ή δημιουργία του buffer για το συγκεκριμένο παιχνίδι (secretS)
                synchronized (gameBuffers) {
                    if (!gameBuffers.containsKey(secretS)) {
                        myBuffer = new LinkedList<>();
                        gameBuffers.put(secretS, myBuffer);
                        
                        // Ξεκινάμε μια νέα Γεννήτρια (Producer) αποκλειστικά για αυτό το παιχνίδι!
                        Thread p = new Thread(new Producer(myBuffer, secretS));
                        p.setDaemon(true);
                        p.start();
                        System.out.println("[SRG] Created new Generator & Buffer for game secret: " + secretS);
                    } else {
                        myBuffer = gameBuffers.get(secretS);
                    }
                }

                int randomNumber;
                // Κατανάλωση ενός αριθμού από τον αποκλειστικό buffer του παιχνιδιού
                synchronized (myBuffer) {
                    while (myBuffer.isEmpty()) {
                        myBuffer.wait();
                    }
                    randomNumber = myBuffer.removeFirst();
                    myBuffer.notifyAll();
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