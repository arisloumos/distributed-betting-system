package gr.aueb.bettingclientapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText etPlayerId;
    private Button btnConnect;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Άνοιγμα των SharedPreferences της εφαρμογής
        prefs = getSharedPreferences("BettingPrefs", Context.MODE_PRIVATE);
        etPlayerId = findViewById(R.id.etPlayerId);
        btnConnect = findViewById(R.id.btnConnect);

        // Αν υπάρχει ήδη αποθηκευμένο Player ID, το εμφανίζουμε στο πεδίο
        etPlayerId.setText(prefs.getString("PLAYER_ID", ""));

        // Λειτουργία του κουμπιού START
        btnConnect.setOnClickListener(v -> {
            String pId = etPlayerId.getText().toString();

            if (pId.isEmpty()) {
                Toast.makeText(this, "Please enter a Player ID", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("PLAYER_ID", pId).apply();

            Toast.makeText(this, "Welcome " + pId, Toast.LENGTH_SHORT).show();

            // Άνοιγμα της SearchActivity
            Intent intent = new Intent(MainActivity.this, SearchActivity.class);

            // Περνάει το Player ID στην επόμενη οθόνη
            intent.putExtra("PLAYER_ID", pId);

            startActivity(intent);
        });
    }
}