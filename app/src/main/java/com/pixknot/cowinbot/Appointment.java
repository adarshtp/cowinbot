package com.pixknot.cowinbot;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;

public class Appointment extends AppCompatActivity {

    private TextView vaccine;
    private TextView pincode;
    private TextView age;
    private TextView loc_date;
    private TextView loc_address;
    private TextView loc_slot;
    private TextView fee;
    private TextView beneficiary;

    private ImageView captcha_image;
    private EditText captcha_text;

    private Button download;
    private Button cancel_appointment;

    private long downloadID;
    private GlobalClass global;

    Intent intentBotIntentService;
    private UpdateReceiver updateReceiver;
    private onDownloadComplete downloadComplete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = (GlobalClass) getApplicationContext();
        HashMap<String, String> dataMap = global.getFromDisk();

        setContentView(R.layout.appointment);
        //getSupportActionBar().setTitle(Html.fromHtml("<font color='#FFFFFF'>Appointment</font>"));

        ActivityCompat.requestPermissions(Appointment.this, global.PERMISSIONS, PackageManager.PERMISSION_GRANTED);

        Intent intent = getIntent();
        //String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);

        vaccine = (TextView) findViewById(R.id.vaccine);
        pincode = (TextView) findViewById(R.id.pincode);
        age = (TextView) findViewById(R.id.age);
        loc_date = (TextView) findViewById(R.id.loc_date);
        loc_address = (TextView) findViewById(R.id.loc_address);
        loc_slot = (TextView) findViewById(R.id.loc_slot);
        fee = (TextView) findViewById(R.id.fee);
        beneficiary = (TextView) findViewById(R.id.beneficiary);

        download = (Button) findViewById(R.id.download);
        cancel_appointment = (Button) findViewById(R.id.cancel_appointment);

        vaccine.setText(dataMap.get("vaccine"));
        pincode.setText(dataMap.get("pincode"));
        age.setText(dataMap.get("center_name"));
        loc_date.setText(dataMap.get("date"));
        loc_address.setText(dataMap.get("address"));
        loc_slot.setText(dataMap.get("slot"));
        fee.setText(dataMap.get("cost"));
        beneficiary.setText(dataMap.get("benificariesdetails"));

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!global.hasPermissions(Appointment.this)) {
                    alertx("Permission not granted");
                    return;
                }
                // loading
                showLoadingAnimation();
                intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
                intentBotIntentService.putExtra("ACTION", "downloadappointment");
                startService(intentBotIntentService);
            }
        });

        cancel_appointment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(Appointment.this);
                builder.setTitle("Alert")
                        .setMessage("Are you sure you want to cancel the appointment?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!global.hasPermissions(Appointment.this)) {
                                    alertx("Permission not granted");
                                    return;
                                }
                                showLoadingAnimation();
                                intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
                                intentBotIntentService.putExtra("ACTION", "cancelbooking");
                                startService(intentBotIntentService);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                //  Action for 'NO' Button
                                dialog.cancel();
                            }
                        });
                //Creating dialog box
                AlertDialog dialog  = builder.create();
                dialog.show();
            }
        });

        // register log receiver
        updateReceiver = new UpdateReceiver();
        IntentFilter intentFilter_update = new IntentFilter(global.ACTION_RESULT);
        intentFilter_update.addCategory(Intent.CATEGORY_DEFAULT);
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, intentFilter_update);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //un-register BroadcastReceiver
        if (updateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
        }
        unregisterDownloadReceiver();
    }

    private void registerDownloadReceiver() {
        downloadComplete = new onDownloadComplete();
        registerReceiver(downloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void unregisterDownloadReceiver() {
        if (downloadComplete != null) {
            unregisterReceiver(downloadComplete);
        }
    }

    public void showLoadingAnimation()
    {
        RelativeLayout pageLoading = (RelativeLayout) findViewById(R.id.main_layoutPageLoading);
        pageLoading.setVisibility(View.VISIBLE);
    }

    public void hideLoadingAnimation()
    {
        RelativeLayout pageLoading = (RelativeLayout) findViewById(R.id.main_layoutPageLoading);
        pageLoading.setVisibility(View.GONE);
    }

    public void alertx(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(Appointment.this);
        builder.setTitle("Alert!")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        //Creating dialog box
        AlertDialog dialog  = builder.create();
        dialog.show();
    }

    private class onDownloadComplete extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Fetching the download id received with the broadcast
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //Checking if the received broadcast is for our enqueued download by matching download id
            if (downloadID == id) {
                hideLoadingAnimation();
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadID));
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if(status == DownloadManager.STATUS_SUCCESSFUL){

                        // download is successful
                        String uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        File pdfFile = new File(Uri.parse(uri).getPath());

                        Uri path = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()),
                                BuildConfig.APPLICATION_ID + ".provider", pdfFile);

                        unregisterDownloadReceiver();
                        Intent pdfIntent = new Intent(Intent.ACTION_VIEW);
                        pdfIntent.setDataAndType(path, "application/pdf");
                        pdfIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        pdfIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        try {
                            startActivity(pdfIntent);
                        } catch (ActivityNotFoundException e) {
                            alertx("No Application available to view PDF");
                            //Toast.makeText(Appointment.this, "No Application available to view PDF", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {
                        // download is assumed cancelled
                    }
                } else {
                    // download is assumed cancelled
                }
                cursor.close();
            }
        }
    };

    private class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            hideLoadingAnimation();
            String data = intent.getStringExtra(BotService.EXTRA_KEY_DATA_UPDATE);
            if(data != null) {
                hideLoadingAnimation();
                if (data.contains("DownloadStarted")) {
                    registerDownloadReceiver();
                } else if (data.contains("Downloadid")) {
                    downloadID = global.getDownloadID();
                } else {
                    alertx(data);
                    if (data.contains("Appointment cancelled")) {
                        Toast.makeText(Appointment.this, data, Toast.LENGTH_SHORT).show();
                        Intent aintent = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(aintent);
                    }
                }
            }
        }
    }

}