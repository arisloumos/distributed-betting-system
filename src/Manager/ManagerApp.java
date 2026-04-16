package Manager;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import Common.Game;
import Common.Config;

/**
 * Manager Console Application: Η διεπαφή του διαχειριστή.
 * Επιτρέπει τη διαχείριση παιχνιδιών και την προβολή στατιστικών 
 * μέσω της διαδικασίας MapReduce.
 */
public class ManagerApp {
    // Φόρτωση στοιχείων σύνδεσης από το system.conf
    private static final String MASTER_IP = Config.get("MASTER_IP", "localhost");
    private static final int MASTER_PORT = Config.getInt("MASTER_PORT", 1234);

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("--- Online Betting Platform: Manager Console ---");
        System.out.println("1. Add New Game (JSON File)");
        System.out.println("2. Remove Existing Game");
        System.out.println("3. Edit Game Risk Level");
        System.out.println("4. View Global Statistics (MapReduce)");
        System.out.print("Select an option: ");
        
        String input = sc.nextLine();
        if (input.isEmpty()) return;
        int choice = Integer.parseInt(input);

        System.out.println("[MANAGER] Connecting to Master at " + MASTER_IP + ":" + MASTER_PORT + "...");
        
        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

            if (choice == 1) {
                // Προσθήκη παιχνιδιού μέσω αρχείου JSON
                System.out.print("Enter JSON file path: ");
                String path = sc.nextLine();
                String content = new String(Files.readAllBytes(Paths.get(path)));
                
                System.out.println("[MANAGER] Parsing JSON and creating Game object...");
                Game g = parseJsonManual(content);
                
                out.writeObject("ADD_GAME");
                out.writeObject(g);
                System.out.println("[MANAGER] Sending game data to Master...");
            } 
            else if (choice == 2) {
                // Αφαίρεση (απενεργοποίηση) παιχνιδιού
                out.writeObject("REMOVE_GAME");
                System.out.print("Enter Game Name to remove: ");
                out.writeUTF(sc.nextLine());
            } 
            else if (choice == 3) {
                // Τροποποίηση επιπέδου ρίσκου
                out.writeObject("EDIT_GAME");
                System.out.print("Enter Game Name: ");
                out.writeUTF(sc.nextLine());
                System.out.print("Enter New Risk Level (low/medium/high): ");
                out.writeUTF(sc.nextLine());
            } 
            else if (choice == 4) {
                // Προβολή στατιστικών (Απαιτεί MapReduce στο Backend)
                System.out.println("[MANAGER] Requesting global statistics. This involves a MapReduce job...");
                out.writeObject("STATS");
                out.flush();
                
                // Λήψη των Reduced αποτελεσμάτων
                Map<String, Map<String, Double>> provs = (Map<String, Map<String, Double>>) in.readObject();
                Map<String, Double> plays = (Map<String, Double>) in.readObject();
                
                System.out.println("\n===========================================");
                System.out.println("   GLOBAL STATISTICS (MAPREDUCE RESULTS)   ");
                System.out.println("===========================================");
                
                // Εμφάνιση ανά Πάροχο και Παιχνίδι
                for (String prov : provs.keySet()) {
                    System.out.println("\nProvider: " + prov);
                    double totalProv = 0;
                    for (Map.Entry<String, Double> gameEntry : provs.get(prov).entrySet()) {
                        System.out.printf("   -> %-15s: %10.2f FUN\n", gameEntry.getKey(), gameEntry.getValue());
                        totalProv += gameEntry.getValue();
                    }
                    System.out.printf("   TOTAL for %-10s: %10.2f FUN\n", prov, totalProv);
                }
                
                // Εμφάνιση ανά Παίκτη
                System.out.println("\n--- Profits/Losses per Player ---");
                plays.forEach((k, v) -> System.out.printf("Player ID: %-15s | P/L: %10.2f FUN\n", k, v));
                
                System.out.println("===========================================\n");
                return; // Τερματισμός μετά την εμφάνιση των στατιστικών
            }
            
            out.flush();
            // Εμφάνιση της απάντησης του Master για τις ενέργειες 1, 2, 3
            System.out.println("[SERVER RESPONSE] " + in.readUTF());
            
        } catch (Exception e) {
            System.err.println("[MANAGER] Error: " + e.getMessage());
        }
    }

    /**
     * Χειροκίνητη ανάλυση JSON (Manual Parsing).
     * Χρησιμοποιείται για την αποφυγή εξάρτησης από εξωτερικές βιβλιοθήκες (π.χ. Jackson, GSON).
     */
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

    /**
     * Βοηθητική μέθοδος εξαγωγής τιμής από String μορφής JSON βάσει κλειδιού.
     */
    private static String extract(String json, String key) {
        String k = "\"" + key + "\"";
        int start = json.indexOf(k);
        if (start == -1) return "";
        
        int colon = json.indexOf(":", start);
        int vStart = colon + 1;
        
        // Παράκαμψη κενών και εισαγωγικών
        while (vStart < json.length() && (json.charAt(vStart) == ' ' || json.charAt(vStart) == '\"')) {
            vStart++;
        }
        
        int vEnd = vStart;
        // Εύρεση του τέλους της τιμής (κόμμα, εισαγωγικό, άγκιστρο ή αλλαγή γραμμής)
        while (vEnd < json.length() && json.charAt(vEnd) != ',' && json.charAt(vEnd) != '\"' && 
               json.charAt(vEnd) != '}' && json.charAt(vEnd) != '\n') {
            vEnd++;
        }
        return json.substring(vStart, vEnd).trim().replace("\r", "");
    }
}