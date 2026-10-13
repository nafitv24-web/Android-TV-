# 🖥️ NAFI TV 24 - PC Desktop (.exe / MSI) সংস্করণ গাইড

এই প্রজেক্টটি **Kotlin & Jetpack Compose** এ তৈরি করা হয়েছে। তাই এটি দিয়ে **Windows PC (.exe / .msi)** সফটওয়্যার তৈরি করতে নিচে উল্লেখিত সম্পূর্ণ রেডি কনফিগারেশন ও ফাইলগুলো অন্তর্ভুক্ত করা হয়েছে।

---

## 📁 PC Desktop প্রজেক্ট স্ট্রাকচার
আপনার প্রজেক্টের রুট ডিরেক্টরিতে `desktop/` মডিউলের মাধ্যমে পিসি ভার্সন পরিচালিত হবে:

```
├── app/                              # Android App (Mobile / TV)
├── desktop/                          # Windows PC Desktop App (.exe)
│   ├── build.gradle.kts              # Compose Desktop & .exe Packaging
│   └── src/
│       └── jvmMain/
│           └── kotlin/
│               └── com/example/desktop/
│                   ├── Main.kt       # Desktop Window & Launcher
│                   ├── DesktopApp.kt  # Desktop UI with Sidebar & Video Player
│                   └── player/
│                       └── DesktopPlayer.kt # VLCJ / Media Player
```

---

## 🚀 আপনার পিসিতে সরাসরি .exe ফাইল বানানোর নিয়ম

### পূর্বশর্ত:
1. আপনার কম্পিউটারে **JDK 17 বা JDK 21** ইনস্টল থাকতে হবে।
2. VLC Media Player (64-bit) ইনস্টল থাকতে হবে (ভিডিও স্মুথলি প্লে করার জন্য LibVLC লাইব্রেরি)।

### কমান্ডগুলো:

```bash
# ১. ডেভেলপমেন্ট টেস্ট রান (পিসিতে সরাসরি চালিয়ে দেখতে):
gradle :desktop:run

# ২. Windows Installer (.exe) তৈরি করতে:
gradle :desktop:packageExe

# ৩. Windows Setup Installer (.msi) তৈরি করতে:
gradle :desktop:packageMsi
```

তৈরি হওয়া `.exe` ইনস্টলার ফাইলটি পাবেন এই ফোল্ডারে:
📂 `desktop/build/compose/binaries/main/exe/`

---

## ⚡ বিশেষ সুবিধা:
- **একই Firebase ডাটাবেস**: অ্যান্ড্রয়েড অ্যাপে আপনি অ্যাডমিন প্যানেল থেকে যে M3U লিংক বা Tapmad JSON আপডেট করবেন, পিসি অ্যাপেও স্বয়ংক্রিয়ভাবে তাই লোড হবে।
- **ওয়াইডস্ক্রিন ও সাইডবার লেআউট**: পিসির মনিটরের জন্য বিশেষ সাইডবার ও গ্রিড লেআউট।
- **HD ও 4K ফুলস্ক্রিন প্লেয়ার**: কিবোর্ড শর্টকাট (F11 দিয়ে ফুলস্ক্রিন, Space দিয়ে পজ/প্লে)।
