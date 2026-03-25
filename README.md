# Distributed Betting System - AUEB 2025-2026

Αυτή είναι η υλοποίηση της εργασίας για το μάθημα **Κατανεμημένα Συστήματα**. Το σύστημα προσομοιώνει μια πλατφόρμα online τυχερών παιχνιδιών χρησιμοποιώντας μια κατανεμημένη αρχιτεκτονική με Master-Worker nodes και MapReduce framework.

---

## 🏗 Αρχιτεκτονική & Υλοποίηση (Backend Core)

Το backend έχει υλοποιηθεί πλήρως σε **Java** χρησιμοποιώντας αποκλειστικά **TCP Sockets** και **Multithreading**.

### 1. SRG (Secured Random Generator)
*   **Λειτουργία:** Αυτόνομος TCP Server που παράγει τυχαίους αριθμούς.
*   **Τεχνικά Χαρακτηριστικά:** Υλοποίηση μοντέλου **Producer-Consumer** με χρήση `synchronized`, `wait()` και `notify()`. 
*   **Ασφάλεια:** Κάθε αριθμός αποστέλλεται μαζί με ένα **SHA-256 Hash** (αριθμός + secret) για την επαλήθευση της εγκυρότητας από τον Worker.

### 2. Master Node
*   **Ρόλος:** Ο κεντρικός ενορχηστρωτής του συστήματος.
*   **Hashing:** Χρησιμοποιεί τη συνάρτηση `H(GameName) mod NumberOfNodes` για τη δίκαιη κατανομή των παιχνιδιών στους Workers.
*   **MapReduce:** Υλοποιεί τη λογική MapReduce για την αναζήτηση παιχνιδιών (Search) και τη συγκέντρωση στατιστικών.

### 3. Worker Node
*   **Δεδομένα:** Αποθηκεύει τα παιχνίδια σε **In-memory** δομές (`HashMap`).
*   **Ποντάρισμα:** Επικοινωνεί με τον SRG, επαληθεύει το Hash και υπολογίζει το κέρδος/ζημιά βάσει των πινάκων ρίσκου (Low, Medium, High) και του Jackpot.
*   **Συγχρονισμός:** Χρήση `synchronized` blocks για τη σωστή ενημέρωση των στατιστικών σε ταυτόχρονα πονταρίσματα.

---

## 🛠 Οδηγίες Εκτέλεσης (Testing)

Για να τρέξετε το σύστημα, ανοίξτε 5 διαφορετικά terminals στον κατάλογο `src`:

1. **SRG Server:** `java SRG.SRGServer`
2. **Worker 1:** `java Worker.WorkerNode 8001`
3. **Worker 2:** `java Worker.WorkerNode 8002`
4. **Master Node:** `java Master.MasterNode`
5. **Manager App:** `java Manager.ManagerApp [GameName]` (προσθέτει παιχνίδι)
6. **Player App:** `java Player.DummyPlayer` (αναζήτηση και ποντάρισμα)

---

## 📝 Εκκρεμότητες για το ΜΕΡΟΣ Α (Deadline: 03/04)

Αυτά είναι τα σημεία που πρέπει να ολοκληρωθούν για την πρώτη παράδοση:

1.  **JSON Parsing (ManagerApp):** 
    *   Αντικατάσταση των hardcoded τιμών με ανάγνωση αρχείου `.json` (χρήση βιβλιοθήκης Gson ή Jackson).
    *   Αποστολή του αντικειμένου `Game` στον Master.
2.  **Filtering Logic (WorkerNode):** 
    *   Η συνάρτηση `search()` πρέπει να δέχεται φίλτρα (Stars, Bet Limits, Risk Level).
    *   Οι Workers πρέπει να επιστρέφουν μόνο τα παιχνίδια που ικανοποιούν τα κριτήρια.
3.  **Aggregation Queries (Stats):** 
    *   Υλοποίηση MapReduce για τον υπολογισμό συνολικών κερδών/ζημιών ανά **Πάροχο** και ανά **Παίκτη**.
    *   Εμφάνιση των αποτελεσμάτων στο Manager Console.

---

## 📱 ΜΕΡΟΣ Β (Deadline: 10/05)

*   **Android Application:** Αντικατάσταση του `DummyPlayer` με UI σε Android.
*   **Async Communication:** Υλοποίηση ασύγχρονης επικοινωνίας (Threads) για να παραμένει το UI διαδραστικό.
*   **Bonus (+20%):** Υλοποίηση **Active Replication** για fault tolerance (αντίγραφα δεδομένων σε πολλαπλούς Workers).

---

## 📂 Δομή Φακέλων
- `Common/`: Κοινές κλάσεις και utility functions.
- `Master/`: Κώδικας του Master Node.
- `Worker/`: Κώδικας του Worker Node.
- `SRG/`: Secured Random Generator server.
- `Manager/`: Console application για διαχειριστές.
- `Player/`: Dummy console application για παίκτες (Phase A).