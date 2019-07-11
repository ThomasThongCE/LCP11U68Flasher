package com.example.nfc_programmer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.storage.StorageManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Class.forName;

/**
 * Example program show how to flash <b>lcp11u68</b> with JNI function and {@link ResetUtil}
 */
public class MainActivity extends AppCompatActivity {

    Button flash_btn, choose_btn;
    TextView file_text;
    ResetUtil reset;

    private String FWPath = "/storage/emulated/0/lpc11u_surisdk.bin";
    private Runnable flash ;
    private Class storageManagerClass = null, volumeInfoClass = null;
    private Method unmount = null, findVolumeByUuid = null, getId = null;
    private StorageManager mStorageManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService executorScheduled = Executors.newSingleThreadScheduledExecutor();

    final static String TAG = "nfc_programmer_main";
    final static String UUID = "0000-0000";
    final static int FILE_REQUEST = 7;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    /**
     * Helper function which show toast in UI thread
     * @param toast String to show in toast
     */
    public void showToast(final String toast)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Thread that handle the process of flashing firmware into <b>lcp11u68</b>
     * The process divided into 4 part
     * - reset mcu into bootloader mode
     * - unmount partition of lcp11u68 which automount
     * - begin flashing process
     * - reset mcu into normal mode
     */
    private class flashTask implements Runnable {
        final static String TAG = "Flash Task";

        @Override
        public void run() {
            int ret;
            try {
                // Make lcp mcu go into bootloader mode
                try {
                    reset.enterNormalMode();
                    if (!reset.enterProgMode())
                    {
                        Log.e(TAG, "Can't enter program mode, did lcp connected?");

                        showToast("can't go into prog mode");
                        return ;
                    }
                } catch (InterruptedException ignored) {}

                // Recognize lcp partition and unmount it
                Object volumeInfo = null;
                try {
                    // Wait for volume to mounted
//                    Thread.sleep(1000);
                    do{
                        volumeInfo = (Object) findVolumeByUuid.invoke(mStorageManager, UUID);
                        Thread.sleep(500);
                    }while (volumeInfo == null);

                    String mVolumeId = null;
                    mVolumeId = (String) getId.invoke(volumeInfo);
                    Log.d(TAG, "volume id: " + mVolumeId);
                    unmount.invoke(mStorageManager, "public:8,0");
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                    Log.e(TAG,"unmount not success. Error msg:" + e.getMessage() + ". Cause: " + e.getCause());
                    reset.enterNormalModeNonBlock();

                    showToast("unmount error");
                    return ;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG,"Error: coundn't found device with UUID: " + UUID + ". Error msg: " + e.getMessage());
                    Log.e(TAG,"Timeout Interrupted");
                    reset.enterNormalModeNonBlock();

                    showToast("Couldn't found device, Timeout");
                    return ;
                }
//                showToast("unmounted");

                // Begin flashing process
//                ret = program("/data/lpc11u_surisdk.bin");
                ret = program("/data/lpc11u_surisdk_v0_0_6.bin");

                // reset lpc
                try {
                    if(!reset.enterNormalMode()){
                        Log.e(TAG, "Can't go into normal mode, may be firmware corrupted");
                    }
                } catch (InterruptedException ignored) {}

                if (ret == -1)
                    showToast("flash error");
                else
                    showToast("flash finish");

            } catch (IOException e){
                e.printStackTrace();
                Log.e(TAG, "Can't open reset driver file. Error msg: " + e.getMessage());
                return ;
            }
        }
    }

    /**
     * Init the program <br>
     * - get hidden api for unmount partition <br>
     * - register broadcast receiver for {@link ResetUtil} <br>
     * - Create {@link flashTask} and implement button click listener <br>
     * @param savedInstanceState
     */
    @SuppressLint("PrivateApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        reset = new ResetUtil();
        flash_btn = findViewById(R.id.flash_btn);
        choose_btn = findViewById(R.id.choose_btn);
        file_text = findViewById(R.id.file_text);
        file_text.setText(FWPath);

        // Init private method which use to detect and unmount lpc partition
        mStorageManager = getApplicationContext().getSystemService(StorageManager.class);
        try {
            storageManagerClass = forName("android.os.storage.StorageManager");
            volumeInfoClass = forName("android.os.storage.VolumeInfo");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            unmount = storageManagerClass.getMethod("unmount", String.class);
            findVolumeByUuid = storageManagerClass.getDeclaredMethod("findVolumeByUuid", String.class);
            getId = volumeInfoClass.getDeclaredMethod("getId");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        unmount.setAccessible(true);
        findVolumeByUuid.setAccessible(true);
        getId.setAccessible(true);

        // setup broadcast controller
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(reset.getUsbBroadcastReceiver(), filter);

        // Create flash task
        flash = new flashTask();

        /*
          Create 2 thread, one for flashing and other for kill flashing thread (timeout)
          Flashing thread is using flashTask class
         */
        flash_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(),"flash begin",Toast.LENGTH_SHORT).show();
                final Future<?> future = executor.submit(flash);
                executorScheduled.schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (!future.isDone()){
                            Log.d(TAG, "Try to cancle thread");
                            future.cancel(true);
                        }
                    }
                },10, TimeUnit.SECONDS);
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

    /**
     * unregister {@link ResetUtil} broadcast receiver and shutdown the executor
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(reset.getUsbBroadcastReceiver());
        executor.shutdownNow();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode){
            case FILE_REQUEST:
                if (resultCode == RESULT_OK){
                    String Path = data.getData().getPath();

                    file_text.setText(Path);
                    FWPath = Path;
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Function that handle low-level access to file, reading firmware file and writing into <b>lcp11u68</b>
     * @param path: full path to firmware binary file which flashed into the mcu
     * @return -1 if fail, 0 if success. (any error occur will be print in Log.i)
     */
    public native int program(String path );
}
