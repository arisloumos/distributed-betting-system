package Reducer;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Config;

/**
 * Reducer Node: Ο κόμβος που συγκεντρώνει τα ενδιάμεσα αποτελέσματα από τους Workers.
 * Υλοποιεί τη φάση "Reduce" του MapReduce, ομαδοποιώντας τα κέρδη/ζημιές 
 * ανά πάροχο και ανά παίκτη.
 */
public class ReducerNode {
    // Φόρτωση του port από το system.conf
    private static final int PORT = Config.getInt("REDUCER_PORT", 4444);
    
    // Δομές αποθήκευσης των συγκεντρωτικών αποτελεσμάτων
    private static final Map<String, Map<String, Double>> providerData = new HashMap<>();
    private static final Map<String, Double> playerData = new HashMap<>();
    
    // Μηχανισμός Barrier: Παρακολουθεί ποιοι Workers ολοκλήρωσαν τη φάση Map
    private static final Set<String> workersFinished = new HashSet<>();
    private static int expectedWorkers = 2; // Αρχική τιμή, ενημερώνεται δυναμικά από τον Master

    public static void main(String[] args) throws IOException {
        // Δυνατότητα ορισμού αριθμού workers και από command line arguments
        if (args.length > 0) {
            expectedWorkers = Integer.parseInt(args[0]);
        }

        System.out.println("[REDUCER] Node started on port " + PORT);
        System.out.println("[REDUCER] Waiting for " + expectedWorkers + " workers to complete Map phase...");

        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket s = ss.accept();
                // Κάθε σύνδεση (από Master ή Worker) εξυπηρετείται σε νέο Thread
                new Thread(new WorkerOrMasterHandler(s)).start();
            }
        }
    }

    /**
     * Handler για την επεξεργασία εντολών από τον Master (Reset, Get Results)
     * και δεδομένων από τους Workers (Map Data).
     */
    static class WorkerOrMasterHandler implements Runnable {
        private Socket s;
        public WorkerOrMasterHandler(Socket s) { this.s = s; }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                String cmd = (String) in.readObject();

                // Εντολή από Master για καθαρισμό των στατιστικών (πριν από νέο query)
                if (cmd.equals("RESET_STATS")) {
                    synchronized (workersFinished) {
                        providerData.clear();
                        playerData.clear();
                        workersFinished.clear();
                        System.out.println("[REDUCER] Stats and Worker-tracker reset for new job.");
                    }
                }
                // Εντολή από Master για δυναμικό ορισμό του πλήθους των Workers
                else if (cmd.equals("SET_WORKER_COUNT")) {
                    int count = in.readInt();
                    synchronized(workersFinished) {
                        expectedWorkers = count;
                        workersFinished.clear();
                    }
                    System.out.println("[REDUCER] Dynamic worker count set to: " + expectedWorkers);
                }
                // Λήψη ενδιάμεσων δεδομένων (Map Phase) από έναν Worker
                else if (cmd.equals("MAP_DATA")) {
                    Map<String, Double> wGames = (Map<String, Double>) in.readObject();
                    Map<String, String> wGameProviders = (Map<String, String>) in.readObject();
                    Map<String, Double> wPlayers = (Map<String, Double>) in.readObject();

                    // Ταυτοποίηση του Worker από τη διεύθυνση σύνδεσης
                    String workerId = s.getInetAddress().toString() + ":" + s.getPort();

                    synchronized (workersFinished) {
                        // 1. Ομαδοποίηση ανά Πάροχο (Provider) -> Παιχνίδι -> PnL
                        for (String gName : wGames.keySet()) {
                            String prov = wGameProviders.get(gName);
                            providerData.putIfAbsent(prov, new HashMap<>());
                            providerData.get(prov).put(gName, wGames.get(gName));
                        }
                        
                        // 2. Συγχώνευση στατιστικών ανά Παίκτη (Player)
                        wPlayers.forEach((k, v) -> playerData.merge(k, v, Double::sum));

                        // Καταγραφή ολοκλήρωσης για τον συγκεκριμένο Worker
                        workersFinished.add(workerId);
                        System.out.println("[REDUCER] Received Map data from worker. Progress: " + 
                                           workersFinished.size() + "/" + expectedWorkers);

                        // Αν όλοι οι Workers έστειλαν δεδομένα, "ξυπνάμε" τον Master (αν περιμένει)
                        if (workersFinished.size() >= expectedWorkers) {
                            System.out.println("[REDUCER] All workers finished. Barrier released.");
                            workersFinished.notifyAll();
                        }
                    }
                    out.writeUTF("OK");
                } 
                // Εντολή από Master για λήψη των τελικών αποτελεσμάτων (Reduce Phase)
                else if (cmd.equals("GET_REDUCED_RESULTS")) {
                    synchronized (workersFinished) {
                        // Μηχανισμός Barrier: Αν δεν έχουν τελειώσει όλοι οι Workers, ο Master μπλοκάρεται
                        while (workersFinished.size() < expectedWorkers) {
                            System.out.println("[REDUCER] Master is waiting for Map phase to complete...");
                            workersFinished.wait(); 
                        }
                    }
                    // Αποστολή των τελικών Map στον Master
                    out.writeObject(new HashMap<>(providerData));
                    out.writeObject(new HashMap<>(playerData));
                    System.out.println("[REDUCER] Final aggregated results dispatched to Master.");
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("[REDUCER] Handler Error: " + e.getMessage());
            }
        }
    }
}