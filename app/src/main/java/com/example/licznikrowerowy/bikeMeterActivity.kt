package com.example.licznikrowerowy

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.licznikrowerowy.ble.ConnectionEventListener
import com.example.licznikrowerowy.ble.ConnectionManager
import com.example.licznikrowerowy.ble.isIndicatable
import com.example.licznikrowerowy.ble.isNotifiable
import com.example.licznikrowerowy.ble.isReadable
import com.example.licznikrowerowy.ble.isWritable
import com.example.licznikrowerowy.ble.isWritableWithoutResponse
import com.example.licznikrowerowy.ble.toHexString
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.selector
import org.jetbrains.anko.yesButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class bikeMeterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bike_meter)
    }
}