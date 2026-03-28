package Worker;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;
import Common.HashUtils;
import Common.Filters;

public class WorkerNode {
    private static int myPort;
    private static final String SRG_HOST = "localhost";
    private static final int SRG_PORT = 5555;

    // Η "βάση δεδομένων" μας στη μνήμη
    private static Map<String, Game> gamesMap = new HashMap<>();
    // Στατιστικά ανά παίκτη: PlayerID -> Συνολικό Κέρδος/Ζημιά
    private static Map<String, Double> playerStats = new HashMap<>();

    // Πίνακες Πολλαπλασιαστών βάσει εκφώνησης
    private static final double[] LOW_RISK = {0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
    private static final double[] MEDIUM_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
    private static final double[] HIGH_RISK = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java WorkerNode <port>");
            return;
        }
        myPort = Integer.parseInt(args[0]);

        System.out.println("Worker Node started on port " + myPort);

        try (ServerSocket serverSocket = new ServerSocket(myPort)) {
            while (true) {
                Socket masterSocket = serverSocket.accept();
                new Thread(new MasterHandler(masterSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class MasterHandler implements Runnable {
        private Socket socket;

        public MasterHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                String command = (String) in.readObject();

                if (command.equals("ADD_GAME")) {
                    Game newGame = (Game) in.readObject();
                    gamesMap.put(newGame.gameName, newGame);
                    System.out.println("Added game: " + newGame.gameName);
                    out.writeUTF("SUCCESS");
                } 
                else if (command.equals("SEARCH")) {
                    System.out.println("[WORKER " + myPort + "] Filtering games for Master...");

                    // Λήψη φίλτρων από τον Master
                    Filters f = (Filters) in.readObject();
                    List<Game> filteredResults = new ArrayList<>();
                    
                    for (Game g : gamesMap.values()) {
                        boolean matches = true;
                        if (g.stars < f.minStars) matches = false;
                        if (f.betCategory != null && !g.betCategory.equals(f.betCategory)) matches = false;
                        if (f.riskLevel != null && !g.riskLevel.equals(f.riskLevel)) matches = false;
                        
                        if (matches) filteredResults.add(g);
                    }
                    out.writeObject(filteredResults);
                }
                else if (command.equals("PLAY")) {
                    String playerId = in.readUTF(); // Λήψη ID παίκτη
                    String gameName = in.readUTF();
                    double betAmount = in.readDouble();
                    
                    System.out.println("[WORKER " + myPort + "] Processing bet for player: " + playerId);

                    String result = processBet(playerId, gameName, betAmount);
                    out.writeUTF(result);
                }
                else if (command.equals("GET_STATS")) {
                    System.out.println("[WORKER " + myPort + "] Sending local stats to Master for Reduction.");
                    
                    // Στέλνουμε αντίγραφα των maps για το Reduce στον Master
                    out.writeObject(new HashMap<>(gamesMap));
                    out.writeObject(new HashMap<>(playerStats));
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String processBet(String playerId, String gameName, double betAmount) {
        Game game = gamesMap.get(gameName);
        if (game == null) return "Game not found";

        try (Socket srgSocket = new Socket(SRG_HOST, SRG_PORT);
            DataOutputStream out = new DataOutputStream(srgSocket.getOutputStream());
            DataInputStream in = new DataInputStream(srgSocket.getInputStream())) {

            out.writeUTF(game.hashKey);
            out.flush();

            int randomNumber = in.readInt();
            String receivedHash = in.readUTF();

            String calculatedHash = HashUtils.sha256(randomNumber + game.hashKey);
            if (!calculatedHash.equals(receivedHash)) {
                return "Security Error: Hash mismatch!";
            }

            double winAmount = 0;
            if (randomNumber % 100 == 0) {
                winAmount = betAmount * game.jackpot;
            } else {
                int index = randomNumber % 10;
                double multiplier = 0;
                if (game.riskLevel.equals("low")) multiplier = LOW_RISK[index];
                else if (game.riskLevel.equals("medium")) multiplier = MEDIUM_RISK[index];
                else if (game.riskLevel.equals("high")) multiplier = HIGH_RISK[index];
                
                winAmount = betAmount * multiplier;
            }

            // Ενημέρωση εσόδων παιχνιδιού (Manager Stats)
            synchronized (game) {
                game.totalProfitLoss += (betAmount - winAmount);
            }

            // Ενημέρωση κέρδους/ζημιάς παίκτη (Player Stats)
            synchronized (playerStats) {
                double currentStatus = playerStats.getOrDefault(playerId, 0.0);
                playerStats.put(playerId, currentStatus + (winAmount - betAmount));
            }

            String status;
            if (winAmount > betAmount) {
                status = "WIN! You won: " + winAmount + " FUN";
            } else if (winAmount == betAmount) {
                status = "DRAW (Money back)";
            } else {
                status = "LOSS. You kept: " + winAmount + " FUN";
            }

            return "Result: " + status;

        } catch (Exception e) {
            return "Error connecting to SRG: " + e.getMessage();
        }
    }
}