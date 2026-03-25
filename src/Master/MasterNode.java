package Master;

import java.io.*;
import java.net.*;
import java.util.*;
import Common.Game;

public class MasterNode {
    private static final int MASTER_PORT = 1234;
    // Λίστα με τους Workers (IP και Port)
    private static List<WorkerInfo> workers = new ArrayList<>();

    public static void main(String[] args) {
        // Για το Μέρος Α, ορίζουμε τους Workers χειροκίνητα
        // Μπορείς να προσθέσεις όσους θες εδώ
        workers.add(new WorkerInfo("localhost", 8001));
        workers.add(new WorkerInfo("localhost", 8002)); 

        System.out.println("Master Node started on port " + MASTER_PORT);

        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Κάθε σύνδεση (Manager ή Player) σε νέο Thread
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
            ) {
                String requestType = (String) in.readObject();
                if (requestType.equals("ADD_GAME")) {
                    Game game = (Game) in.readObject();
                    // 1. Hashing για επιλογή Worker
                    int workerIdx = Math.abs(game.gameName.hashCode()) % workers.size();
                    WorkerInfo target = workers.get(workerIdx);
                    
                    // 2. Προώθηση στον Worker
                    String response = forwardToWorker(target, "ADD_GAME", game);
                    out.writeUTF(response);
                } 
                else if (requestType.equals("SEARCH")) {
                    // MAP REDUCE: Ζητάμε από όλους τους Workers
                    List<Game> allGames = new ArrayList<>();
                    for (WorkerInfo w : workers) {
                        List<Game> workerGames = (List<Game>) forwardToWorkerObject(w, "SEARCH", null);
                        if (workerGames != null) allGames.addAll(workerGames);
                    }
                    out.writeObject(allGames);
                }
                else if (requestType.equals("PLAY")) {
                    String gameName = in.readUTF();
                    double amount = in.readDouble();
                    
                    // Hashing για να βρούμε ΠΟΥ είναι το παιχνίδι
                    int workerIdx = Math.abs(gameName.hashCode()) % workers.size();
                    WorkerInfo target = workers.get(workerIdx);
                    
                    // Προώθηση εντολής PLAY στον σωστό Worker
                    String result = forwardToWorkerPlay(target, gameName, amount);
                    out.writeUTF(result);
                }
                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Βοηθητική μέθοδος για αποστολή εντολής ADD_GAME σε Worker
        private String forwardToWorker(WorkerInfo w, String cmd, Game game) {
            try (Socket s = new Socket(w.host, w.port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject(cmd);
                out.writeObject(game);
                return in.readUTF();
            } catch (Exception e) { return "Worker Error"; }
        }

        // Βοηθητική μέθοδος για SEARCH (Map Phase)
        private Object forwardToWorkerObject(WorkerInfo w, String cmd, Object data) {
            try (Socket s = new Socket(w.host, w.port);
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                out.writeObject(cmd);
                return in.readObject();
            } catch (Exception e) { return null; }
        }

        // Βοηθητική μέθοδος για PLAY
        private String forwardToWorkerPlay(WorkerInfo w, String gameName, double amount) {
            System.out.println("[Master] Forwarding PLAY to Worker at " + w.port); // DEBUG
            try (Socket s = new Socket(w.host, w.port);
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                out.writeObject("PLAY");
                out.writeUTF(gameName);
                out.writeDouble(amount);
                out.flush(); // ΣΤΕΙΛΕ ΤΑ ΤΩΡΑ
                
                return in.readUTF(); // Περίμενε την απάντηση από τον Worker
            } catch (Exception e) { 
                e.printStackTrace();
                return "Worker Error"; 
            }
        }
    }

    static class WorkerInfo {
        String host;
        int port;
        WorkerInfo(String h, int p) { this.host = h; this.port = p; }
    }
}