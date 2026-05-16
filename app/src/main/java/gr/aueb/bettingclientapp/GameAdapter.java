package gr.aueb.bettingclientapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import gr.aueb.bettingclientapp.Common.Game;
import java.util.List;
import android.widget.ImageView;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
    private List<Game> gameList;

    // Ο Listener που χρησιμοποιείται όταν ο χρήστης πατάει πάνω σε ένα παιχνίδι
    private OnGameClickListener listener;

    public interface OnGameClickListener {
        void onGameClick(Game game);
    }

    // Ορίζει τον listener που θα εκτελεστεί στο click ενός παιχνιδιού
    public void setOnGameClickListener(OnGameClickListener listener) {
        this.listener = listener;
    }

    public GameAdapter(List<Game> gameList) {
        this.gameList = gameList;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Δημιουργεί το layout για κάθε κάρτα παιχνιδιού
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        // Παίρνουμε το παιχνίδι που αντιστοιχεί στη συγκεκριμένη θέση
        Game game = gameList.get(position);

        // Εμφανίζουμε τα βασικά στοιχεία του παιχνιδιού στα TextViews
        holder.tvGameName.setText(game.gameName);
        holder.tvLimits.setText("Limits: " + String.format("%.2f - %.2f", game.minBet, game.maxBet));
        holder.tvStats.setText(String.format("Rating: %.1f/5 | Risk: %s | Jackpot: x%.0f", game.stars, game.riskLevel, game.jackpot));

        // Βρίσκουμε δυναμικά την εικόνα του παιχνιδιού από το drawable folder
        int imageResId = holder.itemView.getContext().getResources().getIdentifier(
                game.gameLogoPath.replace(".png", ""),
                "drawable",
                holder.itemView.getContext().getPackageName()
        );

        // Αν βρεθεί εικόνα, την εμφανίζουμε στο ImageView
        if (imageResId != 0) {
            holder.ivGameLogo.setImageResource(imageResId);
        }

        // Ορίζουμε τι θα γίνει όταν ο χρήστης πατήσει πάνω στην κάρτα παιχνιδιού
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGameClick(game);
            }
        });
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    // ViewHolder που κρατάει τις αναφορές στα views κάθε κάρτας παιχνιδιού
    static class GameViewHolder extends RecyclerView.ViewHolder {

        // TextViews όνομα, όρια πονταρίσματος και επιπλέον στοιχεία παιχνιδιού
        TextView tvGameName, tvLimits, tvStats;
        // ImageView εικόνα του παιχνιδιού
        ImageView ivGameLogo;

        GameViewHolder(View itemView) {
            super(itemView);
            tvGameName = itemView.findViewById(R.id.tvGameName);
            tvLimits = itemView.findViewById(R.id.tvLimits);
            tvStats = itemView.findViewById(R.id.tvStats);
            ivGameLogo = itemView.findViewById(R.id.ivGameLogo);
        }
    }
}