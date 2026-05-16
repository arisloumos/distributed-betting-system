package gr.aueb.bettingclientapp;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import gr.aueb.bettingclientapp.network.NetworkManager;

public class BalanceActivity extends AppCompatActivity {


    private TextView tvBalance;
    private String pId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);


        pId = getIntent().getStringExtra("PLAYER_ID");


        tvBalance = findViewById(R.id.tvBalance);

        // Ζητάμε από τον server το τρέχον balance
        loadBalance();
    }

    private void loadBalance() {
        // Στέλνουμε request στον server για να πάρουμε το balance του παίκτη
        NetworkManager.sendGetBalanceRequest(pId, new NetworkManager.NetworkCallback<String>() {
            @Override
            public void onSuccess(String balance) {
                // Αν το request πετύχει, εμφανίζουμε το balance στην οθόνη
                tvBalance.setText(balance);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(
                        BalanceActivity.this,
                        "Error: " + e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}