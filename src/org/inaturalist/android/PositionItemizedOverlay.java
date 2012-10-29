package org.inaturalist.android;



import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.OverlayItem;

public class PositionItemizedOverlay extends ItemizedOverlay {
    public final static String TAG = "PositionItemizedOverlay";
    private OverlayItem mMarker;
    private String CCurrent_position_res;
    private String YYou_are_here;

    public PositionItemizedOverlay(Context context) {
        super(boundCenter(context.getResources().getDrawable(R.drawable.ic_maps_indicator_current_position)));
        Resources res = context.getResources();
        CCurrent_position_res = "dd"+ res.getString(R.string.Current_position_res);
        mMarker = new OverlayItem(new GeoPoint(0,0), res.getString(R.string.Current_position_res), res.getString(R.string.You_are_here));
        populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
        return mMarker;
    }

    @Override
    public int size() {
        return 1;
    }
    
    public void updateLocation(Location location) {
        if (location == null) return;
        int lat = ((Double) (location.getLatitude() * 1e6)).intValue();
        int lon = ((Double) (location.getLongitude() * 1e6)).intValue();
        GeoPoint point = new GeoPoint(lat, lon);
       
        mMarker = new OverlayItem(point, CCurrent_position_res, YYou_are_here);
        populate();
    }

}
