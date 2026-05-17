package gr.aueb.bettingclientapp.Reducer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import gr.aueb.bettingclientapp.Common.Config;

/**
 * Reducer Node: Ο κόμβος που συγκεντρώνει τα ενδιάμεσα αποτελέσματα από τους Workers.
 * Υλοποιεί τη φάση "Reduce" του MapReduce, ομαδοποιώντας τα κέρδη/ζημιές 
 * ανά πάροχο και ανά παίκτη.
 */
public class ReducerNode {
    // Φόρτωση του port από το system.conf
    private static final int PORT = Config.getInt("REDUCER_PORT", 4444);
    

    // Κλάση που κρατάει την κατάσταση για ΚΑΘΕ ξεχωριστό MapReduce Job
    static class JobState {
        Map<String, Map<String, Double>> providerData = new HashMap<>();
        Map<String, Double> playerData = new HashMap<>();
        Set<String> workersFinished = new HashSet<>();
        int expectedWorkers = 0;
    }

    // Map που συνδέει το UUID του Job με την κατάστασή του
    private static final Map<String, JobState> activeJobs = new ConcurrentHashMap<>();
    public static void main(String[] args) throws IOException {
        System.out.println("[REDUCER] Node started on port " + PORT);

        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(new WorkerOrMasterHandler(s)).start();
            }
        }
    }

    /**
     * Handler για την επεξεργασία εντολών από τον Master (Reset, Get Results)
     * και δεδομένων από τους Workers (Map Data).
     */
    static class WorkerOrMasterHandler implements Runnable {
        private Socket s;
        public WorkerOrMasterHandler(Socket s) { this.s = s; }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                String cmd = (String) in.readObject();
                
                // Εντολή από Master για αρχικοποίηση νέου MapReduce Job
                if (cmd.equals("INIT_JOB")) {
                    String jobId = in.readUTF();
                    int count = in.readInt();
                    
                    JobState job = new JobState();
                    job.expectedWorkers = count;
                    activeJobs.put(jobId, job);
                    
                    System.out.println("[REDUCER] Initialized Job: " + jobId + " expecting " + count + " workers.");
                }
                // Λήψη ενδιάμεσων δεδομένων (Map Phase) από έναν Worker
                else if (cmd.equals("MAP_DATA")) {
                    String jobId = in.readUTF();
                    Map<String, Double> wGames = (Map<String, Double>) in.readObject();
                    Map<String, String> wGameProviders = (Map<String, String>) in.readObject();
                    Map<String, Double> wPlayers = (Map<String, Double>) in.readObject();

                    String workerId = s.getInetAddress().toString() + ":" + s.getPort();
                    JobState job = activeJobs.get(jobId);

                    if (job != null) {
                        // Κλειδώνουμε ΜΟΝΟ το συγκεκριμένο Job, όχι όλο τον Reducer!
                        synchronized (job) {
                            for (String gName : wGames.keySet()) {
                                String prov = wGameProviders.get(gName);
                                job.providerData.putIfAbsent(prov, new HashMap<>());
                                job.providerData.get(prov).put(gName, wGames.get(gName));
                            }
                            wPlayers.forEach((k, v) -> job.playerData.merge(k, v, Double::sum));

                            job.workersFinished.add(workerId);
                            System.out.println("[REDUCER] Job " + jobId + " Progress: " + 
                                               job.workersFinished.size() + "/" + job.expectedWorkers);

                            if (job.workersFinished.size() >= job.expectedWorkers) {
                                job.notifyAll(); // Ξυπνάμε τον Master που περιμένει ΑΥΤΟ το job
                            }
                        }
                    }
                    out.writeUTF("OK");
                }
                else if (cmd.equals("GET_REDUCED_RESULTS")) {
                    String jobId = in.readUTF();
                    JobState job = activeJobs.get(jobId);
                    
                    if (job != null) {
                        synchronized (job) {
                            // Μηχανισμός Barrier: Αν δεν έχουν τελειώσει όλοι οι Workers, ο Master μπλοκάρεται
                            while (job.workersFinished.size() < job.expectedWorkers) {
                                System.out.println( "[REDUCER] Master is waiting for Job " + jobId + " to complete...");
                                job.wait(); 
                            }
                        }
                        out.writeObject(job.providerData);
                        out.writeObject(job.playerData);
                        
                        // Καθαρισμός μνήμης (Garbage Collection) αφού τελειώσει το job
                        activeJobs.remove(jobId);
                        System.out.println("[REDUCER] Job " + jobId + " completed and cleaned up.");
                    } else {
                        // Αν κάτι πάει στραβά, επιστρέφουμε άδεια Maps για να μην κρασάρει ο Master
                        out.writeObject(new HashMap<>());
                        out.writeObject(new HashMap<>());
                    }
                }
                out.flush();
            } catch (Exception e) {
                System.err.println("[REDUCER] Handler Error: " + e.getMessage());
            }
        }
    }
}