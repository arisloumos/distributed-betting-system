package Player;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;
import Common.Filters;

public class DummyPlayer {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 1234;
    private static String playerId;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Welcome to Distributed Gaming Platform ---");
        
        System.out.print("Enter your Player ID to login: ");
        playerId = scanner.nextLine();

        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // 1. Ρύθμιση Φίλτρων
            System.out.println("\nSet Search Filters (press Enter to skip a filter):");
            
            System.out.print("Minimum Stars (1-5): ");
            String starsIn = scanner.nextLine();
            int minStars = starsIn.isEmpty() ? 0 : Integer.parseInt(starsIn);

            System.out.print("Bet Category ($, $$, $$$): ");
            String betCat = scanner.nextLine();
            if (betCat.isEmpty()) betCat = null;

            System.out.print("Risk Level (low, medium, high): ");
            String risk = scanner.nextLine();
            if (risk.isEmpty()) risk = null;

            Filters filters = new Filters(minStars, betCat, risk);

            // 2. SEARCH - Αποστολή εντολής και αντικειμένου Filters
            out.writeObject("SEARCH");
            out.writeObject(filters);
            out.flush();

            @SuppressWarnings("unchecked")
            List<Game> availableGames = (List<Game>) in.readObject();

            if (availableGames.isEmpty()) {
                System.out.println("No games found matching your criteria.");
                return;
            }

            System.out.println("\n--- Games Found ---");
            for (int i = 0; i < availableGames.size(); i++) {
                System.out.println(i + ". " + availableGames.get(i));
            }

            // 3. PLAY - Επιλογή και ποντάρισμα
            System.out.print("\nSelect game index to play: ");
            int choice = Integer.parseInt(scanner.nextLine());
            Game selected = availableGames.get(choice);

            System.out.print("Enter bet amount (Min: " + selected.minBet + "): ");
            double amount = Double.parseDouble(scanner.nextLine());

            playGame(selected.gameName, amount);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void playGame(String gameName, double amount) {
        System.out.println("\nSending bet to Master... please wait.");
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("PLAY");
            out.writeUTF(playerId); // Στέλνουμε ποιος παίζει
            out.writeUTF(gameName);
            out.writeDouble(amount);
            out.flush();

            String result = in.readUTF();
            System.out.println("\n========================");
            System.out.println("PLAYER ID: " + playerId);
            System.out.println(result);
            System.out.println("========================\n");

        } catch (Exception e) {
            System.out.println("Error during play: " + e.getMessage());
        }
    }
}