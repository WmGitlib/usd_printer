package com.example.usd_printer;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;

// TAIYUN TECH PRINTER SDK
import com.printer.sdk.PrinterInstance;
import com.printer.sdk.usb.USBPort;

// iMin PRINTER SDK
import com.imin.printerlib.IminPrintUtils;

import java.util.ArrayList;
import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** UsdPrinterPlugin */
public class UsdPrinterPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private static final String ACTION_USB_PERMISSION = "com.android.usb.USB_PERMISSION";
  private UsbDevice _usbDevice;
  private HashMap<String, UsbDevice> _printerMap;
  private Activity activity;

  private final BroadcastReceiver _broadcastReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          context.unregisterReceiver(_broadcastReceiver);
          UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && _usbDevice.equals(device)) {
            PrinterInstance.mPrinter.openConnection();
          }
        }
      }
    }
  };

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "usd_printer");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("discover")) {
      ArrayList<String> deviceList = discoverPrinters();
      result.success(deviceList);
    } else if (call.method.equals("discoverall")) {
      ArrayList<String> deviceList = discoverAll();
      result.success(deviceList);
    } else if (call.method.equals("printimin")) {
      byte[] data = call.argument("data");
      boolean success = printImin(data);
      result.success(success);
    } else if (call.method.equals("connect")) {
      String deviceName = call.argument("deviceName");
      String status = connectDevice(deviceName);
      if (status.isEmpty())
        result.success(true);
      else
        result.error("CONNECT_FAILED", status, null);
    } else if (call.method.equals("disconnect")) {
      boolean success = disconnectDevice();
      if (success)
        result.success(true);
      else
        result.error("DISCONNECT_FAILED", "Failed to disconnect from device", null);
    } else if (call.method.equals("print")) {
      byte[] data = call.argument("data");
      boolean success = printData(data);
      if (success)
        result.success(true);
      else
        result.error("PRINT_FAILED", "Failed to print data", null);
    } else {
      result.notImplemented();
    }

  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  private ArrayList<String> discoverPrinters() {

    UsbManager usbManager = (UsbManager) activity.getApplicationContext().getSystemService(Context.USB_SERVICE);
    _printerMap = usbManager.getDeviceList();
    ArrayList<String> deviceList = new ArrayList<String>();
    for (UsbDevice device : _printerMap.values()) {
      if (USBPort.isUsbPrinter(device)) {
        int interfaceCount = device.getInterfaceCount();
        for (int i = 0; i < interfaceCount; i++) {
          if (device.getInterface(i).getInterfaceClass() == 7) {
            deviceList.add(device.getDeviceName());
            break;
          }
        }
      }
    }
    return deviceList;
  }

  private ArrayList<String> discoverAll() {
    UsbManager usbManager = (UsbManager) activity.getApplicationContext().getSystemService(Context.USB_SERVICE);
    _printerMap = usbManager.getDeviceList();

    ArrayList<String> deviceList = new ArrayList<String>();

    for (UsbDevice device : _printerMap.values()) {
      int interfaceCount = device.getInterfaceCount();
      for (int i = 0; i < interfaceCount; i++) {
        if (device.getInterface(i).getInterfaceClass() == 7) {
          deviceList.add(device.getDeviceName());
          break;
        }
      }
    }
    return deviceList;
  }

  private boolean printImin(byte[] data) {
    IminPrintUtils mIminPrintUtils = IminPrintUtils.getInstance(activity.getApplicationContext());
    mIminPrintUtils.initPrinter(0);
    mIminPrintUtils.sendRAWData(data);
    mIminPrintUtils.release();
    return true;
  }

  private String connectDevice(String deviceName) {
    _usbDevice = _printerMap.get(deviceName);
    if (_usbDevice == null)
      return "Device " + deviceName + " not found";

    PrinterInstance printer = PrinterInstance.getPrinterInstance(activity.getApplicationContext(), _usbDevice, null);
    UsbManager mUsbManager = (UsbManager) activity.getApplicationContext().getSystemService(Context.USB_SERVICE);
    if (mUsbManager.hasPermission(_usbDevice))
      return printer.openConnection() ? "" : "Failed to connect to device " + deviceName;
    else {
      PendingIntent pendingIntent = PendingIntent.getBroadcast(activity.getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
      IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
      filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
      filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
      activity.getApplicationContext().registerReceiver(_broadcastReceiver, filter);
      mUsbManager.requestPermission(_usbDevice, pendingIntent);
      return "Requesting permission to open device " + deviceName;
    }
  }

  private boolean printData(byte[] data) {
    PrinterInstance printer = PrinterInstance.mPrinter;
    if (printer == null)
      return false;

    printer.initPrinter();
    int dataSent = printer.sendBytesData(data);
    return dataSent == data.length;
  }

  private boolean disconnectDevice() {
    PrinterInstance printer = PrinterInstance.mPrinter;
    if (printer == null)
      return true;

    printer.closeConnection();
    return true;
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }
  @Override
  public void onDetachedFromActivityForConfigChanges() {
    this.onDetachedFromActivity();
  }
  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    this.onAttachedToActivity(binding);
  }
  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }
}
