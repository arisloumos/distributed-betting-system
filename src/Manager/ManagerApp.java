package Manager;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import Common.Game;
import Common.Config;

public class ManagerApp {
    private static final String MASTER_IP = Config.get("MASTER_IP", "localhost");
    private static final int MASTER_PORT = Config.getInt("MASTER_PORT", 1234);
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("--- Manager Console ---");
        System.out.println("1. Add Game (JSON)\n2. Remove Game\n3. Edit Risk\n4. Stats");
        System.out.print("Choice: ");
        int choice = Integer.parseInt(sc.nextLine());

        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

            if (choice == 1) {
                System.out.print("JSON path: ");
                String path = sc.nextLine();
                String content = new String(Files.readAllBytes(Paths.get(path)));
                Game g = parseJsonManual(content);
                out.writeObject("ADD_GAME");
                out.writeObject(g);
            } else if (choice == 2) {
                out.writeObject("REMOVE_GAME");
                System.out.print("Game Name: ");
                out.writeUTF(sc.nextLine());
            } else if (choice == 3) {
                out.writeObject("EDIT_GAME");
                System.out.print("Game Name: ");
                out.writeUTF(sc.nextLine());
                System.out.print("New Risk: ");
                out.writeUTF(sc.nextLine());
            } else if (choice == 4) {
                out.writeObject("STATS");
                out.flush();
                Map<String, Map<String, Double>> provs = (Map<String, Map<String, Double>>) in.readObject();
                Map<String, Double> plays = (Map<String, Double>) in.readObject();
                
                System.out.println("\n=== GLOBAL STATISTICS (MapReduce) ===");
                for (String prov : provs.keySet()) {
                    System.out.println("Provider: " + prov);
                    double totalProv = 0;
                    for (Map.Entry<String, Double> gameEntry : provs.get(prov).entrySet()) {
                        System.out.printf("   -> %-15s: %10.2f FUN\n", gameEntry.getKey(), gameEntry.getValue());
                        totalProv += gameEntry.getValue();
                    }
                    System.out.printf("   TOTAL for %-10s: %10.2f FUN\n", prov, totalProv);
                }
                
                System.out.println("\n--- Profits/Losses per Player ---");
                plays.forEach((k, v) -> System.out.printf("Player ID: %-15s | P/L: %10.2f FUN\n", k, v));
                return;
            }
            out.flush();
            System.out.println("Response: " + in.readUTF());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static Game parseJsonManual(String json) {
        String name = extract(json, "GameName");
        String prov = extract(json, "ProviderName");
        int stars = Integer.parseInt(extract(json, "Stars"));
        int votes = Integer.parseInt(extract(json, "NoOfVotes"));
        String logo = extract(json, "GameLogo");
        double min = Double.parseDouble(extract(json, "MinBet"));
        double max = Double.parseDouble(extract(json, "MaxBet"));
        String risk = extract(json, "RiskLevel");
        String key = extract(json, "HashKey");
        return new Game(name, prov, stars, votes, logo, min, max, risk, key);
    }

    private static String extract(String json, String key) {
        String k = "\"" + key + "\"";
        int start = json.indexOf(k);
        int colon = json.indexOf(":", start);
        int vStart = colon + 1;
        while (json.charAt(vStart) == ' ' || json.charAt(vStart) == '\"') vStart++;
        int vEnd = vStart;
        while (vEnd < json.length() && json.charAt(vEnd) != ',' && json.charAt(vEnd) != '\"' && json.charAt(vEnd) != '}' && json.charAt(vEnd) != '\n') vEnd++;
        return json.substring(vStart, vEnd).trim();
    }
}