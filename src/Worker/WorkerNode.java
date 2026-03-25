package Worker;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;
import Common.HashUtils;

public class WorkerNode {
    private static int myPort;
    private static final String SRG_HOST = "localhost";
    private static final int SRG_PORT = 5555;

    // Η "βάση δεδομένων" μας στη μνήμη
    private static Map<String, Game> gamesMap = new HashMap<>();

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
                // Διαβάζουμε την εντολή από τον Master
                String command = (String) in.readObject();

                if (command.equals("ADD_GAME")) {
                    Game newGame = (Game) in.readObject();
                    gamesMap.put(newGame.gameName, newGame);
                    System.out.println("Added game: " + newGame.gameName);
                    out.writeUTF("SUCCESS");
                } 
                else if (command.equals("SEARCH")) {
                    // Map Phase: Φιλτράρισμα παιχνιδιών
                    // Εδώ θα έρθουν τα φίλτρα (θα το κάνουμε στο επόμενο βήμα)
                    out.writeObject(new ArrayList<>(gamesMap.values()));
                }
                else if (command.equals("PLAY")) {
                    String gameName = in.readUTF();
                    double betAmount = in.readDouble();
                    
                    String result = processBet(gameName, betAmount);
                    out.writeUTF(result);
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Η λογική του πονταρίσματος
    private static String processBet(String gameName, double betAmount) {
        Game game = gamesMap.get(gameName);
        if (game == null) return "Game not found";

        System.out.println("[Worker] Connecting to SRG for game: " + gameName); // DEBUG

        try (Socket srgSocket = new Socket(SRG_HOST, SRG_PORT);
            DataOutputStream out = new DataOutputStream(srgSocket.getOutputStream());
            DataInputStream in = new DataInputStream(srgSocket.getInputStream())) {

            out.writeUTF(game.hashKey);
            out.flush(); // ΠΟΛΥ ΣΗΜΑΝΤΙΚΟ

            int randomNumber = in.readInt();
            String receivedHash = in.readUTF();
            System.out.println("[Worker] Received number from SRG: " + randomNumber); // DEBUG

            String calculatedHash = HashUtils.sha256(randomNumber + game.hashKey);
            if (!calculatedHash.equals(receivedHash)) {
                return "Security Error: Hash mismatch!";
            }

            double winAmount = 0;
            if (randomNumber % 100 == 0) {
                winAmount = betAmount * game.jackpot;
                System.out.println("[Worker] JACKPOT!!!"); // DEBUG
            } else {
                int index = randomNumber % 10;
                double multiplier = 0;
                if (game.riskLevel.equals("low")) multiplier = LOW_RISK[index];
                else if (game.riskLevel.equals("medium")) multiplier = MEDIUM_RISK[index];
                else if (game.riskLevel.equals("high")) multiplier = HIGH_RISK[index];
                
                winAmount = betAmount * multiplier;
            }

            synchronized (game) {
                game.totalProfitLoss += (betAmount - winAmount);
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