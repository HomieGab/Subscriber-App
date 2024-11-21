package com.example.subscriberapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.IMqttToken
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.content.Context

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private val mqttBrokerUrl = "tcp://broker.sundaebytestt.com:1883"
    private val topic = "assignment/location"
    private val receivedLocations = mutableListOf<LatLng>()
    private lateinit var dbHelper: DBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        dbHelper = DBHelper(this)

        var mqttClient =
            MqttAndroidClient(applicationContext, mqttBrokerUrl, MqttClient.generateClientId())
        subscribeToTopic()
    }

    private fun subscribeToTopic() {
        val mqttClient
        mqttClient.connect(MqttConnectOptions(), object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                mqttClient.subscribe(topic, 1) { _, message ->
                    val payload = String(message.payload)
                    val data = parsePayload(payload)
                    val location = LatLng(data.latitude, data.longitude)
                    receivedLocations.add(location)
                    saveLocationToDatabase(data)
                    updateMap()
                }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                exception?.printStackTrace()
            }
        })
    }

    private fun parsePayload(payload: String) {
        // Implement JSON parsing here to extract location details.
        // Return a custom LocationData object.
    }

    private fun saveLocationToDatabase(data: LocationData) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DBHelper.COLUMN_LATITUDE, data.latitude)
            put(DBHelper.COLUMN_LONGITUDE, data.longitude)
            put(DBHelper.COLUMN_SPEED, data.speed)
        }
        db.insert(DBHelper.TABLE_NAME, null, values)
    }

    private fun updateMap() {
        if (::googleMap.isInitialized) {
            googleMap.clear()
            googleMap.addPolyline(PolylineOptions().addAll(receivedLocations))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(receivedLocations.last(), 15f))
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }
}

data class LocationData(val latitude: Double, val longitude: Double, val speed: Float)

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_LATITUDE REAL," +
                "$COLUMN_LONGITUDE REAL," +
                "$COLUMN_SPEED REAL)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "locations.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "locations"
        const val COLUMN_ID = "id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_SPEED = "speed"
    }
}