import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

/// UpdateService for Musixa Player (Google Play Store Edition)
/// Strictly complies with Google Play Policy: Device and Network Abuse.
/// Updates are handled seamlessly through Google Play Store.
class UpdateService {
  static const String playStoreUrl = 'https://play.google.com/store/apps/details?id=com.musixa.player';

  static Future<void> checkForUpdate(BuildContext context, {bool autoPrompt = false, bool isAdmin = false}) async {
    if (autoPrompt) return; // Google Play handles background auto-updates automatically

    if (context.mounted) {
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          backgroundColor: const Color(0xFF131A2A),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Row(
            children: const [
              Icon(Icons.verified_rounded, color: Color(0xFF10B981), size: 24),
              SizedBox(width: 8),
              Text(
                'Musixa Player Up to Date',
                style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          content: const Text(
            'You are running the official Google Play Store release (v1.0.0). All future updates will be delivered safely via Google Play.',
            style: TextStyle(color: Colors.white70, fontSize: 13),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('OK', style: TextStyle(color: Color(0xFF00E5FF), fontWeight: FontWeight.bold)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF10B981),
                foregroundColor: Colors.white,
              ),
              onPressed: () {
                Navigator.pop(ctx);
                openPlayStoreListing();
              },
              child: const Text('View on Play Store', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      );
    }
  }

  static Future<void> openPlayStoreListing() async {
    final uri = Uri.parse(playStoreUrl);
    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    } catch (_) {}
  }
}
