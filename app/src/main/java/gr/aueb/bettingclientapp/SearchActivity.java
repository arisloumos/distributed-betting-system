package gr.aueb.bettingclientapp;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import gr.aueb.bettingclientapp.Common.Filters;
import gr.aueb.bettingclientapp.Common.Game;
import gr.aueb.bettingclientapp.network.NetworkManager;
import java.util.List;
import android.content.Intent;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SearchActivity extends AppCompatActivity {

    private EditText etStars;
    private Spinner spinnerBet, spinnerRisk;
    private Button btnSearch;
    private Button btnSettings;
    private Button btnViewBalance;
    private Button btnAddBalance;

    private String pId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etStars = findViewById(R.id.etStars);
        spinnerBet = findViewById(R.id.spinnerBet);
        spinnerRisk = findViewById(R.id.spinnerRisk);
        btnSearch = findViewById(R.id.btnSearch);
        btnSettings = findViewById(R.id.btnSettings);
        btnViewBalance = findViewById(R.id.btnViewBalance);
        btnAddBalance = findViewById(R.id.btnAddBalance);
        pId = getIntent().getStringExtra("PLAYER_ID");

        btnViewBalance.setOnClickListener(v -> viewBalance());
        btnAddBalance.setOnClickListener(v -> showAddBalanceDialog());

        // Ρύθμιση Spinners
        ArrayAdapter<String> betAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Any", "$", "$$", "$$$"});
        spinnerBet.setAdapter(betAdapter);

        ArrayAdapter<String> riskAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Any", "low", "medium", "high"});
        spinnerRisk.setAdapter(riskAdapter);

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        btnSearch.setOnClickListener(v -> performSearch());
    }


    private void viewBalance() {
        NetworkManager.sendGetBalanceRequest(pId, new NetworkManager.NetworkCallback<String>() {
            @Override
            public void onSuccess(String balance) {
                Toast.makeText(SearchActivity.this, balance, Toast.LENGTH_LONG).show();
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(SearchActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddBalanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Balance");

        final EditText etAmount = new EditText(this);
        etAmount.setHint("Amount of tokens");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(etAmount);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = etAmount.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);

                // Κλήση της νέας μεθόδου του NetworkManager
                NetworkManager.sendBalanceRequest(pId, amount, new NetworkManager.NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Toast.makeText(SearchActivity.this, result, Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(SearchActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText etIp = new EditText(this);
        etIp.setHint("Master IP");
        // Εδώ θα μπορούσες να διαβάσεις την τρέχουσα IP από τα SharedPreferences

        final EditText etPort = new EditText(this);
        etPort.setHint("Master Port");

        layout.addView(etIp);
        layout.addView(etPort);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            // Αποθήκευση στα SharedPreferences
            getSharedPreferences("BettingPrefs", MODE_PRIVATE).edit()
                    .putString("MASTER_IP", etIp.getText().toString())
                    .putInt("MASTER_PORT", Integer.parseInt(etPort.getText().toString()))
                    .apply();
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void performSearch() {
        int stars = etStars.getText().toString().isEmpty() ? 0 : Integer.parseInt(etStars.getText().toString());
        String bet = spinnerBet.getSelectedItem().toString().equals("Any") ? null : spinnerBet.getSelectedItem().toString();
        String risk = spinnerRisk.getSelectedItem().toString().equals("Any") ? null : spinnerRisk.getSelectedItem().toString();

        Filters filters = new Filters(stars, bet, risk);

        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();

        // Κλήση του NetworkManager (Ασύγχρονα!)
        NetworkManager.sendRequest("SEARCH", filters, new NetworkManager.NetworkCallback<List<Game>>() {

            @Override
            public void onSuccess(List<Game> games) {
                if (games.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "No games found", Toast.LENGTH_SHORT).show();
                } else {
                    // 1. Δημιουργούμε τον Adapter
                    GameAdapter adapter = new GameAdapter(games);

                    // 2. Ορίζουμε τι θα γίνει όταν πατηθεί ένα παιχνίδι
                    adapter.setOnGameClickListener(game -> {
                        Intent intent = new Intent(SearchActivity.this, PlayActivity.class);
                        intent.putExtra("GAME", game); // Περνάμε το αντικείμενο Game
                        intent.putExtra("PLAYER_ID", pId); // Περνάμε το ID του παίκτη
                        startActivity(intent);
                    });

                    // 3. Συνδέουμε τον Adapter στο RecyclerView
                    RecyclerView rvGames = findViewById(R.id.rvGames);
                    rvGames.setLayoutManager(new LinearLayoutManager(SearchActivity.this));
                    rvGames.setAdapter(adapter);
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(SearchActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}