package com.pixknot.cowinbot;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class HTTPSingleton {
    private static HTTPSingleton mInstance;
    private RequestQueue mRequestQueue;
    private Context context;
    MyHTTPCallback callback;

    private HTTPSingleton(Context c) {
        context = c.getApplicationContext();
        mRequestQueue = Volley.newRequestQueue(context);
    }

    public static synchronized HTTPSingleton getInstance(Context context) {
        if(mInstance == null) {
            mInstance = new HTTPSingleton(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    public void post(final String url, JSONObject params, Map<String, String> headers) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, params, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                //JSONObject response = response.getJSONObject("station");
                callback.callbackCall(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callback.callbackError(error);
                //error.printStackTrace();
            }
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
                //Map<String, String> params = new HashMap<String, String>();
                //params.put("User-Agent", "android");
                //params.put("Content-Type", "application/json; charset=utf-8");

                //return params;
            }
/*
            @Override
            protected JsonObjectRequest getParams() throws AuthFailureError {
                return params;
                //Map<String, String> params = new HashMap<String, String>();
                //params.put("device_id", token);
                //return params;
            }*/
        };
        mRequestQueue.add(request);

        /*
        jsonObjRequest.setRetryPolicy(new DefaultRetryPolicy(30 * 1000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
*/
    }

    public void get(final String url, Map<String, String> headers) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {
                //JSONObject response = response.getJSONObject("station");
                callback.callbackCall(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callback.callbackError(error);
            }
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        mRequestQueue.add(request);
    }

    public void getRaw(final String url, Map<String, String> headers) {
        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {

            @Override
            public void onResponse(String response) {
                callback.callbackCallString(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callback.callbackError(error);
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        mRequestQueue.add(request);
    }

    public void download(final String url, Map<String, String> headers) {
        InputStreamVolleyRequest request = new InputStreamVolleyRequest(Request.Method.GET, url,
                new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        callback.callbackCallbyte(response);
                    }
                } , new Response.ErrorListener() {

                @Override
                public void onErrorResponse(VolleyError error) {
                    callback.callbackError(error);
                }
            }, headers, null);
        mRequestQueue.add(request);
    }

    class InputStreamVolleyRequest extends Request<byte[]> {
        private final Response.Listener<byte[]> mListener;
        private Map<String, String> mParams;
        private Map<String, String> mHeaders;

        //create a static map for directly accessing headers
        public Map<String, String> responseHeaders ;

        public InputStreamVolleyRequest(int method, String mUrl ,Response.Listener<byte[]> listener,
                                        Response.ErrorListener errorListener, Map<String, String> headers, HashMap<String, String> params) {
            // TODO Auto-generated constructor stub

            super(method, mUrl, errorListener);
            // this request would never use cache.
            setShouldCache(false);
            mListener = listener;
            mParams=params;
            mHeaders=headers;
        }

        @Override
        protected Map<String, String> getParams()
                throws com.android.volley.AuthFailureError {
            return mParams;
        };

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            return mHeaders;
        }

        @Override
        protected void deliverResponse(byte[] response) {
            mListener.onResponse(response);
        }

        @Override
        protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {

            //Initialise local responseHeaders map with response headers received
            responseHeaders = response.headers;

            //Pass the response data here
            return Response.success( response.data, HttpHeaderParser.parseCacheHeaders(response));
        }
    }
}
