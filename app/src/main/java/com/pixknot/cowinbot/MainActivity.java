package com.pixknot.cowinbot;

import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    private StringBuilder sb;
    AlertDialog dialog;

    private EditText date;
    private DatePickerDialog datePickerDialog;

    private Button run;
    private EditText phone;
    private Spinner state;
    private Spinner district;
    private EditText pincodes;
    private RadioGroup age_group;
    private RadioGroup vaccine;
    private RadioGroup dose_type;
    private RadioGroup fee_type;
    private ToggleButton show_hide_logs;
    private ScrollView logScrollView;
    private TextView logs;
    private ScrollView parentLayout;
    private LinearLayout pincodecontainer;

    private GlobalClass global;

    private JSONObject statesHash;
    static HashMap<String, String> distMap;
    static HashMap<String, String> stateMap;

    Intent intentBotIntentService;
    private LogUpdateReceiver logReceiver;
    private UpdateReceiver updateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //getSupportActionBar().setTitle(Html.fromHtml("<font color='#FFFFFF'>CowinBot</font>"));
        // Calling Application class (see application tag in AndroidManifest.xml)
        global = (GlobalClass) getApplicationContext();
        HashMap<String, String> dataMap = global.getFromDisk();

        ActivityCompat.requestPermissions(MainActivity.this, global.PERMISSIONS, PackageManager.PERMISSION_GRANTED);
        sb = new StringBuilder();
        global.logger.d("Initializing...");

        // initiate the date picker and a button
        date = (EditText) findViewById(R.id.date);
        run = (Button) findViewById(R.id.run);
        phone = (EditText) findViewById(R.id.phone);
        state = (Spinner) findViewById(R.id.state);
        district = (Spinner) findViewById(R.id.district);
        pincodes = (EditText) findViewById(R.id.pincodes);
        vaccine = (RadioGroup) findViewById(R.id.vaccine);
        age_group = (RadioGroup) findViewById(R.id.age_group);
        dose_type = (RadioGroup) findViewById(R.id.dose_type);
        fee_type = (RadioGroup) findViewById(R.id.fee_type);
        show_hide_logs = (ToggleButton) findViewById(R.id.show_hide_logs);
        logScrollView = (ScrollView) findViewById(R.id.logScrollView);
        logs = (TextView) findViewById(R.id.logs);
        pincodecontainer = (LinearLayout) findViewById(R.id.pincodecontainer);

        date.setText(DateFormat.format("dd-MM-yyyy", new java.util.Date()).toString());

        if(!global.isEmpty(global.getAppointmentConfirmationNumber())) {
            Intent intent = new Intent(MainActivity.this, Appointment.class);
            this.startActivity(intent);
            return;
        }
        if(!global.isEmpty(global.getCaptchaImage())) {
            Intent intent = new Intent(MainActivity.this, CaptchaActivity.class);
            this.startActivity(intent);
            return;
        }

        distMap = new HashMap<String, String>();
        stateMap = new HashMap<String, String>();

        initStates();
        loadStates();

        if(!global.isDisclaimerShown()) {
            showDisclaimer();
        }

        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (!global.hasPermissions(MainActivity.this)) {
                    alertx("Permission not granted");
                    return;
                }
                boolean isChecked = run.getText().toString().equals("Run");
                if (isChecked) {
                    if(validate()) {
                        // The toggle is enabled
                        run.setTextAppearance(R.style.button_green);
                        //run.setBackgroundResource(R.color.red);
                        run.setText("Stop");
                        disableAll();

                        startBotService();
                    }
                } else {
                    // The toggle is disabled
                    stopBotService();
                    run.setTextAppearance(R.style.button_wh);
                    //run.setBackgroundResource(R.color.green);
                    run.setText("Run");
                    enableAll();
                }
            }
        });

        state.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItemText = (String) parent.getItemAtPosition(position);
                loadDistricts(selectedItemText);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        district.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItemText = (String) parent.getItemAtPosition(position);
                global.setDistrict(getDistrictID(selectedItemText));
                global.setState(getStateID(selectedItemText));
                global.setDistrictString(selectedItemText);
                pincodecontainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // perform click event on edit text
        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar c = Calendar.getInstance();
                int mYear = c.get(Calendar.YEAR); // current year
                int mMonth = c.get(Calendar.MONTH); // current month
                int mDay = c.get(Calendar.DAY_OF_MONTH); // current day
                // date picker dialog
                datePickerDialog = new DatePickerDialog(MainActivity.this,
                        new DatePickerDialog.OnDateSetListener() {

                            @Override
                            public void onDateSet(DatePicker view, int year,
                                                  int monthOfYear, int dayOfMonth) {
                                Calendar calendar = Calendar.getInstance();
                                calendar.set(year, monthOfYear, dayOfMonth);
                                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
                                String dateString = dateFormat.format(calendar.getTime());
                                // set day of month , month and year value in the edit text
                                date.setText(dateString);
                            }
                        }, mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });



        show_hide_logs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    logScrollView.setVisibility(View.VISIBLE);
                } else {
                    // The toggle is disabled
                    logScrollView.setVisibility(View.GONE);
                }
            }
        });

        if(global.isRunning()) {
            if(validate()) {
                // The toggle is enabled
                run.setTextAppearance(R.style.button_green);
                //run.setBackgroundResource(R.color.red);
                run.setText("Stop");
                disableAll();

                //startBotService(); // handled by alarammanager
            }
        } else {
            //run.setChecked(false);
            run.setText("Run");
            enableAll();
            run.setTextAppearance(R.style.button_wh);
            //run.setBackgroundResource(R.color.green);
            //stopBotService(); //handled by alarammanager
        }

        // register log receiver
        logReceiver = new LogUpdateReceiver();
        IntentFilter intentFilter_log = new IntentFilter(global.ACTION_LOG);
        intentFilter_log.addCategory(Intent.CATEGORY_DEFAULT);
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, intentFilter_log);

        updateReceiver = new UpdateReceiver();
        IntentFilter intentFilter_updater = new IntentFilter(global.ACTION_RESULT);
        intentFilter_updater.addCategory(Intent.CATEGORY_DEFAULT);
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, intentFilter_updater);
    }

    private void stopBotService() {
        if(intentBotIntentService != null){
            stopService(intentBotIntentService);
            intentBotIntentService = null;
            intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
            intentBotIntentService.putExtra("ACTION", "stop");
            startService(intentBotIntentService);
        }
    }

    private void startBotService() {
        if(intentBotIntentService != null){
            stopBotService();
        }
        if(intentBotIntentService != null){
            stopService(intentBotIntentService);
        }
        intentBotIntentService = new Intent(getApplicationContext(), BotService.class);
        startService(intentBotIntentService);
        alertx("You will be notified once your booking is ready.\nMake sure to check your device notifications");
    }

    private void showDisclaimer() {
        // create a WebView with the current stats
        WebView webView = new WebView(getApplicationContext());
        webView.loadUrl("file:///android_asset/disclaimer.html");

        // display the WebView in an AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Disclaimer")
                .setView(webView)
                .setCancelable(false)
                .setPositiveButton("I Agree", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        global.setDisclaimerShown();
                    }
                })
                .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private class MyWebViewClient extends WebViewClient
    {
        @Override
        //show the web page in webview but not in web browser
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl (url);
            return true;
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if(dialog != null) {
            dialog.dismiss();
        }
        saveFormData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreFormData();
        if(!global.isEmpty(global.getAppointmentConfirmationNumber())) {
            Intent intent = new Intent(getApplicationContext(), Appointment.class);
            this.startActivity(intent);
        } else if(!global.isEmpty(global.getCaptchaImage())) {
            Intent intent = new Intent(getApplicationContext(), CaptchaActivity.class);
            this.startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //un-register BroadcastReceiver
        if (logReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
        }
        if (updateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
        }
    }

    public void hideKeyboard(View view){
        if(!(view instanceof EditText)){
            InputMethodManager inputMethodManager=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);
        }
    }

    private void enableAll() {
        for (int i = 0; i < vaccine.getChildCount(); i++) {
            vaccine.getChildAt(i).setEnabled(true);
        }
        for (int i = 0; i < age_group.getChildCount(); i++) {
            age_group.getChildAt(i).setEnabled(true);
        }
        for (int i = 0; i < dose_type.getChildCount(); i++) {
            dose_type.getChildAt(i).setEnabled(true);
        }
        for (int i = 0; i < fee_type.getChildCount(); i++) {
            fee_type.getChildAt(i).setEnabled(true);
        }
        phone.setEnabled(true);
        district.setEnabled(true);
        state.setEnabled(true);
        pincodes.setEnabled(true);
        date.setEnabled(true);
    }

    private void disableAll() {
        for (int i = 0; i < vaccine.getChildCount(); i++) {
            vaccine.getChildAt(i).setEnabled(false);
        }
        for (int i = 0; i < age_group.getChildCount(); i++) {
            age_group.getChildAt(i).setEnabled(false);
        }
        for (int i = 0; i < dose_type.getChildCount(); i++) {
            dose_type.getChildAt(i).setEnabled(false);
        }
        for (int i = 0; i < fee_type.getChildCount(); i++) {
            fee_type.getChildAt(i).setEnabled(false);
        }
        phone.setEnabled(false);
        district.setEnabled(false);
        state.setEnabled(false);
        pincodes.setEnabled(false);
        date.setEnabled(false);
    }

    private boolean validate() {
        int selectedVaccine = vaccine.getCheckedRadioButtonId();
        int selectedAge = age_group.getCheckedRadioButtonId();
        int selectedDose = dose_type.getCheckedRadioButtonId();
        int selectedFee = fee_type.getCheckedRadioButtonId();
        String ddate = date.getText().toString();
        String aphone = phone.getText().toString();


        RadioButton selectedVaccineradioButton = (RadioButton) findViewById(selectedVaccine);
        RadioButton selectedAgeradioButton = (RadioButton) findViewById(selectedAge);
        RadioButton selectedDoseradioButton = (RadioButton) findViewById(selectedDose);
        RadioButton selectedFeeradioButton = (RadioButton) findViewById(selectedFee);

        if(global.isEmpty(aphone)) {
            alertx("Please enter your phone number.");
            return false;
        }

        if(aphone.length() != 10) {
            alertx("Please enter a valid phone number.");
            return false;
        }

        if(global.isEmpty(selectedVaccineradioButton.getText().toString())) {
            alertx("Please select vaccination type.");
            return false;
        }
        if(global.isEmpty(selectedAgeradioButton.getText().toString())) {
            alertx("Please select age group.");
            return false;
        }
        if(global.isEmpty(selectedDoseradioButton.getText().toString())) {
            alertx("Please select Dose.");
            return false;
        }
        if(global.isEmpty(selectedFeeradioButton.getText().toString())) {
            alertx("Please select fee type.");
            return false;
        }
        if(global.isEmpty(phone.getText().toString())) {
            alertx("Please enter your phone number.");
            return false;
        }
        if(global.isEmpty(ddate)) {
            alertx("Please enter a date.");
            return false;
        }

        global.setDate(ddate);

        global.setVaccine(selectedVaccineradioButton.getText().toString());
        String age = selectedAgeradioButton.getText().toString();
        age = age.contains("18") ? "18" : "45";
        global.setAge(age);

        String dose = selectedDoseradioButton.getText().toString();
        dose = dose.contains("2") ? "2" : "1";
        global.setDose(dose);
        global.setFees(selectedFeeradioButton.getText().toString());

        global.setPhone(aphone);
        if (!global.isEmpty(pincodes.getText().toString())) {
            String codes = pincodes.getText().toString();
            codes = codes.trim().replaceAll(" +", " ");
            codes = codes.replaceAll("[^\\d,]", "");
            String[] pincodesArray = codes.split(",");
            global.setPincodes(pincodesArray);
        } else {
            global.setPincodes(new String[0]);
        }
        String xx = global.getVaccine() + "\n" +
                global.getAge() + "\n" +
                global.getDose() + "\n" +
                global.getFees() + "\n" +
                global.getPhone() + "\n" +
                TextUtils.join(",", global.getPincodes()) + "\n" +
                global.getDate() + "\n" ;
        global.log(xx);

        return true;
    }

    public void alertx(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Alert!")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        //Creating dialog box
        dialog  = builder.create();
        dialog.show();
    }

    private void loadStates() {
        ArrayAdapter<String> aArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item);
        aArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Iterator<String> stringIterator = statesHash.keys();
        SortedMap map = new TreeMap();
        while (stringIterator.hasNext()) {
            String key = stringIterator.next();
            map.put(key, "");
            //aArrayAdapter.add(key);
        }

        Set<String> keys = map.keySet();
        for ( String key: keys) {
            aArrayAdapter.add(key);
        }
        state.setAdapter(aArrayAdapter);
    }

    private void loadDistricts(String state) {
        ArrayAdapter<String> aArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item);
        aArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        try {
            JSONArray dists = statesHash.getJSONArray(state);
            distMap = new HashMap<String, String>();
            stateMap = new HashMap<String, String>();
            for (int i = 0; i < dists.length(); i++) {
                JSONObject sdata = dists.getJSONObject(i);
                String key = sdata.getString("district_name");
                String value = sdata.getString("district_id");
                String svalue = sdata.getString("state_id");
                aArrayAdapter.add(key);
                distMap.put(key, value);
                stateMap.put(key, svalue);
            }
        } catch (JSONException e) {
            global.logger.e(e);
        }
        district.setAdapter(aArrayAdapter);
    }

    public static String getDistrictID(String param) {
        return distMap.get(param);
    }

    public static String getStateID(String param) {
        return stateMap.get(param);
    }

    private void initStates() {
        try {
            statesHash = new JSONObject();
            JSONObject obj = new JSONObject(loadJSONFromAsset());
            JSONArray m_jArry = obj.getJSONArray("districts");

            for (int i = 0; i < m_jArry.length(); i++) {
                JSONObject jo_inside = m_jArry.getJSONObject(i);
                String key = jo_inside.getString("state_name");
                JSONArray dists;
                if (!statesHash.has(key)) {
                    dists = new JSONArray();
                } else {
                    dists = statesHash.getJSONArray(key);
                }
                dists.put(jo_inside);
                statesHash.put(key, dists);
            }
            return;
        } catch (JSONException e) {
            global.logger.e(e);
        }
        return;
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("districts.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

    private void saveFormData() {
        int selectedVaccine = vaccine.getCheckedRadioButtonId();
        int selectedAge = age_group.getCheckedRadioButtonId();
        int selectedDose = dose_type.getCheckedRadioButtonId();
        int selectedFee = fee_type.getCheckedRadioButtonId();

        SharedPreferences sharedPreferences = getSharedPreferences("CowinBotFormData", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        // write all the data entered by the user in SharedPreference and apply
        myEdit.putString("phone", phone.getText().toString());
        myEdit.putString("pincodes", pincodes.getText().toString());
        myEdit.putString("date", date.getText().toString());
        myEdit.putInt("vaccine", selectedVaccine);
        myEdit.putInt("age_group", selectedAge);
        myEdit.putInt("dose_type", selectedDose);
        myEdit.putInt("fee_type", selectedFee);
        myEdit.putInt("district", district.getSelectedItemPosition());
        myEdit.putInt("state", state.getSelectedItemPosition());
        myEdit.apply();
    }

    private void restoreFormData() {
        SharedPreferences pref = getSharedPreferences("CowinBotFormData", MODE_PRIVATE);
        if(pref == null) {
            return;
        }
        phone.setText(pref.getString("phone", ""));
        pincodes.setText(pref.getString("pincodes", ""));
        date.setText(pref.getString("date", DateFormat.format("dd-MM-yyyy", new java.util.Date()).toString()));
        district.setSelection(pref.getInt("district", 0));
        state.setSelection(pref.getInt("state", -1));

        for (int i = 0; i < vaccine.getChildCount(); i++) {
            if(vaccine.getChildAt(i).getId() == pref.getInt("vaccine", -1)) {
                RadioButton selectedradio = (RadioButton) vaccine.getChildAt(i);
                selectedradio.setChecked(true);
            }
        }
        for (int i = 0; i < age_group.getChildCount(); i++) {
            if(age_group.getChildAt(i).getId() == pref.getInt("age_group", -1)) {
                RadioButton selectedradio = (RadioButton) age_group.getChildAt(i);
                selectedradio.setChecked(true);
            }
        }
        for (int i = 0; i < dose_type.getChildCount(); i++) {
            if(dose_type.getChildAt(i).getId() == pref.getInt("dose_type", -1)) {
                RadioButton selectedradio = (RadioButton) dose_type.getChildAt(i);
                selectedradio.setChecked(true);
            }
        }
        for (int i = 0; i < fee_type.getChildCount(); i++) {
            if(fee_type.getChildAt(i).getId() == pref.getInt("fee_type", -1)) {
                RadioButton selectedradio = (RadioButton) fee_type.getChildAt(i);
                selectedradio.setChecked(true);
            }
        }
    }

    private class LogUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(BotService.EXTRA_KEY_LOG_UPDATE);
            sb.append(data).append("\n");
            logs.setText(sb.toString());
            logScrollView.smoothScrollTo(0, logs.getBottom());
        }
    }

    private class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(BotService.EXTRA_KEY_DATA_UPDATE);
            if(data != null) {
                if (data.equals("confirm_captcha")) {
                    Intent captchaintent = new Intent(getApplicationContext(), CaptchaActivity.class);
                    startActivity(captchaintent);
                }

            }
        }
    }

}
