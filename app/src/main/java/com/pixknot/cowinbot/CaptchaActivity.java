package com.pixknot.cowinbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.pixplicity.sharp.Sharp;

import org.json.JSONException;

public class CaptchaActivity extends AppCompatActivity {

    private TextView vaccine;
    private TextView loc_date;
    private TextView loc_address;
    private TextView loc_slot;

    private ImageView captcha_image;
    private EditText captcha_text;

    private Button proceed_button;
    private Button stop_button;
    private ImageButton refresh_captcha;

    private GlobalClass global;

    Intent intentBotIntentService;
    private UpdateReceiver updateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captcha);
        //getSupportActionBar().setTitle(Html.fromHtml("<font color='#FFFFFF'>Captcha</font>"));

        Intent intent = getIntent();
        //String message = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);

        vaccine = (TextView) findViewById(R.id.vaccine);
        loc_date = (TextView) findViewById(R.id.loc_date);
        loc_address = (TextView) findViewById(R.id.loc_address);
        loc_slot = (TextView) findViewById(R.id.loc_slot);

        captcha_image = (ImageView) findViewById(R.id.captcha_image);
        captcha_text = (EditText) findViewById(R.id.captcha_text);

        proceed_button = (Button) findViewById(R.id.proceed);
        stop_button = (Button) findViewById(R.id.stop);
        refresh_captcha = (ImageButton) findViewById(R.id.refresh_captcha);

        global = (GlobalClass) getApplicationContext();

        updateCaptchaImage();

        captcha_text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                global.stopAlarm();
            }
        });

        try {
            if(global.getSession() != null && global.getSession().length() > 0) {
                vaccine.setText(global.getVaccine());
                loc_date.setText(global.getSession().getJSONObject("session").getString("date"));
                loc_address.setText(global.getSession().getJSONObject("center").getString("address"));
                loc_slot.setText(global.getSession().getJSONObject("session").getJSONArray("slots").get(0).toString());
            }
        } catch (JSONException e) {
            global.logger.e(e);
        }

        refresh_captcha.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
                intentBotIntentService.putExtra("ACTION", "getcaptcha");
                startService(intentBotIntentService);
                captcha_text.setText("");
            }
        });

        proceed_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String captcha = captcha_text.getText().toString();
                if(global.isEmpty(captcha)) {
                    alertx("Please enter the captcha", false);
                } else {
                    showLoadingAnimation();
                    global.setCaptchaInput(captcha);
                    intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
                    intentBotIntentService.putExtra("ACTION", "captcha");
                    startService(intentBotIntentService);
                    captcha_text.setText("");
                }
            }
        });
        stop_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(CaptchaActivity.this);
                builder.setTitle("Alert")
                        .setMessage("Are you sure you want to reschedule the appointment?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                global.setCaptchaImage("");
                                global.setAppointmentConfirmationNumber("");
                                if(intentBotIntentService != null){
                                    stopService(intentBotIntentService);
                                    intentBotIntentService = null;
                                }
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(intent);
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
        global.stopAlarm();

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
    }

    private void updateCaptchaImage() {
        String captchaImage = global.getCaptchaImage();
        if(!global.isEmpty(captchaImage)) {
            try {
                captchaImage = captchaImage.replace("viewBox", "viewBoxSuppress");
                Sharp.loadString(captchaImage).into(captcha_image);
            } catch(Exception e) {
                global.logger.e(e);
            }
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

    public void alertx(String message, boolean redirect) {
        AlertDialog.Builder builder = new AlertDialog.Builder(CaptchaActivity.this);
        builder.setTitle("Error")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        if (redirect) {
                            Intent aintent = new Intent(getApplicationContext(), MainActivity.class);
                            startActivity(aintent);
                        }
                    }
                });
        //Creating dialog box
        AlertDialog dialog  = builder.create();
        dialog.show();
    }

    private class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(BotService.EXTRA_KEY_DATA_UPDATE);
            if(data != null) {
                hideLoadingAnimation();
                if (data.equals("update_captcha")) {
                    updateCaptchaImage();
                } else if (data.contains("Error")) {
                    alertx(data, true);
                } else {
                    Intent aintent = new Intent(getApplicationContext(), Appointment.class);
                    startActivity(aintent);
                }

            }
        }
    }

}