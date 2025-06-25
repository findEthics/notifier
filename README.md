# Notifier

A minimal Android launcher with notification management, optimized Spotify controls, and calendar integration. Built for performance with lazy initialization patterns and battery-efficient design.

## Features

### 🚀 Core Functionality
- **Minimal Android Launcher**: Can be set as default home launcher with clean, efficient interface
- **Smart Notification Management**: Centralized notification center with swipe-to-dismiss and tap-to-open functionality
- **Dynamic Spotify Integration**: Smart player visibility with dedicated connection button, 10-second timeout fallback, and 2-minute auto-disconnect
- **Smart Calendar Integration**: Intelligent event caching with battery-efficient daily refresh, past event filtering, and token expiration resilience
- **Quick App Launchers**: Fast access to WhatsApp, Claude Assistant, and Maps applications
- **Volume & Ring Mode Control**: Toggle between mute/unmute and ring/vibrate modes

### ⚡ Performance Optimizations
- **Lazy Initialization**: Spotify and Calendar components only load when needed
- **Fast App Startup**: ~50ms improvement from deferred expensive operations  
- **Dynamic UI Visibility**: Spotify controls hidden by default, shown only when connected
- **Intelligent Calendar Caching**: Daily event storage with midnight expiration and token expiration resilience
- **Smart Event Filtering**: Real-time past event filtering with 15-minute grace period
- **Contextual Permissions**: Calendar permissions only requested when calendar is accessed
- **Battery Efficient**: 2-minute auto-disconnect, minimal background processing, and smart connection management
- **Memory Optimized**: Features consume memory only when actively used

## Screenshots

*Add screenshots of your app here*

## Requirements

- Android 12+ (API level 31+)
- Spotify app installed for music control features (optional, lazy-loaded)
- Google account for calendar integration (optional, contextual setup)
- Notification access permission for notification management

## Permissions

The app uses a progressive permission strategy:

### Required Permissions
- `INTERNET` - For Google Calendar API and Spotify Web API access
- `BIND_NOTIFICATION_LISTENER_SERVICE` - To capture and manage system notifications

### Contextual Permissions (requested only when needed)
- `POST_NOTIFICATIONS` - Requested when calendar button is clicked for event reminders
- `SCHEDULE_EXACT_ALARM` - Requested for precise calendar reminder notifications

### Launcher Permissions (optional)
- `android.intent.category.HOME` - Allows app to be set as default Android launcher

## Installation

1. Clone the repository:
```bash
git clone https://github.com/findEthics/notifier.git
cd notifier
```

2. Open the project in Android Studio

3. Create a `local.properties` file in the root directory and add your API credentials:
```properties
GOOGLE_WEB_CLIENT_ID=your_google_client_id
GOOGLE_WEB_CLIENT_SECRET=your_google_client_secret
SPOTIFY_CLIENT_ID=your_spotify_client_id
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
1. Register your app at [Spotify Developer Dashboard](https://developer.spotify.com/dashboard/)
2. Add your Spotify Client ID to `local.properties`:
   ```properties
   SPOTIFY_CLIENT_ID=your_spotify_client_id_here
   ```
3. Configure your redirect URI in Spotify Dashboard: `notifier://callback`

**Note**: Spotify functionality uses lazy initialization - authentication only starts when user first interacts with music controls.

## Architecture

### Key Components

- **MainActivity**: Main launcher activity with lazy initialization patterns for optimal performance
- **AppNotificationListenerService**: Background service for notification interception and filtering  
- **SpotifyManager**: Battery-efficient Spotify integration with two-touch interaction system
- **SetupCalendar**: Smart Google Calendar integration with intelligent caching and lazy permission requests
- **CalendarCacheManager**: Battery-efficient daily event caching with automatic past event filtering
- **NotificationAdapter**: RecyclerView adapter for displaying captured notifications with swipe gestures

### Performance Architecture

- **Lazy Initialization Pattern**: Components only created when user interacts with them
- **Contextual Permission Requests**: Permissions requested with clear user context
- **Two-Touch Spotify Interaction**: Ensures reliable player status before actions
- **Memory Efficient**: Features only consume resources when actively used

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Android Views with ViewBinding
- **Networking**: Ktor Client for HTTP requests
- **Authentication**: 
  - Spotify Android SDK for music integration
  - Google OAuth 2.0 for calendar access
