package xyz.syzygylabs.urbanroute;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Animatable2;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.flipboard.bottomsheet.BottomSheetLayout;
import com.flipboard.bottomsheet.OnSheetDismissedListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionButton;
import com.oguzdev.circularfloatingactionmenu.library.FloatingActionMenu;
import com.oguzdev.circularfloatingactionmenu.library.SubActionButton;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import me.toptas.fancyshowcase.FancyShowCaseQueue;
import me.toptas.fancyshowcase.FancyShowCaseView;
import me.toptas.fancyshowcase.OnViewInflateListener;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private GoogleMap map;
    private LinkedHashMap<String,MarkerOptions> markers = new LinkedHashMap<>();
    private SupportMapFragment mapFragment;
    private FusedLocationProviderClient mFusedLocationClient;
    private GeoQuery geoQuery;
    private GeoFire geoFire;
    private DatabaseReference ref;

    private Location lastLocation;
    private double scanRange = 1.5; //In km
    private BottomSheetLayout bottomSheet;
    private FloatingActionButton actionButton;
    private FloatingActionMenu actionMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        bottomSheet = (BottomSheetLayout) findViewById(R.id.bottomsheet);

        //Database
        ref = FirebaseDatabase.getInstance().getReference("geodata");
        geoFire = new GeoFire(ref);

        //Map and Location
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        0);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        0);
            }
        }else{
            mapFragment.getMapAsync(this);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapFragment.getMapAsync(this);

                } else {
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.map),
                            "This app does not function without location.", Snackbar.LENGTH_INDEFINITE);
                    snackbar.show();
                }
                return;
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        LatLng me = new LatLng(0,0);
        //Set-Up Map
        map = googleMap;
        map.setOnMarkerClickListener(this);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.setMyLocationEnabled(true);

        mFusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),location.getLongitude()), 18.0f));
                        updateMap(location);
                        lastLocation = location;

                        map.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                            @Override
                            public void onMyLocationChange(final Location location) {
                                actionButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        if(actionMenu.isOpen()){
                                            actionMenu.close(true);
                                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                                                    location.getLongitude()), 18.0f));
                                        }else{
                                            actionMenu.open(true);
                                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                                                    location.getLongitude()), 20.0f));
                                        }
                                    }
                                });
                                if (lastLocation.distanceTo(location) > 100){
                                    updateMap(location);
                                    lastLocation=location;
                                }
                            }
                        });
                    }
                }
        });

        setupUI();

    }
    void updateMap(final Location location){
        geoQuery = geoFire.queryAtLocation(new GeoLocation(location.getLatitude(), location.getLongitude()), scanRange);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                updateMarkers(key,location);
            }

            @Override
            public void onKeyExited(String key) {
                markers.remove(key);
                map.clear();
                for (int i=0; i< markers.size();i++){
                    map.addMarker((MarkerOptions) markers.values().toArray()[i]);
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                updateMarkers(key,location);
            }

            @Override
            public void onGeoQueryReady() {
                final CardView topCard = (CardView) findViewById(R.id.topCard);
                TextView reportNumber = (TextView) findViewById(R.id.inc_number);
                reportNumber.setText(markers.size() + " Incidents Nearby");
                topCard.setY(-250);
                topCard.setVisibility(View.VISIBLE);
                topCard.animate()
                        .translationY(0)
                        .setDuration(300)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                topCard.animate()
                                        .setStartDelay(8000)
                                        .setDuration(300)
                                        .translationY(-250);
                            }
                        });
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

        map.clear();
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker arg0) {
            }

            @SuppressWarnings("unchecked")
            @Override
            public void onMarkerDragEnd(Marker arg0) {
                geoFire.setLocation(arg0.getTitle(), new GeoLocation(arg0.getPosition().latitude, arg0.getPosition().longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
                arg0.remove();
            }

            @Override
            public void onMarkerDrag(Marker arg0) {
            }

        });

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        bottomSheet.showWithSheetView(LayoutInflater.from(this).inflate(R.layout.incident_sheet, bottomSheet, false));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(marker.getPosition().latitude,marker.getPosition().longitude),
                ((map.getCameraPosition().zoom < 17) ? 19.0f : map.getCameraPosition().zoom)));
        bottomSheet.setShouldDimContentView(false);
        actionButton.setVisibility(View.GONE);

        TextView title = (TextView) bottomSheet.getSheetView().findViewById(R.id.title);
        TextView distance = (TextView) bottomSheet.getSheetView().findViewById(R.id.distance);
        TextView street = (TextView) bottomSheet.getSheetView().findViewById(R.id.street);

        Location location = new Location("");
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this, Locale.getDefault());


        try {
            addresses = geocoder.getFromLocation(marker.getPosition().latitude, marker.getPosition().longitude, 1);
        } catch (IOException e) {
            e.printStackTrace();
            addresses = null;
        }

        location.setLatitude(marker.getPosition().latitude);
        location.setLongitude(marker.getPosition().longitude);
        if (marker.getTitle().substring(0,1).equals("W")){
            title.setText("Water Hazard");
        }else if(marker.getTitle().substring(0,1).equals("G")){
            title.setText("Group");
        }else if(marker.getTitle().substring(0,1).equals("P")){
            title.setText("Suspicious Person");
        }else if(marker.getTitle().substring(0,1).equals("H")){
            title.setText("Homeless");
        }else if(marker.getTitle().substring(0,1).equals("B")){
            title.setText("Panhandler");
        }
        else{
            title.setText("Unknown Hazard");
        }

        distance.setText((int )lastLocation.distanceTo(location) + " m away");
        if (addresses.get(0).getAddressLine(0).length()>30){
            street.setText(addresses.get(0).getAddressLine(0).substring(0,30) +"... ");
        }else{
            street.setText(addresses.get(0).getAddressLine(0)+" ");
        }
        bottomSheet.addOnSheetDismissedListener(new OnSheetDismissedListener() {
            @Override
            public void onDismissed(BottomSheetLayout bottomSheetLayout) {
                actionButton.setVisibility(View.VISIBLE);

            }
        });
        return true;
    }

    private void updateMarkers(String key, GeoLocation location){
        if (key.substring(0,1).equals("P")){
            markers.put(key,new MarkerOptions()
                    .position(new LatLng(location.latitude,location.longitude))
                    .draggable(true)
                    .title(key)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_perm_identity_black_24dp)));
        }else if (key.substring(0,1).equals("W")){
            markers.put(key,new MarkerOptions()
                    .position(new LatLng(location.latitude,location.longitude))
                    .draggable(true)
                    .title(key)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_pool_black_24dp)));
        }else if(key.substring(0,1).equals("G")){
            markers.put(key,new MarkerOptions()
                    .position(new LatLng(location.latitude,location.longitude))
                    .draggable(true)
                    .title(key)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_group_black_24dp)));
        }else if(key.substring(0,1).equals("H")){
            markers.put(key,new MarkerOptions()
                    .position(new LatLng(location.latitude,location.longitude))
                    .draggable(true)
                    .title(key)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.homeless)));
        }else if(key.substring(0,1).equals("B")){
            markers.put(key,new MarkerOptions()
                    .position(new LatLng(location.latitude,location.longitude))
                    .draggable(true)
                    .title(key)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.coins_black)));
        }
        map.clear();
        for (int i=0; i< markers.size();i++){
            map.addMarker((MarkerOptions) markers.values().toArray()[i]);
        }
    }

    private void setupUI(){
        ImageView icon = new ImageView(this); // Create an icon
        icon.setImageDrawable(getResources().getDrawable(R.drawable.ic_report_white_48dp));

        actionButton = new FloatingActionButton.Builder(this)
                .setContentView(icon)
                .setTheme(FloatingActionButton.THEME_DARK)
                .build();

        SubActionButton.Builder itemBuilder = new SubActionButton.Builder(this);

        ImageView itemIcon = new ImageView(this);
        itemIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_perm_identity_white_36dp));

        ImageView itemIcon2 = new ImageView(this);
        itemIcon2.setImageDrawable(getResources().getDrawable(R.drawable.ic_pool_white_48dp));

        ImageView itemIcon3 = new ImageView(this);
        itemIcon3.setImageDrawable(getResources().getDrawable(R.drawable.ic_group_white_48dp));

        ImageView itemIcon4 = new ImageView(this);
        itemIcon4.setImageDrawable(getResources().getDrawable(R.drawable.homeless_white));

        ImageView itemIcon5 = new ImageView(this);
        itemIcon5.setImageDrawable(getResources().getDrawable(R.drawable.coins_white));

        SubActionButton button1 = itemBuilder.setContentView(itemIcon).setTheme(SubActionButton.THEME_DARK)
                .setLayoutParams(new FloatingActionButton.LayoutParams(150,150)).build();

        SubActionButton button2 = itemBuilder.setContentView(itemIcon2).setTheme(SubActionButton.THEME_DARK)
                .setLayoutParams(new FloatingActionButton.LayoutParams(150,150)).build();

        SubActionButton button3 = itemBuilder.setContentView(itemIcon3).setTheme(SubActionButton.THEME_DARK)
                .setLayoutParams(new FloatingActionButton.LayoutParams(150,150)).build();

        SubActionButton button4 = itemBuilder.setContentView(itemIcon4).setTheme(SubActionButton.THEME_DARK)
                .setLayoutParams(new FloatingActionButton.LayoutParams(150,150)).build();

        SubActionButton button5 = itemBuilder.setContentView(itemIcon5).setTheme(SubActionButton.THEME_DARK)
                .setLayoutParams(new FloatingActionButton.LayoutParams(150,150)).build();

        actionMenu = new FloatingActionMenu.Builder(this)
                .addSubActionView(button1)
                .addSubActionView(button2)
                .addSubActionView(button3)
                .addSubActionView(button4)
                .addSubActionView(button5)
                .attachTo(actionButton)
                .build();


        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionMenu.close(true);
                geoFire.setLocation("P" + map.getCameraPosition().target.hashCode(), new GeoLocation(map.getCameraPosition()
                        .target.latitude, map.getCameraPosition().target.longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionMenu.close(true);
                geoFire.setLocation("W" + map.getCameraPosition().target.hashCode(), new GeoLocation(map.getCameraPosition()
                        .target.latitude, map.getCameraPosition().target.longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
            }
        });

        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionMenu.close(true);
                geoFire.setLocation("G" + map.getCameraPosition().target.hashCode(), new GeoLocation(map.getCameraPosition()
                        .target.latitude, map.getCameraPosition().target.longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
            }
        });
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionMenu.close(true);
                geoFire.setLocation("H" + map.getCameraPosition().target.hashCode(), new GeoLocation(map.getCameraPosition()
                        .target.latitude, map.getCameraPosition().target.longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
            }
        });
        button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                actionMenu.close(true);
                geoFire.setLocation("B" + map.getCameraPosition().target.hashCode(), new GeoLocation(map.getCameraPosition()
                        .target.latitude, map.getCameraPosition().target.longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                    }
                });
            }
        });

    }
}
