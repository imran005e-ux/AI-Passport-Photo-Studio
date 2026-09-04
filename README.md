# Passport Photo Studio AI v2

AI upgrade:
- Google ML Kit Subject Segmentation for automatic foreground/background separation.
- White background compositing.
- Google ML Kit Face Detection for automatic face-aware passport cropping.
- 3.5 x 4.5 cm target at 300 DPI (413 x 531 px).
- Per-photo copy counts.
- Adjustable spacing.
- Thin black cutting stroke.
- Automatic A4 pagination.
- Share/print-ready PDF chooser.

ML Kit subject segmentation is a beta API and requires Android API 24+. Its model is downloaded through Google Play services on first use. Face detection is bundled in this build. See official Google documentation for current API details.
