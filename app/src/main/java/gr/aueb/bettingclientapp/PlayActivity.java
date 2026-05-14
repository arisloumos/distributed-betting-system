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

    // Δήλωση των UI στοιχείων
    private TextView tvGameTitle, tvGameInfo, tvLiveBalance, tvResultMsg;
    private LinearLayout layoutBetting, layoutResult;
    private EditText etBet;
    private Button btnPlay, btnSkip;
    private RatingBar ratingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // 1. Σύνδεση με το XML (findViewById)
        tvGameTitle = findViewById(R.id.tvGameTitle);
        tvGameInfo = findViewById(R.id.tvGameInfo);
        tvLiveBalance = findViewById(R.id.tvLiveBalance);
        layoutBetting = findViewById(R.id.layoutBetting);
        layoutResult = findViewById(R.id.layoutResult);
        etBet = findViewById(R.id.etBet);
        btnPlay = findViewById(R.id.btnPlay);
        tvResultMsg = findViewById(R.id.tvResultMsg);
        ratingBar = findViewById(R.id.ratingBar);
        btnSkip = findViewById(R.id.btnSkip);

        // 2. Λήψη των δεδομένων από το Intent
        game = (Game) getIntent().getSerializableExtra("GAME");
        pId = getIntent().getStringExtra("PLAYER_ID");

        // 3. Γέμισμα του Header με τις πληροφορίες του παιχνιδιού
        tvGameTitle.setText(game.gameName);

        String infoText = String.format("Limits: %.2f - %.2f\nRating: %.1f/5 | Risk: %s | Jackpot: x%.0f",
                game.minBet, game.maxBet, game.stars, game.riskLevel, game.jackpot);
        tvGameInfo.setText(infoText);

        // 4. Φόρτωση του Live Balance
        fetchLiveBalance();

        // --- ΛΟΓΙΚΗ ΠΟΝΤΑΡΙΣΜΑΤΟΣ ---
        btnPlay.setOnClickListener(v -> {
            String betStr = etBet.getText().toString();
            if (betStr.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }
            double amt = Double.parseDouble(betStr);

            // Απενεργοποίηση κουμπιού για αποφυγή διπλού κλικ
            btnPlay.setEnabled(false);
            btnPlay.setText("Processing...");

            NetworkManager.sendPlayRequest(pId, game.gameName, amt, new NetworkManager.NetworkCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    // Αν το Master επιστρέψει ERROR ή REJECTED (π.χ. δεν έχεις λεφτά)
                    if (result.startsWith("ERROR") || result.startsWith("REJECTED")) {
                        Toast.makeText(PlayActivity.this, result, Toast.LENGTH_LONG).show();
                        btnPlay.setEnabled(true);
                        btnPlay.setText("Place Bet");
                    } else {
                        // ΕΠΙΤΥΧΙΑ! Αλλάζουμε το UI
                        layoutBetting.setVisibility(View.GONE); // Κρύβουμε το κουτί πονταρίσματος
                        layoutResult.setVisibility(View.VISIBLE); // Εμφανίζουμε το αποτέλεσμα

                        // Το result είναι π.χ. "WIN | New Balance: 567.50"
                        tvResultMsg.setText(result);

                        // Αλλάζουμε το χρώμα ανάλογα με το αν κέρδισε ή έχασε
                        if (result.startsWith("WIN") || result.startsWith("JACKPOT")) {
                            tvResultMsg.setTextColor(android.graphics.Color.parseColor("#9CFF22"));
                        } else {
                            tvResultMsg.setTextColor(android.graphics.Color.parseColor("#FF6B6B"));
                        }

                        // Ανανεώνουμε το Live Balance πάνω-πάνω!
                        fetchLiveBalance();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(PlayActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnPlay.setEnabled(true);
                    btnPlay.setText("Place Bet");
                }
            });
        });

        // --- ΛΟΓΙΚΗ ΒΑΘΜΟΛΟΓΙΑΣ (Fix Infinite Glitch) ---
        ratingBar.setOnRatingBarChangeListener((rb, rating, fromUser) -> {
            if (fromUser) {
                // 1. ΚΛΕΙΔΩΝΟΥΜΕ το RatingBar για να μην ξαναπατηθεί!
                ratingBar.setIsIndicator(true);

                NetworkManager.sendRateRequest(game.gameName, (int) rating, new NetworkManager.NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String res) {
                        Toast.makeText(PlayActivity.this, "Thank you for rating!", Toast.LENGTH_SHORT).show();
                        // 2. Κλείνουμε την οθόνη και γυρνάμε στο Search
                        finish();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(PlayActivity.this, "Rating failed", Toast.LENGTH_SHORT).show();
                        ratingBar.setIsIndicator(false); // Ξεκλείδωμα αν απέτυχε το δίκτυο
                    }
                });
            }
        });

        // --- ΛΟΓΙΚΗ SKIP ---
        btnSkip.setOnClickListener(v -> {
            // Απλά κλείνει την τρέχουσα οθόνη και σε γυρνάει στην προηγούμενη
            finish();
        });
    }

    /**
     * Ζητάει το τρέχον υπόλοιπο από τον Master και ενημερώνει την οθόνη.
     */
    private void fetchLiveBalance() {
        NetworkManager.sendGetBalanceRequest(pId, new NetworkManager.NetworkCallback<String>() {
            @Override
            public void onSuccess(String balance) {
                // Ο Master επιστρέφει π.χ. "Current balance: 500.0"
                tvLiveBalance.setText(balance);
            }

            @Override
            public void onError(Exception e) {
                tvLiveBalance.setText("Balance Error");
                Toast.makeText(PlayActivity.this, "Could not fetch balance", Toast.LENGTH_SHORT).show();
            }
        });
    }
}