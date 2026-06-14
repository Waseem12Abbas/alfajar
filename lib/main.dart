import 'dart:async';
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:workmanager/workmanager.dart';
import 'core/sync/firebase_sync_service.dart';
import 'core/sync/firebase_realtime_listener.dart';

// Unique background task identifier for period worker pooling
const String syncTaskName = "com.pakpass.backgroundSync";

/// Callback dispatcher for native background Workmanager processing.
/// This executes inside an isolated background isolate without a UI context.
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    debugPrint("Workmanager background dispatcher awake. Task: $task");
    try {
      // Ensure Flutter services are binded
      WidgetsFlutterBinding.ensureInitialized();

      // Initialize Firebase inside the background context
      await Firebase.initializeApp();

      // Perform the 2-way decentralized background sync (PUSH then PULL)
      final success = await FirebaseSyncService.sync();
      debugPrint("Workmanager background sync result: $success");
      return success;
    } catch (e) {
      debugPrint("Workmanager background dispatcher failed: $e");
      return false;
    }
  });
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 1. Initialize Firebase globally for public collection validation matching rules
  try {
    await Firebase.initializeApp();
    debugPrint("Firebase initialized successfully on main thread.");
  } catch (e) {
    debugPrint("Firebase global initialization failed/no Google Services: $e");
  }

  // 2. Configure background Workmanager periodical sync pooling (every 15 minutes)
  try {
    await Workmanager().initialize(
      callbackDispatcher,
      isInDebugMode: kDebugMode, // set to true for debugging notifications
    );
    
    await Workmanager().registerPeriodicTask(
      "1", // unique periodic task code identifier
      syncTaskName,
      frequency: const Duration(minutes: 15),
      existingWorkPolicy: ExistingWorkPolicy.keep,
      constraints: Constraints(
        networkType: NetworkType.connected, // Only trigger if connected to internet
        requiresBatteryNotLow: true,
      ),
    );
    debugPrint("Workmanager periodic synchronized background worker active.");
  } catch (e) {
    debugPrint("Failed to register Workmanager background service: $e");
  }

  // 3. Establish active, continuous stream connection to Firestore collections
  try {
    FirebaseRealtimeListener.startListening();
    debugPrint("Active real-time collection stream listeners connected.");
  } catch (e) {
    debugPrint("Real-time stream listener setup skipped: $e");
  }

  runApp(const PakPassApp());
}

class PakPassApp extends StatelessWidget {
  const PakPassApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'PakPass Sync Panel',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF005129), // Urdu Pakistan green theme identity
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const SyncDashboardScreen(),
    );
  }
}

class SyncDashboardScreen extends StatefulWidget {
  const SyncDashboardScreen({super.key});

  @override
  State<SyncDashboardScreen> createState() => _SyncDashboardScreenState();
}

