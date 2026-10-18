# WYRIX BOOK GENERATOR AI
Ek prompt -> poori book (Hindi/English/Dono), free AI models fallback ke saath, PDF + EPUB export.

## APK banana (GitHub Actions)
1. Is folder ko ek GitHub repo me push karein.
2. Actions tab -> "Build APK" chalayein -> Artifacts se `WYRIX-BOOK-GENERATOR-AI-APK` download karein.
(Ya Android Studio me folder open karke Run karein.)

## Use
Settings me kam se kam ek free API key daalein -> Nayi book -> prompt likhein -> Book banao.
Models hardcode nahi hain: app har provider ki key se uske free models khud fetch karti hai (Settings -> 'Models fetch karo'). Naya provider `Providers.kt` ki list me ek line se jud jaata hai.
