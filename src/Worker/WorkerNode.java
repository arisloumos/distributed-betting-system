package Worker;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.*;

public class WorkerNode {
    private static int myPort;
    
    // Διαβάζουμε τις IPs από το system.conf μέσω της Config
    private static final String REDUCER_IP = Config.get("REDUCER_IP", "localhost");
    private static final int REDUCER_PORT = Config.getInt("REDUCER_PORT", 4444);
    private static final String SRG_IP = Config.get("SRG_IP", "localhost");
    private static final int SRG_PORT = Config.getInt("SRG_PORT", 5555);

    // Χρησιμοποιούμε απλές HashMaps αλλά με αυστηρό συγχρονισμό
    private static final Map<String, Game> gamesMap = new HashMap<>();
    private static final Map<String, Double> playerStats = new HashMap<>();

    // Σταθερές Ρίσκου
    private static final double[] LOW_RISK = {0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
    private static final double[] MEDIUM_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HIGH_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java WorkerNode <port>");
            return;
        }
        
        // Το port το παίρνουμε από το argument για να μπορούμε να τρέξουμε πολλούς workers στο ίδιο PC
        myPort = Integer.parseInt(args[0]);

        System.out.println("Worker Node started on port " + myPort);
        System.out.println("Configured Reducer: " + REDUCER_IP + ":" + REDUCER_PORT);
        System.out.println("Configured SRG: " + SRG_IP + ":" + SRG_PORT);

        try (ServerSocket ss = new ServerSocket(myPort)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(new MasterHandler(s)).start();
            }
        }
    }

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
                    System.out.println("[WORKER] Added game: " + g.gameName);
                    out.writeUTF("SUCCESS");
                } 
                else if (cmd.equals("SEARCH")) {
                    Filters f = (Filters) in.readObject();
                    List<Game> results = new ArrayList<>();
                    synchronized (gamesMap) {
                        for (Game g : gamesMap.values()) {
                            if (g.isActive && g.stars >= f.minStars && 
                               (f.betCategory == null || g.betCategory.equals(f.betCategory)) &&
                               (f.riskLevel == null || g.riskLevel.equals(f.riskLevel))) {
                                results.add(g);
                            }
                        }
                    }
                    out.reset();
                    out.writeObject(results);
                } 
                else if (cmd.equals("PLAY")) {
                    String pId = in.readUTF();
                    String gName = in.readUTF();
                    double amt = in.readDouble();
                    double winAmount = processBetNumeric(pId, gName, amt);
                    out.writeDouble(winAmount); 
                } 
                else if (cmd.equals("REMOVE_GAME")) {
                    String name = in.readUTF();
                    synchronized (gamesMap) {
                        if (gamesMap.containsKey(name)) {
                            gamesMap.get(name).isActive = false;
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
                    sendToReducer();
                    out.writeUTF("DATA_SENT");
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("Handler Error: " + e.getMessage());
            }
        }
    }
    
    private static void sendToReducer() {
        Map<String, Double> gamePNLs = new HashMap<>();
        Map<String, String> gameProviders = new HashMap<>();
        Map<String, Double> playerStatsSnapshot;

        synchronized (gamesMap) {
            for (Game g : gamesMap.values()) {
                gamePNLs.put(g.gameName, g.totalProfitLoss);
                gameProviders.put(g.gameName, g.providerName);
            }
        }
        synchronized (playerStats) {
            playerStatsSnapshot = new HashMap<>(playerStats);
        }

        // ΔΙΟΡΘΩΜΕΝΟ try-with-resources (Fix Syntax Error)
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("MAP_DATA");
            out.writeObject(gamePNLs);
            out.writeObject(gameProviders);
            out.writeObject(playerStatsSnapshot);
            out.flush();
            in.readUTF(); 
        } catch (Exception e) { 
            System.err.println("Reducer Connection Error: " + e.getMessage()); 
        }
    }

    private static double processBetNumeric(String playerId, String gameName, double betAmount) {
        Game game;
        synchronized (gamesMap) {
            game = gamesMap.get(gameName);
        }
        
        if (game == null || !game.isActive) return -1.0;

        try (Socket srgSocket = new Socket(SRG_IP, SRG_PORT);
             DataOutputStream out = new DataOutputStream(srgSocket.getOutputStream());
             DataInputStream in = new DataInputStream(srgSocket.getInputStream())) {
            
            out.writeUTF(game.hashKey); 
            out.flush();
            
            int num = in.readInt(); 
            String receivedHash = in.readUTF();
            
            if (!HashUtils.sha256(num + game.hashKey).equals(receivedHash)) {
                return -2.0;
            }

            double win = 0;
            if (num % 100 == 0) {
                win = betAmount * game.jackpot;
            } else {
                double[] table = game.riskLevel.equals("low") ? LOW_RISK : 
                                (game.riskLevel.equals("medium") ? MEDIUM_RISK : HIGH_RISK);
                win = betAmount * table[num % 10];
            }

            synchronized (game) { game.totalProfitLoss += (betAmount - win); }
            synchronized (playerStats) { playerStats.merge(playerId, win - betAmount, Double::sum); }
            
            return win;
        } catch (Exception e) { 
            return -3.0; 
        }
    }
}