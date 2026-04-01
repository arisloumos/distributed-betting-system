package Reducer;
import java.io.*;
import java.net.*;
import java.util.*;

public class ReducerNode {
    private static final int PORT = 4444;
    // Τα δεδομένα μας
    private static final Map<String, Map<String, Double>> providerData = new HashMap<>();
    private static final Map<String, Double> playerData = new HashMap<>();
    
    // Μηχανισμός Συγχρονισμού (Fix 3.2)
    private static final Set<String> workersFinished = new HashSet<>();
    private static int expectedWorkers = 2; // Προσαρμόζεται ανάλογα με το workers.conf

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            expectedWorkers = Integer.parseInt(args[0]);
        }

        ServerSocket ss = new ServerSocket(PORT);
        System.out.println("Reducer Node started on port " + PORT);
        System.out.println("Waiting for " + expectedWorkers + " workers to complete Map phase...");

        while (true) {
            Socket s = ss.accept();
            new Thread(new WorkerOrMasterHandler(s)).start();
        }
    }

    static class WorkerOrMasterHandler implements Runnable {
        private Socket s;
        public WorkerOrMasterHandler(Socket s) { this.s = s; }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                String cmd = (String) in.readObject();

                if (cmd.equals("RESET_STATS")) {
                    synchronized (workersFinished) {
                        providerData.clear();
                        playerData.clear();
                        workersFinished.clear();
                        System.out.println("[REDUCER] Stats and Worker-tracker reset.");
                    }
                }
                if (cmd.equals("SET_WORKER_COUNT")) {
                    int count = in.readInt();
                    synchronized(workersFinished) {
                        expectedWorkers = count; // Τώρα ο Reducer ξέρει ακριβώς πόσους να περιμένει
                        workersFinished.clear();
                    }
                    System.out.println("[REDUCER] Now expecting " + expectedWorkers + " workers.");
                }
                else if (cmd.equals("MAP_DATA")) {
                    Map<String, Double> wGames = (Map<String, Double>) in.readObject();
                    Map<String, String> wGameProviders = (Map<String, String>) in.readObject();
                    Map<String, Double> wPlayers = (Map<String, Double>) in.readObject();

                    // Ταυτοποίηση του Worker (IP + Port)
                    String workerId = s.getInetAddress().toString() + ":" + s.getPort();

                    synchronized (workersFinished) {
                        // Επεξεργασία Provider Data
                        for (String gName : wGames.keySet()) {
                            String prov = wGameProviders.get(gName);
                            providerData.putIfAbsent(prov, new HashMap<>());
                            providerData.get(prov).put(gName, wGames.get(gName));
                        }
                        // Επεξεργασία Player Data
                        wPlayers.forEach((k, v) -> playerData.merge(k, v, Double::sum));

                        // Καταγραφή ότι αυτός ο Worker τελείωσε
                        workersFinished.add(workerId);
                        System.out.println("[REDUCER] Received data from worker. Progress: " + 
                                           workersFinished.size() + "/" + expectedWorkers);

                        // Αν όλοι οι workers έστειλαν δεδομένα, ξύπνα τον Master που περιμένει
                        if (workersFinished.size() >= expectedWorkers) {
                            workersFinished.notifyAll();
                        }
                    }
                    out.writeUTF("OK");
                } 
                else if (cmd.equals("GET_REDUCED_RESULTS")) {
                    synchronized (workersFinished) {
                        // Fix 3.2: Αν δεν έχουν τελειώσει όλοι οι workers, ο Master περιμένει (wait)
                        while (workersFinished.size() < expectedWorkers) {
                            System.out.println("[REDUCER] Master requested results, but Map phase is not finished. Waiting...");
                            workersFinished.wait(); 
                        }
                    }
                    // Στέλνουμε αντίγραφα για ασφάλεια
                    out.writeObject(new HashMap<>(providerData));
                    out.writeObject(new HashMap<>(playerData));
                    System.out.println("[REDUCER] Sent final aggregated results to Master.");
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}