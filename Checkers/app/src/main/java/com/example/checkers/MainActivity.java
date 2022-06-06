package com.example.checkers;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * This class manages the MainActivity in the application.
 *
 * @author Tal Simhayev
 * @version 1.0
 */
public class MainActivity extends AppCompatActivity {

    protected Button login;
    protected Button settings;

    /**
     * Handle the login and settings button presses, and redirect to the corresponding activity.
     *
     * @param savedInstanceState The saved instance bundle from the last run (if is not null) which is passed to the super.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) // if user already logged in
        {
            System.out.println("user already logged in, redirecting to WaitingRoom");
            startActivity(new Intent(getApplicationContext(), LobbyActivity.class));
        }

        login = findViewById(R.id.login);
        settings = findViewById(R.id.settings);

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });

        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
            }
        });
    }
}