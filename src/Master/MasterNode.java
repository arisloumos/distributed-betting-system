package Master;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import Common.*;

/**
 * Master Node: Ο κεντρικός συντονιστής του συστήματος.
 * Διαχειρίζεται τη δρομολόγηση των παιχνιδιών, τα υπόλοιπα των παικτών
 * και τον συντονισμό των MapReduce εργασιών.
 */
public class MasterNode {
    private static final int MASTER_PORT = Config.getInt("MASTER_PORT", 1234);
    private static final String REDUCER_IP = Config.get("REDUCER_IP", "localhost");
    private static final int REDUCER_PORT = Config.getInt("REDUCER_PORT", 4444);
    
    // Λίστα με τους διαθέσιμους Workers (φορτώνεται από το config)
    private static List<WorkerInfo> workers = new ArrayList<>();
    
    // Shared State: Τα υπόλοιπα των παικτών (Tokens)
    private static Map<String, Double> playerBalances = new HashMap<>();

    public static void main(String[] args) {
        // 1. Δυναμική Φόρτωση Workers από το αρχείο workers.conf
        try {
            java.util.List<String> lines = Files.readAllLines(Paths.get("workers.conf"));
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(":");
                String host = parts[0].trim().replace("\r", ""); // Καθαρίζει το κρυφό \r
                int port = Integer.parseInt(parts[1].trim().replace("\r", ""));
                workers.add(new WorkerInfo(host, port));
            }
            System.out.println("[MASTER] Loaded " + workers.size() + " workers from configuration.");
        } catch (Exception e) {
            System.err.println("[MASTER] Critical Error: Could not load workers.conf.");
            return;
        }

        System.out.println("[MASTER] Server started on port " + MASTER_PORT);

