package com.example.mytelegram;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GroupInfoActivity extends AppCompatActivity {
    private String groupId;
    private String chatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        groupId = getIntent().getStringExtra("groupId");
        chatId = getIntent().getStringExtra("chatId");

        TextView infoText = findViewById(R.id.groupInfoText);
        infoText.setText("Информация о группе\nGroup ID: " + groupId + "\nChat ID: " + chatId);
    }
}