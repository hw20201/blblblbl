# ARCA floating glass dock overlay fix

## Changes
- Main page viewport now fills the available screen behind the floating dock.
- Removed the dock-top hard clipping from the main pager and page wrapper.
- Kept the dock itself as a later sibling layer, so it remains above the page and keeps its own rounded glass geometry.
- Updated the regression test to assert that the page scene extends behind the floating dock.

## Expected visual result
Cards and other page content continue naturally behind the translucent dock instead of being cut off at a horizontal boundary. The dock remains transparent so the underlying content can be seen through it.

## Validation
The targeted Gradle unit test could not be run in this environment because the Gradle 9.6.1 distribution could not be downloaded (network/DNS unavailable). Please run:
`./gradlew :app:testDebugUnitTest --tests 'com.github.arca.android.ui.components.MainDockContentInsetTest'`
