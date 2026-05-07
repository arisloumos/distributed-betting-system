package gr.aueb.bettingclientapp.network;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import gr.aueb.bettingclientapp.Common.Constants;

public class NetworkManager {
    // Executor για να τρέχουμε τα Sockets σε background thread
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Interface για να παίρνουμε την απάντηση πίσω στο UI
    public interface NetworkCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static <T> void sendRequest(final String type, final Object data, final NetworkCallback<T> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    // Αποστολή τύπου και δεδομένων
                    out.writeObject(type);
                    if (data != null) {
                        out.writeObject(data);
                    }
                    out.flush();

                    // Λήψη απάντησης (το cast θα γίνει στο Activity)
                    final T response = (T) in.readObject();

                    // Επιστροφή στο UI Thread
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onSuccess(response));

                } catch (final Exception e) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    public static void sendBalanceRequest(final String pId, final double amount, final NetworkCallback<String> callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                // Το πρωτόκολλο που περιμένει ο Master:
                out.writeObject("ADD_BALANCE");
                out.writeUTF(pId);
                out.writeDouble(amount);
                out.flush();

                final String response = in.readUTF(); // Ο Master απαντάει με String

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onSuccess(response));

            } catch (final Exception e) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void sendGetBalanceRequest(final String pId, final NetworkCallback<String> callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                // Πρωτόκολλο για GET_BALANCE
                out.writeObject("GET_BALANCE");
                out.writeUTF(pId); // Στέλνουμε String (UTF)
                out.flush();

                final String response = in.readUTF(); // Διαβάζουμε String (UTF)

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onSuccess(response));

            } catch (final Exception e) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void sendPlayRequest(String pId, String gName, double amt, NetworkCallback<String> callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("PLAY");
                out.writeUTF(pId);
                out.writeUTF(gName);
                out.writeDouble(amt);
                out.flush();

                final String result = in.readUTF();
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) { /* handle error */ }
        });
    }

    public static void sendRateRequest(String gName, int rating, NetworkCallback<String> callback) {
        executor.execute(() -> {
            try (Socket socket = new Socket(Constants.MASTER_IP, Constants.MASTER_PORT);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("RATE_GAME");
                out.writeUTF(gName);
                out.writeInt(rating);
                out.flush();

                final String result = in.readUTF();
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) { /* handle error */ }
        });
    }
}