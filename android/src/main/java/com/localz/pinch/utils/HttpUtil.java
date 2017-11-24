package com.localz.pinch.utils;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;

import com.localz.pinch.models.HttpRequest;
import com.localz.pinch.models.HttpResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class HttpUtil {
    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    private String getResponseBody(InputStream responseStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(responseStream));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
        }
        bufferedReader.close();

        return sb.toString();
    }

    private WritableMap getResponseHeaders(HttpsURLConnection connection) {
        WritableMap jsonHeaders = Arguments.createMap();
        Map<String, List<String>> headerMap = connection.getHeaderFields();

        for (Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
            if (entry.getKey() != null) {
                jsonHeaders.putString(entry.getKey(), entry.getValue().get(0));
            }
        }

        return jsonHeaders;
    }

    private HttpsURLConnection prepareRequestHeaders(HttpsURLConnection connection, JSONObject headers) throws JSONException {
        connection.setRequestProperty("Content-Type", DEFAULT_CONTENT_TYPE);
        connection.setRequestProperty("Accept", DEFAULT_CONTENT_TYPE);

        if (headers != null) {
            Iterator<String> iterator = headers.keys();
            while (iterator.hasNext()) {
                String nextKey = iterator.next();
                connection.setRequestProperty(nextKey, headers.get(nextKey).toString());
            }
        }

        return connection;
    }

    private HttpsURLConnection prepareRequest(HttpRequest request)
            throws IOException, KeyStoreException, CertificateException, KeyManagementException, NoSuchAlgorithmException, JSONException {
        HttpsURLConnection connection;
        URL url = new URL(request.endpoint);
        String method = request.method.toUpperCase();

        connection = (HttpsURLConnection) url.openConnection();
        if (request.certFilenames != null) {
            connection.setSSLSocketFactory(KeyPinStoreUtil.getInstance(request.certFilenames).getContext().getSocketFactory());
        }
        connection.setRequestMethod(method);

        connection = prepareRequestHeaders(connection, request.headers);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        connection.setAllowUserInteraction(false);
        connection.setConnectTimeout(request.timeout);
        connection.setReadTimeout(request.timeout);

        if (request.body != null && (method.equals("POST") || method.equals("PUT") || method.equals("DELETE"))) {
            // Set the content length of the body.

            JSONObject root = new JSONObject(request.body);

            JSONArray array= root.getJSONArray("_parts");
            Map<String, String> params = new HashMap<>();
            for(int i=0;i<array.length();i++)
            {
                JSONArray object= array.getJSONArray(i);
                params.put(object.getString(0), object.getString(1));
            }
            StringBuilder postData = new StringBuilder();

            for (Map.Entry<String, String> param : params.entrySet())
            {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");


            connection.setRequestProperty("Content-length", postDataBytes.length + "");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // Send the JSON as body of the request.
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(postDataBytes);
            outputStream.close();
        }

        return connection;
    }


    private InputStream prepareResponseStream(HttpsURLConnection connection) throws IOException {
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            return connection.getErrorStream();
        }
    }

    public HttpResponse sendHttpRequest(HttpRequest request)
            throws IOException, KeyStoreException, CertificateException, KeyManagementException, NoSuchAlgorithmException, JSONException {
        InputStream responseStream = null;
        HttpResponse response = new HttpResponse();
        HttpsURLConnection connection;
        int status;
        String statusText;

        try {
            connection = prepareRequest(request);

            connection.connect();

            status = connection.getResponseCode();
            statusText = connection.getResponseMessage();
            responseStream = prepareResponseStream(connection);

            response.statusCode = status;
            response.statusText = statusText;
            response.bodyString = getResponseBody(responseStream);
            response.headers = getResponseHeaders(connection);

            return response;
        } finally {
            if (responseStream != null) {
                responseStream.close();
            }
        }
    }
}
