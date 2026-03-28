package Manager;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import Common.Game;

public class ManagerApp {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 1234;

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        if (command.equals("add")) {
            if (args.length < 2) {
                System.out.println("Error: Please provide the path to the JSON file.");
                return;
            }
            addGame(args[1]);
        } else if (command.equals("stats")) {
            showStats();
        } else {
            printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("--- Manager Console App Usage ---");
        System.out.println("1. Add a game:   java Manager.ManagerApp add <json_file_path>");
        System.out.println("2. Show stats:   java Manager.ManagerApp stats");
    }

    private static void addGame(String jsonPath) {
        try {
            // 1. Διάβασμα του αρχείου JSON
            String content = new String(Files.readAllBytes(Paths.get(jsonPath)));
            
            // 2. Parsing του JSON (Manual για αποφυγή εξωτερικών βιβλιοθηκών)
            Game game = parseJsonManual(content);
            
            System.out.println("Parsed Game: " + game.gameName + " by " + game.providerName);

            // 3. Σύνδεση στον Master
            try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("ADD_GAME");
                out.writeObject(game);
                out.flush();

                String response = in.readUTF();
                System.out.println("Master response: " + response);
            }
        } catch (Exception e) {
            System.err.println("Error adding game: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void showStats() {
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            System.out.println("Requesting Global Stats (MapReduce Aggregation)...");
            out.writeObject("STATS");
            out.flush();

            // Λήψη των αποτελεσμάτων του Reduce από τον Master
            Map<String, Double> providerStats = (Map<String, Double>) in.readObject();
            Map<String, Double> playerStats = (Map<String, Double>) in.readObject();

            System.out.println("\n=== TOTAL PROFITS/LOSSES PER PROVIDER ===");
            if (providerStats.isEmpty()) System.out.println("No data available.");
            for (Map.Entry<String, Double> entry : providerStats.entrySet()) {
                System.out.printf("Provider: %-15s | Total P/L: %10.2f FUN\n", entry.getKey(), entry.getValue());
            }

            System.out.println("\n=== TOTAL PROFITS/LOSSES PER PLAYER ===");
            if (playerStats.isEmpty()) System.out.println("No data available.");
            for (Map.Entry<String, Double> entry : playerStats.entrySet()) {
                System.out.printf("Player ID: %-15s | Total P/L: %10.2f FUN\n", entry.getKey(), entry.getValue());
            }
            System.out.println("========================================\n");

        } catch (Exception e) {
            System.err.println("Error fetching stats: " + e.getMessage());
        }
    }

    // Manual JSON Parser: Εξάγει τιμές ανάμεσα σε quotes
    private static Game parseJsonManual(String json) {
        String name = extractValue(json, "GameName");
        String provider = extractValue(json, "ProviderName");
        int stars = Integer.parseInt(extractValue(json, "Stars"));
        int votes = Integer.parseInt(extractValue(json, "NoOfVotes"));
        String logo = extractValue(json, "GameLogo");
        double minBet = Double.parseDouble(extractValue(json, "MinBet"));
        double maxBet = Double.parseDouble(extractValue(json, "MaxBet"));
        String risk = extractValue(json, "RiskLevel");
        String secret = extractValue(json, "HashKey");

        return new Game(name, provider, stars, votes, logo, minBet, maxBet, risk, secret);
    }

    private static String extractValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) return "";
        
        // Βρίσκουμε την άνω-κάτω τελεία μετά το κλειδί
        int colonIdx = json.indexOf(":", startIdx);
        
        // Βρίσκουμε την αρχή της τιμής (αν είναι string έχει ", αν είναι νούμερο όχι)
        int valueStart = colonIdx + 1;
        while (json.charAt(valueStart) == ' ' || json.charAt(valueStart) == '\"') {
            valueStart++;
        }
        
        // Βρίσκουμε το τέλος της τιμής (κόμμα ή κλείσιμο αγκύλης)
        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != ',' && 
               json.charAt(valueEnd) != '\"' && json.charAt(valueEnd) != '}' && 
               json.charAt(valueEnd) != '\n' && json.charAt(valueEnd) != '\r') {
            valueEnd++;
        }
        
        return json.substring(valueStart, valueEnd).trim();
    }
}