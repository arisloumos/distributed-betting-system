package Master;
import java.io.Serializable;
public class WorkerInfo implements Serializable {
    public String host;
    public int port;
    public WorkerInfo(String host, int port) { this.host = host; this.port = port; }
}