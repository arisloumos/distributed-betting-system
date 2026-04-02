package Player;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.*;

/**
 * Dummy Player Application: Μια κονσόλα που προσομοιώνει τη λειτουργία του παίκτη.
 * Επιτρέπει την αναζήτηση παιχνιδιών με φίλτρα, το ποντάρισμα και τη βαθμολόγηση.
 * Αντικαθιστά την Android εφαρμογή για το Παραδοτέο Α.
 */
public class DummyPlayer {
    private static Scanner sc = new Scanner(System.in);
    private static String pId;
    
    // Φόρτωση στοιχείων σύνδεσης του Master από το system.conf
    private static final String MASTER_IP = Config.get("MASTER_IP", "localhost");
    private static final int MASTER_PORT = Config.getInt("MASTER_PORT", 1234);

    public static void main(String[] args) {
        System.out.println("--- Online Gaming Platform: Player Console ---");
        System.out.print("Enter your Player ID: ");
        pId = sc.nextLine();
        if(pId.isEmpty()) pId = "Guest_" + new Random().nextInt(1000);

        System.out.println("Welcome, " + pId + "!");

        while (true) {
            System.out.println("\n--- PLAYER MENU ---");
            System.out.println("1. Add Balance (Tokens)");
            System.out.println("2. Search & Play Games");
            System.out.println("3. View Current Balance");
            System.out.println("4. Exit");
            System.out.print("Choice: ");
            
            String choice = sc.nextLine();
            if (choice.equals("1")) {
                addBalance();
            } else if (choice.equals("2")) {
                searchAndPlay();
            } else if (choice.equals("3")) {
                viewBalance();
            } else if (choice.equals("4")) {
                System.out.println("Goodbye!");
                break;
            }
        }
    }

    /**
     * Αποστολή αιτήματος στον Master για προσθήκη tokens στο υπόλοιπο του παίκτη.
     */
    private static void addBalance() {
        System.out.print("Enter amount of tokens to add: ");
        try {
            double amount = Double.parseDouble(sc.nextLine());
            
            System.out.println("[PLAYER] Connecting to Master at " + MASTER_IP + " to add balance...");
            try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                out.writeObject("ADD_BALANCE");
                out.writeUTF(pId);
                out.writeDouble(amount);
                out.flush();
                
                // Εμφάνιση επιβεβαίωσης από τον Server
                System.out.println("[SERVER] " + in.readUTF());
            } 
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount format.");
        } catch (Exception e) {
            System.err.println("[PLAYER] Connection Error: " + e.getMessage());
        }
    }

    /**
     * Αποστολή αιτήματος στον Master για προβολή του τρέχοντος υπολοίπου.
     */
    private static void viewBalance() {
        System.out.println("[PLAYER] Fetching balance from Master...");
        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("GET_BALANCE");
            out.writeUTF(pId);
            out.flush();
            
            System.out.println("[SERVER] " + in.readUTF());
        } catch (Exception e) {
            System.err.println("[PLAYER] Connection Error: " + e.getMessage());
        }
    }

    /**
     * Διαδικασία αναζήτησης παιχνιδιών με φίλτρα και επιλογή για ποντάρισμα.
     */
    private static void searchAndPlay() {
        System.out.println("\n--- Search Filters (Press Enter to Skip) ---");
        System.out.print("Min Stars (1-5): "); 
        String sIn = sc.nextLine();
        int stars = sIn.isEmpty() ? 0 : Integer.parseInt(sIn);

        System.out.print("Bet Category ($, $$, $$$): ");
        String betCat = sc.nextLine();
        if(betCat.isEmpty()) betCat = null;

        System.out.print("Risk Level (low/medium/high): ");
        String risk = sc.nextLine();
        if(risk.isEmpty()) risk = null;

        System.out.println("[PLAYER] Sending search request to Master...");
        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

            // Αποστολή του αντικειμένου Filters στον Master
            out.writeObject("SEARCH");
            out.writeObject(new Filters(stars, betCat, risk));
            out.flush();

            // Λήψη της λίστας παιχνιδιών που ικανοποιούν τα κριτήρια
            List<Game> games = (List<Game>) in.readObject();
            if (games.isEmpty()) {
                System.out.println("[SERVER] No games found matching your criteria.");
                return;
            }

            System.out.println("\n--- Available Games ---");
            for (int i = 0; i < games.size(); i++) {
                System.out.println(i + ". " + games.get(i));
            }

            System.out.print("\nChoose index to play (or Enter to cancel): "); 
            String idxIn = sc.nextLine();
            if(idxIn.isEmpty()) return;
            
            int idx = Integer.parseInt(idxIn);
            if (idx < 0 || idx >= games.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            System.out.print("Enter Bet amount: "); 
            double amt = Double.parseDouble(sc.nextLine());

            // Μετάβαση στη διαδικασία πονταρίσματος
            playGame(pId, games.get(idx).gameName, amt);

        } catch (Exception e) {
            System.err.println("[PLAYER] Search Error: " + e.getMessage());
        }
    }

    /**
     * Διαδικασία εκτέλεσης πονταρίσματος και προαιρετικής βαθμολόγησης.
     */
    private static void playGame(String pId, String gName, double amt) {
        System.out.println("[PLAYER] Sending bet request for '" + gName + "'...");
        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("PLAY");
            out.writeUTF(pId); 
            out.writeUTF(gName); 
            out.writeDouble(amt);
            out.flush();
            
            // Εμφάνιση αποτελέσματος (WIN/LOSS/DRAW) και νέου υπολοίπου
            String result = in.readUTF();
            System.out.println("\n>>> GAME RESULT: " + result);
            
            // Προαιρετική βαθμολόγηση μετά το παιχνίδι
            if (!result.startsWith("REJECTED") && !result.startsWith("ERROR")) {
                System.out.print("Rate this game (1-5) or Enter to skip: ");
                String rIn = sc.nextLine();
                if (!rIn.isEmpty()) {
                    rateGame(gName, Integer.parseInt(rIn));
                }
            }
        } catch (Exception e) {
            System.err.println("[PLAYER] Play Error: " + e.getMessage());
        }
    }

    /**
     * Αποστολή βαθμολογίας για ένα συγκεκριμένο παιχνίδι.
     */
    private static void rateGame(String gName, int rating) {
        try (Socket s = new Socket(MASTER_IP, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("RATE_GAME");
            out.writeUTF(gName); 
            out.writeInt(rating);
            out.flush();
            
            System.out.println("[SERVER] " + in.readUTF());
        } catch (Exception e) {
            System.err.println("[PLAYER] Rating Error: " + e.getMessage());
        }
    }
}