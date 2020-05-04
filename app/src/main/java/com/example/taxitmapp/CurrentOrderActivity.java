package com.example.taxitmapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class CurrentOrderActivity extends AppCompatActivity {
    private MapView mapView;
    private String server, apiKey, order_id, params, url, hashApiKey, abortedStateId;
    private SharedPreferences.Editor editor;

    private ProgressBar progressBar;
    private TextView infoTextView;

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String MAPKIT_API_KEY = "331a7ae7-9f07-4053-8010-41cfb9f289c0";
        MapKitFactory.setApiKey(MAPKIT_API_KEY);
        MapKitFactory.initialize(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_order);

        SharedPreferences sharedPreferences = this.getSharedPreferences("mySharedPrefereces", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        order_id = sharedPreferences.getString("ORDER_ID", "");

        progressBar = findViewById(R.id.progressBar);
        infoTextView = findViewById(R.id.infoTextView);
        ImageButton cancelButton = findViewById(R.id.cancelButton);

        //Point point = new Point(Double.parseDouble(sharedPreferences.getString("source_address_lat", "")), Double.parseDouble(sharedPreferences.getString("source_address_lon", "")));
        Point point = new Point(55.75370903771494, 37.61981338262558);
        mapView = (MapView)findViewById(R.id.mapview);
        mapView.getMap().move(
                new CameraPosition(point, 18.0f, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1),
                null);

        PlacemarkMapObject mark = mapView.getMap().getMapObjects().addPlacemark(point, ImageProvider.fromResource(this, R.drawable.gps_marker));

        SettingsServer settingsServer = new SettingsServer();
        server = settingsServer.getServer();
        apiKey = settingsServer.getApiKey();
        abortedStateId = settingsServer.getAbortedStateId();



        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                getInfoOrderId(server, apiKey, order_id);
            }
        }, 1, 5, TimeUnit.SECONDS);

    }

    @Override
    public void onBackPressed() { }

    private void getInfoOrderId(String server, String apiKey, String order_id) {
        params = "order_id=" + order_id;
        url = server + "get_order_state?" + params;
        Md5Hash md5Hash = new Md5Hash();
        hashApiKey = md5Hash.md5(params + apiKey);
        GetRequest getRequest = new GetRequest(this,url,params, hashApiKey);
        getRequest.getString(new GetRequest.VolleyCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onSuccess(String req, String jsonArray) throws JSONException {
                if (req.equals("OK")) {
                    JSONObject mainObject = new JSONObject(jsonArray);
                    if(mainObject.getString("state_kind").equals("new_order")
                            || (mainObject.getString("state_kind").equals("driver_assigned") & mainObject.getString("confirmed").equals("not_confirmed"))){
                        progressBar.setVisibility(View.VISIBLE);
                        infoTextView.setText("Идет поиск автомобиля...");
                    } else if (mainObject.getString("state_kind").equals("driver_assigned") & !mainObject.getString("confirmed").equals("not_confirmed")){
                        progressBar.setVisibility(View.GONE);
                        infoTextView.setText("К вам подъедет: \n" + "Автомобиль: " + mainObject.getString("car_mark")  + " " + mainObject.getString("car_model") + "\n" +
                                "Цвет: " + mainObject.getString("car_color") + "\n" + "Гос номер: " + mainObject.getString("car_number"));
                    } else if (mainObject.getString("state_kind").equals("car_at_place") || mainObject.getString("state_kind").equals("client_inside")){
                        progressBar.setVisibility(View.GONE);
                        infoTextView.setText("Вас ожидает: \n" + "Автомобиль: " + mainObject.getString("car_mark")  + " " + mainObject.getString("car_model") + "\n" +
                                "Цвет: " + mainObject.getString("car_color") + "\n" + "Гос номер: " + mainObject.getString("car_number"));
                        //ТУТ НАДО ПРЕДУМАТЬ КАК УВЕДОМЛЯТЬ О УСПЕШНОМ ИЛИ ОТМЕНЕНОМ ЗАКАЗЕ
                    } else if (mainObject.getString("state_kind").equals("finished")){
                        editor.remove("ORDER_ID");
                        editor.apply();
                        finish();
                    } else if (mainObject.getString("state_kind").equals("aborted")){
                        editor.remove("ORDER_ID");
                        editor.apply();
                        finish();
                    }
                } else {
                    editor.remove("ORDER_ID");
                    editor.apply();
                    startActivity(new Intent(CurrentOrderActivity.this, OrderActivity.class));
                }
            }
        });
        }

    @Override
    protected void onStop() {
        // Вызов onStop нужно передавать инстансам MapView и MapKit.
        mapView.onStop();
        MapKitFactory.getInstance().onStop();
        super.onStop();
    }

    @Override
    protected void onStart() {
        // Вызов onStart нужно передавать инстансам MapView и MapKit.
        super.onStart();
        MapKitFactory.getInstance().onStart();
        mapView.onStart();
    }

    public void abortedOrder(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle("Предупреждение !");
        builder.setMessage("Вы действительно хотите отказаться от заказа ?");
        builder.setPositiveButton("Да !", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                params = "order_id=" + order_id + "&new_state=" + abortedStateId;
                url = server + "change_order_state?" + params;
                Md5Hash md5Hash = new Md5Hash();
                hashApiKey = md5Hash.md5(params + apiKey);
                PostRequest postRequest = new PostRequest(CurrentOrderActivity.this,url,params, hashApiKey);
                postRequest.getString(new PostRequest.VolleyCallback() {
                    @Override
                    public void onSuccess(String req, String jsonArray) throws JSONException {
                        if (req.equals("OK")){
                            editor.remove("ORDER_ID");
                            editor.apply();
                            startActivity(new Intent(CurrentOrderActivity.this, OrderActivity.class));
                        } else {
                        }
                    }
                });

            }
        }).setNegativeButton("НЕТ !", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.create().show();
    }



}