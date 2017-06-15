package com.osamufujimoto.glasscontrol;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.glass.companion.Proto;
import com.thalmic.android.myoglass.GlassDevice;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements GlassDevice.GlassConnectionListener {

    public static final String TAG = "GlassControlActivity";

    public static final int PERMISSION_BLUETOOTH = 1;

    private GlassDevice mGlassDevice;

    private TextView mGlassStatus;

    private boolean mScreencastEnabled = false;

    private static boolean mGlassConnected = false;

    private ImageView mScreencastView;
    private Button mStartScreencast;

    private Button mSwipeLeftBtn;
    private Button mSwipeRightBtn;
    private Button mSwipeDownBtn;
    private Button mTapBtn;

    private static ExecutorService sExecutorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                PERMISSION_BLUETOOTH);

        mGlassDevice = new GlassDevice();

        mSwipeLeftBtn       = (Button) findViewById(R.id.btnSwipeLeft);
        mSwipeRightBtn      = (Button) findViewById(R.id.btnSwipeRight);
        mSwipeDownBtn       = (Button) findViewById(R.id.btnSwipeDown);
        mTapBtn             = (Button) findViewById(R.id.btnTap);
        mStartScreencast    = (Button) findViewById(R.id.btnStartScreencast);

        mGlassStatus = (TextView) findViewById(R.id.glass_status);

        mScreencastView = (ImageView) findViewById(R.id.screenshot);

        mGlassDevice.registerListener(MainActivity.this);

        updateGlassStatus(mGlassDevice.getConnectionStatus());

        enableGlassControl(false);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGlassDevice.unregisterListener(MainActivity.this);
        sExecutorService.shutdown();
        mGlassDevice.close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mScreencastEnabled = false;
        mGlassDevice.stopScreenshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, PERMISSION_BLUETOOTH);
        }
    }


    /**
     * Connect to the Glass device
     * @param address the MAC address of Glass
     */
    private void connectGlassDevice(final String address) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                mGlassDevice.connect(address);
                return true;
            }
            @Override
            protected void onPostExecute(Boolean result) {
                updateGlassStatus(mGlassDevice.getConnectionStatus());
                // Log.d(TAG, "connectGlassDevice() -> " + mGlassDevice.getConnectionStatus());
            }
        }.executeOnExecutor(sExecutorService);
    }

    /**
     * Disconnect from the Glass
     */
    private void closeGlassDevice() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                mGlassDevice.close();
                return true;
            }
            @Override
            protected void onPostExecute(Boolean aBoolean) {
                updateGlassStatus(mGlassDevice.getConnectionStatus());
                // Log.d(TAG, "closedGlassDevice() -> " + mGlassDevice.getConnectionStatus());
            }
        }.executeOnExecutor(sExecutorService);
    }



    /**
     * Enable the control buttons (swipe down, swipe left, swipe down, tap and screencast)
     * @param enable whether to enable or disable the buttons
     */
    private void enableGlassControl(boolean enable) {
        mSwipeLeftBtn.setEnabled(enable);
        mSwipeRightBtn.setEnabled(enable);
        mSwipeDownBtn.setEnabled(enable);
        mTapBtn.setEnabled(enable);
        mStartScreencast.setEnabled(enable);
    }

    /**
     * Connect or disconnect to Glass. The Glass must be previously paired with the phone.
     * @param view the view object
     */
    public void onChooseGlassClicked(View view) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        /* Show an error if no devices has been paired to the phone */
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.no_devices_title)
                    .setMessage(R.string.no_devices_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        /* Close the existing Glass connection */
        if (mGlassDevice != null &&
                mGlassDevice.getConnectionStatus() == GlassDevice.ConnectionStatus.CONNECTED) {
            // mGlassDevice.close();
            if (mScreencastEnabled) {
                mScreencastEnabled = false;
                setScreencastEnabled(mScreencastEnabled);
            }
            closeGlassDevice();
            return;
        }

        /* Get a list of the paired devices */
        String[] glassNames = new String[pairedDevices.size()];
        final String[] glassAddresses = new String[pairedDevices.size()];

        int i = 0;
        for (BluetoothDevice device : pairedDevices) {
            glassNames[i] = device.getName();
            glassAddresses[i] = device.getAddress();
            i++;
        }

        /* Show all the Bluetooth devices and connected to the one specified by the user */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose_glass)
                .setItems(glassNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // mGlassDevice.connect(glassAddresses[which]);
                        connectGlassDevice(glassAddresses[which]);
                    }
                })
                .show();
    }

    /**
     * Send swipe left command to Glass
     * @param view the view object
     */
    public void onSwipeLeftBtn(View view) {
        if (mGlassDevice != null) {
            mGlassDevice.swipeLeft();
        }
    }

    /**
     * Send swipe right command to Glass
     * @param view the view object
     */
    public void onSwipeRightBtn(View view) {
        if (mGlassDevice != null) {
            mGlassDevice.swipeRight();
        }
    }

    /**
     * Send swipe down command to Glass
     * @param view the view object
     */
    public void onSwipeDownBtn(View view) {
        if (mGlassDevice != null) {
            mGlassDevice.swipeDown();
        }
    }

    /**
     * Send tap command to Glass
     * @param view the view object
     */
    public void onTapBtn(View view) {
        if (mGlassDevice != null) {
            mGlassDevice.tap();
        }
    }


    /**
     * Update the connection status of Glass and enable or disable the Glass controls
     * @param status the connection status of Glass
     */
    private void updateGlassStatus(GlassDevice.ConnectionStatus status) {
        mGlassConnected = (status == GlassDevice.ConnectionStatus.CONNECTED);

        enableGlassControl(mGlassConnected);

        mGlassStatus.setText(status.name());
    }

    /**
     * Enable Glass screencast
     * @param enable whether to enable/disable screencasting.
     */
    private void setScreencastEnabled(boolean enable) {
        mScreencastEnabled = enable;
        updateScreencastState();
    }

    /**
     * Update the text of the button and toggle the visibility of the screencast
     */
    private void updateScreencastState() {
        mStartScreencast.setText(mScreencastEnabled ? R.string.stop_screencast : R.string.start_screencast);
        mScreencastView.setVisibility(mScreencastEnabled ? View.VISIBLE : View.INVISIBLE);
    }


    /**
     * Show the screen on the Glass and update the text on the button
     * @param view the view object
     */
    public void onScreenBtn(View view) {
        setScreencastEnabled(!mScreencastEnabled);
        if (mScreencastEnabled) {
            mGlassDevice.requestScreenshot();
        } else {
            mGlassDevice.stopScreenshot();
        }
    }


    @Override
    public void onConnectionStatusChanged(GlassDevice.ConnectionStatus status) {
        updateGlassStatus(status);
    }

    @Override
    public void onReceivedEnvelope(Proto.Envelope envelope) {
        if (envelope.screenshot != null) {
            if (envelope.screenshot.screenshotBytesG2C != null) {
                InputStream in = new ByteArrayInputStream(envelope.screenshot.screenshotBytesG2C);
                final Bitmap bp = BitmapFactory.decodeStream(in);

                // Update the UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mScreencastView.setImageBitmap(bp);
                    }
                });
            }
        }
    }
}
