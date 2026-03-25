package Player;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;

public class DummyPlayer {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 1234;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Dummy Player App ---");

        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // 1. SEARCH - Ζητάμε τα διαθέσιμα παιχνίδια
            out.writeObject("SEARCH");
            out.flush();

            @SuppressWarnings("unchecked")
            List<Game> availableGames = (List<Game>) in.readObject();

            if (availableGames.isEmpty()) {
                System.out.println("No games available. Add a game first!");
                return;
            }

            System.out.println("\nAvailable Games:");
            for (int i = 0; i < availableGames.size(); i++) {
                System.out.println(i + ". " + availableGames.get(i));
            }

            // 2. PLAY - Επιλογή παιχνιδιού και ποντάρισμα
            System.out.print("\nSelect game index to play: ");
            int choice = scanner.nextInt();
            Game selected = availableGames.get(choice);

            System.out.print("Enter bet amount: ");
            double amount = scanner.nextDouble();

            // Στέλνουμε το αίτημα στον Master (ο οποίος θα το στείλει στον Worker)
            // Πρέπει να ανοίξουμε νέα σύνδεση ή να στείλουμε νέο request
            // Για ευκολία στο Μέρος Α, ας ανοίξουμε νέα σύνδεση για το Play
            playGame(selected.gameName, amount);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static void playGame(String name, double amount) {
        System.out.println("Sending bet to Master... please wait.");
        try (Socket socket = new Socket(MASTER_HOST, MASTER_PORT);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("PLAY");
            out.writeUTF(name);
            out.writeDouble(amount);
            out.flush();

            String result = in.readUTF();
            System.out.println("\n========================");
            System.out.println(result);
            System.out.println("========================\n");

        } catch (Exception e) {
            System.out.println("Error during play: " + e.getMessage());
        }
    }
}