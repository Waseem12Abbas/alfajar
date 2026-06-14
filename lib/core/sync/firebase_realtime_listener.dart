import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import '../database/db_helper.dart';
import 'firebase_sync_service.dart';

class FirebaseRealtimeListener {
  static const String _tag = "FirebaseRealtimeListener";
  static StreamSubscription<QuerySnapshot<Map<String, dynamic>>>? _subscription;

  /// Starts listening to real-time additions and modifications across Firestore collection.
  static Future<void> startListening() async {
    if (_subscription != null) {
      debugPrint("$_tag: Listening is already active. Ignoring invocation.");
      return;
    }

    try {
      final localDeviceId = await FirebaseSyncService.getDeviceId();
      debugPrint("$_tag: Establishing continuous sync stream listener connection (Device: $localDeviceId)");

      final firestore = FirebaseFirestore.instance;
      final dbHelper = DbHelper();

      // Listen to the Firestore snapshots stream on the entire vehicle_passes collection
      _subscription = firestore.collection('vehicle_passes').snapshots().listen(
        (querySnapshot) async {
          debugPrint("$_tag: Snapshots event received. Processing ${querySnapshot.docChanges.length} changes.");

          for (var change in querySnapshot.docChanges) {
            // Only process added or modified documents
            if (change.type == DocumentChangeType.added || change.type == DocumentChangeType.modified) {
              final doc = change.doc;
              final data = doc.data();

              if (data == null) continue;

              final String cloudDeviceId = data['device_id'] as String? ?? '';

              // Skip updates originated by the local device client to avoid circular operations
              if (cloudDeviceId == localDeviceId) {
                continue;
              }

              final String uniqueId = data['unique_id'] as String? ?? doc.id;
              final String ownerName = data['owner_name'] as String? ?? '';
              final String cnic = data['cnic'] as String? ?? '';
              final String vehicleNo = data['vehicle_no'] as String? ?? '';
              final String? phone = data['phone'] as String?;
              final String expiryDate = data['expiry_date'] as String? ?? '';
              final int isRevoked = data['is_revoked'] as int? ?? 0;

              // Convert timestamps correctly
              final Timestamp? createdAtTimestamp = data['created_at'] as Timestamp?;
              final Timestamp? lastModTimestamp = data['last_modified'] as Timestamp?;

              final createdAtStr = createdAtTimestamp != null
                  ? createdAtTimestamp.toDate().toUtc().toIso8601String()
                  : DateTime.now().toUtc().toIso8601String();
              final lastModifiedStr = lastModTimestamp != null
                  ? lastModTimestamp.toDate().toUtc().toIso8601String()
                  : DateTime.now().toUtc().toIso8601String();

              // Check existing SQLite record
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
                // Instantly sync download and save local DB record
                await dbHelper.insertOrReplaceVehiclePass(recordToSave);
                debugPrint("$_tag Realtime: New record $uniqueId propagated and synced locally.");
              } else {
                // Conflict resolution comparison
                final localLastModified = localRecord['last_modified'] as String? ?? '';
                DateTime localTime;
                try {
                  localTime = DateTime.parse(localLastModified);
                } catch (_) {
                  localTime = DateTime.fromMillisecondsSinceEpoch(0);
                }

                final cloudTime = lastModTimestamp?.toDate().toUtc() ?? DateTime.fromMillisecondsSinceEpoch(0);

                if (cloudTime.isAfter(localTime)) {
                  await dbHelper.insertOrReplaceVehiclePass({
                    ...recordToSave,
                    'id': localRecord['id'], // Maintain local primary DB column key ID integrity
                  });
                  debugPrint("$_tag Realtime: Conflict resolved for $uniqueId. Local copy upgraded with cloud version.");
                }
              }
            }
          }
        },
        onError: (error) {
          debugPrint("$_tag: Snapshot connection encountered stream errors: $error");
        },
        onDone: () {
          debugPrint("$_tag: Snapshots active listener stopped or closed.");
        },
      );
    } catch (e) {
      debugPrint("$_tag: Failed to subscribe to real-time streams: $e");
    }
  }

  /// Cleanly closes, cancels and releases the Firebase snapshot stream listener references.
  static void stopListening() {
    if (_subscription != null) {
      _subscription!.cancel();
      _subscription = null;
      debugPrint("$_tag: Stream cancelled and destroyed successfully.");
    }
  }
}
