# Distributed Betting Platform (AUEB 2025-2026)

Ολοκληρωμένη υλοποίηση του **Μέρους Α** για το μάθημα "Κατανεμημένα Συστήματα". Το σύστημα αποτελεί μια πλήρως κατανεμημένη πλατφόρμα online τυχερών παιχνιδιών, βασισμένη στην αρχιτεκτονική **Master-Worker** με ενσωματωμένο **MapReduce Framework** και κεντρικό σύστημα δυναμικών ρυθμίσεων.

---

## 🏗 Αρχιτεκτονική & Τεχνικά Χαρακτηριστικά

Το σύστημα αποτελείται από 5 κύρια κατανεμημένα components που επικοινωνούν αποκλειστικά μέσω **TCP Sockets**:

1.  **Master Node (Orchestrator):** 
    *   **Routing:** Hashing `H(GameName) mod N` για δίκαιη κατανομή φορτίου.
    *   **Transaction Safety:** Atomic-like διαχείριση Balances με **Refund logic** σε περίπτωση αποτυχίας του Worker ή του SRG.
    *   **MapReduce Coordinator:** Ενορχηστρώνει τις φάσεις Map και Reduce, ενημερώνοντας δυναμικά τον Reducer για το πλήθος των ενεργών Workers.

2.  **Worker Nodes (Data Nodes):** 
    *   **Thread-Safe Storage:** Χρήση `synchronized` blocks για την προστασία των in-memory δεδομένων από ταυτόχρονα πονταρίσματα/αναζητήσεις.
    *   **Verified Betting:** Επαλήθευση αποτελεσμάτων μέσω **SHA-256 HMAC** σε συνεργασία με τον SRG.

3.  **Reducer Node (Aggregation Node):** 
    *   **Barrier Synchronization:** Υλοποίηση Barrier με `wait/notify`. Ο Master μπλοκάρεται μέχρι όλοι οι Workers να ολοκληρώσουν τη φάση Map.
    *   **Dynamic Scaling:** Προσαρμόζεται αυτόματα στον αριθμό των Workers που ορίζει ο Master.

4.  **Secured Random Generator (SRG):** 
    *   **Producer-Consumer:** Παραγωγή αριθμών σε background thread με χρήση ενδιάμεσου Buffer για ελαχιστοποίηση του latency.

5.  **Config System:** 
    *   Κεντρική διαχείριση μέσω των αρχείων `system.conf` και `workers.conf`, επιτρέποντας την εκτέλεση σε διαφορετικά laptops χωρίς ανάγκη για re-compile.

---

## 🚀 Λειτουργίες (Features)

### 👨‍💼 Διαχείριση (Manager)
*   **Add Game:** Δυναμική προσθήκη παιχνιδιών μέσω **Manual JSON Parsing** (χωρίς εξωτερικές βιβλιοθήκες).
*   **Edit Risk:** Αλλαγή επιπέδου ρίσκου (Low, Medium, High) με αυτόματο επανυπολογισμό του Jackpot.
*   **Remove Game:** Απενεργοποίηση παιχνιδιού (**Soft Delete**) ώστε να μην εμφανίζεται στους παίκτες αλλά να διατηρούνται τα ιστορικά στατιστικά.
*   **Real-time Analytics:** Προβολή κερδών/ζημιών μέσω MapReduce με ανάλυση: `Provider -> Game -> Total P/L` και `Player -> Total P/L`.

### 🕹 Εμπειρία Παίκτη (Player)
*   **Token System:** Πλήρες σύστημα Balance. Έλεγχος υπολοίπου πριν από κάθε ποντάρισμα και αυτόματη ενημέρωση μετά το αποτέλεσμα.
*   **Advanced Filtering:** Αναζήτηση παιχνιδιών με συνδυαστικά φίλτρα (Stars, Bet Category $, Risk Level).
*   **Rating System:** Δυνατότητα βαθμολογίας (1-5 αστέρια) με δυναμική ενημέρωση του μέσου όρου (double precision) και του πλήθους των ψήφων.
*   **Secure Betting:** Κάθε ποντάρισμα επαληθεύεται με κρυπτογραφικό hash για την αποτροπή απάτης στη μεταφορά του τυχαίου αριθμού.

---

## 🛠 Οδηγίες Εκτέλεσης (Setup)

### 1. Ρύθμιση Δικτύου (Configuration)

#### Σύνδεση Συσκευών
Βάλτε τα laptops στο ίδιο Wi-Fi ή στο ίδιο Hotspot κινητού.

#### Εύρεση IP Διευθύνσεων
**MacOS**
```bash
ipconfig getifaddr en0
```
Παράδειγμα IP: `192.168.1.10`

