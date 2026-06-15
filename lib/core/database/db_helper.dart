import 'dart:async';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'package:flutter/foundation.dart';

/// Elite local database engine orchestrating vehicle pass tables,
/// synchronization queues, and defensive real-time verification lookups.
class DbHelper {
  static const String _tag = "DbHelper";
  static const String _dbName = "pakpass_local.db";
  static const int _dbVersion = 1;

  static Database? _database;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, _dbName);

    return await openDatabase(
      path,
      version: _dbVersion,
      onCreate: _onCreate,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    debugPrint("$_tag: Initializing local SQLite schema records...");
    await db.execute('''
      CREATE TABLE vehicle_passes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        unique_id TEXT UNIQUE NOT NULL,
        owner_name TEXT NOT NULL,
        cnic TEXT NOT NULL,
        vehicle_no TEXT NOT NULL,
        phone TEXT,
        expiry_date TEXT NOT NULL,
        is_revoked INTEGER DEFAULT 0,
        created_at TEXT NOT NULL,
        last_modified TEXT NOT NULL,
        device_id TEXT
      )
    ''');
  }

  /// Bulk upsert supporting synchronization from Firebase Cloud Firestore standard schema
  Future<void> insertOrReplaceVehiclePass(Map<String, dynamic> passRow) async {
    final db = await database;
    await db.insert(
      'vehicle_passes',
      passRow,
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  /// Retrieve full local records database modified after the last successful sync timestamp.
  Future<List<Map<String, dynamic>>> getModifiedSince(String lastSyncTime) async {
    final db = await database;
    return await db.query(
      'vehicle_passes',
      where: 'last_modified > ?',
      whereArgs: [lastSyncTime],
    );
  }

  /// Look up a specific vehicle pass record by its exact identifier.
  Future<Map<String, dynamic>?> getPassByUniqueId(String uniqueId) async {
    final db = await database;
    final sanitizedId = uniqueId.trim(); // Defensive String Sanitization

    final List<Map<String, dynamic>> results = await db.query(
      'vehicle_passes',
      where: 'unique_id = ?',
      whereArgs: [sanitizedId],
    );

    if (results.isEmpty) {
      return null;
    }
    return results.first;
  }

  /// Refactored Local Validation & Verification Query Query Method (Defensive security verification block)
  /// Checks for:
  /// 1. Record existence in the database.
  /// 2. Active status: verifies that `is_revoked != 1` (treating 0 or null as active).
  /// 3. Safe Expiration Check: Includes a lenient 5-minute clock drift margin.
  Future<ValidationResult> verifyVehiclePass(String rawUniqueId) async {
    final cleanUniqueId = rawUniqueId.trim(); // Trim scanning inputs to exclude hidden newlines and spaces
    debugPrint("$_tag: Starting verification query loop for clean UID: '$cleanUniqueId'");

    try {
      final pass = await getPassByUniqueId(cleanUniqueId);
      if (pass == null) {
        debugPrint("$_tag Validation Error: No matching vehicle record found for UID '$cleanUniqueId'");
        return ValidationResult(
          isValid: false,
          reasonUrdu: "پاس ریکارڈ لوکل ڈیٹا بیس میں موجود نہیں ہے",
          reasonEnglish: "INVALID - Pass record not found in local database database",
        );
      }

      // Check Revocation Status: strictly invalid only if explicitly set to 1
      final int isRevokedVal = pass['is_revoked'] as int? ?? 0;
      if (isRevokedVal == 1) {
        debugPrint("$_tag Validation Error: Pass '$cleanUniqueId' was revoked by administrator.");
        return ValidationResult(
          isValid: false,
          reasonUrdu: "پاس منسوخ کر دیا گیا ہے",
          reasonEnglish: "REVOKED - Pass manually revoked by Admin",
        );
      }

      // Check Expiration Status with a 5-minute clock drift cushion
      final String expiryDateStr = pass['expiry_date'] as String? ?? '';
      final DateTime? expiryDate = DateTime.tryParse(expiryDateStr);

      if (expiryDate != null) {
        // Build end of day representation (23:59:59.999 UTC) of expiration date
        final endOfDayExpiry = DateTime.utc(
          expiryDate.year,
          expiryDate.month,
          expiryDate.day,
          23,
          59,
          59,
          999,
        );

        final now = DateTime.now().toUtc();
        // Shift check threshold back by 5 minutes to accommodate minor peer clock variance
        final thresholdWithDrift = now.subtract(const Duration(minutes: 5));

        if (thresholdWithDrift.isAfter(endOfDayExpiry)) {
          debugPrint("$_tag Validation Error: Expiration mismatch. Pass expired on '$expiryDateStr'. Current: '$now'");
          return ValidationResult(
            isValid: false,
            reasonUrdu: "پاس کی میعاد ختم ہو چکی ہے",
            reasonEnglish: "EXPIRED - Pass expired on $expiryDateStr",
          );
        }
      } else {
        // Fallback string literal check if date parse fails
        final todayStr = DateTime.now().toUtc().toIso8601String().substring(0, 10);
        if (expiryDateStr.compareTo(todayStr) < 0) {
          debugPrint("$_tag Validation Error: Expiration string fallback fail. $expiryDateStr < $todayStr");
          return ValidationResult(
            isValid: false,
            reasonUrdu: "پاس کی میعاد ختم ہو چکی ہے (اسٹرنگ میچ)",
            reasonEnglish: "EXPIRED - Pass has expired (comparative string validation failed)",
          );
        }
      }

      // Successfully validated the record
      debugPrint("$_tag: Validation succeeded for pass ID '$cleanUniqueId'");
      return ValidationResult(
        isValid: true,
        reasonUrdu: "پاس درست اور فعال ہے",
        reasonEnglish: "VALID - Active and authorized to pass",
        passData: pass,
      );

    } catch (e) {
      debugPrint("$_tag: Critical error in local database inquiry process: $e");
      return ValidationResult(
        isValid: false,
        reasonUrdu: "تصدیقی عمل میں ایرر آگیا ہے",
        reasonEnglish: "ERROR - Critical exception occurred inside local inquiry pipeline",
      );
    }
  }
}

/// Helper container class to pass descriptive, user-facing verification reports back to UI controllers.
class ValidationResult {
  final bool isValid;
  final String reasonUrdu;
  final String reasonEnglish;
  final Map<String, dynamic>? passData;

  ValidationResult({
    required this.isValid,
    required this.reasonUrdu,
    required this.reasonEnglish,
    this.passData,
  });
}
