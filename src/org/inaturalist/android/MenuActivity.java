package org.inaturalist.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.inaturalist.android.R.string;

import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class MenuActivity extends ListActivity {
    public static String TAG = "MenuActivity";
    private List<Map> MENU_ITEMS;
//    static final List<Map> MENU_ITEMS;
    static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
    private Button mAddObservationButton;
    private Button mTakePictureButton;
    private Uri mPhotoUri;
    private INaturalistApp app;
    private ActivityHelper mHelper;
    public string asta; 
	
    /*static {
        MENU_ITEMS = new ArrayList<Map>();
        Map<String,String> map;
        
        map = new HashMap<String,String>();
        map.put("title", "Observations");
        map.put("description", "Observations list");
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", "Map");
        map.put("description", "Observations map");
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", "Updates feed");
        map.put("description", "Updates from people you follow on iNat");
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title",  asta);
        map.put("description", "Sign in/out");
        MENU_ITEMS.add(map);
    }
    */
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);
        
        ///----------------------
    	Resources res = getResources();
    	//"Settings"
    	 Map<String,String> map;
    	MENU_ITEMS = new ArrayList<Map>();
        //Map<String,String> map;
        
        map = new HashMap<String,String>();
        map.put("title", res.getString(R.string.Observations_res));
        map.put("description", res.getString(R.string.Observations_list_res));
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", res.getString(R.string.Map_res));
        map.put("description", res.getString(R.string.Observations_map_res));
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title", res.getString(R.string.Updates_feed_res));
        map.put("description",  res.getString(R.string.Updates_feed_res));
        MENU_ITEMS.add(map);
        
        map = new HashMap<String,String>();
        map.put("title",  res.getString(R.string.Settings_res));
        map.put("description", res.getString(R.string.Sign_in_out_res));
        MENU_ITEMS.add(map);
        ///----------------------
        
        
        
        SimpleAdapter adapter = new SimpleAdapter(this, 
                (List<? extends Map<String, ?>>) MENU_ITEMS, 
                R.layout.menu_item,
                new String[] {"title"},
                new int[] {R.id.title});
        ListView lv = getListView();
        LinearLayout header = (LinearLayout) getLayoutInflater().inflate(R.layout.menu_header, lv, false);
        lv.addHeaderView(header, null, false);
        setListAdapter(adapter);
        
        if  (savedInstanceState != null) {
            String photoUri = savedInstanceState.getString("mFileUri");
            if (photoUri != null) {mPhotoUri = Uri.parse(photoUri);}
        }
        
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }
        if (mHelper == null) { mHelper = new ActivityHelper(this);}
        
        mAddObservationButton = (Button) findViewById(R.id.add_observation);
        mTakePictureButton = (Button) findViewById(R.id.take_picture);
        
        mAddObservationButton.setOnClickListener(new View.OnClickListener() {
           // @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI));
            }
        });
        
        mTakePictureButton.setOnClickListener(new View.OnClickListener() {           
           // @Override
            public void onClick(View v) {
                mPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mPhotoUri);
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (app == null) { app = (INaturalistApp) getApplicationContext(); }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mHelper.stopLoading();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mPhotoUri != null) { outState.putString("mFileUri", mPhotoUri.toString()); }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                mHelper.loading("Processing...");
                Intent intent = new Intent(Intent.ACTION_INSERT, ObservationPhoto.CONTENT_URI, this, ObservationEditor.class);
                intent.putExtra("photoUri", mPhotoUri);
                startActivity(intent);
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
                getContentResolver().delete(mPhotoUri, null, null);
            } else {
                // Image capture failed, advise user
                Toast.makeText(this, "Blast, something went wrong:\n" + mPhotoUri, Toast.LENGTH_LONG).show();
                Log.e(TAG, "camera bailed, requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data.getData());
                getContentResolver().delete(mPhotoUri, null, null);
            }
            mPhotoUri = null; // don't let this hang around
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	Resources res = getResources();
    	Map<String,String> item = (Map<String,String>) l.getItemAtPosition(position);
        String title = item.get("title");
        if (title.equals(res.getString(R.string.Observations_res))) {
            startActivity(new Intent(this, ObservationListActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(res.getString(R.string.Map_res))) {
            startActivity(new Intent(this, INaturalistMapActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(res.getString(R.string.Updates_feed_res))) {
            startActivity(new Intent(this, WebActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (title.equals(res.getString(R.string.Settings_res))) {
            startActivity(new Intent(this, INaturalistPrefsActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }
}
