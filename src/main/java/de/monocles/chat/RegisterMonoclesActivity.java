package de.monocles.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.MagicCreateActivity;

public class RegisterMonoclesActivity extends MagicCreateActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_monocles);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setupHyperlink();

        Button SignUpButton = (Button) findViewById(R.id.activity_main_link);
        SignUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpPage.class);

            startActivity(intent);
        });

        Button alternative = (Button) findViewById(R.id.alternative);
        alternative.setOnClickListener(view -> {
            Intent intent = new Intent(this, MagicCreateActivity.class);
            startActivity(intent);
        });
    }

    private void setupHyperlink() {
        TextView link2TextView = findViewById(R.id.instructions_monocles_account);
        link2TextView.setMovementMethod(LinkMovementMethod.getInstance());
    }}
