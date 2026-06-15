import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../database/db_helper.dart';

/// Elite Global Peer-to-Peer Synchronization Service.
/// Implements a complete two-way mirror-sync between the local SQLite database
/// and Cloud Firestore without any device exclusions, ensuring peer devices
/// log into the same database and fully mirror all global registration entries.
class FirebaseSyncService {
  static const String _tag = "FirebaseSyncService";
  static const String _lastSyncPrefKey = "last_sync_time";

  /// Standard utility to check screen/connection status.
  static Future<bool> isOnline() async {
    try {
      final connectivityResult = await Connectivity().checkConnectivity();
      if (connectivityResult == ConnectivityResult.none) {
        return false;
      }
      return true;
    } catch (e) {
      debugPrint("$_tag: Class connectivity check failed: $e");
      return false;
    }
  }

  /// Retrieve the unique hardware string ID of this device.
  static Future<String> getDeviceId() async {
    try {
      final deviceInfo = DeviceInfoPlugin();
      if (defaultTargetPlatform == TargetPlatform.android) {
        final androidInfo = await deviceInfo.androidInfo;
        return androidInfo.id;
      } else if (defaultTargetPlatform == TargetPlatform.iOS) {
        final iosInfo = await deviceInfo.iosInfo;
        return iosInfo.identifierForVendor ?? "ios_unknown";
      }
      return "device_unknown";
    } catch (e) {
      debugPrint("$_tag: Error fetching device fingerprint ID: $e");
      return "device_unknown";
    }
  }

