package com.pixknot.cowinbot;

import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Telephony;

import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotService extends IntentService {
    private int MAX_RETRIES = 10;
    private int generate_otp_retry = 0;
    private int confirm_otp_retry = 0;

    private GlobalClass global;

    public static final String EXTRA_KEY_LOG_UPDATE = "EXTRA_KEY_LOG_UPDATE";
    public static final String EXTRA_KEY_DATA_UPDATE = "EXTRA_KEY_DATA_UPDATE";
    public static final int alarmRequestCode = 9999;

    private String ACTION;

    public BotService() {
        super("com.pixknot.androidintentservice.BotService");
    }

    @Override
    public void onCreate() {
        super.onCreate(); // if you override onCreate(), make sure to call super().
        // If a Context object is needed, call getApplicationContext() here.
        global = (GlobalClass) getApplicationContext();
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();
        if (workIntent.getStringExtra("ACTION") != null) {
            ACTION = workIntent.getStringExtra("ACTION");
        } else {
            ACTION = "";
        }
        global.setRunning(true);

        if (ACTION.equals("stop")) {
            stop();
        } else if (ACTION.equals("captcha")) { // from captcha
            global.log("Booking slot.");
            book();
        } else if (ACTION.equals("cancelbooking")) {
            cancelbooking();
        } else if (ACTION.equals("getcaptcha")) {
            getCaptcha();
        } else if (ACTION.equals("getbeneficaries")) {
            getBeneficiaries();
        } else if (ACTION.equals("downloadappointment")) {
            downloadAppointment();
        } else if (ACTION.equals("fetchslots")) { // alarm manager
            fetchSlots();
        } else if (ACTION.equals("generateotp")) { // alarm manager
            generateOTP();
        } else if (ACTION.equals("readsms")) { // alarm manager
            readMessages(false);
        } else if (ACTION.equals("confirmotp")) { // alarm manager
            confirmOTP();
        } else {
            global.log("Starting fetching service.");
            start();
        }
    }

    private void startAlarm(String payload, long delay) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        long alarmTimeAtUTC = System.currentTimeMillis() + delay;
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        alarmIntent.setAction(GlobalClass.ACTION_ALARM);
        alarmIntent.putExtra("EXECUTE", payload);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), alarmRequestCode, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTimeAtUTC, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTimeAtUTC, pendingIntent);
        }
    }

    private void stopAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        alarmIntent.setAction(GlobalClass.ACTION_ALARM);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), alarmRequestCode, alarmIntent, PendingIntent.FLAG_NO_CREATE);
        if(pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
        global.releaseWakeLock();
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("User-Agent", global.getUA());
        headers.put("Content-Type", "application/json; charset=utf-8");
        if(!global.isEmpty(global.getToken())) {
            headers.put("Authorization", "Bearer " + global.getToken());
        }
        return headers;
    }

    public void parseVolleyError(VolleyError error) {
        try {
            String responseBody = new String(error.networkResponse.data, "utf-8");
            JSONObject data = new JSONObject(responseBody);
            String message = data.getString("error");
            global.log(message);
            global.sendDataUpdate("Error: " + message);
        } catch (JSONException | UnsupportedEncodingException e) {
            global.logger.e(e);
        }
    }

    private void fetchSlots() {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        global.log("Fetching available slots...");
        String url = global.getBaseUrl() + "appointment/sessions" + (!global.isEmpty(global.getToken()) ? "/" : "/public/") +
                "calendarByDistrict?district_id=" + global.getDistrict() + "&date=" + global.getDate();
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    JSONArray centers = response.getJSONArray("centers");
                    global.log("Got centers...");
                    check(centers);
                    //logger.json(beneficiaries.toString());
                } catch (JSONException e) {
                    global.logger.e(e);
                }
                stopAlarm();
                startAlarm("fetchslots", 6000);
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while getting slots");
                parseVolleyError(error);
                stopAlarm();
                startAlarm("fetchslots", 30000);
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        worker.get(url, headers);
    }

    public void check(JSONArray _centers) throws JSONException {
        global.log("Checking availability...");
        global.setAvailable(new JSONArray());
        for (int i = 0; i < _centers.length(); i++) {
            JSONObject center = _centers.getJSONObject(i);
            //global.log("Checking pincode " + center.getString("pincode") + " with " + TextUtils.join(",", global.getPincodes()));
            if (global.getPincodes() != null && global.getPincodes().length > 0 && !Arrays.asList(global.getPincodes()).contains(center.getString("pincode"))) {
                global.log("Pincode not matched");
                continue;
            }
            if (!global.isEmpty(global.getFees()) &&
                    !global.getFees().toLowerCase().equals("any") &&
                    !global.getFees().toLowerCase().equals(center.getString("fee_type").toLowerCase())) {
                global.log("Fees not matched: " + global.getFees().toLowerCase() + " with " + center.getString("fee_type").toLowerCase());
                continue;
            }
            JSONArray sessions = center.getJSONArray("sessions");
            for (int j = 0; j < sessions.length(); j++) {
                if(!this.filterSession(sessions.getJSONObject(j))) {
                    continue;
                }
                JSONObject tmp = new JSONObject();
                tmp.put("center", center);
                tmp.put("session", sessions.getJSONObject(j));
                global.getAvailable().put(tmp);
            }
        }

        if (global.getAvailable().length() > 0) {
            stopAlarm();
            global.log("Slot available:");
            global.logger.d(global.getAvailable().toString());
            global.setSession(global.getAvailable().getJSONObject(0));
            login();
        } else {
            global.log("No slots available");
        }
    }

    private boolean filterSession (JSONObject s) throws JSONException {
        global.logger.json(s.toString());
        String cap = "available_capacity_dose" + global.getDose();
        int requiredNums = global.getBenificaryData().size() > 0
                ? global.getBenificaryData().size()
                : 1;

        if (s.getInt("available_capacity") < requiredNums) {
            global.log("Got available " + s.getInt("available_capacity") + " need atleast " + requiredNums);
            return false;
        }
        if (s.getInt("min_age_limit") != Integer.parseInt(global.getAge())) {
            global.log("Age not matched: " + s.getInt("min_age_limit") + " with " + global.getAge());
            return false;
        }

        if (!global.isEmpty(global.getVaccine()) && !global.getVaccine().toLowerCase().equals(s.getString("vaccine").replaceAll(" ", "-").toLowerCase())) {
            global.log("Vaccine type not matched: " + global.getVaccine().toLowerCase() + " with " + s.getString("vaccine").replaceAll(" ", "-").toLowerCase());
            return false;
        }
        if (s.getInt(cap) < requiredNums ) {
            global.log("Dose not matched: " + s.getInt(cap) + " needed " + requiredNums);
            return false;
        }

        return true;
    }

    public void generateOTP() {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        if(global.isEmpty(global.getPhone())) {
            global.log("No mobile phone provided");
            global.releaseWakeLock();
            stop();
            return;
        }
        if (generate_otp_retry > MAX_RETRIES) {
            global.log("Number blocked. will retry in 30 min");
            generate_otp_retry = 0;
            stopAlarm();
            startAlarm("fetchslots", 1800000);
            return;
        }
        global.log("generating OTP");
        //global.log("generating OTP for "+ global.getPhone());
        String url = global.getBaseUrl() + "auth/generateMobileOTP";
        HTTPSingleton worker = HTTPSingleton.getInstance(this);
        worker.callback = new MyHTTPCallback() {

            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    String txnId = response.getString("txnId");
                    global.setTxId(txnId);
                    readMessages(true);
                    global.log("Waiting for SMS");
                    stopAlarm();
                    startAlarm("readsms", 5000);
                    //new android.os.Handler().postDelayed(
                    //        () -> checkSMS(), 5000);
                } catch (JSONException e) {
                    global.logger.e(e);
                    stopAlarm();
                    startAlarm("generateotp", 10000);
                }
            }

            @Override
            public void callbackCallString(String response) {

            }

            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while generating OTP");
                parseVolleyError(error);
                stopAlarm();
                startAlarm("generateotp", 10000);
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final JSONObject params = new JSONObject();
        try {
            params.put("mobile", global.getPhone());
            params.put("secret", global.getSecret());
        } catch (JSONException e) {
            global.logger.e(e);
            //e.printStackTrace();
        }
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        global.logger.json(params.toString());
        worker.post(url, params, headers);
    }
