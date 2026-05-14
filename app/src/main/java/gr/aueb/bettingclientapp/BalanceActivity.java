package gr.aueb.bettingclientapp;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import gr.aueb.bettingclientapp.network.NetworkManager;

public class BalanceActivity extends AppCompatActivity {

    private TextView tvBalance;
    private TextView tvStats;
    private String pId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);

        tvBalance = findViewById(R.id.tvBalance);
        tvStats = findViewById(R.id.tvStats);

        pId = getIntent().getStringExtra("PLAYER_ID");

        loadBalance();
    }

    private void loadBalance() {
        NetworkManager.sendGetBalanceRequest(pId, new NetworkManager.NetworkCallback<String>() {
            @Override
            public void onSuccess(String balance) {
                tvBalance.setText(balance);

                tvStats.setText(
                        "Portfolio Overview\n\n" +
                                "Available Tokens: " + balance + "\n" +
                                "Total Games Played: --\n" +
                                "Total Wins: --\n" +
                                "Total Losses: --\n" +
                                "Profit / Loss: --"
                );
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(BalanceActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}