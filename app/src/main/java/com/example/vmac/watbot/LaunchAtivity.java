package com.example.vmac.watbot;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LaunchAtivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_ativity);

        //define buttons and link to view.
        Button elsebethButton = (Button) findViewById(R.id.elsebethButton);
        Button henrikButton = (Button) findViewById(R.id.henrikButton);
        Button davidButton = (Button) findViewById(R.id.davidButton);

        //set onclicklistener.
        elsebethButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), MainActivity.class);
                intent.putExtra("Person_Id", "1");
                startActivity(intent);
            }
        });
        henrikButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), MainActivity.class);
                intent.putExtra("Person_Id", "2");
                startActivity(intent);
            }
        });
        davidButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), MainActivity.class);
                intent.putExtra("Person_Id", "3");
                startActivity(intent);
            }
        });
    }
}