**Windows**
```bash
ipconfig
```
Βρείτε το πεδίο **IPv4 Address**.
Παράδειγμα IP: `192.168.1.20`

### 2. Ρύθμιση Αρχείων (`src` folder)

Τα παρακάτω αρχεία πρέπει να είναι ίδια σε όλα τα laptops.

*   **`system.conf`**: Ορίστε τις IPs των laptops που θα τρέχουν τους servers (Master, Reducer, SRG).
*   **`workers.conf`**: Προσθέστε τις διευθύνσεις (IP:Port) όλων των Workers που θα συμμετέχουν.

*Για τοπική εκτέλεση σε ένα laptop μπορείτε να χρησιμοποιήσετε `localhost` (default).*

### 2. Compile
Σε κάθε laptop, ανοίξτε terminal μέσα στον φάκελο `src` και εκτελέστε:

```bash
javac Common/*.java SRG/*.java Reducer/*.java Worker/*.java Master/*.java Manager/*.java Player/*.java
```

### 3. Σειρά Εκτέλεσης
Για σωστή λειτουργία, εκκινήστε τα components με την εξής σειρά:

1.  **SRG:** `java SRG.SRGServer`
2.  **Reducer:** `java Reducer.ReducerNode`
3.  **Master:** `java Master.MasterNode`
4.  **Workers:** `java Worker.WorkerNode <port>` (π.χ. `8001`, `8002` κλπ.)
5.  **Clients:** `java Manager.ManagerApp` ή `java Player.DummyPlayer`

---

## 📂 Δομή Φακέλων (Project Structure)

Το project είναι οργανωμένο σε Java Packages:

```text
src/
├── Common/         # Κοινές οντότητες (Game, Filters, Config, HashUtils)
├── Master/         # MasterNode και διαχείριση Workers
├── Worker/         # WorkerNode και επεξεργασία πονταρίσματος
├── Reducer/        # ReducerNode (MapReduce Aggregation)
├── SRG/            # Secured Random Generator (Producer-Consumer)
├── Manager/        # Manager Console Application
├── Player/         # Dummy Player Application (Console)
├── Games/          # Αρχεία JSON με δεδομένα παιχνιδιών
├── system.conf     # Κεντρικές ρυθμίσεις IPs και Ports
└── workers.conf    # Λίστα με τις διευθύνσεις των Workers
```

---

## 📊 Η Διαδικασία MapReduce (Stats Flow)
Όταν ο Manager ζητάει στατιστικά:
1.  **Master:** Στέλνει `SET_WORKER_COUNT` στον Reducer και `PUSH_TO_REDUCER` στους Workers.
2.  **Map Phase:** Οι Workers στέλνουν snapshots των τοπικών PnL δεδομένων στον Reducer.
3.  **Barrier:** Ο Reducer συλλέγει τα δεδομένα. Αν ο Master ζητήσει αποτελέσματα πρόωρα, ο Reducer τον θέτει σε κατάσταση `wait()`.
4.  **Reduce Phase:** Μόλις ληφθούν όλα τα δεδομένα, ο Reducer εκτελεί `notifyAll()`, απελευθερώνει τον Master και του στέλνει το τελικό aggregated Map.

---

## 📦 Διαχείριση Δεδομένων
*   **Zero-Library JSON Parsing**: Υλοποίηση custom parser για την ανάγνωση των αρχείων παιχνιδιών, αποφεύγοντας εξωτερικές βιβλιοθήκες (Jackson/GSON) σύμφωνα με τους περιορισμούς της εργασίας.
*   **In-Memory Storage**: Χρήση συγχρονισμένων HashMaps για μέγιστη ταχύτητα χωρίς τη χρήση βάσης δεδομένων.

---

## 🛡 Ασφάλεια & Αξιοπιστία
*   **Security:** Κάθε τυχαίος αριθμός συνοδεύεται από `SHA256(number + secretS)`. Ο Worker επαληθεύει το hash πριν οριστικοποιήσει το ποντάρισμα.
*   **Fault Tolerance:** Αν ένας Worker είναι μη διαθέσιμος κατά τη διάρκεια του παιχνιδιού, ο Master ανιχνεύει το σφάλμα και εκτελεί αυτόματο **Refund** στο balance του παίκτη.
*   **Data Integrity:** Χρήση snapshots κατά τη φάση Map για αποφυγή `ConcurrentModificationException` κατά τη διάρκεια ενεργών πονταρισμάτων.

---
*Υλοποιήθηκε στα πλαίσια του μαθήματος "Κατανεμημένα Συστήματα" του Οικονομικού Πανεπιστημίου Αθηνών - Εαρινό Εξάμηνο 2025-2026.*
