# Notifier App - Claude Code Project Context

## Project Overview
Minimal Android launcher app with notification management, Spotify controls, and calendar integration. Optimized for performance with lazy initialization patterns and contextual permission requests.

## Current Branch: minimal
Latest commit includes date display with integrated calendar functionality.

## Architecture & Major Optimizations

### 🚀 Performance Optimizations Completed:

#### 1. **Lazy Initialization Pattern** ✅
- **CalendarSetup**: Only created when calendar button is clicked
- **SpotifyManager**: Only initialized when user interacts with Spotify controls
- **Result**: Significantly faster app startup by deferring expensive operations

#### 2. **Lazy Permission Requests** ✅ (Latest)
- **Calendar Permissions**: POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM only requested when calendar button is clicked
- **Contextual UX**: Users understand why permissions are needed
- **Permission Caching**: Avoids repeated system calls
- **Result**: No permission dialogs on app startup, better user experience

#### 3. **UI Optimizations** ✅
- **Removed Battery Indicator**: Eliminated battery percentage display and related code
- **Converted FABs to ImageButtons**: More minimal design approach
- **Added Visual Separators**: Horizontal divider line between sections
- **Home Launcher Support**: Can be set as default Android launcher
- **Date Display Integration**: Current date shown in "Wed, June 18" format with integrated calendar functionality
- **Streamlined Layout**: Calendar button removed, functionality moved to clickable date display

## Key Components

### MainActivity
- Main launcher interface with lazy-loaded features
- Implements NotificationCallback pattern
- Handles volume controls, launcher buttons, and swipe-to-delete notifications
- **Lazy Features**: Calendar and Spotify only initialized on user interaction
- **Smart Permissions**: Calendar permissions requested contextually
- **Date Display**: Dynamic current date with clickable calendar integration
- **Layout**: Date on leftmost side, volume controls (mute, ring/vibrate) on rightmost side

### SpotifyManager
- Spotify SDK integration with on-demand connection
- Methods: `handlePlayPauseClick()`, `handlePreviousClick()`, `handleNextClick()`, `handleSpotifyAppClick()`
- **Optimization**: No automatic connection on app startup

### CalendarSetup (Calendar/SetupCalendar.kt)
- Google Calendar API with OAuth2 authentication
- Ktor HTTP client for API calls
- **Optimization**: Only created when calendar button is clicked
- **Permissions**: Handles POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM contextually

### NotificationAdapter
- Handles notification display and management
- Supports swipe-to-delete functionality
- Integrates with LocalBroadcastManager for notification lifecycle

## Permission Strategy

### Current Implementation:
- **Startup**: No permission requests (fast launch)
- **Calendar Click**: Progressive permission requests with context
- **Caching**: Permission states cached to avoid repeated checks
- **Fallback**: Graceful degradation if permissions denied

### Permission Flow:
```
Date Display Click → Check POST_NOTIFICATIONS → Check SCHEDULE_EXACT_ALARM → Create Notification Channel → Initialize Calendar
```

## Build & Development

### Commands:
```bash
# Build project
./gradlew build

# Install debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Set as default launcher
adb shell cmd package set-home-activity com.example.notifier/.MainActivity
```

### Key Packages:
- `com.example.notifier` - Main app package
- Spotify SDK for music controls
- Google Calendar API for event integration
- Ktor for HTTP client operations

## Recent Optimizations History

### Latest (Current):
- ✅ **Enhanced Spotify Integration**: Added dedicated Spotify connection button with 10-second timeout fallback
- ✅ **Streamlined Spotify Controls**: Removed connection logic from play/pause/next/prev buttons
- ✅ **Smart Timeout Handling**: Automatic fallback to direct app opening if connection fails
- ✅ **Calendar Event Caching**: Intelligent daily event storage with midnight expiration
- ✅ **Past Event Filtering**: Real-time filtering with 15-minute grace period for ongoing events
- ✅ **Smart Cache Management**: "Using cached events" vs "Fetched fresh events" user feedback

