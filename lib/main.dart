import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'theme/app_theme.dart';
import 'providers/player_provider.dart';
import 'services/cloud_backend_service.dart';
import 'services/language_service.dart';
import 'services/r2_storage_service.dart';
import 'services/ad_service.dart';
import 'screens/main_navigation_screen.dart';
import 'screens/auth/auth_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Google AdMob for Monetization
  await AdService().initialize();

  // Initialize background audio playback with system notification controls
  try {
    await JustAudioBackground.init(
      androidNotificationChannelId: 'com.musixa.player.channel.audio',
      androidNotificationChannelName: 'Musixa Music Playback',
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: true,
      androidNotificationIcon: 'mipmap/ic_launcher',
    );
  } catch (_) {}

  // Initialize Cloud & Authentication Backend
  await R2StorageService().init();
  final backend = CloudBackendService();
  await backend.init();
  await LanguageService().init();

  // Set system UI to immersive dark styling
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF131C2E),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const MusixaPlayerApp());
}

class MusixaPlayerApp extends StatelessWidget {
  const MusixaPlayerApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<PlayerProvider>(
          create: (_) => PlayerProvider(),
        ),
        ChangeNotifierProvider<CloudBackendService>.value(
          value: CloudBackendService(),
        ),
        ChangeNotifierProvider<R2StorageService>.value(
          value: R2StorageService(),
        ),
        ChangeNotifierProvider<LanguageService>.value(
          value: LanguageService(),
        ),
        ChangeNotifierProvider<AdService>.value(
          value: AdService(),
        ),
      ],
      child: AnimatedBuilder(
        animation: LanguageService(),
        builder: (context, _) {
          return MaterialApp(
            title: 'Musixa Player',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.darkTheme,
            home: const UserAppRootView(),
          );
        },
      ),
    );
  }
}

class UserAppRootView extends StatefulWidget {
  const UserAppRootView({Key? key}) : super(key: key);

  @override
  State<UserAppRootView> createState() => _UserAppRootViewState();
}

class _UserAppRootViewState extends State<UserAppRootView> {
  // Google Play Compliant: Allow offline music playback & equalizer out of the box
  @override
  Widget build(BuildContext context) {
    return const MainNavigationScreen();
  }
}
