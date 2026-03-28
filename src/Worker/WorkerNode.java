package Worker;
import java.io.*;
import java.net.*;
import java.util.*;
import Common.*;

public class WorkerNode {
    private static int myPort;
    private static Map<String, Game> gamesMap = new HashMap<>();
    private static Map<String, Double> playerStats = new HashMap<>();
    private static final double[] LOW_RISK = {0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
    private static final double[] MEDIUM_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HIGH_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};

    public static void main(String[] args) throws IOException {
        myPort = Integer.parseInt(args[0]);
        ServerSocket ss = new ServerSocket(myPort);
        System.out.println("Worker Node started on port " + myPort);
        while (true) {
            Socket s = ss.accept();
            new Thread(new MasterHandler(s)).start();
        }
    }

    static class MasterHandler implements Runnable {
        private Socket s;
        public MasterHandler(Socket s) { this.s = s; }
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                String cmd = (String) in.readObject();
                if (cmd.equals("ADD_GAME")) {
                    Game g = (Game) in.readObject();
                    gamesMap.put(g.gameName, g);
                    System.out.println("[WORKER] Added game: " + g.gameName);
                    out.writeUTF("SUCCESS");
                } else if (cmd.equals("SEARCH")) {
                    Filters f = (Filters) in.readObject();
                    List<Game> results = new ArrayList<>();
                    for (Game g : gamesMap.values()) {
                        if (g.isActive && g.stars >= f.minStars && 
                        (f.betCategory == null || g.betCategory.equals(f.betCategory)) &&
                        (f.riskLevel == null || g.riskLevel.equals(f.riskLevel))) {
                            results.add(g);
                        }
                    }
                    out.reset();
                    out.writeObject(results);
                    out.flush();
                    System.out.println("[WORKER] Executed Search.");
                } else if (cmd.equals("PLAY")) {
                    String pId = in.readUTF();
                    String gName = in.readUTF();
                    double amt = in.readDouble();
                    
                    // Κλήση της μεθόδου που υπολογίζει το ποντάρισμα
                    double winAmount = processBetNumeric(pId, gName, amt);
                    
                    // ΣΤΕΛΝΟΥΜΕ DOUBLE
                    out.writeDouble(winAmount); 
                    out.flush();
                    System.out.println("[WORKER] Sent win amount " + winAmount + " to Master.");
                } else if (cmd.equals("REMOVE_GAME")) {
                    String name = in.readUTF();
                    if(gamesMap.containsKey(name)) { gamesMap.get(name).isActive = false; out.writeUTF("Game deactivated."); }
                    else out.writeUTF("Not found.");
                } else if (cmd.equals("EDIT_GAME")) {
                    String name = in.readUTF(); String risk = in.readUTF();
                    if(gamesMap.containsKey(name)) { gamesMap.get(name).riskLevel = risk; gamesMap.get(name).updateJackpot(); out.writeUTF("Risk updated."); }
                    else out.writeUTF("Not found.");
                } else if (cmd.equals("RATE_GAME")) {
                    String name = in.readUTF(); int r = in.readInt();
                    if(gamesMap.containsKey(name)) { gamesMap.get(name).addRating(r); out.writeUTF("Rating added."); }
                    else out.writeUTF("Not found.");
                } else if (cmd.equals("PUSH_TO_REDUCER")) {
                    sendToReducer();
                    out.writeUTF("DATA_SENT");
                    out.flush(); // Βεβαιώσου ότι έφυγε η επιβεβαίωση
                    System.out.println("[WORKER] Pushed data to Reducer.");
                }
                out.flush();
            } catch (Exception e) {}
        }
    }
    
    private static void sendToReducer() {
        try (Socket s = new Socket("localhost", 4444);
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            Map<String, Double> gamePNLs = new HashMap<>();
            Map<String, String> gameProviders = new HashMap<>();
            
            for (Game g : gamesMap.values()) {
                gamePNLs.put(g.gameName, g.totalProfitLoss);
                gameProviders.put(g.gameName, g.providerName);
            }
            
            out.writeObject("MAP_DATA");
            out.writeObject(gamePNLs);
            out.writeObject(gameProviders);
            out.writeObject(new HashMap<>(playerStats));
            out.flush();
            in.readUTF(); 
        } catch (Exception e) { System.err.println("Reducer Error: " + e.getMessage()); }
    }

    private static double processBetNumeric(String playerId, String gameName, double betAmount) {
        Game game = gamesMap.get(gameName);
        if (game == null || !game.isActive) return -1.0; // Error code

        try (Socket srgSocket = new Socket("localhost", 5555);
            DataOutputStream out = new DataOutputStream(srgSocket.getOutputStream());
            DataInputStream in = new DataInputStream(srgSocket.getInputStream())) {
            
            out.writeUTF(game.hashKey); out.flush();
            int num = in.readInt(); String hash = in.readUTF();
            if (!HashUtils.sha256(num + game.hashKey).equals(hash)) return -2.0; // Security Error

            double win = 0;
            if (num % 100 == 0) win = betAmount * game.jackpot;
            else {
                double[] table = game.riskLevel.equals("low") ? LOW_RISK : (game.riskLevel.equals("medium") ? MEDIUM_RISK : HIGH_RISK);
                win = betAmount * table[num % 10];
            }

            synchronized (game) { game.totalProfitLoss += (betAmount - win); }
            synchronized (playerStats) { playerStats.merge(playerId, win - betAmount, Double::sum); }
            
            return win; // Επιστρέφει πόσα κέρδισε (π.χ. 0.0 αν έχασε, 35.0 αν κέρδισε)
        } catch (Exception e) { return -3.0; } // SRG Error
    }
}