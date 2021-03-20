package com.example.mobileoffloading;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class ComputeActivity extends AppCompatActivity {

    TextView r1, r2, r3, r4;
    TextView est1, est2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compute);

        Intent intent = getIntent();
        String row1 =intent.getStringExtra("row1");
        String row2 =intent.getStringExtra("row2");
        String row3 =intent.getStringExtra("row3");
        String row4 =intent.getStringExtra("row4");

        String estimation1 =intent.getStringExtra("estimation1");
        String estimation2 =intent.getStringExtra("estimation2");

        //initialize the labels:
        r1 = (TextView) findViewById(R.id.row1);
        r2 = (TextView) findViewById(R.id.row2);
        r3 = (TextView) findViewById(R.id.row3);
        r4 = (TextView) findViewById(R.id.row4);
        est1 = (TextView) findViewById(R.id.estimation1);
        est2 = (TextView) findViewById(R.id.estimation2);

        //Set the labels now with the intent values:
        r1.setText(row1);
        r2.setText(row2);
        r3.setText(row3);
        r4.setText(row4);

        est1.setText(estimation1);
        est2.setText(estimation2);


        //Bundle Matrix = getIntent().getExtras();
    }
}
