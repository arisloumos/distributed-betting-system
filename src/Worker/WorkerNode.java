package Worker;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.*;

/**
 * Worker Node: Υπεύθυνος για την αποθήκευση παιχνιδιών, την εκτέλεση πονταρισμάτων
 * και την παροχή δεδομένων για τη διαδικασία MapReduce.
 */
public class WorkerNode {
    private static int myPort;
    
    // Φόρτωση διευθύνσεων από το αρχείο ρυθμίσεων
    private static final String REDUCER_IP = Config.get("REDUCER_IP", "localhost");
    private static final int REDUCER_PORT = Config.getInt("REDUCER_PORT", 4444);
    private static final String SRG_IP = Config.get("SRG_IP", "localhost");
    private static final int SRG_PORT = Config.getInt("SRG_PORT", 5555);

    // Δομές δεδομένων στη μνήμη (In-memory storage)
    // Χρησιμοποιούμε synchronized blocks για την ασφάλεια των νημάτων (Thread Safety)
    private static final Map<String, Game> gamesMap = new HashMap<>();
    private static final Map<String, Double> playerStats = new HashMap<>();

    // Πίνακες πολλαπλασιαστών βάσει επιπέδου ρίσκου (από την εκφώνηση)
    private static final double[] LOW_RISK = {0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
    private static final double[] MEDIUM_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HIGH_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java WorkerNode <port>");
            return;
        }
        
        myPort = Integer.parseInt(args[0]);

        System.out.println("[WORKER] Node started on port " + myPort);
        System.out.println("[WORKER] Target Reducer: " + REDUCER_IP + ":" + REDUCER_PORT);
        System.out.println("[WORKER] Target SRG: " + SRG_IP + ":" + SRG_PORT);

