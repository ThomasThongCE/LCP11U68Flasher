package com.example.nfc_programmer;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.Class.forName;

/**
 * Thread that handle the process of flashing firmware into <b>lcp11u68</b>
 * The process divided into 4 part
 * - reset mcu into bootloader mode
 * - unmount partition of lcp11u68 which automount
 * - begin flashing process
 * - reset mcu into normal mode
 */
public class FlashTask extends AsyncTask <String, String, String> {
    final static String TAG = "Flash Task";
    final static String UUID = "0000-0000";

    ProgressDialog progressDialog;
    Context context;
    private Class storageManagerClass = null, volumeInfoClass = null;
    private Method unmount = null, findVolumeByUuid = null, getId = null;
    private StorageManager mStorageManager;
    private ResetUtil reset;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    /**
     * Init reset Util + private API access parameter + ProgressDialog
     * @param context: UI context
     */
    FlashTask(Context context){
        this.context = context;

        // Init private method which use to detect and unmount lpc partition
        mStorageManager = context.getSystemService(StorageManager.class);
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
        reset = new ResetUtil();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        context.registerReceiver(reset.getUsbBroadcastReceiver(), filter);
    }

    /**
     * Show ProgressDialog
     */
    @Override
    protected void onPreExecute() {
        progressDialog = ProgressDialog.show(context,
                "Flashing",
                "");

        super.onPreExecute();
    }

    /**
     * Update ProgressDialog message
     * @param values: Message to show in Progress Dialog
     */
    @Override
    protected void onProgressUpdate(String... values) {
        progressDialog.setMessage(values[0]);
        progressDialog.show();

        super.onProgressUpdate(values);
    }

    /**
     * Show result of flashing in toast
     * @param s: message to show
     */
    @Override
    protected void onPostExecute(String s) {
        progressDialog.dismiss();
        Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
        context.unregisterReceiver(reset.getUsbBroadcastReceiver());

        super.onPostExecute(s);
    }

    /**
     * Flashing MCU
     * @param path: Path which firmware file located
     * @return : Result message which will show in toast later
     */
    @Override
    protected String doInBackground(String... path) {
        int ret;

        try {
            // Make lcp mcu go into bootloader mode
            try {
                publishProgress("Entering program mode");
                reset.enterNormalMode();
                if (!reset.enterProgMode())
                {
                    Log.e(TAG, "Can't enter program mode, did lcp connected?");

                    return ("can't go into prog mode");
                }
            } catch (InterruptedException ignored) {}

            // Recognize lcp partition and unmount it
            Object volumeInfo = null;
            try {
                // Wait for volume to mounteda
//                    Thread.sleep(1000);
                publishProgress("Waiting for device to mount");
                do{
                    volumeInfo = (Object) findVolumeByUuid.invoke(mStorageManager, UUID);
                    Thread.sleep(500);
                }while (volumeInfo == null);


                String mVolumeId = null;
                mVolumeId = (String) getId.invoke(volumeInfo);

                publishProgress("unmount device with id " + mVolumeId);

                Log.d(TAG, "volume id: " + mVolumeId);
                unmount.invoke(mStorageManager, "public:8,0");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
                Log.e(TAG,"unmount not success. Error msg:" + e.getMessage() + ". Cause: " + e.getCause());
                reset.enterNormalModeNonBlock();

                return ("unmount error");
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG,"Error: coundn't found device with UUID: " + UUID + ". Error msg: " + e.getMessage());
                Log.e(TAG,"Timeout Interrupted");
                reset.enterNormalModeNonBlock();

                return ("Couldn't found device, Timeout");
            }

            // Begin flashing process
            publishProgress("Begin flashing");
//            ret = program("/data/lpc11u_surisdk_v0_0_6.bin");
            ret = program(path[0]);

            // reset lpc
            try {
                publishProgress("Reseting mcu");
                if(!reset.enterNormalMode()){
                    Log.e(TAG, "Can't go into normal mode, may be firmware corrupted");
                }
            } catch (InterruptedException ignored) {}

            if (ret == -1)
                return ("flash error");
            else
                return ("flash finish");

        } catch (IOException e){
            e.printStackTrace();
            Log.e(TAG, "Can't open reset driver file. Error msg: " + e.getMessage());
        } finally {
            progressDialog.dismiss();
        }

        return "Success";
    }

    /**
     * Function that handle low-level access to file, reading firmware file and writing into <b>lcp11u68</b>
     * @param path: full path to firmware binary file which flashed into the mcu
     * @return -1 if fail, 0 if success. (any error occur will be print in Log.i)
     */
    public native int program(String path );
}
