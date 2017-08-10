package com.roadbump;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.roadbump.util.Constants;
import com.roadbump.util.Utils;

/**
 * Created by harshikesh.kumar on 21/09/16.
 */
public class CustomMapFragment extends SupportMapFragment {

  private GoogleMap mGoogleMap;

  private GoogleApiClient mGoogleApiClient;

  public OnMapReadyCallback mMapReadyListener;
  private Activity mContext;

  public CustomMapFragment() {

  }

  public static CustomMapFragment newInstance() {
    //CameraPosition newCameraPosition =
    //    new CameraPosition.Builder().target(latLng).zoom(zoomLevel).build();

    //GoogleMapOptions options = new GoogleMapOptions();
    //options.camera(newCameraPosition);
    CustomMapFragment fragment = new CustomMapFragment();
    return fragment;
  }

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);
    mContext = activity;
  }

  public static class Builder {
    private OnMapReadyCallback mapReadyListener;

    public Builder() {
    }

    public Builder setMapReadyListener(OnMapReadyCallback onMapReadyCallback) {
      mapReadyListener = onMapReadyCallback;
      return this;
    }

    public CustomMapFragment build() {
      CustomMapFragment fragment = new CustomMapFragment();
      fragment.mMapReadyListener = mapReadyListener;
      return fragment;
    }
  }

  @Override public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
  }

  @Override
  public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
    View mapView = super.onCreateView(layoutInflater, viewGroup, bundle);
    init();

    // Get the button view
    View locationButton =
        ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(
            Integer.parseInt("2"));

    // and next place it, for exemple, on bottom right (as Google Maps app)
    RelativeLayout.LayoutParams rlp =
        (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
    // position on right bottom
    rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
    rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
    rlp.setMargins(0, 0, 30, 30);
    return mapView;
  }

  public void init() {
    getMapAsync(new OnMapReadyCallback() {

      public static final int REQ_CODE = 101;

      @Override public void onMapReady(GoogleMap googleMap) {
        if (googleMap != null && mContext != null) {
          mGoogleMap = googleMap;
          mGoogleMap.setIndoorEnabled(false);
          mGoogleMap.setMaxZoomPreference(Constants.MAP_MAX_ZOOM);

          if (!Utils.checkMapPermission(mContext)) {
            ActivityCompat.requestPermissions(mContext,
                new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION, }, REQ_CODE);
          }
          mGoogleMap.setMyLocationEnabled(true);
          UiSettings mapSettings = mGoogleMap.getUiSettings();
          mapSettings.setZoomControlsEnabled(false);
          mapSettings.setCompassEnabled(false);
          mapSettings.setRotateGesturesEnabled(false);
          mapSettings.setTiltGesturesEnabled(false);
          mapSettings.setMyLocationButtonEnabled(true);

          if (mMapReadyListener != null) {
            mMapReadyListener.onMapReady(googleMap);
          }
        }
      }
    });
  }

  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 101) {

    }
  }
}