        // Εκκίνηση του ServerSocket για την αποδοχή εντολών από τον Master
        try (ServerSocket ss = new ServerSocket(myPort)) {
            while (true) {
                Socket s = ss.accept();
                // Κάθε αίτημα του Master εξυπηρετείται σε ξεχωριστό Thread
                new Thread(new MasterHandler(s)).start();
            }
        }
    }

    /**
     * Handler για την επικοινωνία με τον Master.
     */
    static class MasterHandler implements Runnable {
        private final Socket socket;
        public MasterHandler(Socket s) { this.socket = s; }

        @Override
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                String cmd = (String) in.readObject();

                if (cmd.equals("ADD_GAME")) {
                    Game g = (Game) in.readObject();
                    synchronized (gamesMap) {
                        gamesMap.put(g.gameName, g);
                    }
                    System.out.println("[WORKER] New game added: " + g.gameName);
                    out.writeUTF("SUCCESS");
                } 
                else if (cmd.equals("SEARCH")) {
                    Filters f = (Filters) in.readObject();
                    List<Game> results = new ArrayList<>();
                    // Συγχρονισμός κατά την ανάγνωση για αποφυγή ConcurrentModificationException
                    synchronized (gamesMap) {
                        for (Game g : gamesMap.values()) {
                            if (g.isActive && g.stars >= f.minStars && 
                               (f.betCategory == null || g.betCategory.equals(f.betCategory)) &&
                               (f.riskLevel == null || g.riskLevel.equals(f.riskLevel))) {
                                results.add(g);
                            }
                        }
                    }
                    out.reset(); // Καθαρισμός cache για σωστή αποστολή ενημερωμένων αντικειμένων
                    out.writeObject(results);
                } 
                else if (cmd.equals("PLAY")) {
                    String pId = in.readUTF();
                    String gName = in.readUTF();
                    double amt = in.readDouble();
                    
                    System.out.println("[WORKER] Processing bet: Player " + pId + " on " + gName + " (" + amt + " tokens)");
                    double winAmount = processBetNumeric(pId, gName, amt);
                    out.writeDouble(winAmount); 
                } 
                else if (cmd.equals("REMOVE_GAME")) {
                    String name = in.readUTF();
                    synchronized (gamesMap) {
                        if (gamesMap.containsKey(name)) {
                            gamesMap.get(name).isActive = false;
                            System.out.println("[WORKER] Game deactivated: " + name);
                            out.writeUTF("Game deactivated.");
                        } else out.writeUTF("Not found.");
                    }
                } 
                else if (cmd.equals("EDIT_GAME")) {
                    String name = in.readUTF(); 
                    String risk = in.readUTF();
                    synchronized (gamesMap) {
                        if (gamesMap.containsKey(name)) {
                            Game g = gamesMap.get(name);
                            g.riskLevel = risk.toLowerCase();
                            g.updateJackpot();
                            System.out.println("[WORKER] Risk updated for " + name + " to " + risk);
                            out.writeUTF("Risk updated.");
                        } else out.writeUTF("Not found.");
                    }
                } 
                else if (cmd.equals("RATE_GAME")) {
                    String name = in.readUTF(); 
                    int r = in.readInt();
                    synchronized (gamesMap) {
                        if (gamesMap.containsKey(name)) {
                            gamesMap.get(name).addRating(r);
                            out.writeUTF("Rating added.");
                        } else out.writeUTF("Not found.");
                    }
                } 
                else if (cmd.equals("PUSH_TO_REDUCER")) {
                    // Εντολή από Master για έναρξη της φάσης Map
                    sendToReducer();
                    out.writeUTF("DATA_SENT");
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("[WORKER] Handler Error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Φάση Map: Στέλνει τα τοπικά στατιστικά στον Reducer.
     */
    private static void sendToReducer() {
        Map<String, Double> gamePNLs = new HashMap<>();
        Map<String, String> gameProviders = new HashMap<>();
        Map<String, Double> playerStatsSnapshot;

        // Δημιουργία αντιγράφων (snapshots) των δεδομένων υπό συγχρονισμό
        synchronized (gamesMap) {
            for (Game g : gamesMap.values()) {
                gamePNLs.put(g.gameName, g.totalProfitLoss);
                gameProviders.put(g.gameName, g.providerName);
            }
        }
        synchronized (playerStats) {
            playerStatsSnapshot = new HashMap<>(playerStats);
        }

        System.out.println("[WORKER] Pushing local statistics to Reducer at " + REDUCER_IP);
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("MAP_DATA");
            out.writeObject(gamePNLs);
            out.writeObject(gameProviders);
            out.writeObject(playerStatsSnapshot);
            out.flush();
            in.readUTF(); // Αναμονή επιβεβαίωσης λήψης
            System.out.println("[WORKER] Map phase data successfully delivered.");
        } catch (Exception e) { 
            System.err.println("[WORKER] Reducer Connection Error: " + e.getMessage()); 
        }
    }

    /**
     * Υπολογισμός αποτελέσματος πονταρίσματος με χρήση του SRG.
     */
    private static double processBetNumeric(String playerId, String gameName, double betAmount) {
        Game game;
        synchronized (gamesMap) {
            game = gamesMap.get(gameName);
        }
        
        if (game == null || !game.isActive) return -1.0;

        // Επικοινωνία με τον Secure Random Generator (SRG) μέσω TCP
        System.out.println("[WORKER] Requesting secure random number from SRG...");
        try (Socket srgSocket = new Socket(SRG_IP, SRG_PORT);
             DataOutputStream out = new DataOutputStream(srgSocket.getOutputStream());
             DataInputStream in = new DataInputStream(srgSocket.getInputStream())) {
            
            out.writeUTF(game.hashKey); // Αποστολή του Secret S
            out.flush();
            
            int num = in.readInt(); 
            String receivedHash = in.readUTF();
            
            // Επαλήθευση ακεραιότητας (Security Check)
            if (!HashUtils.sha256(num + game.hashKey).equals(receivedHash)) {
                System.err.println("[SECURITY] Hash mismatch! Potential tampering detected.");
                return -2.0;
            }

            System.out.println("[WORKER] Received verified number: " + num);

            double win = 0;
            // Έλεγχος για Jackpot (υπόλοιπο 100 == 0)
            if (num % 100 == 0) {
                win = betAmount * game.jackpot;
                System.out.println("[WORKER] JACKPOT! Player won " + win);
            } else {
                // Υπολογισμός βάσει πίνακα ρίσκου (υπόλοιπο 10)
                double[] table = game.riskLevel.equals("low") ? LOW_RISK : 
                                (game.riskLevel.equals("medium") ? MEDIUM_RISK : HIGH_RISK);
                win = betAmount * table[num % 10];
            }

            // Ενημέρωση στατιστικών (Thread-safe)
            synchronized (game) { game.totalProfitLoss += (betAmount - win); }
            synchronized (playerStats) { playerStats.merge(playerId, win - betAmount, Double::sum); }
            
            return win;
        } catch (Exception e) { 
            System.err.println("[WORKER] SRG Communication Error: " + e.getMessage());
            return -3.0; 
        }
    }
}