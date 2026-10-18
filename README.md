# WYRIX BOOK GENERATOR AI
Write one prompt and get a full book (Hindi, English or both). Uses many free AI models with automatic fallback. Exports PDF and EPUB.

## Build the APK (GitHub Actions)
1. Push this folder to a GitHub repo.
2. Open the Actions tab, run "Build APK", then download the `WYRIX-BOOK-GENERATOR-AI-APK` artifact.
(Or open the folder in Android Studio and run it.)

## Use
Settings -> add at least one free API key -> Fetch models -> New book -> write your prompt -> Generate book.
Models are discovered automatically from each provider. To add a provider, add one line to the list in `Providers.kt`.