- **Data Storage**: SharedPreferences for app settings and intelligent calendar event caching
- **Architecture**: MVVM pattern with Repository pattern for data management

## Usage

### 🏠 Launcher Setup
1. **Set as Default Launcher**: Go to Settings > Apps > Default Apps > Home App > Select Notifier

### 📱 Daily Use
1. **Notification Management**: View all captured notifications, tap to open source app, or swipe to dismiss
2. **Dynamic Spotify Control**: 
   - Hidden by default for clean interface
   - Dedicated Spotify button: Shows controls and handles connection setup with 10-second timeout fallback
   - Control buttons: Play/pause, previous/next only visible when connected
   - Auto-hide: Controls disappear after 2 minutes of inactivity or when disconnected
   - Album art/text: Click to open Spotify app (when connected)
3. **Calendar Access**: Click current date display to access calendar with smart caching, token expiration resilience, and contextual permission requests
4. **Quick App Access**: Use buttons for WhatsApp, Claude Assistant, and Maps
5. **System Controls**: Toggle mute/vibrate modes with dedicated buttons

### 🔋 Performance Features
- **Fast Startup**: No background loading - features initialize only when needed
- **Dynamic UI**: Spotify controls hidden by default, shown only when needed
- **Battery Efficient**: 2-minute auto-disconnect, Spotify and Calendar only active during use
- **Smart Calendar Caching**: Events cached daily with automatic midnight expiration and token expiration fallback
- **Intelligent Event Filtering**: Past events filtered in real-time with 15-minute grace period
- **Smart Permissions**: Context-aware permission requests with user guidance

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

## Optimizations & Performance

### 🚀 Startup Performance
- **~50ms faster startup** through lazy initialization
- **No automatic service connections** - components load on-demand
- **Minimal onCreate() overhead** - deferred expensive operations

### 🔋 Battery Efficiency  
- **Dynamic Spotify UI** - controls hidden by default, shown only when connected
- **2-minute auto-disconnect** - faster disconnect for improved battery life
- **Lazy Spotify integration** - only connects when user interacts with controls
- **Intelligent Calendar Caching** - daily event storage minimizes API calls with token expiration resilience
- **Smart Event Management** - automatic past event filtering reduces processing
- **Contextual permission requests** - no permission dialogs on app startup
- **Smart connection management** - disconnects when not in use
- **Memory efficient** - features only exist when actively used

### 📱 User Experience
- **Clean Interface** - Spotify controls hidden until needed for minimal distraction
- **Streamlined Spotify interaction** - dedicated connection button with automatic fallback and smart visibility
- **Smart timeout handling** - 10-second fallback to direct app opening if connection fails
- **Progressive permission requests** - clear context for why permissions are needed
- **Calendar token resilience** - shows cached events when authentication expires
- **Instant feedback** - toast messages guide user through setup processes
- **Home launcher capability** - can replace default Android launcher

### 🛠️ Code Quality
- **Kotlin-first development** with modern Android patterns
- **Separation of concerns** - clear component boundaries
- **Error handling with fallbacks** - graceful degradation
- **Consistent naming conventions** and code organization

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
- Use the dedicated Spotify button (next to volume controls) to establish connection
- Spotify controls will appear when you click the connection button
- Wait up to 10 seconds for connection - app will automatically open Spotify if connection fails
- Controls automatically hide after 2 minutes of inactivity or when disconnected
- Check that the redirect URI matches in both the app and Spotify Developer Dashboard
- Control buttons only work after successful connection via the Spotify button

**Calendar events not loading**
- Click the current date display to trigger contextual permission requests
- Check for "Using cached events" or "Fetched fresh events" toast messages for status
- When tokens expire, cached events are shown automatically with "Using cached events" message
- If cache is stale, events will automatically refresh at midnight or on force refresh
- Verify Google API credentials are correctly configured in `local.properties`
- Ensure the Google Calendar API is enabled in Google Cloud Console
- Check that the OAuth consent screen is properly configured

**Reminders not firing**
- Grant "Allow setting alarms and reminders" permission in app settings
- Ensure the app is not being battery optimized (exempt from Doze mode)

## Contact

For questions or support, please open an issue on GitHub.
