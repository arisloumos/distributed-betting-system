package Master;
import java.io.Serializable;

/**
 * Αποθηκεύει τις πληροφορίες σύνδεσης (IP και Port) ενός Worker Node.
 * Χρησιμοποιείται από τον Master για τη δρομολόγηση των αιτημάτων.
 */
public class WorkerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    public String host;
    public int port;

    public WorkerInfo(String host, int port) { 
        this.host = host; 
        this.port = port; 
    }
}