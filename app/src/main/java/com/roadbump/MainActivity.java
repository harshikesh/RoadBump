package com.roadbump;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.roadbump.database.DBHelper;
import com.roadbump.service.LocationService;
import com.roadbump.util.Utils;

public class MainActivity extends AppCompatActivity
    implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {

  private static final String TAG = MainActivity.class.getSimpleName();
  private static final float INIT_ZOOM = 14;


  private CustomMapFragment mMapFragment;
  private DBHelper dbHelper;
  private GoogleMap mGoogleMap;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            .setAction("Action", null)
            .show();
      }
    });

    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    ActionBarDrawerToggle toggle =
        new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open,
            R.string.navigation_drawer_close);
    drawer.setDrawerListener(toggle);
    toggle.syncState();

    NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
    navigationView.setNavigationItemSelectedListener(this);

    mMapFragment = new CustomMapFragment.Builder().setMapReadyListener(this).build();
    initMap();
    dbHelper = new DBHelper(this);
    if(!isMyServiceRunning(LocationService.class)) {
      Intent in = new Intent(MainActivity.this, LocationService.class);
      startService(in);
    }
  }

  private boolean isMyServiceRunning(Class<?> serviceClass) {
    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.getName().equals(service.service.getClassName())) {
        return true;
      }
    }
    return false;
  }

  private void initMap() {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.map_container, mMapFragment)
        .commit();
  }

  @Override public void onMapReady(GoogleMap googleMap) {
    Log.d(TAG, "OnMapReady");
    mGoogleMap = googleMap;
    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    Criteria criteria = new Criteria();

    Utils.checkMapPermission(getApplicationContext());
    Location location =
        locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
    if (location != null) {
      googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
          new LatLng(location.getLatitude(), location.getLongitude()), INIT_ZOOM));
    }
  }

  @Override public void onBackPressed() {
    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    if (drawer.isDrawerOpen(GravityCompat.START)) {
      drawer.closeDrawer(GravityCompat.START);
    } else {
      super.onBackPressed();
    }
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @SuppressWarnings("StatementWithEmptyBody") @Override
  public boolean onNavigationItemSelected(MenuItem item) {
    // Handle navigation view item clicks here.
    int id = item.getItemId();

    if (id == R.id.nav_logout) {
      // Handle the camera action
    } else if (id == R.id.nav_settings) {

    } else if (id == R.id.nav_share) {

    } else if (id == R.id.nav_send) {

    }
    else if (id == R.id.nav_login) {

    }

    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    drawer.closeDrawer(GravityCompat.START);
    return true;
  }
}
