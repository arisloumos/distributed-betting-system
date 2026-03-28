package Player;
import java.io.*;
import java.net.*;
import java.util.*;
import Common.*;

public class DummyPlayer {
    private static Scanner sc = new Scanner(System.in);
    private static String pId;

    public static void main(String[] args) {
        System.out.print("Enter Player ID: ");
        pId = sc.nextLine();
        if(pId.isEmpty()) pId = "Guest";

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
                break;
            }
        }
    }

    private static void addBalance() {
        System.out.print("Enter amount of tokens to add: ");
        double amount = Double.parseDouble(sc.nextLine());
        try (Socket s = new Socket("localhost", 1234);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            
            out.writeObject("ADD_BALANCE");
            out.writeUTF(pId);
            out.writeDouble(amount);
            out.flush();
            System.out.println("Server: " + in.readUTF());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void viewBalance() {
        try (Socket s = new Socket("localhost", 1234);
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            out.writeObject("GET_BALANCE");
            out.writeUTF(pId);
            out.flush();
            System.out.println(in.readUTF());
        } catch (Exception e) { e.printStackTrace(); }
    }

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

        try (Socket s = new Socket("localhost", 1234);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {

            out.writeObject("SEARCH");
            out.writeObject(new Filters(stars, betCat, risk));
            out.flush();

            List<Game> games = (List<Game>) in.readObject();
            if (games.isEmpty()) { System.out.println("No games found."); return; }

            for (int i=0; i<games.size(); i++) System.out.println(i + ". " + games.get(i));

            System.out.print("\nChoose index to play (or Enter to cancel): "); 
            String idxIn = sc.nextLine();
            if(idxIn.isEmpty()) return;
            int idx = Integer.parseInt(idxIn);

            System.out.print("Bet amount: "); 
            double amt = Double.parseDouble(sc.nextLine());

            playGame(pId, games.get(idx).gameName, amt);

        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }

    private static void playGame(String pId, String gName, double amt) {
        try (Socket s = new Socket("localhost", 1234);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            out.writeObject("PLAY");
            out.writeUTF(pId); out.writeUTF(gName); out.writeDouble(amt);
            out.flush();
            System.out.println("\n>>> Result: " + in.readUTF());
            
            System.out.print("Rate (1-5) or Enter to skip: ");
            String rIn = sc.nextLine();
            if (!rIn.isEmpty()) {
                try (Socket s2 = new Socket("localhost", 1234);
                     ObjectOutputStream out2 = new ObjectOutputStream(s2.getOutputStream());
                     ObjectInputStream in2 = new ObjectInputStream(s2.getInputStream())) {
                    out2.writeObject("RATE_GAME");
                    out2.writeUTF(gName); out2.writeInt(Integer.parseInt(rIn));
                    out2.flush();
                    in2.readUTF();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}