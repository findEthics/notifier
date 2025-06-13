# Notifier

A comprehensive Android notification management app with Spotify integration and Google Calendar support.

## Features

- **Smart Notification Management**: Centralized notification center that captures, filters, and organizes notifications from WhatsApp, Mudita apps, and the Notifier app itself
- **Spotify Integration**: Full media control with play/pause, track navigation, and artist/album information display
- **Google Calendar Integration**: View upcoming events and set custom reminders with exact alarm scheduling
- **Quick Action Buttons**: Fast access to WhatsApp, Claude Assistant, and Maps applications
- **Volume & Ring Mode Control**: Toggle between mute/unmute and ring/vibrate modes directly from the app
- **Battery Status Display**: Real-time battery percentage monitoring
- **Swipe to Dismiss**: Remove notifications with intuitive swipe gestures

## Screenshots

*Add screenshots of your app here*

## Requirements

- Android 12+ (API level 31+)
- Spotify app installed for music control features
- Google account for calendar integration
- Notification access permission

## Permissions

The app requires the following permissions:

- `INTERNET` - For Google Calendar API and Spotify Web API access
- `POST_NOTIFICATIONS` - To display calendar event reminders
- `SCHEDULE_EXACT_ALARM` - For precise reminder notifications
- `BIND_NOTIFICATION_LISTENER_SERVICE` - To capture and manage system notifications

## Installation

1. Clone the repository:
```bash
git clone https://github.com/findEthics/notifier.git
cd notifier
```

2. Open the project in Android Studio

3. Create a `local.properties` file in the root directory and add your Google API credentials:
```properties
GOOGLE_WEB_CLIENT_ID=your_google_client_id
GOOGLE_WEB_CLIENT_SECRET=your_google_client_secret
```

4. Build and run the app on your Android device

## Configuration

### Google Calendar Setup
1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google Calendar API
4. Create OAuth 2.0 credentials for Android
5. Add your credentials to `local.properties`

### Spotify Setup
The app includes a pre-configured Spotify Client ID. For production use, you should:
1. Register your app at [Spotify Developer Dashboard](https://developer.spotify.com/dashboard/)
2. Update the `CLIENT_ID` in `SpotifyManager.kt`
3. Configure your redirect URI: `notifier://callback`

## Architecture

### Key Components

- **MainActivity**: Main activity handling UI interactions and system controls
- **AppNotificationListenerService**: Background service for notification interception and filtering
- **SpotifyManager**: Handles Spotify authentication and media control
- **SetupCalendar**: Manages Google Calendar integration and OAuth flow
- **NotificationAdapter**: RecyclerView adapter for displaying captured notifications

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Android Views with ViewBinding
- **Networking**: Ktor Client for HTTP requests
- **Authentication**: 
  - Spotify Android SDK for music integration
  - Google OAuth 2.0 for calendar access
- **Data Storage**: SharedPreferences for app settings
- **Architecture**: MVVM pattern with Repository pattern for data management

## Usage

1. **Initial Setup**: Grant notification access and alarm permissions when prompted
2. **Notification Management**: View all captured notifications in the main list, tap to open source app, or swipe to dismiss
3. **Spotify Control**: Use media buttons to control playback, tap album art/track info to open Spotify
4. **Calendar**: Tap calendar button to view upcoming events and set reminders
5. **Quick Actions**: Use floating action buttons for WhatsApp, Assistant, and Maps access
6. **System Controls**: Toggle mute/vibrate modes using the dedicated buttons

## Dependencies

### Core Android
- androidx.core:core-ktx:1.8.0
- androidx.appcompat:appcompat:1.6.1
- com.google.android.material:material:1.5.0
- androidx.constraintlayout:constraintlayout:2.1.3

### Networking & Serialization
- io.ktor:ktor-client-core:2.3.12
- io.ktor:ktor-client-android:2.3.12
- io.ktor:ktor-client-content-negotiation:2.3.12
- io.ktor:ktor-serialization-kotlinx-json:2.3.12
- com.google.code.gson:gson:2.10.1

### Spotify Integration
- Spotify App Remote SDK (local AAR)
- com.spotify.android:auth:1.2.5

### Additional Libraries
- androidx.browser:browser:1.7.0 (Custom Tabs for OAuth)
- androidx.localbroadcastmanager:localbroadcastmanager:1.1.0

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Spotify for their Android SDK and App Remote API
- Google for Calendar API and OAuth libraries
- Material Design for UI components and icons

## Troubleshooting

### Common Issues

**Notifications not appearing**
- Ensure notification listener permission is granted in Settings > Notification access
- Verify the app is in the allowed packages list in `AppNotificationListenerService.kt`

**Spotify not connecting**
- Make sure Spotify app is installed and logged in
- Check that the redirect URI matches in both the app and Spotify Developer Dashboard

**Calendar events not loading**
- Verify Google API credentials are correctly configured in `local.properties`
- Ensure the Google Calendar API is enabled in Google Cloud Console
- Check that the OAuth consent screen is properly configured

**Reminders not firing**
- Grant "Allow setting alarms and reminders" permission in app settings
- Ensure the app is not being battery optimized (exempt from Doze mode)

## Contact

For questions or support, please open an issue on GitHub.