        // 2. Κύριος βρόχος αποδοχής συνδέσεων (Clients & Manager)
        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Κάθε σύνδεση εξυπηρετείται σε νέο Thread (Πολυνηματικότητα)
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("[MASTER] Server Error: " + e.getMessage());
        }
    }

    /**
     * ClientHandler: Διαχειρίζεται το πρωτόκολλο επικοινωνίας για κάθε αίτημα.
     */
    static class ClientHandler implements Runnable {
        private Socket s;
        public ClientHandler(Socket s) { this.s = s; }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                String type = (String) in.readObject();
                System.out.println("[MASTER] Request received: " + type);

                // --- ΔΙΑΧΕΙΡΙΣΗ ΠΑΙΧΝΙΔΙΩΝ (Manager) ---
                if (type.equals("ADD_GAME")) {
                    Game g = (Game) in.readObject();
                    // Επιλογή Worker βάσει Hash του ονόματος (Routing Strategy)
                    int idx = Math.abs(g.gameName.hashCode()) % workers.size();
                    
                    if (checkIfGameExists(workers.get(idx), g.gameName)) {
                        out.writeUTF("REJECTED: Game already exists.");
                    } else {
                        System.out.println("[MASTER] Routing ADD_GAME to Worker " + idx);
                        out.writeUTF(forwardAdd(workers.get(idx), g));
                    }
                }
                else if (type.equals("REMOVE_GAME") || type.equals("EDIT_GAME") || type.equals("RATE_GAME")) {
                    String name = in.readUTF();
                    int idx = Math.abs(name.hashCode()) % workers.size();
                    out.writeUTF(forwardUpdateToWorker(workers.get(idx), type, name, in));
                }

                // --- ΑΝΑΖΗΤΗΣΗ (Player) ---
                else if (type.equals("SEARCH")) {
                    Filters f = (Filters) in.readObject();
                    List<Game> allResults = new ArrayList<>();
                    // Συγκέντρωση αποτελεσμάτων από όλους τους Workers (Scatter-Gather)
                    for (WorkerInfo w : workers) {
                        allResults.addAll(forwardSearch(w, f));
                    }
                    out.writeObject(allResults);
                }

                // --- MAPREDUCE ΣΤΑΤΙΣΤΙΚΑ (Manager) ---
                else if (type.equals("STATS")) {
                    System.out.println("[MASTER] Initiating MapReduce Job...");
                    
                    // Βήμα 1: Ενημέρωση Reducer για το πλήθος των Workers
                    try (Socket sR = new Socket(REDUCER_IP, REDUCER_PORT);
                         ObjectOutputStream outR = new ObjectOutputStream(sR.getOutputStream())) {
                        outR.writeObject("SET_WORKER_COUNT");
                        outR.writeInt(workers.size());
                        outR.flush();
                    } catch (Exception e) { System.err.println("[MASTER] Reducer Error: " + e.getMessage()); }

                    // Βήμα 2: Καθαρισμός παλιών στατιστικών
                    callReducer("RESET_STATS");

                    // Βήμα 3: Ειδοποίηση Workers να στείλουν δεδομένα (Map Phase)
                    for (WorkerInfo w : workers) {
                        forwardSimpleCmd(w, "PUSH_TO_REDUCER"); 
                    }

                    // Βήμα 4: Λήψη τελικών αποτελεσμάτων (Reduce Phase)
                    Map<String, Object> results = getResultsFromReducer();
                    out.writeObject(results.get("providers"));
                    out.writeObject(results.get("players"));
                    System.out.println("[MASTER] MapReduce Job completed.");
                }

                // --- ΔΙΑΔΙΚΑΣΙΑ ΠΟΝΤΑΡΙΣΜΑΤΟΣ (Player) ---
                else if (type.equals("PLAY")) {
                    String pId = in.readUTF();
                    String gName = in.readUTF();
                    double amt = in.readDouble();

                    if (amt <= 0) {
                        out.writeUTF("REJECTED: Invalid amount.");
                    } else {
                        synchronized(playerBalances) {
                            double currentBal = playerBalances.getOrDefault(pId, 0.0);
                            
                            if (currentBal < amt) {
                                System.out.println("[MASTER] Play rejected: Insufficient balance for " + pId);
                                out.writeUTF("REJECTED: Insufficient Balance.");
                            } else {
                                // 1. Δέσμευση ποσού (Atomic-like transaction)
                                playerBalances.put(pId, currentBal - amt);
                                
                                int idx = Math.abs(gName.hashCode()) % workers.size();
                                System.out.println("[MASTER] Calling Worker for bet processing...");
                                
                                // 2. Κλήση του Worker για τον υπολογισμό του κέρδους
                                double winAmount = forwardPlayToWorkerNumeric(workers.get(idx), pId, gName, amt);
                                
                                if (winAmount >= 0) {
                                    // 3α. Επιτυχία: Ενημέρωση υπολοίπου με το κέρδος
                                    playerBalances.put(pId, playerBalances.get(pId) + winAmount);
                                    String res = (winAmount > amt) ? "WIN" : (winAmount == amt ? "DRAW" : "LOSS");
                                    out.writeUTF(res + " | New Balance: " + String.format("%.2f", playerBalances.get(pId)));
                                } else {
                                    // 3β. Σφάλμα (π.χ. Worker down): Επιστροφή χρημάτων (Refund)
                                    playerBalances.put(pId, playerBalances.get(pId) + amt);

                                    // Κατηγοριοποίηση σφάλματος για καλύτερη ενημέρωση του παίκτη
                                    String errorType;
                                    if (winAmount == -1.0) errorType = "Game not found";
                                    else if (winAmount == -2.0) errorType = "Security/Hash Error";
                                    else if (winAmount == -4.0) errorType = "Bet amount out of game limits";
                                    else errorType = "Server Error";
                                    
                                    System.out.println("[MASTER] Refund issued to " + pId + ": " + errorType);
                                    out.writeUTF("ERROR: " + errorType + ". Your balance was restored.");
                                }
                            }
                        }
                    }
                }

                // --- ΔΙΑΧΕΙΡΙΣΗ ΥΠΟΛΟΙΠΟΥ ---
                else if (type.equals("ADD_BALANCE")) {
                    String pId = in.readUTF();
                    double amount = in.readDouble();
                    synchronized(playerBalances) {
                        playerBalances.put(pId, playerBalances.getOrDefault(pId, 0.0) + amount);
                    }
                    out.writeUTF("Balance updated. Current: " + playerBalances.get(pId));
                }
                else if (type.equals("GET_BALANCE")) {
                    String pId = in.readUTF();
                    out.writeUTF("Current balance: " + playerBalances.getOrDefault(pId, 0.0));
                }

                out.flush();
            } catch (Exception e) { 
                System.err.println("[MASTER] Client Handler Error: " + e.getMessage());
            }
        }

        // --- HELPER METHODS ΓΙΑ ΕΠΙΚΟΙΝΩΝΙΑ ΜΕ WORKERS ---

        private String forwardUpdateToWorker(WorkerInfo w, String type, String name, ObjectInputStream clientIn) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject(type);
                outW.writeUTF(name);
                if (type.equals("EDIT_GAME")) outW.writeUTF(clientIn.readUTF());
                else if (type.equals("RATE_GAME")) outW.writeInt(clientIn.readInt());
                outW.flush();
                return inW.readUTF();
            } catch (Exception e) { return "Worker Unreachable"; }
        }

        private String forwardAdd(WorkerInfo w, Game g) {
            System.out.println("[DEBUG] Master trying to connect to Worker at: " + w.host + ":" + w.port);
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("ADD_GAME");
                outW.writeObject(g);
                outW.flush();
                return inW.readUTF();
            } catch (Exception e) { return "Worker Unreachable"; }
        }

        private boolean checkIfGameExists(WorkerInfo w, String gameName) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("SEARCH");
                outW.writeObject(new Filters(0, null, null));
                outW.flush();
                List<Game> games = (List<Game>) inW.readObject();
                for (Game g : games) if (g.gameName.equalsIgnoreCase(gameName)) return true;
            } catch (Exception e) { return false; }
            return false;
        }

        private List<Game> forwardSearch(WorkerInfo w, Filters f) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("SEARCH");
                outW.writeObject(f);
                outW.flush();
                return (List<Game>) inW.readObject();
            } catch (Exception e) { return new ArrayList<>(); }
        }

        private void forwardSimpleCmd(WorkerInfo w, String cmd) {
            try (Socket sock = new Socket(w.host, w.port);
                 ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                 ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject(cmd);
                outW.flush();
                inW.readUTF(); // Συγχρονισμός: Περιμένει επιβεβαίωση
            } catch (Exception e) {}
        }

        private double forwardPlayToWorkerNumeric(WorkerInfo w, String pId, String gName, double amt) {
            try (Socket sock = new Socket(w.host, w.port);
                ObjectOutputStream outW = new ObjectOutputStream(sock.getOutputStream());
                ObjectInputStream inW = new ObjectInputStream(sock.getInputStream())) {
                outW.writeObject("PLAY");
                outW.writeUTF(pId); outW.writeUTF(gName); outW.writeDouble(amt);
                outW.flush();
                return inW.readDouble(); 
            } catch (Exception e) { return -3.0; }
        }
    }

    // --- ΜΕΘΟΔΟΙ ΕΠΙΚΟΙΝΩΝΙΑΣ ΜΕ REDUCER ---

    private static void callReducer(String cmd) {
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(cmd);
            out.flush();
        } catch (Exception e) {}
    }

    private static Map<String, Object> getResultsFromReducer() {
        try (Socket s = new Socket(REDUCER_IP, REDUCER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            out.writeObject("GET_REDUCED_RESULTS");
            out.flush();
            Map<String, Object> res = new HashMap<>();
            res.put("providers", in.readObject());
            res.put("players", in.readObject());
            return res;
        } catch (Exception e) { return new HashMap<>(); }
    }
}