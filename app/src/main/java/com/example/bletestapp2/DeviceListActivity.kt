package com.example.bletestapp2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class DeviceListActivity : AppCompatActivity() {

    internal class DeviceListAdapter(activity: Activity) : BaseAdapter() {
        private val mDeviceList: ArrayList<BluetoothDevice>
        private val mInflator: LayoutInflater

        init {
            mDeviceList = ArrayList()
            mInflator = activity.layoutInflater
        }

        // リストへの追加
        fun addDevice(device: BluetoothDevice) {
            if (!mDeviceList.contains(device)) {    // 加えられていなければ加える
                mDeviceList.add(device)
                notifyDataSetChanged() // ListViewの更新
            }
        }

        // リストのクリア
        fun clear() {
            mDeviceList.clear()
            notifyDataSetChanged() // ListViewの更新
        }

        override fun getCount(): Int {
            return mDeviceList.size
        }

        override fun getItem(position: Int): Any {
            return mDeviceList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        internal class ViewHolder {
            var deviceName: TextView? = null
            var deviceAddress: TextView? = null
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            var convertView: View? = convertView
            val viewHolder: ViewHolder
            // General ListView optimization code.
            if (null == convertView) {
                convertView = mInflator.inflate(R.layout.listitem_device, parent, false)
                viewHolder = ViewHolder()
                viewHolder.deviceAddress = convertView.findViewById(R.id.textview_deviceaddress)
                viewHolder.deviceName = convertView.findViewById(R.id.textview_devicename)
                convertView.setTag(viewHolder)
            } else {
                viewHolder = convertView.getTag() as ViewHolder
            }
            val device = mDeviceList[position]
            val deviceName = device.name
            if (null != deviceName && 0 < deviceName.length) {
                viewHolder.deviceName!!.text = deviceName
            } else {
                viewHolder.deviceName?.setText(R.string.unknown_device)
            }
            viewHolder.deviceAddress!!.text = device.address
            return convertView
        }
    }

    // 定数
    private val REQUEST_ENABLEBLUETOOTH = 1 // Bluetooth機能の有効化要求時の識別コード
    private val SCAN_PERIOD: Long = 10000 // スキャン時間。単位はミリ秒。
    val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
    val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"

    // メンバー変数
    private var mBluetoothAdapter // BluetoothAdapter : Bluetooth処理で必要
            : BluetoothAdapter? = null
    private var mDeviceListAdapter // リストビューの内容
            : DeviceListAdapter? = null
    private var mHandler // UIスレッド操作ハンドラ : 「一定時間後にスキャンをやめる処理」で必要
            : Handler? = null
    private var mScanning = false // スキャン中かどうかのフラグ

    // デバイススキャンコールバック
    private val mLeScanCallback: ScanCallback = object : ScanCallback() {
        // スキャンに成功（アドバタイジングは一定間隔で常に発行されているため、本関数は一定間隔で呼ばれ続ける）
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            runOnUiThread { mDeviceListAdapter!!.addDevice(result.getDevice()) }
        }

        // スキャンに失敗
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        // 戻り値の初期化
        setResult(RESULT_CANCELED)

        // リストビューの設定
        mDeviceListAdapter = DeviceListAdapter(this) // ビューアダプターの初期化
        val listView: ListView = findViewById<View>(R.id.devicelist) as ListView // リストビューの取得
        listView.setAdapter(mDeviceListAdapter) // リストビューにビューアダプターをセット
        listView.setOnItemClickListener(this) // クリックリスナーオブジェクトのセット

        // UIスレッド操作ハンドラの作成（「一定時間後にスキャンをやめる処理」で使用する）
        mHandler = Handler()

        // Bluetoothアダプタの取得
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (null == mBluetoothAdapter) {    // デバイス（＝スマホ）がBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show()
            finish() // アプリ終了宣言
            return
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    override fun onResume() {
        super.onResume()

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature()

        // スキャン開始
        startScan()
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    override fun onPause() {
        super.onPause()

        // スキャンの停止
        stopScan()
    }

    // デバイスのBluetooth機能の有効化要求
    private fun requestBluetoothFeature() {
        if (mBluetoothAdapter!!.isEnabled) {
            return
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
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
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // スキャンの開始
    private fun startScan() {
        // リストビューの内容を空にする。
        mDeviceListAdapter!!.clear()

        // BluetoothLeScannerの取得
        // ※Runnableオブジェクト内でも使用できるようfinalオブジェクトとする。
        val scanner = mBluetoothAdapter!!.bluetoothLeScanner ?: return

        // スキャン開始（一定時間後にスキャン停止する）
        mHandler!!.postDelayed({
            mScanning = false
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@postDelayed
            }
            scanner.stopScan(mLeScanCallback)

            // メニューの更新
            invalidateOptionsMenu()
        }, SCAN_PERIOD)
        mScanning = true
        scanner.startScan(mLeScanCallback)

        // メニューの更新
        invalidateOptionsMenu()
    }

    // スキャンの停止
    private fun stopScan() {
        // 一定期間後にスキャン停止するためのHandlerのRunnableの削除
        mHandler!!.removeCallbacksAndMessages(null)

        // BluetoothLeScannerの取得
        val scanner = mBluetoothAdapter!!.bluetoothLeScanner ?: return
        mScanning = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        scanner.stopScan(mLeScanCallback)

        // メニューの更新
        invalidateOptionsMenu()
    }

    // リストビューのアイテムクリック時の処理
    fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        // クリックされたアイテムの取得
        val device = mDeviceListAdapter!!.getItem(position) as BluetoothDevice ?: return
        // 戻り値の設定
        val intent = Intent()
        intent.putExtra(EXTRAS_DEVICE_NAME, device.name)
        intent.putExtra(EXTRAS_DEVICE_ADDRESS, device.address)
        setResult(RESULT_OK, intent)
        finish()
    }

    // オプションメニュー作成時の処理
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_device_list, menu)
        if (!mScanning) {
            menu.findItem(R.id.menuitem_stop).setVisible(false)
            menu.findItem(R.id.menuitem_scan).setVisible(true)
            menu.findItem(R.id.menuitem_progress).setActionView(null)
        } else {
            menu.findItem(R.id.menuitem_stop).setVisible(true)
            menu.findItem(R.id.menuitem_scan).setVisible(false)
            menu.findItem(R.id.menuitem_progress)
                .setActionView(R.layout.actionbar_indeterminate_progress)
        }
        return true
    }

    // オプションメニューのアイテム選択時の処理
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.menuitem_scan -> startScan() // スキャンの開始
            R.id.menuitem_stop -> stopScan() // スキャンの停止
        }
        return true
    }
}
