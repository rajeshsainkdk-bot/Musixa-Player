import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// AdService manages Google AdMob Monetization for Musixa Player:
/// - Banner Ads (Bottom bar)
/// - Rewarded Video Ads (Unlock Pro Equalizer & 3D Bass Boost)
/// - In-App Ad-Free status
class AdService extends ChangeNotifier {
  static final AdService _instance = AdService._internal();
  factory AdService() => _instance;
  AdService._internal();

  bool _isAdFree = false;
  bool get isAdFree => _isAdFree;

  BannerAd? _bannerAd;
  bool _isBannerAdLoaded = false;
  BannerAd? get bannerAd => _bannerAd;
  bool get isBannerAdLoaded => _isBannerAdLoaded && !_isAdFree;

  RewardedAd? _rewardedAd;
  bool _isRewardedAdLoaded = false;
  bool get isRewardedAdLoaded => _isRewardedAdLoaded;

  DateTime? _proUnlockedUntil;
  bool get isProFeatureUnlocked {
    if (_isAdFree) return true;
    if (_proUnlockedUntil != null) {
      return DateTime.now().isBefore(_proUnlockedUntil!);
    }
    return false;
  }

  // Official Google AdMob Test Ad Unit IDs (Guaranteed to work without bans)
  static const String testBannerAdUnitId = 'ca-app-pub-3940256099942544/6300978111';
  static const String testRewardedAdUnitId = 'ca-app-pub-3940256099942544/5224354917';

  Future<void> initialize() async {
    try {
      await MobileAds.instance.initialize();
      final prefs = await SharedPreferences.getInstance();
      _isAdFree = prefs.getBool('is_ad_free') ?? false;

      final proMillis = prefs.getInt('pro_unlocked_until') ?? 0;
      if (proMillis > 0) {
        _proUnlockedUntil = DateTime.fromMillisecondsSinceEpoch(proMillis);
      }

      if (!_isAdFree) {
        loadBannerAd();
        loadRewardedAd();
      }
    } catch (e) {
      debugPrint('AdService initialize error: $e');
    }
  }

  /// Load Bottom Banner Ad
  void loadBannerAd() {
    if (_isAdFree) return;
    _bannerAd = BannerAd(
      adUnitId: testBannerAdUnitId,
      size: AdSize.banner,
      request: const AdRequest(),
      listener: BannerAdListener(
        onAdLoaded: (ad) {
          _isBannerAdLoaded = true;
          notifyListeners();
        },
        onAdFailedToLoad: (ad, error) {
          debugPrint('BannerAd failed to load: $error');
          ad.dispose();
          _isBannerAdLoaded = false;
          notifyListeners();
        },
      ),
    );
    _bannerAd?.load();
  }

  /// Load Rewarded Video Ad
  void loadRewardedAd() {
    if (_isAdFree) return;
    RewardedAd.load(
      adUnitId: testRewardedAdUnitId,
      request: const AdRequest(),
      rewardedAdLoadCallback: RewardedAdLoadCallback(
        onAdLoaded: (ad) {
          _rewardedAd = ad;
          _isRewardedAdLoaded = true;
          notifyListeners();
        },
        onAdFailedToLoad: (error) {
          debugPrint('RewardedAd failed to load: $error');
          _rewardedAd = null;
          _isRewardedAdLoaded = false;
        },
      ),
    );
  }

  /// Show Rewarded Video Ad with callback
  Future<bool> showRewardedAd({required VoidCallback onRewardEarned}) async {
    if (_rewardedAd != null) {
      _rewardedAd!.fullScreenContentCallback = FullScreenContentCallback(
        onAdDismissedFullScreenContent: (ad) {
          ad.dispose();
          loadRewardedAd();
        },
        onAdFailedToShowFullScreenContent: (ad, error) {
          ad.dispose();
          loadRewardedAd();
        },
      );

      _rewardedAd!.show(
        onUserEarnedReward: (ad, reward) {
          onRewardEarned();
        },
      );
      _rewardedAd = null;
      _isRewardedAdLoaded = false;
      return true;
    } else {
      loadRewardedAd();
      return false;
    }
  }

  /// Show Rewarded Video Ad to unlock Pro features (e.g. 3D Bass Boost / Equalizer for 24h)
  void showRewardedAdForProUnlock({required VoidCallback onUnlocked}) {
    if (_rewardedAd != null) {
      _rewardedAd!.fullScreenContentCallback = FullScreenContentCallback(
        onAdDismissedFullScreenContent: (ad) {
          ad.dispose();
          loadRewardedAd();
        },
        onAdFailedToShowFullScreenContent: (ad, error) {
          ad.dispose();
          loadRewardedAd();
        },
      );

      _rewardedAd!.show(
        onUserEarnedReward: (ad, reward) async {
          final until = DateTime.now().add(const Duration(hours: 24));
          _proUnlockedUntil = until;
          final prefs = await SharedPreferences.getInstance();
          await prefs.setInt('pro_unlocked_until', until.millisecondsSinceEpoch);
          notifyListeners();
          onUnlocked();
        },
      );
      _rewardedAd = null;
      _isRewardedAdLoaded = false;
    } else {
      loadRewardedAd();
    }
  }

  /// Set Ad-Free Status (after In-App Purchase)
  Future<void> setAdFree(bool value) async {
    _isAdFree = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_ad_free', value);
    if (_isAdFree) {
      _bannerAd?.dispose();
      _bannerAd = null;
      _isBannerAdLoaded = false;
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _bannerAd?.dispose();
    _rewardedAd?.dispose();
    super.dispose();
  }
}

/// Small Banner Ad Widget for easy placement anywhere in UI
class MusixaBannerAdWidget extends StatelessWidget {
  const MusixaBannerAdWidget({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: AdService(),
      builder: (context, _) {
        final adService = AdService();
        if (!adService.isBannerAdLoaded || adService.bannerAd == null) {
          return const SizedBox.shrink();
        }

        return Container(
          width: adService.bannerAd!.size.width.toDouble(),
          height: adService.bannerAd!.size.height.toDouble(),
          alignment: Alignment.center,
          margin: const EdgeInsets.symmetric(vertical: 4),
          child: AdWidget(ad: adService.bannerAd!),
        );
      },
    );
  }
}

