package com.example.licznikrowerowy

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.licznikrowerowy.ble.*
import kotlinx.android.synthetic.main.activity_ble_operations.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.sdk27.coroutines.onEditorAction
import org.jetbrains.anko.systemHealthManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n")
class BleOperationsActivity : AppCompatActivity() {

    private val CYCLING_SPEED_AND_CADENCE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val PRESSURE_CHARACTERISTIC_UUID = UUID.fromString("00002A6D-0000-1000-8000-00805f9b34fb")

    private lateinit var device: BluetoothDevice
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.map { characteristic ->
            characteristic to mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }.toMap()
    }

    private var notifyingCharacteristics = mutableListOf<UUID>()

    lateinit var mainHandler: Handler

    private val updateTextTask = object : Runnable {
        override fun run() {
            read_ble()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private var time_until_end: Long = 0
    private var wheel_diameter = 20 //cm
    private var distance: Double = 0.0 //km
    private var reference_elevation: Double = 0.0 //m
    private val sea_level_pressure = 101325  //1013,25hPa
    private var elevation_gain = 0;


    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        setContentView(R.layout.activity_bike_meter)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = "Licznik Rowerowy"
        }

        mainHandler = Handler(Looper.getMainLooper())
        val wheel_diameter_field: TextView = findViewById(R.id.wheel_diameter_field)

        findViewById<EditText>(R.id.wheel_diameter_field).setOnEditorActionListener { v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEND -> {
                    wheel_diameter = wheel_diameter_field.text.toString().toInt()
                    true
                }
                else -> false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(updateTextTask)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(updateTextTask)
    }

    fun read_ble() {
        time_until_end += 1
        if (time_until_end>0) {

            ConnectionManager.servicesOnDevice(device)?.forEach { service ->
                service.characteristics?.forEach { characteristic ->
                    if (characteristic.uuid == CYCLING_SPEED_AND_CADENCE_CHARACTERISTIC_UUID){
                        if (characteristic.isReadable()) {
                            ConnectionManager.readCharacteristic(device, characteristic)
                        }
                    }
                    if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID){
                        if (characteristic.isReadable()) {
                            ConnectionManager.readCharacteristic(device, characteristic)
                        }
                    }
                    if (characteristic.uuid == PRESSURE_CHARACTERISTIC_UUID){
                        if (characteristic.isReadable()) {
                            ConnectionManager.readCharacteristic(device, characteristic)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected from device."
                        positiveButton("OK") { onBackPressed() }
                    }.show()
                }
            }
            onCharacteristicRead = { _, characteristic ->
                if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID){
                    val battery_percentage: ByteArray = characteristic.value
                    val battery_percentage_view: TextView = findViewById(R.id.battery_percentage_view)
                    battery_percentage_view.setText("${battery_percentage[0].toInt()}%")
                }
                else if (characteristic.uuid == CYCLING_SPEED_AND_CADENCE_CHARACTERISTIC_UUID){
                    val wheel_rpm_and_cadence: ByteArray = characteristic.value

                    val wheel_rpm =  byteArrayOf(wheel_rpm_and_cadence[1], wheel_rpm_and_cadence[2], wheel_rpm_and_cadence[3], wheel_rpm_and_cadence[4])
                    var wheel_rpm_int = littleEndianConversion_to_int(wheel_rpm)
                    if (wheel_rpm_int < 0 ){
                        wheel_rpm_int = 120
                    }
                    val wheel_rpm_view: TextView = findViewById(R.id.wheel_rpm_view)
                    wheel_rpm_view.setText("${wheel_rpm_int.toUInt()}")

                    val current_speed = calculate_current_speed(wheel_rpm_int)
                    val current_speed_view: TextView = findViewById(R.id.current_speed_view)
                    current_speed_view.setText("${current_speed}")

                    val distance_to_view = calculate_distance(wheel_rpm_int)
                    val distance_view: TextView = findViewById(R.id.distance_view)
                    distance_view.setText("${distance_to_view}")

                    val average_speed = calculate_average_speed()
                    val average_speed_view: TextView = findViewById(R.id.average_speed_view)
                    average_speed_view.setText("${average_speed}")

                    val cadence =  byteArrayOf(wheel_rpm_and_cadence[7], wheel_rpm_and_cadence[8], 0x0, 0x0)
                    var cadence_int = littleEndianConversion_to_int(cadence).toUInt()
                    if (cadence_int > 120u){
                        cadence_int = 120u
                    }
                    val cadence_value_view: TextView = findViewById(R.id.cadence_value_view)
                    cadence_value_view.setText("${cadence_int}")
                }
                else if (characteristic.uuid == PRESSURE_CHARACTERISTIC_UUID){
                    val pressure = littleEndianConversion_to_int(characteristic.value)
                    val elevation_diffrence = calculate_elevation_diffrence(pressure)
                    val elevation_diffrence_view: TextView = findViewById(R.id.elevation_diffrence_view)
                    elevation_diffrence_view.setText("${elevation_diffrence}")
                }


                }

            onCharacteristicWrite = { _, characteristic ->
            }

            onMtuChanged = { _, mtu ->
            }

            onCharacteristicChanged = { _, characteristic ->
            }

            onNotificationsEnabled = { _, characteristic ->
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                notifyingCharacteristics.remove(characteristic.uuid)
            }

       }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }


    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

    fun littleEndianConversion_to_int(bytes: ByteArray): Int {

        var result: UInt = 0u
        for (i in bytes.indices) {
            result = result or ((bytes[i].toInt() shl 8 * i).toUInt())
        }
        return result.toInt()
    }

    fun calculate_current_speed(wheel_rpm: Int): BigDecimal? {
        val speed: Double = PI *wheel_diameter*60 * wheel_rpm/10000   //60 minutes in hour and 10000 cm in kilometer
        return speed.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN)
    }

    fun calculate_distance(wheel_rpm: Int): BigDecimal? {
        val distance_in_second: Double = PI *wheel_diameter * wheel_rpm/600000    //60 seconds in minute * 10000 cm in kilometer
        this.distance += distance_in_second
        return this.distance.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN)
    }

    fun calculate_average_speed(): BigDecimal? {
        val average_speed = this.distance * 3600 /this.time_until_end     //3600 seconds in hour
        return average_speed.toBigDecimal().setScale(1, RoundingMode.HALF_EVEN)
    }

    fun calculate_elevation_diffrence(pressure: Int): Int {
        //val altitude: Double =  (10.0.pow(log(10.0, ((pressure/this.sea_level_pressure).toDouble()))/5.2558797) - 1) / -6.8755856 * 10.0.pow(-6)
        val altitude: Double = pressure.toDouble()/10000
        if (this.time_until_end<3){
            this.reference_elevation = altitude
        }
        else if (this.reference_elevation - altitude >= 1.0){
            elevation_gain += (this.reference_elevation - altitude).toBigDecimal().setScale(0, RoundingMode.HALF_EVEN).toInt()
            this.reference_elevation = altitude
        }
        else if (this.reference_elevation - altitude <= -1.0){
            this.reference_elevation = altitude
        }
        return elevation_gain
    }
}
