package com.example.nfc_programmer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Example program show how to flash <b>lcp11u68</b> with JNI function and {@link ResetUtil}
 */
public class MainActivity extends AppCompatActivity {

    public Button flash_btn, choose_btn;
    public TextView file_text;
    public FlashTask flash;

    private String FWPath = "/storage/emulated/0/lpc11u_surisdk.bin";

    final static String TAG = "nfc_programmer_main";
    final static int FILE_REQUEST = 7;

    /**
     * Init the program <br>
     * - get hidden api for unmount partition <br>
     * - register broadcast receiver for {@link ResetUtil} <br>
     * - Create {@link FlashTask} and implement button click listener <br>
     *
     * @param savedInstanceState
     */
    @SuppressLint("PrivateApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flash_btn = findViewById(R.id.flash_btn);
        choose_btn = findViewById(R.id.choose_btn);
        file_text = findViewById(R.id.file_text);
        file_text.setText(FWPath);

        /*
          Create 2 thread, one for flashing and other for kill flashing thread (timeout)
          Flashing thread is using flashTask class
         */
        flash_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flash = new FlashTask(MainActivity.this);
                flash.execute(FWPath);
            }
        });

        choose_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("*/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, FILE_REQUEST);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case FILE_REQUEST:
                if (resultCode == RESULT_OK) {
                    String path = GetPath.getPath(this, data.getData());
                    file_text.setText(path);
                    FWPath = path;
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