  /// Retrieve last successful sync timestamp from Local SharedPreferences fallback
  static Future<String> getLastSyncTime() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_lastSyncPrefKey) ?? "2020-01-01T00:00:00Z";
  }

  /// Set last successful sync timestamp inside SharedPreferences local storage
  static Future<void> setLastSyncTime(String isoTime) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_lastSyncPrefKey, isoTime);
  }

  /// Conducts a full automated synchronization (PUSH then PULL) to Google Firestore.
  static Future<bool> sync({Function(String)? onStatusUpdate}) async {
    if (!await isOnline()) {
      debugPrint("$_tag: Device is offline. Skipping remote sync.");
      onStatusUpdate?.call("Offline");
      return false;
    }

    try {
      onStatusUpdate?.call("Syncing...");
      final dbHelper = DbHelper();
      final deviceId = await getDeviceId();
      final lastSyncTime = await getLastSyncTime();

      debugPrint("$_tag: Initiating Decentralized Peer-to-Peer Sync. Device ID: $deviceId, Last Sync: $lastSyncTime");

      // 1. Push modern local modifications to Firebase Cloud Firestore
      final pushSuccess = await pushToCloud(dbHelper, deviceId, lastSyncTime);
      if (!pushSuccess) {
        debugPrint("$_tag: Push stage encountered obstacles, continuing to pull phase to preserve sync integrity.");
      }

      // 2. Pull ALL external cloud changes and resolve conflicts using "Last Modified Wins"
      final pullSuccess = await pullFromCloud(dbHelper, deviceId, lastSyncTime);

      if (pullSuccess) {
        final currentSyncCompletedTime = DateTime.now().toUtc().toIso8601String();
        await setLastSyncTime(currentSyncCompletedTime);
        debugPrint("$_tag: Synchronization completed successfully at $currentSyncCompletedTime.");
        onStatusUpdate?.call("Synced");
        return true;
      } else {
        debugPrint("$_tag: Pull stage encountered errors.");
        onStatusUpdate?.call("Sync Error");
        return false;
      }
    } catch (e) {
      debugPrint("$_tag: Critical execution error during sync lifecycle: $e");
      onStatusUpdate?.call("Sync Error");
      return false;
    }
  }

  /// Pushes local sqlite modifications since the last sync to Firebase Cloud Firestore.
  static Future<bool> pushToCloud(DbHelper dbHelper, String deviceId, String lastSyncTime) async {
    try {
      final recordsToPush = await dbHelper.getModifiedSince(lastSyncTime);
      if (recordsToPush.isEmpty) {
        debugPrint("$_tag Push: No local changes detected since $lastSyncTime.");
        return true;
      }

      debugPrint("$_tag Push: Found ${recordsToPush.length} changed passes to sync upstream.");
      final firestore = FirebaseFirestore.instance;
      
      const int batchLimit = 400;
      WriteBatch batch = firestore.batch();
      int batchCount = 0;

      for (var record in recordsToPush) {
        final uniqueId = record['unique_id'] as String;
        final docRef = firestore.collection('vehicle_passes').doc(uniqueId);

        final createdAtStr = record['created_at'] as String;
        final lastModifiedStr = record['last_modified'] as String? ?? DateTime.now().toUtc().toIso8601String();

        final createdAtDate = DateTime.tryParse(createdAtStr) ?? DateTime.now().toUtc();
        final lastModifiedDate = DateTime.tryParse(lastModifiedStr) ?? DateTime.now().toUtc();

        final data = {
          'unique_id': uniqueId,
          'owner_name': record['owner_name'],
          'cnic': record['cnic'],
          'vehicle_no': record['vehicle_no'],
          'phone': record['phone'],
          'expiry_date': record['expiry_date'],
          'is_revoked': record['is_revoked'] as int? ?? 0,
          'created_at': Timestamp.fromDate(createdAtDate),
          'last_modified': Timestamp.fromDate(lastModifiedDate),
          'device_id': (record['device_id'] as String?).isNotNullOrEmpty ? record['device_id'] : deviceId,
        };

        batch.set(docRef, data, SetOptions(merge: true));
        batchCount++;

        if (batchCount >= batchLimit) {
          await batch.commit();
          batch = firestore.batch();
          batchCount = 0;
        }
      }

      if (batchCount > 0) {
        await batch.commit();
      }

      // Record diagnostic device heartbeat sync timestamp on Firestore
      await firestore.collection('sync_logs').doc(deviceId).set({
        'device_id': deviceId,
        'last_synced_at': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));

      debugPrint("$_tag Push: Successfully uploaded peer records to Cloud.");
      return true;
    } catch (e) {
      debugPrint("$_tag Push: Remote push transaction crashed: $e");
      return false;
    }
  }

  /// Pulls ALL external/global changes updated in Firestore and merges into SQLite.
  /// No device exclusions are applied, allowing global synchronization across logins.
  static Future<bool> pullFromCloud(DbHelper dbHelper, String localDeviceId, String lastSyncTime) async {
    try {
      final firestore = FirebaseFirestore.instance;
      final lastSyncDate = DateTime.tryParse(lastSyncTime) ?? DateTime.fromMillisecondsSinceEpoch(0);
      final lastSyncTimestamp = Timestamp.fromDate(lastSyncDate);

      // Select documents in vehicle_passes where last_modified > lastSyncTime
      final querySnapshot = await firestore
          .collection('vehicle_passes')
          .where('last_modified', isGreaterThan: lastSyncTimestamp)
          .get();

      if (querySnapshot.docs.isEmpty) {
        debugPrint("$_tag Pull: Already in sync with remote cloud.");
        return true;
      }

      debugPrint("$_tag Pull: Retrieved ${querySnapshot.docs.length} modified documents from cloud.");
      int countMerged = 0;

      for (var doc in querySnapshot.docs) {
        final data = doc.data();
        final cloudDeviceId = data['device_id'] as String? ?? '';

        final uniqueId = data['unique_id'] as String? ?? doc.id;
        final ownerName = data['owner_name'] as String? ?? '';
        final cnic = data['cnic'] as String? ?? '';
        final vehicleNo = data['vehicle_no'] as String? ?? '';
        final phone = data['phone'] as String?;
        final expiryDate = data['expiry_date'] as String? ?? '';
        final isRevoked = data['is_revoked'] as int? ?? 0;

        // Convert native Firestore Timestamps to ISO 8601 strings for local SQLite
        final Timestamp? createdAtTimestamp = data['created_at'] as Timestamp?;
        final Timestamp? lastModTimestamp = data['last_modified'] as Timestamp?;

        final createdAtStr = createdAtTimestamp != null
            ? createdAtTimestamp.toDate().toUtc().toIso8601String()
            : DateTime.now().toUtc().toIso8601String();
        final lastModifiedStr = lastModTimestamp != null
            ? lastModTimestamp.toDate().toUtc().toIso8601String()
            : DateTime.now().toUtc().toIso8601String();

        // Query the local database for matching records
        final localRecord = await dbHelper.getPassByUniqueId(uniqueId);

        final recordToSave = {
          'unique_id': uniqueId,
          'owner_name': ownerName,
          'cnic': cnic,
          'vehicle_no': vehicleNo,
          'phone': phone,
          'expiry_date': expiryDate,
          'is_revoked': isRevoked,
          'created_at': createdAtStr,
          'last_modified': lastModifiedStr,
          'device_id': cloudDeviceId,
        };

        if (localRecord == null) {
          // Direct insert of newly discovered remote document
          await dbHelper.insertOrReplaceVehiclePass(recordToSave);
          countMerged++;
        } else {
          // Decentralized automatic conflict resolution: "Last modified wins"
          final localLastModified = localRecord['last_modified'] as String? ?? '';
          
          DateTime localTime;
          try {
            localTime = DateTime.parse(localLastModified);
          } catch (_) {
            localTime = DateTime.fromMillisecondsSinceEpoch(0);
          }

          final cloudTime = lastModTimestamp?.toDate().toUtc() ?? DateTime.fromMillisecondsSinceEpoch(0);

          if (cloudTime.isAfter(localTime)) {
            // Cloud version is newer, perform transactional update
            await dbHelper.insertOrReplaceVehiclePass({
              ...recordToSave,
              'id': localRecord['id'], // Maintain local primary key autoincrement ID integrity
            });
            countMerged++;
            debugPrint("$_tag Pull: Local database record $uniqueId updated by cloud (last modified wins conflict resolution).");
          } else {
            debugPrint("$_tag Pull: Local database record $uniqueId is more recent or identical. Skipping cloud pull replacement.");
          }
        }
      }

      debugPrint("$_tag Pull: Successfully merged $countMerged external updates into local SQLite DB.");
      return true;
    } catch (e) {
      debugPrint("$_tag Pull: Sync merge engine crashed: $e");
      return false;
    }
  }
}

extension on String? {
  bool get isNotNullOrEmpty => this != null && this!.trim().isNotEmpty;
}
