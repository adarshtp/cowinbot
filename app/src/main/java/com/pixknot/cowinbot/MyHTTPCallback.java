package com.pixknot.cowinbot;

import com.android.volley.VolleyError;

import org.json.JSONObject;

interface MyHTTPCallback {
    void callbackCall(JSONObject response);
    void callbackCallString(String response);
    void callbackError(VolleyError error);
    void callbackCallbyte(byte[] data);
}