/*
    public void checkSMS() {
        if(!global.isRunning()) {
            if(readMessagesInterval != null) {
                readMessagesInterval.cancel();
                readMessagesInterval = null;
            }
            return;
        }
        readMessagesInterval = new Timer();
        readMessagesInterval.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                readMessages(false);
            }
        },0, 5000);
    }*/

    public void readMessages(boolean isfirst) {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        if (isfirst) {
            global.setSMSTimestamp(global.currentTimestamp());
            return;
        }
        if (global.getSMSTimestamp() != 0) {
            long diff = TimeUnit.MILLISECONDS.toSeconds(global.currentTimestamp() - global.getSMSTimestamp());
            if (diff > 180) { // 3 min
                stopAlarm();
                relogin();
            }
        }

        String smsData = null;
        try (Cursor cursor = getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI, // Official
                new String[] { Telephony.Sms.Inbox.BODY }, // Select body text
                null, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        )) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                smsData = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
            }
        }
        if (smsData == null) {
            global.log("No SMS found");
            stopAlarm();
            startAlarm("readsms", 5000);
            return;
        }
        global.logger.d("SMS DATA: "+ smsData);
        Pattern pattern = Pattern.compile(".*CoWIN is\\s+(\\d{6}).*");
        Matcher matcher = pattern.matcher(smsData);
        if (matcher.find()) {
            String otp = matcher.group(1);
            global.logger.d("got OTP: ~%s~", otp);
            global.setOTP(otp);
            stopAlarm();
            startAlarm("confirmotp",10000);
        } else {
            stopAlarm();
            startAlarm("readsms", 5000);
        }
        
    }

    public static String convertStringToSha(String key) {
        MessageDigest md;
        StringBuffer hexString = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(key.getBytes());

            byte byteData[] = md.digest();

            // convert the byte to hex format method 1
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                sb.append(Integer
                        .toString((byteData[i] & 0xff) + 0x100, 16)
                        .substring(1));
            }

            // convert the byte to hex format method 2
            hexString = new StringBuffer();
            for (int i = 0; i < byteData.length; i++) {
                String hex = Integer.toHexString(0xff & byteData[i]);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return hexString.toString();
    }

    public void confirmOTP() {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        if(!global.isEmpty(global.getToken())) {
            stopAlarm();
            return;
        }
        if (confirm_otp_retry > MAX_RETRIES) {
            global.log("Number blocaked. will retry in 30 min");
            confirm_otp_retry = 0;
            stopAlarm();
            startAlarm("fetchslots", 1800000);
            return;
        }
        global.log("confirming OTP");
        //global.log("confirming OTP "+global.getOTP()+" for " + global.getPhone());
        global.setToken("");
        String eotp = convertStringToSha(global.getOTP());
        String url = global.getBaseUrl() + "auth/validateMobileOtp";
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    String token = response.getString("token");
                    global.setToken(token);
                    global.setTokenTimestamp(global.currentTimestamp());
                    global.log("got token "+ token);
                    stopAlarm();
                    //checkTokenRoutine();
                    if (ACTION.equals("getcaptcha")) {
                        getCaptcha();
                    } else if (ACTION.equals("cancelbooking")) {
                        cancelbooking();
                    } else if (ACTION.equals("getbeneficaries")) {
                        getBeneficiaries();
                    } else if (ACTION.equals("downloadappointment")) {
                        downloadAppointment();
                    } else {
                        getBeneficiaries();
                    }
                } catch (JSONException e) {
                    global.logger.e(e);
                }
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while confirming OTP");
                parseVolleyError(error);
                generate_otp_retry = generate_otp_retry + 1;
                confirm_otp_retry = confirm_otp_retry + 1;
                if (generate_otp_retry < MAX_RETRIES) {
                    global.setToken("");
                    stopAlarm();
                    startAlarm("generateotp", 5000);
                }
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final JSONObject params = new JSONObject();
        try {
            params.put("txnId", global.getTxId());
            params.put("otp", eotp);
        } catch (JSONException e) {
            global.logger.e(e);
            //e.printStackTrace();
        }
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        global.logger.json(params.toString());
        worker.post(url, params, headers);
    }

    public void getBeneficiaries() {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        if (global.isEmpty(global.getToken()) || !isTokenValid()){
            stopAlarm();
            ACTION = "getbeneficaries";
            relogin();
            return;
        }
        global.log("getting beneficiaries...");
        String url = global.getBaseUrl() + "appointment/beneficiaries";
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    JSONArray bens = response.getJSONArray("beneficiaries");
                    global.log("got beneficiaries: ");
                    try {
                        List<String> beneficiaries = new ArrayList<String>();
                        List<String> beneficiariesname = new ArrayList<String>();

                        for (int i = 0; i < bens.length(); i++) {
                            JSONObject person = bens.getJSONObject(i);
                            beneficiaries.add(person.getString("beneficiary_reference_id"));
                            beneficiariesname.add(person.getString("name"));
                            global.log(i +": " +person.getString("name"));
                        }
                        global.setBenificaryData(beneficiaries);
                        global.setBenificaryDataDetails(beneficiariesname);
                        getCaptcha();
                    } catch (JSONException e) {
                        global.logger.e(e);
                    }
                } catch (JSONException e) {
                    global.logger.e(e);
                }
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while getting beneficiaries");
                parseVolleyError(error);
                stopAlarm();
                startAlarm("getbeneficaries", 5000);
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        worker.get(url, headers);
    }

    private void getCaptcha() {
        if(!global.isRunning()) {
            stopAlarm();
            return;
        }
        if (global.isEmpty(global.getToken()) || !isTokenValid()){
            stopAlarm();
            ACTION = "getcaptcha";
            relogin();
            return;
        }

        String url = global.getBaseUrl() + "auth/getRecaptcha";
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    String captcha = response.getString("captcha");
                    captcha = captcha.replaceAll("\\\\", "");
                    global.setCaptchaImage(captcha);
                    if (!ACTION.equals("getcaptcha")) {
                        global.playAlarm();
                        global.showNotification("Click here to confirm booking");
                        global.sendDataUpdate("confirm_captcha"); // look at mainActiity broadcast listener too
                    } else {
                        global.sendDataUpdate("update_captcha");
                    }
                } catch (JSONException e) {
                    global.logger.e(e);
                }
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while getting captcha");
                parseVolleyError(error);
                stopAlarm();
                startAlarm("fetchslots", 6000);
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final JSONObject params = new JSONObject();
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        global.logger.json(params.toString());
        worker.post(url, params, headers);

    }

    public void book() {
        try {
            if (global.isEmpty(global.getToken()) ||
                    global.getSession() == null ||
                    !global.getSession().getJSONObject("session").has("session_id")){
                global.sendDataUpdate("Error: Invalid session. Please try again.");
                stop();
                return;
            }
        } catch (JSONException e) {
            global.sendDataUpdate("Error while getting session data.");
            global.logger.e(e);
            stop();
            return;
        }
        String session_id = "";
        String slot = "";
        String captcha = global.getCaptchaInput();

        try {
            session_id = global.getSession().getJSONObject("session").getString("session_id");
            slot = global.getSession().getJSONObject("session").getJSONArray("slots").getString(0);
        } catch (JSONException e) {
            global.sendDataUpdate("Error while getting session data.");
            global.logger.e(e);
            stop();
        }
        if(global.isEmpty(session_id)) {
            global.sendDataUpdate("Error: No session_id found.");
            global.log("No session_id found.");
            stop();
            return;
        }
        if(global.isEmpty(slot)) {
            global.sendDataUpdate("Error: No slots found");
            global.log("No slots found.");
            stop();
            return;
        }
        global.log("booking...");

        String url = global.getBaseUrl() + "appointment/schedule";
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.logger.json(response.toString());
                try {
                    String appointment_confirmation_no = response.getString("appointment_confirmation_no");
                    global.setAppointmentConfirmationNumber(appointment_confirmation_no);
                    global.log("Appointment booked: " + appointment_confirmation_no);
                    global.log("Name: " + global.getSession().getJSONObject("center").getString("name"));
                    global.log("Address: " + global.getSession().getJSONObject("center").getString("address"));
                    global.log("Pincode: " + global.getSession().getJSONObject("center").getString("pincode"));
                    global.saveToDisk();
                    global.sendDataUpdate("Appointment booked");
                    stop();
                } catch (JSONException e) {
                    global.logger.e(e);
                    stop();
                }
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                global.log("Error while booking appointment");
                parseVolleyError(error);
                stop();
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final JSONObject params = new JSONObject();
        try {
            params.put("dose", global.getDose());
            params.put("session_id", session_id);
            params.put("slot", slot);
            params.put("beneficiaries", new JSONArray(global.getBenificaryData()));
            params.put("captcha", captcha);
        } catch (JSONException e) {
            global.logger.e(e);
        }
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        global.logger.json(params.toString());
        worker.post(url, params, headers);
    }

    public void cancelbooking() {
        // mover this to cancel activity
        if (global.isEmpty(global.getAppointmentConfirmationNumber()) || global.getBenificaryData() == null){
            global.sendDataUpdate("Error: No appointment or beneficiary found.");
            global.log("Error: No appointment or beneficiary found.\"");
            stop();
            return;
        }

        if (global.isEmpty(global.getToken()) || !isTokenValid()){
            stopAlarm();
            ACTION = "cancelbooking";
            relogin();
            return;
        }

        global.log("canceling booking...");

        String url = global.getBaseUrl() + "appointment/cancel";
        HTTPSingleton worker = HTTPSingleton.getInstance(getApplicationContext());
        worker.callback = new MyHTTPCallback() {
            @Override
            public void callbackCall(JSONObject response) {
                global.setAppointmentConfirmationNumber("");
                global.logger.json(response.toString());
                clearData();
                global.log("Appointment cancelled.");
                global.sendDataUpdate("Appointment cancelled");
                stop();
            }
            @Override
            public void callbackCallString(String response) {

            }
            @Override
            public void callbackError(VolleyError error) {
                global.logger.e(error);
                clearData();
                global.log("Error while cancelling appointment");
                global.sendDataUpdate("Appointment cancelled");
                //parseVolleyError(error);
                stop();
            }
            @Override
            public void callbackCallbyte(byte[] data) {

            }
        };
        final JSONObject params = new JSONObject();
        try {
            params.put("appointment_id", global.getAppointmentConfirmationNumber());
            params.put("beneficiariesToCancel", new JSONArray(global.getBenificaryData()));
        } catch (JSONException e) {
            global.logger.e(e);
        }
        final Map headers = this.getHeaders();
        global.logger.d(url);
        global.logger.d(headers);
        global.logger.json(params.toString());
        worker.post(url, params, headers);
    }

    private void clearData() {
        global.setCaptchaImage("");
        global.setAppointmentConfirmationNumber("");
        global.saveToDisk();
    }

    public void downloadAppointment() {
        if (global.isEmpty(global.getToken()) || !isTokenValid()){
            stopAlarm();
            ACTION = "downloadappointment";
            relogin();
            return;
        }
        global.log("Downloading appointment slip...");
        String url = global.getBaseUrl() + "appointment/appointmentslip/download?appointment_id=" + global.getAppointmentConfirmationNumber();

        Uri.Builder builder = Uri.parse(url).buildUpon();
        DownloadManager.Request request = new DownloadManager.Request(builder.build());
        request.addRequestHeader("Accept", "application/pdf");
        final Map headers = this.getHeaders();
        Iterator<Map.Entry<String, String>> itr = headers.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            request.addRequestHeader(entry.getKey(), entry.getValue());
        }

        // Save the file in the "Downloads" folder of SDCARD
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "cowin_appointment.pdf");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle("Cowin Bot");
        request.setDescription("Downloading Cowin Appointment Slip");

        global.sendDataUpdate("DownloadStarted");
        DownloadManager downloadManager = (DownloadManager) getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadID = downloadManager.enqueue(request);
        global.setDownloadID(downloadID);
        global.sendDataUpdate("Downloadid");
        stop();
    }

    public boolean isTokenValid() {
        long diff = TimeUnit.MILLISECONDS.toSeconds(global.currentTimestamp() - global.getTokenTimestamp());
        if (diff < 900) return true;
        //if (diff < 15) return true;
        return false;
    }

    public void login() {
        global.setOTP("");
        generateOTP();
    }

    public void logout() {
        global.setOTP("");
        global.setTxId("");
        global.setSMSTimestamp(0);
        global.setCaptchaImage("");
        global.removeNotification();
        global.setCaptchaInput("");
    }

    public void relogin() {
        global.log("Session expired. Loggging in...");
        logout();
        generate_otp_retry = generate_otp_retry + 1;
        generateOTP();
    }

    public void start() {
        // start the service
        global.setRunning(true);
        stopAlarm();
        startAlarm("fetchslots", 6000);
    }

    public void stop() {
        // stop service
        global.log("Stopping...");
        stopAlarm();
        global.setRunning(false);
        global.removeNotification();
        global.stopAlarm();
        logout();
    }

}