class _SyncDashboardScreenState extends State<SyncDashboardScreen> {
  String _syncStatus = "Synced"; // Driven reactively: 'Synced', 'Syncing...', 'Offline'
  String _lastSyncStr = "2026-06-14T01:00:00Z";
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _refreshSyncMetadata();
    // Listening for active connections periodically
    Timer.periodic(const Duration(seconds: 15), (_) => _refreshSyncMetadata());
  }

  Future<void> _refreshSyncMetadata() async {
    final stamp = await FirebaseSyncService.getLastSyncTime();
    final online = await FirebaseSyncService.isOnline();
    if (mounted) {
      setState(() {
        _lastSyncStr = stamp;
        if (!online) {
          _syncStatus = "Offline";
        } else if (_isLoading) {
          _syncStatus = "Syncing...";
        } else {
          _syncStatus = "Synced";
        }
      });
    }
  }

  Future<void> _triggerManualSync() async {
    if (_isLoading) return;

    setState(() {
      _isLoading = true;
      _syncStatus = "Syncing...";
    });

    try {
      final success = await FirebaseSyncService.sync(
        onStatusUpdate: (status) {
          if (mounted) {
            setState(() {
              _syncStatus = status;
            });
          }
        },
      );

      if (success) {
        debugPrint("Manual direct sync successful.");
      } else {
        debugPrint("Manual direct sync returned non-success response.");
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _syncStatus = "Sync Error";
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
        _refreshSyncMetadata();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          "پاک پاس سنکرونائزیشن | PakPass Sync",
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        backgroundColor: const Color(0xFF005129),
        foregroundColor: Colors.white,
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Visual State Header Banner Card
              Card(
                elevation: 0,
                color: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: const BorderSide(color: Color(0xFFE2E8F0)),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    children: [
                      const Icon(
                        Icons.cloud_sync_rounded,
                        size: 64,
                        color: Color(0xFF005129),
                      ),
                      const SizedBox(height: 16),
                      const Text(
                        "دو طرفہ سنکرونائزیشن (Two-Way Sync)",
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        "This engine maintains a fully functional local SQLite instance when offline, and automatically bridges updates with Firestore peers.",
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                      ),
                      const SizedBox(height: 20),
                      _buildSyncStateChip(_syncStatus),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Settings card for manual operational controls
              Card(
                elevation: 0,
                color: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: const BorderSide(color: Color(0xFFE2E8F0)),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.history, color: Color(0xFF64748B)),
                          const SizedBox(width: 8),
                          Text(
                            "آخری سنک کی تاریخ | Sync Metadata",
                            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: const Color(0xFF0F172A),
                                ),
                          ),
                        ],
                      ),
                      const Divider(height: 24, color: Color(0xFFF1F5F9)),
                      _buildMetadataRow("Last Successful Sync", _lastSyncStr),
                      const SizedBox(height: 10),
                      _buildMetadataRow("Background Worker", "Active (Every 15 mins)"),
                      const SizedBox(height: 24),
                      Row(
                        children: [
                          Expanded(
                            child: ElevatedButton.icon(
                              onPressed: _isLoading ? null : _triggerManualSync,
                              icon: _isLoading
                                  ? const SizedBox(
                                      width: 16,
                                      height: 16,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                                      ),
                                    )
                                  : const Icon(Icons.sync_outlined, size: 18),
                              label: Text(
                                _isLoading ? "سنکرونائز ہو رہا ہے..." : "ابھی سنک کریں | Sync Now",
                                style: const TextStyle(fontWeight: FontWeight.bold),
                              ),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFF005129),
                                foregroundColor: Colors.white,
                                disabledBackgroundColor: Colors.grey[200],
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                padding: const EdgeInsets.symmetric(vertical: 14),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 24),
              // Setup directives helper guide
              _buildSetupInstructionsCard(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSyncStateChip(String status) {
    Color chipColor;
    Color textColor;
    IconData icon;
    String textUrdu;

    switch (status) {
      case "Syncing...":
        chipColor = const Color(0xFFFEF3C7); // Amber yellow
        textColor = const Color(0xFFD97706);
        icon = Icons.hourglass_empty_rounded;
        textUrdu = "Syne ho raha hai...";
        break;
      case "Offline":
        chipColor = const Color(0xFFFEE2E2); // Soft red
        textColor = const Color(0xFFDC2626);
        icon = Icons.wifi_off_rounded;
        textUrdu = "آف لائن | Offline";
        break;
      case "Synced":
      default:
        chipColor = const Color(0xFFDCFCE7); // Soft green
        textColor = const Color(0xFF16A34A);
        icon = Icons.check_circle_rounded;
        textUrdu = "سنک شدہ | Synced";
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: chipColor,
        borderRadius: BorderRadius.circular(50),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: textColor),
          const SizedBox(width: 8),
          Text(
            textUrdu,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: textColor,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetadataRow(String key, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          key,
          style: const TextStyle(fontSize: 13, color: Color(0xFF64748B)),
        ),
        Text(
          value,
          style: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: Color(0xFF1E293B),
          ),
        ),
      ],
    );
  }

  Widget _buildSetupInstructionsCard() {
    return Card(
      elevation: 0,
      color: const Color(0xFFF1F5F9),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.security, size: 20, color: Color(0xFF475569)),
                SizedBox(width: 8),
                Text(
                  "فائر بیس سیکیورٹی رولز | Security Rules Guide",
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF334155),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            const Text(
              "To enable seamless public peer-to-peer visual updates across the decentralized network, configure non-secure public prototyping rules on your Firestore console:\n",
              style: TextStyle(fontSize: 12, color: Color(0xFF475569)),
            ),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFF0F172A),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Text(
                  "rules_version = '2';\n"
                  "service cloud.firestore {\n"
                  "  match /databases/{database}/documents {\n"
                  "    match /vehicle_passes/{document} {\n"
                  "      allow read, write: if true;\n"
                  "    }\n"
                  "    match /sync_logs/{document} {\n"
                  "      allow read, write: if true;\n"
                  "    }\n"
                  "  }\n"
                  "}",
                  style: TextStyle(
                    fontFamily: "monospace",
                    fontSize: 11,
                    color: Color(0xFF38BDF8),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              "Note: For fully secure production environments, replace 'allow read, write: if true' with robust Firebase Authentication token checks.",
              style: TextStyle(
                fontSize: 11,
                fontStyle: FontStyle.italic,
                color: Color(0xFF64748B),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
