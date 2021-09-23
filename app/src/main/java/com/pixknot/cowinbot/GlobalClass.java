package com.pixknot.cowinbot;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.ClassicFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class GlobalClass extends Application {
    final int NOTIFICATION_ID = 1;
    public static final String ACTION_LOG = "com.pixknot.androidintentservice.LOGUPDATE";
    public static final String ACTION_RESULT = "com.pixknot.androidintentservice.DATAUPDATE";
    public static final String ACTION_ALARM = "com.pixknot.androidintentservice.ALARMUPDATE";
    // logging
    public static Printer globalFilePrinter;
    private static final long MAX_TIME = 1000 * 60 * 60 * 24 * 2; // two days

    private String age = "45";
    private String dose; // 1 or 2
    private String fee;
    private String vaccine = "COVISHIELD";
    private String[] pincodes;
    private String date;
    private String phone;
    private String district_id = "307"; // Ernakulam
    private String state_id = "17"; // Kerala
    private String state_string;
    private String district_string;
    private String captcha_input;
    private long download_id;

    private String appointment_confirmation_no;
    private String txnId;
    private String otp;
    private String token;
    private long tokentimestamp = 0;
    private long firstSMSread = 0;

    List<String> beneficiaries = new ArrayList<String>();
    List<String> beneficiariesname = new ArrayList<String>();
    private JSONArray available;
    private JSONObject session;
    private boolean isRunning = false;

    private String base_url = "https://cdn-api.co-vin.in/api/v2/";
    private String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";
    private String secret = "U2FsdGVkX18cac6Reok/IA4js7PrC4/z9Zlpv7IbkONBu7SYXC1fRxWRbdOvfBAftJjvNI1be2wZR9Fb/gUa3w==";

    MediaPlayer mp;
    private PowerManager.WakeLock wakeLock;
    public static final String[] PERMISSIONS = {Manifest.permission.READ_SMS, Manifest.permission.INTERNET, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    RequestQueue mRequestQueue;

    public Logger logger;

    @Override
    public void onCreate() {
        super.onCreate();
        mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "CowinBot::MyWakelockTag");
        mp = new MediaPlayer();
        try{
            mp.setLooping(true);
            AssetFileDescriptor descriptor = getAssets().openFd("Alert.mp3");
            mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    mp.start();
                }
            });
        }catch(Exception e){
            e.printStackTrace();}
        initXlog();
    }

    /**
     * Initialize XLog.
     */
    private void initXlog() {
        LogConfiguration config = new LogConfiguration.Builder()
                .logLevel(LogLevel.ALL)            // Specify log level, logs below this level won't be printed, default: LogLevel.ALL
                .tag("COWINBOT")                   // Specify TAG, default: "X-LOG"
                // .enableThreadInfo()                                 // Enable thread info, disabled by default
                // .enableStackTrace(2)                                // Enable stack trace info with depth 2, disabled by default
                 .enableBorder()                                     // Enable border, disabled by default
                // .jsonFormatter(new MyJsonFormatter())               // Default: DefaultJsonFormatter
                // .xmlFormatter(new MyXmlFormatter())                 // Default: DefaultXmlFormatter
                // .throwableFormatter(new MyThrowableFormatter())     // Default: DefaultThrowableFormatter
                // .threadFormatter(new MyThreadFormatter())           // Default: DefaultThreadFormatter
                // .stackTraceFormatter(new MyStackTraceFormatter())   // Default: DefaultStackTraceFormatter
                // .borderFormatter(new MyBoardFormatter())            // Default: DefaultBorderFormatter
                // .addObjectFormatter(AnyClass.class,                 // Add formatter for specific class of object
                //     new AnyClassObjectFormatter())                  // Use Object.toString() by default
                //.addInterceptor(new BlacklistTagsFilterInterceptor(    // Add blacklist tags filter
                //        "blacklist1", "blacklist2", "blacklist3"))
                // .addInterceptor(new WhitelistTagsFilterInterceptor( // Add whitelist tags filter
                //     "whitelist1", "whitelist2", "whitelist3"))
                // .addInterceptor(new MyInterceptor())                // Add a log interceptor
                .build();

        Printer androidPrinter = new AndroidPrinter();             // Printer that print the log using android.util.Log
        Printer filePrinter = new FilePrinter                      // Printer that print the log to the file system
                //.Builder(new File(getExternalCacheDir().getAbsolutePath(), "log").getPath())       // Specify the path to save log file
                .Builder(new File(getApplicationContext().getFilesDir().getAbsolutePath(), "log").getPath())
                .fileNameGenerator(new DateFileNameGenerator())        // Default: ChangelessFileNameGenerator("log")
                // .backupStrategy(new MyBackupStrategy())             // Default: FileSizeBackupStrategy(1024 * 1024)
                .cleanStrategy(new FileLastModifiedCleanStrategy(MAX_TIME))     // Default: NeverCleanStrategy()
                .flattener(new ClassicFlattener())                     // Default: DefaultFlattener
                .build();

        XLog.init(                                                 // Initialize XLog
                config,                                                // Specify the log configuration, if not specified, will use new LogConfiguration.Builder().build()
                androidPrinter,                                        // Specify printers, if no printer is specified, AndroidPrinter(for Android)/ConsolePrinter(for java) will be used.
                filePrinter);

        // For future usage: partial usage in MainActivity.
        globalFilePrinter = filePrinter;
        logger = XLog.tag("COWINBOT").build();
    }

    public boolean isEmpty(String data) {
        return TextUtils.isEmpty(data);
    }

    public void log(String alog) {
        logger.d(alog);
        showNotification(alog);
        sendLogUpdate(alog);
    }

    public void playAlarm() {
        mp.prepareAsync();
    }

    public void stopAlarm() {
        if(mp.isPlaying()) {
            mp.stop();
        }
    }

    public static boolean hasPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && PERMISSIONS != null) {
            for (String permission : PERMISSIONS) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isRunning() {
        //return isRunning;
        SharedPreferences pref = getSharedPreferences("CowinBotRunning", MODE_PRIVATE);
        return pref.getBoolean("running", false);
    }

    public boolean isDisclaimerShown() {
        //return isRunning;
        SharedPreferences pref = getSharedPreferences("CowinBotDisclaimer", MODE_PRIVATE);
        return pref.getBoolean("disclaimer", false);
    }

    private void sendLogUpdate(String data) {
        //send update
        Intent intentUpdate = new Intent();
        intentUpdate.setAction(ACTION_LOG);
        intentUpdate.addCategory(Intent.CATEGORY_DEFAULT);
        intentUpdate.putExtra(BotService.EXTRA_KEY_LOG_UPDATE, data);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentUpdate);
    }

    public void sendDataUpdate(String data) {
        //send update
        Intent intentUpdate = new Intent();
        intentUpdate.setAction(ACTION_RESULT);
        intentUpdate.addCategory(Intent.CATEGORY_DEFAULT);
        intentUpdate.putExtra(BotService.EXTRA_KEY_DATA_UPDATE, data);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intentUpdate);
    }

    public void showNotification(String message) {
        NotificationManager mNotificationManager;

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext(), "notify_001");
        Intent ii;
        if (isEmpty(getCaptchaImage())) {
            ii = new Intent(getApplicationContext(), MainActivity.class);
        } else {
            ii = new Intent(getApplicationContext(), CaptchaActivity.class);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, ii, 0);

        NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();
        bigText.bigText(message);
        bigText.setBigContentTitle("CowinBot");
        bigText.setSummaryText("In detail");

        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher_round);
        mBuilder.setContentTitle("CowinBot");
        mBuilder.setContentText("Searching for slots...");
        mBuilder.setPriority(Notification.PRIORITY_MAX);
        mBuilder.setStyle(bigText);
        mBuilder.setAutoCancel(false);

        mNotificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // === Removed some obsoletes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            String channelId = "Cowinbot_channel_id";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Cowin Bot",
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId(channelId);
        }

        Notification notification = mBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void removeNotification() {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(ns);
        nMgr.cancel(NOTIFICATION_ID);
    }


    public boolean callCaptcha() {
        // if true will use CaptchaIntent
        return true;
    }


    public long currentTimestamp() {
        return System.currentTimeMillis();
    }


    public void setRunning(boolean bit) {
        //isRunning = bit;
        SharedPreferences pref = getSharedPreferences("CowinBotRunning", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putBoolean("running", bit);
        edit.apply();
        if (bit) {
            getWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    public void setDisclaimerShown() {
        SharedPreferences pref = getSharedPreferences("CowinBotDisclaimer", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putBoolean("disclaimer", true);
        edit.apply();
    }

    public void getWakeLock() {
        wakeLock.acquire();
    }

    public void releaseWakeLock() {
        if(wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public String getPhone() {
        SharedPreferences pref = getSharedPreferences("CowinBotPhone", MODE_PRIVATE);
        return pref.getString("phone", phone);
    }

    public String getAge() {
        SharedPreferences pref = getSharedPreferences("CowinBotAge", MODE_PRIVATE);
        return pref.getString("age", age);
    }

    public String getFees() {
        SharedPreferences pref = getSharedPreferences("CowinBotFee", MODE_PRIVATE);
        return pref.getString("fee", fee);
    }

    public String getDose() {
        SharedPreferences pref = getSharedPreferences("CowinBotDose", MODE_PRIVATE);
        return pref.getString("dose", dose);
    }

    public String getVaccine() {
        SharedPreferences pref = getSharedPreferences("CowinBotVaccine", MODE_PRIVATE);
        return pref.getString("vaccine", vaccine);
    }

    public String[] getPincodes() {
        SharedPreferences pref = getSharedPreferences("CowinBotPin", MODE_PRIVATE);
        String pin = pref.getString("pin", "");
        String[] pincodes = pin.split(",");
        return pincodes;
    }

    public String getDate() {
        SharedPreferences pref = getSharedPreferences("CowinBotDate", MODE_PRIVATE);
        return pref.getString("date", date);
    }

    public String getCaptchaInput() {
        return captcha_input;
    }

    public String getState() {
        return state_id;
    }

    public String getDistrict() {
        SharedPreferences pref = getSharedPreferences("CowinBotDist", MODE_PRIVATE);
        return pref.getString("dist_id", district_id);
    }

    public String getStateString() {
        return state_string;
    }

    public String getDistrictString() {
        return district_string;
    }

    public String getUA() {
        return ua;
    }

    public String getToken() {
        return token;
    }

    public String getSecret() {
        return secret;
    }

    public String getBaseUrl() {
        return base_url;
    }

    public JSONArray getAvailable() {
        return available;
    }

    public JSONObject getSession() {
        return session;
    }

    public List<String> getBenificaryData() {
        return beneficiaries;
    }

    public List<String> getBenificaryDataDetails() {
        return beneficiariesname;
    }

    public String getAppointmentConfirmationNumber() {
        return appointment_confirmation_no;
    }

    public String getCaptchaImage() {
        SharedPreferences pref = getSharedPreferences("CowinBotCaptchaImage", MODE_PRIVATE);
        return pref.getString("captcha_image", "");
    }

    public String getTxId() {
        return txnId;
    }
    public String getOTP() {
        return otp;
    }

    public long getDownloadID() {
        return download_id;
    }

    public long getTokenTimestamp() {
        return tokentimestamp;
    }

    public long getSMSTimestamp() {
        return firstSMSread;
    }




    public void setTokenTimestamp(long atokentimestamp) {
        tokentimestamp = atokentimestamp;
    }

    public void setSMSTimestamp(long afirstSMSread) {
        firstSMSread = afirstSMSread;
    }

    public void setDownloadID(long adownload_id) {
        download_id = adownload_id;
    }

    public void setOTP(String aotp) {
        otp = aotp;
    }

    public void setTxId(String atxnId) {
        txnId = atxnId;
    }

    public void setCaptchaImage(String acaptcha_image) {
        SharedPreferences pref = getSharedPreferences("CowinBotCaptchaImage", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("captcha_image", acaptcha_image);
        edit.apply();
    }


    public void setAppointmentConfirmationNumber(String aappointment_confirmation_no) {
        appointment_confirmation_no = aappointment_confirmation_no;
    }

    public void setBenificaryData(List<String> abeneficiaries) {
        beneficiaries = abeneficiaries;
    }

    public void setBenificaryDataDetails(List<String> abeneficiariesname) {
        beneficiariesname = abeneficiariesname;
    }

    public void clearBenificaryData() {
        beneficiaries.clear();
    }

    public void setSession(JSONObject asession) {
        session = asession;
    }

    public void setAvailable(JSONArray aavailable) {
        available = aavailable;
    }

    public void setStateString(String astate_str) {
        state_string = astate_str;
    }

    public void setDistrictString(String adistrict_str) {
        district_string = adistrict_str;
    }

    public void setCaptchaInput(String acaptcha) {
        captcha_input = acaptcha;
    }

    public void setState(String astate_id) {
        state_id = astate_id;
    }

    public void setDistrict(String adistrict_id) {
        SharedPreferences pref = getSharedPreferences("CowinBotDist", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("dist_id", adistrict_id);
        edit.apply();

        district_id = adistrict_id;
    }

    public void setAge(String aage) {
        SharedPreferences pref = getSharedPreferences("CowinBotAge", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("age", aage);
        edit.apply();
        age = aage;
    }

    public void setPhone(String aphone) {
        SharedPreferences pref = getSharedPreferences("CowinBotPhone", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("phone", aphone);
        edit.apply();
        phone = aphone;
    }

    public void setFees(String afee) {
        SharedPreferences pref = getSharedPreferences("CowinBotFee", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("fee", afee);
        edit.apply();
        fee = afee;
    }

    public void setDose(String adose) {
        SharedPreferences pref = getSharedPreferences("CowinBotDose", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("dose", adose);
        edit.apply();
        dose = adose;
    }

    public void setVaccine(String avaccine) {
        SharedPreferences pref = getSharedPreferences("CowinBotVaccine", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("vaccine", avaccine);
        edit.apply();
        vaccine = avaccine;
    }

    public void setPincodes(String[] apincodes) {
        SharedPreferences pref = getSharedPreferences("CowinBotPin", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("pin", TextUtils.join(",",apincodes));
        edit.apply();
        pincodes = apincodes;
    }

    public void setDate(String adate) {
        SharedPreferences pref = getSharedPreferences("CowinBotDate", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putString("date", adate);
        edit.apply();
        date = adate;
    }

    public void setToken(String aToken) {
        token = aToken;
    }

    public void saveToDisk() {
        SharedPreferences pref = getSharedPreferences("CowinBotSharedPref", MODE_PRIVATE);

        SharedPreferences.Editor edit = pref.edit();
        edit.putString("appointment_confirmation_no", getAppointmentConfirmationNumber());
        edit.putString("vaccine", getVaccine());
        try {
            if(getSession() != null) {
                edit.putString("center_id", getSession().getJSONObject("center").getString("center_id"));
                edit.putString("pincode", getSession().getJSONObject("center").getString("pincode"));
                edit.putString("center_name", getSession().getJSONObject("center").getString("name"));
                edit.putString("address", getSession().getJSONObject("center").getString("address"));
                edit.putString("date", getSession().getJSONObject("session").getString("date"));
                edit.putString("cost", getSession().getJSONObject("center").getString("fee_type"));
                edit.putString("slot", getSession().getJSONObject("session").getJSONArray("slots").get(0).toString());
            }
            edit.putString("phone", getPhone());
            edit.putString("benificaries", TextUtils.join(",", getBenificaryData()));
            edit.putString("benificariesdetails", TextUtils.join(",", getBenificaryDataDetails()));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        edit.commit();
    }

    public HashMap<String, String> getFromDisk() {
        SharedPreferences pref = getSharedPreferences("CowinBotSharedPref", MODE_PRIVATE);
        HashMap<String, String> datatMap = new HashMap<>();
        datatMap.put("appointment_confirmation_no", pref.getString("appointment_confirmation_no", ""));
        datatMap.put("vaccine", pref.getString("vaccine", ""));
        datatMap.put("center_id", pref.getString("center_id", ""));
        datatMap.put("center_name", pref.getString("center_name", ""));
        datatMap.put("pincode", pref.getString("pincode", ""));
        datatMap.put("address", pref.getString("address", ""));
        datatMap.put("date", pref.getString("date", ""));
        datatMap.put("cost", pref.getString("cost", ""));
        datatMap.put("slot", pref.getString("slot", ""));
        datatMap.put("phone", pref.getString("phone", ""));
        datatMap.put("benificaries", pref.getString("benificaries", null));
        datatMap.put("benificariesdetails", pref.getString("benificariesdetails", null));

        setAppointmentConfirmationNumber(pref.getString("appointment_confirmation_no", ""));
        setPhone(pref.getString("phone", ""));
        String hbenificaries = pref.getString("benificaries", null);
        if(!isEmpty((hbenificaries))) {
            List<String> mStringList = Arrays.asList(hbenificaries.split(","));
            setBenificaryData(mStringList);
        }

        return datatMap;
    }

    public void clearFromDisk() {

    }
}
