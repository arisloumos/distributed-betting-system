# Distributed Gaming & Betting Platform (AUEB 2025-2026)

Ολοκληρωμένη υλοποίηση του **Μέρους Α** για το μάθημα "Κατανεμημένα Συστήματα". Το σύστημα αποτελεί μια πλήρως κατανεμημένη πλατφόρμα online τυχερών παιχνιδιών, βασισμένη στην αρχιτεκτονική **Master-Worker** με ενσωματωμένο **MapReduce Framework** για την επεξεργασία δεδομένων μεγάλης κλίμακας.

---

## 🏗 Αρχιτεκτονική Συστήματος

Το σύστημα αποτελείται από 4 κύρια κατανεμημένα components που επικοινωνούν αποκλειστικά μέσω **TCP Sockets**:

1.  **Master Node (Orchestrator):** 
    *   Η πύλη εισόδου για Manager και Παίκτες.
    *   **Load Balancing:** Υλοποίηση Hashing `H(GameName) mod N` για τη δίκαιη κατανομή των παιχνιδιών στους Workers.
    *   **Transaction Management:** Κεντρική διαχείριση των Balances (Tokens) των παικτών με thread-safe ελέγχους.
    *   **MapReduce Coordinator:** Ενορχηστρώνει τις φάσεις Map και Reduce.

2.  **Worker Nodes (Data Nodes):** 
    *   Αποθηκεύουν τα δεδομένα των παιχνιδιών σε **In-memory** δομές.
    *   Εκτελούν τη φάση **Map** κατά την αναζήτηση (Filtering) και τη συγκέντρωση στατιστικών.
    *   Διαχειρίζονται τη λογική πονταρίσματος και επικοινωνούν με τον SRG.

3.  **Reducer Node (Aggregation Node):** 
    *   Ανεξάρτητος Server που εκτελεί τη φάση **Reduce**.
    *   Συλλέγει ενδιάμεσα αποτελέσματα από όλους τους Workers και τα συγχωνεύει σε τελικά analytics ανά Πάροχο, ανά Παιχνίδι και ανά Παίκτη.

4.  **Secured Random Generator (SRG):** 
    *   TCP Server που λειτουργεί με το μοντέλο **Producer-Consumer** (`wait/notify`).
    *   **Security:** Παραγωγή τυχαίων αριθμών με επαλήθευση **SHA-256 Hash** (αριθμός + secret) για την αποτροπή απάτης.

---

## 🚀 Λειτουργίες (Features)

### 👨‍💼 Διαχείριση (Manager)
*   **Add Game:** Δυναμική προσθήκη παιχνιδιών μέσω **JSON Parsing**.
*   **Edit Risk:** Αλλαγή επιπέδου ρίσκου (Low, Medium, High) με αυτόματο επανυπολογισμό του Jackpot.
*   **Remove Game:** Απενεργοποίηση παιχνιδιού (Soft Delete) ώστε να μην εμφανίζεται στους παίκτες αλλά να διατηρούνται τα ιστορικά στατιστικά.
*   **Real-time Analytics:** Προβολή κερδών/ζημιών με ανάλυση: `Provider -> Game -> Total P/L`.

### 🕹 Εμπειρία Παίκτη (Player)
*   **Token System:** Πλήρες σύστημα Balance. Έλεγχος υπολοίπου πριν από κάθε ποντάρισμα και αυτόματη ενημέρωση μετά το αποτέλεσμα.
*   **Advanced Filtering:** Αναζήτηση παιχνιδιών με συνδυαστικά φίλτρα (Stars, Bet Category $, Risk Level).
*   **Rating System:** Δυνατότητα βαθμολογίας (1-5 αστέρια) με δυναμική ενημέρωση του μέσου όρου και του πλήθους των ψήφων.

---

## 🛠 Οδηγίες Εκτέλεσης (Setup)

Για την πλήρη προσομοίωση του κατανεμημένου περιβάλλοντος, απαιτούνται **7 τερματικά** στον κατάλογο `src`:

### 1. Compile
```bash
javac Common/*.java SRG/*.java Reducer/*.java Worker/*.java Master/*.java Manager/*.java Player/*.java
```

### 2. Configuration (Dynamic Workers)
Πριν την εκκίνηση, βεβαιωθείτε ότι υπάρχει το αρχείο `workers.conf` στον κατάλογο `src` με τις διευθύνσεις των Workers:
```text
localhost:8001
localhost:8002
```

### 3. Εκκίνηση Backend (Με τη σειρά)
1.  **SRG:** `java SRG.SRGServer`
2.  **Reducer:** `java Reducer.ReducerNode`
3.  **Worker 1:** `java Worker.WorkerNode 8001`
4.  **Worker 2:** `java Worker.WorkerNode 8002`
5.  **Master:** `java Master.MasterNode` (Θα φορτώσει αυτόματα τους Workers από το .conf)

### 4. Εκκίνηση Clients
*   **Manager:** `java Manager.ManagerApp`
*   **Player:** `java Player.DummyPlayer`

 **Player:** `java Player.DummyPlayer`

---

## 📊 Η Διαδικασία MapReduce (Stats Flow)
Όταν ο Manager ζητάει στατιστικά, το σύστημα εκτελεί τα εξής:
1.  **Master:** Στέλνει σήμα `RESET` στον Reducer και εντολή `PUSH` στους Workers.
2.  **Map Phase:** Κάθε Worker επεξεργάζεται τα τοπικά του δεδομένα και στέλνει τα ενδιάμεσα αποτελέσματα στον Reducer.
3.  **Shuffle:** Η μεταφορά των δεδομένων γίνεται μέσω TCP Sockets.
4.  **Reduce Phase:** Ο Reducer αθροίζει τα δεδομένα ανά κλειδί (Provider/Player).
5.  **Final Result:** Ο Master λαμβάνει το τελικό aggregated Map και το εμφανίζει στον Manager.

---

## 📂 Δομή Φακέλων
*   `Common/`: `Game`, `Filters`, `HashUtils` (Κοινές οντότητες).
*   `Master/`: `MasterNode`, `WorkerInfo` (Ενορχήστρωση).
*   `Worker/`: `WorkerNode` (Διαχείριση δεδομένων & Ποντάρισμα).
*   `Reducer/`: `ReducerNode` (Συγκέντρωση στατιστικών).
*   `SRG/`: `SRGServer` (Ασφαλής γεννήτρια).
*   `Manager/`: `ManagerApp` (Διαχειριστικό console).
*   `Player/`: `DummyPlayer` (Εφαρμογή παίκτη).
*   `Games/`: `game1`, `game2`, `game3` (Παιχνίδια JSON).

---
*Υλοποιήθηκε στα πλαίσια του μαθήματος "Κατανεμημένα Συστήματα" του Οικονομικού Πανεπιστημίου Αθηνών - Εαρινό Εξάμηνο 2025-2026.*