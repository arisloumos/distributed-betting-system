package gr.aueb.bettingclientapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import gr.aueb.bettingclientapp.Common.Filters;
import gr.aueb.bettingclientapp.Common.Game;
import gr.aueb.bettingclientapp.network.NetworkManager;
import java.util.List;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SearchActivity extends AppCompatActivity {


    private EditText etStars;
    private Spinner spinnerBet, spinnerRisk;
    private Button btnSearch;
    private Button btnSettings;
    private Button btnViewBalance;
    private Button btnAddBalance;
    private Button btnFilter;
    private String pId;
    private boolean hasSearched = false;

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

        // Παίρνουμε το PLAYER_ID που στάλθηκε από την MainActivity
        pId = getIntent().getStringExtra("PLAYER_ID");

        btnViewBalance.setOnClickListener(v -> viewBalance());
        btnAddBalance.setOnClickListener(v -> showAddBalanceDialog());

        btnFilter = findViewById(R.id.btnFilter);
        btnFilter.setOnClickListener(v -> showFilterDialog());

        // Adapter για τις επιλογές του bet filter
        ArrayAdapter<String> betAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Any", "$", "$$", "$$$"}
        );
        spinnerBet.setAdapter(betAdapter);
        // Adapter για τις επιλογές του risk filter
        ArrayAdapter<String> riskAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Any", "low", "medium", "high"}
        );
        spinnerRisk.setAdapter(riskAdapter);


        btnSettings.setOnClickListener(v -> showSettingsDialog());

        btnSearch.setOnClickListener(v -> { hasSearched = true;performSearch(); });

    }
    //Για ενημέρωση αξιολόγησης
    @Override
    protected void onResume() {
        super.onResume();
        if (hasSearched) {
            performSearch();
        }
    }
    private void viewBalance() {
        // Άνοιγμα της BalanceActivity και μεταφορά του PLAYER_ID
        Intent intent = new Intent(SearchActivity.this, BalanceActivity.class);
        intent.putExtra("PLAYER_ID", pId);
        startActivity(intent);
    }

    private void showAddBalanceDialog() {
        // Δημιουργία dialog για προσθήκη balance
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 35, 50, 20);

        // Τίτλος dialog
        TextView title = new TextView(this);
        title.setText("Add Balance");
        title.setTextColor(Color.parseColor("#9CFF22"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 25);

        // Πεδίο εισαγωγής ποσού
        final EditText etAmount = new EditText(this);
        etAmount.setHint("Amount of tokens");
        etAmount.setHintTextColor(Color.parseColor("#B8B8C8"));
        etAmount.setTextColor(Color.WHITE);
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        layout.addView(title);
        layout.addView(etAmount);

        builder.setView(layout);

        // κουμπί για την αποστολή του ποσού στον server
        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = etAmount.getText().toString();

            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
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

        //κουμπί για ακύρωση
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            // Αλλαγή background του dialog ώστε να ταιριάζει με το dark theme
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
            }

            // Χρωματισμός των κουμπιών του dialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#9CFF22"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#9CFF22"));
        });

        dialog.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 35, 50, 20);


        TextView title = new TextView(this);
        title.setText("Connection Settings");
        title.setTextColor(Color.parseColor("#9CFF22"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 25);

        // Πεδίο για την IP του Master
        final EditText etIp = new EditText(this);
        etIp.setHint("Master IP");
        etIp.setHintTextColor(Color.parseColor("#B8B8C8"));
        etIp.setTextColor(Color.WHITE);

        // Πεδίο για το port του Master
        final EditText etPort = new EditText(this);
        etPort.setHint("Master Port");
        etPort.setHintTextColor(Color.parseColor("#B8B8C8"));
        etPort.setTextColor(Color.WHITE);
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        layout.addView(title);
        layout.addView(etIp);
        layout.addView(etPort);

        builder.setView(layout);

        // Αποθήκευση των ρυθμίσεων στα SharedPreferences
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newIp = etIp.getText().toString();
            int newPort = Integer.parseInt(etPort.getText().toString());

            getSharedPreferences("BettingPrefs", MODE_PRIVATE).edit()
                    .putString("MASTER_IP", newIp)
                    .putInt("MASTER_PORT", newPort)
                    .apply();

            // Ενημερώνουμε τον NetworkManager
            NetworkManager.setServerDetails(newIp, newPort);

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#9CFF22"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#9CFF22"));
        });

        dialog.show();
    }

    private void performSearch() {
        // Διαβάζουμε τις τιμές των φίλτρων από τα κρυφά views
        int stars = etStars.getText().toString().isEmpty() ? 0 : Integer.parseInt(etStars.getText().toString());
        String bet = spinnerBet.getSelectedItem().toString().equals("Any") ? null : spinnerBet.getSelectedItem().toString();
        String risk = spinnerRisk.getSelectedItem().toString().equals("Any") ? null : spinnerRisk.getSelectedItem().toString();

        // Δημιουργία αντικειμένου Filters που θα σταλεί στον server
        Filters filters = new Filters(stars, bet, risk);

        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();

        // Αποστολή request στον server για αναζήτηση παιχνιδιών
        NetworkManager.sendRequest("SEARCH", filters, new NetworkManager.NetworkCallback<List<Game>>() {

            @Override
            public void onSuccess(List<Game> games) {
                if (games.isEmpty()) {
                    Toast.makeText(SearchActivity.this, "No games found", Toast.LENGTH_SHORT).show();
                } else {
                    // Δημιουργία adapter με τα παιχνίδια που επέστρεψε ο server
                    GameAdapter adapter = new GameAdapter(games);

                    // Όταν πατηθεί ένα παιχνίδι, ανοίγει η PlayActivity
                    adapter.setOnGameClickListener(game -> {
                        Intent intent = new Intent(SearchActivity.this, PlayActivity.class);
                        intent.putExtra("GAME", game);
                        intent.putExtra("PLAYER_ID", pId);
                        startActivity(intent);
                    });

                    // Σύνδεση του adapter με το RecyclerView
                    RecyclerView rvGames = findViewById(R.id.rvGames);
                    rvGames.setLayoutManager(new GridLayoutManager(SearchActivity.this, 2));
                    rvGames.setAdapter(adapter);
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(SearchActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private ArrayAdapter<String> createDarkSpinnerAdapter(String[] items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Εμφάνιση της επιλεγμένης τιμής του spinner
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.parseColor("#FFFFFF"));
                view.setTextSize(16);
                view.setPadding(12, 12, 12, 12);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                // Εμφάνιση κάθε επιλογής μέσα στο dropdown
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.parseColor("#FFFFFF"));
                view.setTextSize(16);
                view.setBackgroundColor(Color.parseColor("#1A1B20"));
                view.setPadding(24, 18, 24, 18);
                return view;
            }
        };
    }

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 35, 50, 20);

        TextView title = new TextView(this);
        title.setText("Filters");
        title.setTextColor(Color.parseColor("#9CFF22"));
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 25);

        // Πεδίο για το ελάχιστο rating
        final EditText dialogStars = new EditText(this);
        dialogStars.setHint("Min Stars 0-5");
        dialogStars.setHintTextColor(Color.parseColor("#B8B8C8"));
        dialogStars.setTextColor(Color.WHITE);
        dialogStars.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        // Spinner για το φίλτρο bet range
        final Spinner dialogBet = new Spinner(this);
        ArrayAdapter<String> betAdapter = createDarkSpinnerAdapter(new String[]{"Any", "$", "$$", "$$$"});
        dialogBet.setAdapter(betAdapter);
        dialogBet.setPopupBackgroundDrawable(new ColorDrawable(Color.parseColor("#1A1B20")));

        // Spinner για το φίλτρο risk level
        final Spinner dialogRisk = new Spinner(this);
        ArrayAdapter<String> riskAdapter = createDarkSpinnerAdapter(new String[]{"Any", "low", "medium", "high"});
        dialogRisk.setAdapter(riskAdapter);
        dialogRisk.setPopupBackgroundDrawable(new ColorDrawable(Color.parseColor("#1A1B20")));

        // Προσθήκη των φίλτρων στο dialog
        layout.addView(title);
        layout.addView(dialogStars);
        layout.addView(dialogBet);
        layout.addView(dialogRisk);

        builder.setView(layout);

        builder.setPositiveButton("Apply", (dialog, which) -> {
            etStars.setText(dialogStars.getText().toString());
            spinnerBet.setSelection(dialogBet.getSelectedItemPosition());
            spinnerRisk.setSelection(dialogRisk.getSelectedItemPosition());

            Toast.makeText(this, "Filters applied", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#9CFF22"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#9CFF22"));
        });

        dialog.show();
    }
}