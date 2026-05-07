package gr.aueb.bettingclientapp;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import gr.aueb.bettingclientapp.Common.Game;
import gr.aueb.bettingclientapp.network.NetworkManager;


public class PlayActivity extends AppCompatActivity {
    private Game game;
    private String pId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        game = (Game) getIntent().getSerializableExtra("GAME");
        pId = getIntent().getStringExtra("PLAYER_ID");

        Button btnPlay = findViewById(R.id.btnPlay);
        EditText etBet = findViewById(R.id.etBet);
        RatingBar ratingBar = findViewById(R.id.ratingBar);


        btnPlay.setOnClickListener(v -> {
            String betStr = etBet.getText().toString();
            if (betStr.isEmpty()) return;
            double amt = Double.parseDouble(betStr);

            // Απενεργοποίηση του κουμπιού για να μην πατηθεί 2η φορά
            btnPlay.setEnabled(false);
            btnPlay.setText("Processing...");

            NetworkManager.sendPlayRequest(pId, game.gameName, amt, new NetworkManager.NetworkCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    // Επαναφορά του κουμπιού
                    btnPlay.setEnabled(true);
                    btnPlay.setText("Place Bet");

                    Toast.makeText(PlayActivity.this, result, Toast.LENGTH_LONG).show();
                    if (!result.startsWith("ERROR") && !result.startsWith("REJECTED")) {
                        ratingBar.setVisibility(View.VISIBLE);
                    }
                }
                @Override
                public void onError(Exception e) {
                    // Επαναφορά του κουμπιού σε περίπτωση λάθους
                    btnPlay.setEnabled(true);
                    btnPlay.setText("Place Bet");
                    Toast.makeText(PlayActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
            NetworkManager.sendRateRequest(game.gameName, (int)rating, new NetworkManager.NetworkCallback<String>() {
                @Override
                public void onSuccess(String res) { Toast.makeText(PlayActivity.this, res, Toast.LENGTH_SHORT).show(); }
                @Override
                public void onError(Exception e) {}
            });
        });
    }
}