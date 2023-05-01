package com.example.bletestapp2

import android.Manifest
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity(), View.OnClickListener {

    // 定数s ,E95D9250251D470AA062FA1922DFA9A8,E95D1B25251D470AA062FA1922DFA9A8
    companion object {
       //private val UUID_SERVICE_PRIVATE:UUID = UUID.fromstring("E95D6100251D470AA062FA1922DFA9A8")
       //private val UUID_CHARACTERISTIC_PRIVATE:UUID = UUID.fromstring("E95D6100251D470AA062FA1922DFA9A8")
    }

    // 定数
    private val REQUEST_ENABLEBLUETOOTH = 1 // Bluetooth機能の有効化要求時の識別コード
    private val REQUEST_CONNECTDEVICE = 2 // デバイス接続要求時の識別コード


    // メンバー変数
    private var mBluetoothAdapter // BluetoothAdapter : Bluetooth処理で必要
            : BluetoothAdapter? = null
    private var mDeviceAddress = "" // デバイスアドレス
    private var mBluetoothGatt: BluetoothGatt? = null // Gattサービスの検索、キャラスタリスティックの読み書き


    // GUIアイテム
    private var mButton_Connect // 接続ボタン
            : Button? = null
    private var mButton_Disconnect // 切断ボタン
            : Button? = null

    // BluetoothGattコールバック
    private val mGattcallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        // 接続状態変更（connectGatt()の結果として呼ばれる。）
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (BluetoothGatt.GATT_SUCCESS != status) {
                return
            }
            if (BluetoothProfile.STATE_CONNECTED == newState) {    // 接続完了
                runOnUiThread { // GUIアイテムの有効無効の設定
                    // 切断ボタンを有効にする
                    mButton_Disconnect!!.isEnabled = true
                }
                return
            }
            if (BluetoothProfile.STATE_DISCONNECTED == newState) {    // 切断完了（接続可能範囲から外れて切断された）
                // 接続可能範囲に入ったら自動接続するために、mBluetoothGatt.connect()を呼び出す。
                mBluetoothGatt!!.connect()
                return
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // GUIアイテム
        val mButton_Connect = findViewById(R.id.button_connect) as Button
        mButton_Connect!!.setOnClickListener(this);
        val mButton_Disconnect = findViewById(R.id.button_disconnect) as Button
        mButton_Disconnect!!.setOnClickListener(this);

        // Android端末がBLEをサポートしてるかの確認
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT).show()
            finish() // アプリ終了宣言
            return
        }

        // Bluetoothアダプタの取得
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (null == mBluetoothAdapter) {    // Android端末がBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish() // アプリ終了宣言
            return
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    override fun onResume() {
        super.onResume();

        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
        mButton_Connect?.setEnabled( false );
        mButton_Disconnect?.setEnabled( false );

        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if( !mDeviceAddress.equals( "" ) )
        {
            mButton_Connect?.setEnabled( true );
        }

        // 接続ボタンを押す
        mButton_Connect?.callOnClick();
    }

    override fun onPause() {
        super.onPause()

        // 切断
        disconnect()
    }

    // アクティビティの終了直前
    override fun onDestroy() {
        super.onDestroy()
        if (null != mBluetoothGatt) {
            mBluetoothGatt!!.close()
            mBluetoothGatt = null
        }
    }

    // Android端末のBluetooth機能の有効化要求
    private fun requestBluetoothFeature() {
        if (mBluetoothAdapter!!.isEnabled) {
            return
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLEBLUETOOTH)
    }

    // 機能の有効化ダイアログの操作結果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLEBLUETOOTH -> if (RESULT_CANCELED == resultCode) {    // 有効にされなかった
                Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show()
                finish() // アプリ終了宣言
                return
            }
            REQUEST_CONNECTDEVICE -> {
                val strDeviceName: String?
                if (RESULT_OK == resultCode) {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data?.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_NAME)
                    mDeviceAddress = data?.getStringExtra(DeviceListActivity.EXTRAS_DEVICE_ADDRESS)!!
                } else {
                    strDeviceName = ""
                    mDeviceAddress = ""
                }
                (findViewById<View>(R.id.textview_devicename) as TextView).text =
                    strDeviceName
                (findViewById<View>(R.id.textview_deviceaddress) as TextView).text =
                    mDeviceAddress
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // オプションメニュー作成時の処理
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    // オプションメニューのアイテム選択時の処理
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menuitem_search -> {
                val devicelistactivityIntent = Intent(this, DeviceListActivity::class.java)
                startActivityForResult(devicelistactivityIntent, REQUEST_CONNECTDEVICE)
                return true
            }
        }
        return false
    }

    override fun onClick(v: View) {
        if (mButton_Connect!!.id == v.id) {
            mButton_Connect!!.isEnabled = false // 接続ボタンの無効化（連打対策）
            connect() // 接続
            return
        }
        if (mButton_Disconnect!!.id == v.id) {
            mButton_Disconnect!!.isEnabled = false // 切断ボタンの無効化（連打対策）
            disconnect() // 切断
            return
        }
    }

    private fun connect() {
        if (mDeviceAddress == "") {    // DeviceAddressが空の場合は処理しない
            return
        }
        if (null != mBluetoothGatt) {    // mBluetoothGattがnullでないなら接続済みか、接続中。
            return
        }

        // 接続
        val device = mBluetoothAdapter!!.getRemoteDevice(mDeviceAddress)
        mBluetoothGatt = device.connectGatt(this, false, mGattcallback)
    }

    // 切断
    private fun disconnect() {
        if (null == mBluetoothGatt) {
            return
        }

        // 切断
        //   mBluetoothGatt.disconnect()ではなく、mBluetoothGatt.close()しオブジェクトを解放する。
        //   理由：「ユーザーの意思による切断」と「接続範囲から外れた切断」を区別するため。
        //   ①「ユーザーの意思による切断」は、mBluetoothGattオブジェクトを解放する。再接続は、オブジェクト構築から。
        //   ②「接続可能範囲から外れた切断」は、内部処理でmBluetoothGatt.disconnect()処理が実施される。
        //     切断時のコールバックでmBluetoothGatt.connect()を呼んでおくと、接続可能範囲に入ったら自動接続する。
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
        // GUIアイテムの有効無効の設定
        // 接続ボタンのみ有効にする
        mButton_Connect!!.isEnabled = true
        mButton_Disconnect!!.isEnabled = false
    }
}