import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';

class UsdPrinter {
  static const MethodChannel _channel = MethodChannel('usd_printer');

  static Future<List> get printerList async {
    try {
      final List devices = await _channel.invokeMethod('discover');
      return devices;
    } on PlatformException {
      return [];
    }
  }

  static Future<List> get allList async {
    try {
      final List devices = await _channel.invokeMethod('discoverall');
      return devices;
    } on PlatformException {
      return [];
    }
  }

  static Future<bool> printImin(Uint8List data) async {
    Map<String, dynamic> params = {"data": data};
    try {
      final bool returned = await _channel.invokeMethod('printimin', params);
      return returned;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> connectPrinter(String deviceName) async {
    Map<String, dynamic> params = {"deviceName": deviceName};
    try {
      final bool returned = await _channel.invokeMethod('connect', params);
      return returned;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> disconnectPrinter() async {
    try {
      final bool returned = await _channel.invokeMethod('disconnect');
      return returned;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> printData(Uint8List data) async {
    Map<String, dynamic> params = {"data": data};
    try {
      final bool returned = await _channel.invokeMethod('print', params);
      return returned;
    } on PlatformException {
      return false;
    }
  }
}