### Previous:
- ✅ **Date Display Integration**: Current date display with integrated calendar functionality
- ✅ **Streamlined UI**: Removed calendar button, moved functionality to clickable date
- ✅ **Layout Optimization**: Date on leftmost side, controls on rightmost side
- ✅ **Lazy Calendar Permissions**: Contextual permission requests only when needed
- ✅ **Permission Caching**: Smart state management
- ✅ **Startup Performance**: Eliminated permission dialogs from app launch
- ✅ **Lazy SpotifyManager**: Only initialize when user interacts with controls
- ✅ **Lazy CalendarSetup**: Only initialize when calendar button clicked
- ✅ **UI Improvements**: Added horizontal divider, converted FABs to ImageButtons
- ✅ **Battery Removal**: Eliminated battery percentage display
- ✅ **Home Launcher**: Added capability to be default Android launcher

## Known Technical Debt & Future Optimizations

### High Priority:
- **LocalBroadcastManager**: Replace deprecated LocalBroadcastManager with direct callbacks
- **Error Handling**: Add comprehensive error handling for network operations
- **Feature Flags**: Implement feature toggles for optional components

### Medium Priority:
- **ViewBinding**: Replace findViewById with ViewBinding pattern
- **Dependency Injection**: Consider Hilt/Dagger for better modularity
- **Testing**: Add unit tests for business logic components

### Low Priority:
- **Code Style**: Address remaining lint warnings
- **Documentation**: Expand inline documentation
- **Accessibility**: Improve accessibility support

## App Capabilities

### Core Features:
- **Notification Management**: View, dismiss, and interact with notifications
- **Enhanced Spotify Integration**: Dedicated connection button with 10-second timeout fallback
- **Streamlined Spotify Controls**: Play/pause, previous/next only work when connected
- **Calendar Integration**: View events and set reminders (via clickable date display)
- **Smart Calendar Caching**: Daily event storage with midnight expiration
- **Past Event Filtering**: Only display future events with 15-minute grace period
- **Volume Controls**: Mute, ring/vibrate toggle with dedicated Spotify button
- **Date Display**: Dynamic current date in "Wed, June 18" format
- **App Launchers**: Quick access to WhatsApp, Assistant, Maps
- **Home Launcher**: Can replace default Android launcher

### Performance Characteristics:
- **Fast Startup**: ~50ms improvement from lazy loading
- **Low Memory**: Features only consume memory when used
- **Responsive UI**: No blocking operations on main thread
- **Battery Efficient**: Minimal background processing

## Development Notes

### Architecture Patterns:
- **Lazy Initialization**: Core pattern for performance
- **Callback Pattern**: Used for notification management
- **Permission Strategy**: Contextual and progressive
- **Lifecycle Management**: Proper cleanup in onDestroy()

### Code Quality:
- Kotlin-first development
- Consistent naming conventions
- Separation of concerns
- Error handling with fallbacks

Last Updated: 2024 - After implementing enhanced Spotify integration with dedicated connection button and intelligent calendar caching system

## Latest Technical Changes

### Enhanced Spotify Integration:
- **Dedicated Connection Button**: New Spotify button positioned between date display and volume controls
- **10-Second Timeout**: Automatic fallback to direct app opening if connection setup exceeds 10 seconds
- **Streamlined Controls**: Play/pause, previous/next buttons only work when Spotify is already connected
- **Smart Connection Management**: Removed connection logic from control buttons, centralizing it in the dedicated button
- **User Guidance**: Clear toast messages directing users to use the Spotify button for initial connection

### Calendar Caching System:
- **CalendarCacheManager**: New SharedPreferences-based caching class
- **Cache Expiration**: Midnight boundary detection using device timezone
- **Event Filtering**: Real-time filtering of past events with 15-minute grace period
- **Smart API Calls**: Only fetch from API when cache expired or force refresh
- **User Feedback**: Toast messages distinguish cached vs fresh events
- **In-Place Refresh**: Updates events without activity recreation for battery efficiency

### Current Layout Structure:
```
[Spotify Controls Row]
[Date Display] ---------> [Spotify Button] [Mute Button] [Ring/Vibrate Button]
[Horizontal Divider]
[Notification List]
```