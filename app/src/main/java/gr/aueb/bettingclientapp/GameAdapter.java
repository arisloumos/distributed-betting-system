package gr.aueb.bettingclientapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import gr.aueb.bettingclientapp.Common.Game;
import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
    private List<Game> gameList;

    private OnGameClickListener listener;
    public interface OnGameClickListener {
        void onGameClick(Game game);
    }
    public void setOnGameClickListener(OnGameClickListener listener) { this.listener = listener; }

    public GameAdapter(List<Game> gameList) { this.gameList = gameList; }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = gameList.get(position);
        holder.tvGameName.setText("Game: " + game.gameName + " [" + game.betCategory + "]");
        holder.tvLimits.setText("Limits: " + String.format("%.2f - %.2f", game.minBet, game.maxBet));
        holder.tvStats.setText(String.format("Rating: %.1f/5 | Risk: %s | Jackpot: x%.0f",
                game.stars, game.riskLevel, game.jackpot));

        // 2. Εδώ προσθέτουμε το κλικ
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGameClick(game);
        });
    }

    @Override
    public int getItemCount() { return gameList.size(); }

    // Εδώ γίνεται η διόρθωση:
    static class GameViewHolder extends RecyclerView.ViewHolder {
        // Δηλώνουμε τα 3 TextViews που υπάρχουν στο XML
        TextView tvGameName, tvLimits, tvStats;

        GameViewHolder(View itemView) {
            super(itemView);
            // Συνδέουμε τα IDs από το XML (item_game.xml)
            tvGameName = itemView.findViewById(R.id.tvGameName);
            tvLimits = itemView.findViewById(R.id.tvLimits);
            tvStats = itemView.findViewById(R.id.tvStats);
        }
    }
}