package org.inaturalist.android;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

public class INaturalistService extends IntentService {
    public static String TAG = "INaturalistService";
    public static String HOST = "http://www.inaturalist.org";
    public static String MEDIA_HOST = "http://www.inaturalist.org";
    public static String USER_AGENT = "iNaturalist/"+INaturalistApp.VERSION + " (" +
            "Android " + System.getProperty("os.version") + " " + android.os.Build.VERSION.INCREMENTAL + "; " +
            "SDK " + android.os.Build.VERSION.SDK + "; " +
            android.os.Build.DEVICE + " " +
            android.os.Build.MODEL + " " + 
            android.os.Build.PRODUCT + ")";
    public static String ACTION_PASSIVE_SYNC = "passive_sync";
    public static String ACTION_SYNC = "sync";
    public static String ACTION_NEARBY = "nearby";
    public static Integer SYNC_OBSERVATIONS_NOTIFICATION = 1;
    public static Integer SYNC_PHOTOS_NOTIFICATION = 2;
    public static Integer AUTH_NOTIFICATION = 3;
    private String mLogin;
    private String mCredentials;
    private SharedPreferences mPreferences;
    private boolean mPassive;
    private INaturalistApp app;

    public INaturalistService() {
        super("INaturalistService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mPreferences = getSharedPreferences("iNaturalistPreferences", MODE_PRIVATE);
        mLogin = mPreferences.getString("username", null);
        mCredentials = mPreferences.getString("credentials", null);
        app = (INaturalistApp) getApplicationContext();
        String action = intent.getAction();
        mPassive = action.equals(ACTION_PASSIVE_SYNC);

        try {
            if (action.equals(ACTION_NEARBY)) {
                getNearbyObservations(intent);
            } else {
                syncObservations();
            }
        } catch (AuthenticationException e) {
            if (!mPassive) {
                requestCredentials();
            }
        }
    }
    
    private void syncObservations() throws AuthenticationException {
        postObservations();
        postPhotos();
//        getUserObservations();
//        Toast.makeText(getApplicationContext(), "Observations synced", Toast.LENGTH_SHORT);
    }

    private void postObservations() throws AuthenticationException {
        Observation observation;
        Resources res = getResources();
        // query observations where _updated_at > updated_at
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "_updated_at > _synced_at AND _synced_at IS NOT NULL AND user_login = '"+mLogin+"'", 
                null, 
                Observation.DEFAULT_SORT_ORDER);
        int updatedCount = c.getCount();
        app.sweepingNotify(SYNC_OBSERVATIONS_NOTIFICATION,
        		res.getString(R.string.Syncing_observations_res),
        		res.getString(R.string.Syncing_res) + c.getCount() + " "+ res.getString(R.string.observations_res),
        		res.getString(R.string.Syncing_res));
        // for each observation PUT to /observations/:id
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            app.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
            		res.getString(R.string.Updating_observations_res), 
            		res.getString(R.string.Updating_res) + " " + (c.getPosition() + 1) + " " + res.getString(R.string.of_res) + " " + c.getCount() + " "+res.getString(R.string.existing_observations_res),
            		res.getString(R.string.Syncing_res));
            observation = new Observation(c);
            handleObservationResponse(
                    observation,
                    put(HOST + "/observations/" + observation.id + ".json", paramsForObservation(observation))
            );
            c.moveToNext();
        }
        c.close();

        // query observations where _synced_at IS NULL
        c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "id IS NULL", null, Observation.DEFAULT_SORT_ORDER);
        int createdCount = c.getCount();
        // for each observation POST to /observations/
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            app.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
            		res.getString(R.string.Posting_new_observations_res), 
            		res.getString(R.string.Posting_res)+" " + (c.getPosition() + 1)+ " " + res.getString(R.string.of_res) + " "  + c.getCount() + " "+res.getString(R.string.new_observations_res), 
            		res.getString(R.string.Syncing_res));
            observation = new Observation(c);
            handleObservationResponse(
                    observation,
                    post(HOST + "/observations.json", paramsForObservation(observation))
            );
            c.moveToNext();
        }
        c.close();
        
        app.notify(SYNC_OBSERVATIONS_NOTIFICATION, 
        		res.getString(R.string.Observation_sync_complete_res), 
                createdCount + " new, " + updatedCount + " updated.",
                res.getString(R.string.Sync_complete_res));
    }
    
    private void postPhotos() throws AuthenticationException {
        ObservationPhoto op;
        int createdCount = 0;
        Resources res = getResources();
        
        // query observations where _updated_at > updated_at
        Cursor c = getContentResolver().query(ObservationPhoto.CONTENT_URI, 
                ObservationPhoto.PROJECTION, 
                "_synced_at IS NULL", null, ObservationPhoto.DEFAULT_SORT_ORDER);
        if (c.getCount() == 0) {
            return;
        }
            
        // for each observation PUT to /observations/:id
        ContentValues cv;
        c.moveToFirst();
        while (c.isAfterLast() == false) {
            app.notify(SYNC_PHOTOS_NOTIFICATION, 
            		res.getString(R.string.Posting_new_photos_res), 
            		res.getString(R.string.Posting_res) + " " + (c.getPosition() + 1)+ " " + res.getString(R.string.of_res) + " "  + c.getCount() + " " + res.getString(R.string.new_photos_res),
            		res.getString(R.string.Syncing_res));
            op = new ObservationPhoto(c);
            ArrayList <NameValuePair> params = op.getParams();
            // http://stackoverflow.com/questions/2935946/sending-images-using-http-post
            Uri photoUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, op._photo_id); 
            Cursor pc = getContentResolver().query(photoUri, 
                    new String[] {MediaStore.MediaColumns._ID, MediaStore.Images.Media.DATA}, 
                    null, 
                    null, 
                    MediaStore.Images.Media.DEFAULT_SORT_ORDER);
            
            if (pc.getCount() == 0) {
                // photo has been deleted, destroy the ObservationPhoto
                getContentResolver().delete(op.getUri(), null, null);
                continue;
            } else {
                pc.moveToFirst();
            }
            
            String imgFilePath = pc.getString(pc.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
            params.add(new BasicNameValuePair("file", imgFilePath));
            
            // TODO LATER resize the image for upload, maybe a 1024px jpg
            JSONArray response = post(MEDIA_HOST + "/observation_photos.json", params);
            try {
                if (response == null || response.length() != 1) {
                    break;
                }
                JSONObject json = response.getJSONObject(0);
                BetterJSONObject j = new BetterJSONObject(json);
                ObservationPhoto jsonObservationPhoto = new ObservationPhoto(j);
                op.merge(jsonObservationPhoto);
                cv = op.getContentValues();
                cv.put(ObservationPhoto._SYNCED_AT, System.currentTimeMillis());
                getContentResolver().update(op.getUri(), cv, null, null);
                createdCount += 1;
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.toString());
            }
            c.moveToNext();
        }
        c.close();
        app.notify(SYNC_PHOTOS_NOTIFICATION, 
        		res.getString(R.string.Photo_sync_complete_res), 
        		res.getString(R.string.Posted_res)+" " + createdCount + " " + res.getString(R.string.new_photos_res),
        		res.getString(R.string.Sync_complete_res));
    }

    private void getUserObservations() throws AuthenticationException {
        if (ensureCredentials() == false) {
            return;
        }
        JSONArray json = get(HOST + "/observations/" + mLogin + ".json");
        if (json == null || json.length() == 0) { return; }
        syncJson(json);
    }
    
    private void getNearbyObservations(Intent intent) throws AuthenticationException {
        Bundle extras = intent.getExtras();
        Double minx = extras.getDouble("minx");
        Double maxx = extras.getDouble("maxx");
        Double miny = extras.getDouble("miny");
        Double maxy = extras.getDouble("maxy");
        String url = HOST + "/observations.json?";
        url += "swlat="+miny;
        url += "&nelat="+maxy;
        url += "&swlng="+minx;
        url += "&nelng="+maxx;
        JSONArray json = get(url);
        Intent reply = new Intent(ACTION_NEARBY);
        reply.putExtra("minx", minx);
        reply.putExtra("maxx", maxx);
        reply.putExtra("miny", miny);
        reply.putExtra("maxy", maxy);
        if (json == null) {
        	Resources res = getResources();
        	reply.putExtra("error", res.getString(R.string.Couldn_t_connect_to_server_res));
        } else {
            syncJson(json);
        }
        sendBroadcast(reply);
    }

    private JSONArray put(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        params.add(new BasicNameValuePair("_method", "PUT"));
        return request(url, "put", params, true);
    }

    private JSONArray post(String url, ArrayList<NameValuePair> params) throws AuthenticationException {
        return request(url, "post", params, true);
    }

    private JSONArray get(String url) throws AuthenticationException {
        return get(url, false);
    }

    private JSONArray get(String url, boolean authenticated) throws AuthenticationException {
        return request(url, "get", null, authenticated);
    }

    private JSONArray request(String url, String method, ArrayList<NameValuePair> params, boolean authenticated) throws AuthenticationException {
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);

        HttpRequestBase request = method == "get" ? new HttpGet(url) : new HttpPost(url);

        // POST params
        if (params != null) {
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).getName().equalsIgnoreCase("image") || params.get(i).getName().equalsIgnoreCase("file")) {
                    // If the key equals to "image", we use FileBody to transfer the data
                    entity.addPart(params.get(i).getName(), new FileBody(new File (params.get(i).getValue())));
                } else {
                    // Normal string data
                    try {
                        entity.addPart(params.get(i).getName(), new StringBody(params.get(i).getValue()));
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "failed to add " + params.get(i).getName() + " to entity for a " + method + " request: " + e);
                    }
                }
            }
            ((HttpPost) request).setEntity(entity);
        }

        // auth
        if (authenticated) {
            ensureCredentials();
            request.setHeader("Authorization", "Basic "+ mCredentials);
        }

        try {
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            JSONArray json = null;
            switch (response.getStatusLine().getStatusCode()) {
            case HttpStatus.SC_OK:
                try {
                    json = new JSONArray(content);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to create JSONArray, JSONException: " + e.toString());
                    try {
                        JSONObject jo = new JSONObject(content);
                        json = new JSONArray();
                        json.put(jo);
                    } catch (JSONException e2) {
                        Log.e(TAG, "Failed to create JSONObject, JSONException: " + e2.toString());
                    }
                }
                return json;
            case HttpStatus.SC_UNAUTHORIZED:
                throw new AuthenticationException();
            case HttpStatus.SC_GONE:
                // TODO create notification that informs user some observations have been deleted on the server, 
                // click should take them to an activity that lets them decide whether to delete them locally 
                // or post them as new observations
            default:
                Log.e(TAG, response.getStatusLine().toString());
            }
        }
        catch (IOException e) {
            request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        }
        return null;
    }

    private boolean ensureCredentials() throws AuthenticationException {
        if (mCredentials != null) { return true; }

        // request login unless passive
        if (!mPassive) {
            throw new AuthenticationException();
        }
        stopSelf();
        return false;
    }

    private void requestCredentials() {
        stopSelf();
        Resources res = getResources();
        Intent intent = new Intent(
                mLogin == null ? "signin" : INaturalistPrefsActivity.REAUTHENTICATE_ACTION, 
                null, getBaseContext(), INaturalistPrefsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.sweepingNotify(AUTH_NOTIFICATION,  res.getString(R.string.Please_sign_in),  res.getString(R.string.Please_sign_in_to_your_iNaturalist_account_or_sign_up_for_a_new_one_res), null, intent);
    }

    public static boolean verifyCredentials(String credentials) {
        DefaultHttpClient client = new DefaultHttpClient();
        client.getParams().setParameter(CoreProtocolPNames.USER_AGENT, USER_AGENT);
        String url = HOST + "/observations/new.json";
        HttpRequestBase request = new HttpGet(url);
        request.setHeader("Authorization", "Basic "+credentials);
        request.setHeader("Content-Type", "application/json");

        try {
            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return true;
            } else {
                Log.e(TAG, "Authentication failed: " + content);
                return false;
            }
        }
        catch (IOException e) {
            request.abort();
            Log.w(TAG, "Error for URL " + url, e);
        }
        return false;
    }

    public static boolean verifyCredentials(String username, String password) {
        String credentials = Base64.encodeToString(
                (username + ":" + password).getBytes(), Base64.URL_SAFE|Base64.NO_WRAP
                );
        return verifyCredentials(credentials);
    }

    public void syncJson(JSONArray json) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        ArrayList<Integer> existingIds = new ArrayList<Integer>();
        ArrayList<Integer> newIds = new ArrayList<Integer>();
        HashMap<Integer,Observation> jsonObservationsById = new HashMap<Integer,Observation>();
        Observation observation;
        Observation jsonObservation;
        // find obs with existing ids
		Integer strutjoinint;
		String strutjoin;
		String virgula;
        virgula = "";
        strutjoin ="";
        
        BetterJSONObject o;
        for (int i = 0; i < json.length(); i++) {
            try {
                o = new BetterJSONObject(json.getJSONObject(i));
                ids.add(o.getInt("id"));
                strutjoinint = o.getInt("id");
                strutjoin += virgula + strutjoinint.toString();
                virgula = ",";                  
                jsonObservationsById.put(o.getInt("id"), new Observation(o));
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.toString());
            }
        }
  
        
        //String joinedIds = StringUtils.join(ids, ",");
        String joinedIds = strutjoin;
        // TODO why doesn't selectionArgs work for id IN (?)
        Cursor c = getContentResolver().query(Observation.CONTENT_URI, 
                Observation.PROJECTION, 
                "id IN ("+joinedIds+")", null, Observation.DEFAULT_SORT_ORDER);

        // update existing
        c.moveToFirst();
        ContentValues cv;
        while (c.isAfterLast() == false) {
            observation = new Observation(c);
            jsonObservation = jsonObservationsById.get(observation.id);
            observation.merge(jsonObservation); 
            cv = observation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(observation.getUri(), cv, null, null);
            existingIds.add(observation.id);
            c.moveToNext();
        }
        c.close();

        // insert new
        newIds = (ArrayList<Integer>) CollectionUtils.subtract(ids, existingIds);
        Collections.sort(newIds);
        for (int i = 0; i < newIds.size(); i++) {			
            jsonObservation = jsonObservationsById.get(newIds.get(i));
            cv = jsonObservation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().insert(Observation.CONTENT_URI, cv);
        }
    }
    
    private ArrayList<NameValuePair> paramsForObservation(Observation observation) {
        ArrayList<NameValuePair> params = observation.getParams();
        params.add(new BasicNameValuePair("ignore_photos", "true"));
        return params;
    }
    
    private void handleObservationResponse(Observation observation, JSONArray response) {
        try {
            if (response == null || response.length() != 1) {
                return;
            }
            JSONObject json = response.getJSONObject(0);
            BetterJSONObject o = new BetterJSONObject(json);
            Observation jsonObservation = new Observation(o);
            observation.merge(jsonObservation);
            ContentValues cv = observation.getContentValues();
            cv.put(Observation._SYNCED_AT, System.currentTimeMillis());
            getContentResolver().update(observation.getUri(), cv, null, null);
        } catch (JSONException e) {
            // Log.d(TAG, "JSONException: " + e.toString());
        }
    }

    private class AuthenticationException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
