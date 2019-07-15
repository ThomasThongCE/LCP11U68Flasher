package com.example.nfc_programmer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * ResetUtil class that using <b>gpio-boot-reset</b> to drive <b>lcp11u68</b> into boot mode or normal mode <br>
 * Verify mcu mode using a usb attach dettach broadcast receiver with <i>wait</i> <i>notify</i> mechanic <br>
 * normal mode: mcu in running mode <br>
 * boot mode: mcu in state that can be program <br>
 */
public class ResetUtil {
    private static final String TAG = "ResetUtil: ";
    private static final String ResetFilePath = "/sys/class/gpio-boot-reset/nfc/mode";

    private static File file;
    private final Object bootToken;
    private final Object resetToken;
    private boolean resetFlag = false, bootFlag = false;

    /**
     * Init driver file path and token object for verify mcu mode
     */
    ResetUtil() {
        file = new File(ResetFilePath);
        bootToken = new Object();
        resetToken = new Object();
    }

    /**
     * Drive mcu into boot mode without verify (<i>wait</i> mechanic)
     *
     * @throws IOException
     */
    public void enterProgModeNonBlock() throws IOException {
        FileOutputStream stream = new FileOutputStream(file);
        stream.write("prog".getBytes());
        stream.close();
    }

    /**
     * Drive mcu into normal mode without verify (<i>wait</i> mechanic)
     *
     * @throws IOException
     */
    public void enterNormalModeNonBlock() throws IOException {
        FileOutputStream stream = new FileOutputStream(file);
        stream.write("normal".getBytes());
        stream.close();
    }

    /**
     * Drive mcu into boot mode with verify (<i>wait</i> mechanic) from broadcast receiver
     *
     * @return <b>true</b> if reset into bootmode success, <b>false</b> if <i>wait()</i> timeout.
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean enterProgMode() throws IOException, InterruptedException {
        bootFlag = false;
        enterProgModeNonBlock();
        synchronized (bootToken) {
            bootToken.wait(1000);
        }
        if (bootFlag) {
            Log.i(TAG, "enter programming mode");
            bootFlag = false;
            return true;
        } else
            return false;
    }

    /**
     * Drive mcu into normal mode with verify (<i>wait</i> mechanic) from broadcast receiver
     *
     * @return <b>true</b> if reset into bootmode success, <b>false</b> if <i>wait()</i> timeout.
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean enterNormalMode() throws IOException, InterruptedException {
        resetFlag = false;
        enterNormalModeNonBlock();
        synchronized (resetToken) {
            resetToken.wait(1000);
        }
        if (resetFlag) {
            Log.i(TAG, "enter normal mode");
            resetFlag = false;
            return true;
        } else
            return false;

    }

    /**
     * Get broadcast receiver
     *
     * @return broadcast receiver
     */
    public BroadcastReceiver getUsbBroadcastReceiver() {
        return m_BroadCastReceiver;
    }

    /**
     * BroadcastReceiver intend to listen to <i>ACTION_USB_DEVICE_ATTACHED</i> and <i>ACTION_USB_DEVICE_DETACHED</i> event <br>
     * in order to verify mcu in boot/normal mode or not. <br>
     * Each mode, lcp11u68 have difference <b>VID</b> and <b>PID</b> which allow to verify mcu state <br>
     */
    private final BroadcastReceiver m_BroadCastReceiver = new BroadcastReceiver() {
        private final static int VID_boot = 0x1fc9;
        private final static int PID_boot = 0x0017;
        private final static int VID = 0x23eb;
        private final static int PID = 0x0004;

        /**
         * Receive <i>ACTION_USB_DEVICE_ATTACHED</i> and <i>ACTION_USB_DEVICE_DETACHED</i> to <i>notify()</i> token which waiting in
         * <b>enterProgMode</b> and <b>enterNormalMode</b> function
         * @param context
         * @param intent
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d(TAG, "usb " + Integer.toHexString(usbDevice.getVendorId()) + ":" + Integer.toHexString(usbDevice.getProductId()) + " connected");
                    synchronized (bootToken) {
                        if ((usbDevice.getVendorId() == VID_boot) && (usbDevice.getProductId() == PID_boot)) {
                            bootFlag = true;
                            bootToken.notify();
                        }
                    }
                    synchronized (resetToken) {
                        if ((usbDevice.getVendorId() == VID) && (usbDevice.getProductId() == PID)) {
                            resetFlag = true;
                            resetToken.notify();
                        }
                    }
                    break;
                }
                case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Log.d(TAG, "usb " + Integer.toHexString(usbDevice.getVendorId()) + ":" + Integer.toHexString(usbDevice.getProductId()) + " disconnected");
                    break;
                }
            }
        }
    };
}
