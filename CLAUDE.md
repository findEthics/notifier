# Notifier App - Claude Code Project Context

## Project Overview
Minimal Android launcher app with notification management, dynamic Spotify controls, and intelligent calendar integration. Optimized for performance with lazy initialization patterns, contextual permission requests, and smart UI visibility management.

## Current Branch: minimal
Latest commit includes dynamic Spotify player visibility control, 2-minute auto-disconnect, and calendar token expiration resilience.

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

#### 4. **Dynamic Spotify Player Visibility** ✅ (Latest)
- **Hidden by Default**: Spotify controls start hidden for clean interface
- **Smart Visibility**: Controls appear only when user clicks Spotify button
- **Auto-Hide on Disconnect**: Controls disappear when Spotify disconnects or times out
- **2-Minute Auto-Disconnect**: Reduced from 5 minutes for better battery efficiency
- **Connection Status Callbacks**: SpotifyManager notifies MainActivity of visibility changes
- **Result**: Cleaner UI, better battery life, contextual control display

#### 5. **Calendar Token Expiration Resilience** ✅ (Latest)
- **Cache Fallback**: Shows cached events when authentication tokens expire
- **Graceful Degradation**: Users see events even when API calls fail
- **Smart Cache Checking**: Checks cache before forcing re-authentication
- **User Feedback**: Clear toast messages for cache vs fresh events
- **Result**: More reliable calendar experience, reduced authentication friction

## Key Components

### MainActivity
- Main launcher interface with lazy-loaded features
- Implements NotificationCallback pattern
- Handles volume controls, launcher buttons, and swipe-to-delete notifications
- **Lazy Features**: Calendar and Spotify only initialized on user interaction
- **Smart Permissions**: Calendar permissions requested contextually
- **Date Display**: Dynamic current date with clickable calendar integration
- **Spotify Visibility Control**: `showSpotifyControls()` and `hideSpotifyControls()` methods
- **Layout**: Date on leftmost side, Spotify button, volume controls (mute, ring/vibrate) on rightmost side

### SpotifyManager
- Spotify SDK integration with on-demand connection and visibility callbacks
- Methods: `handlePlayPauseClick()`, `handlePreviousClick()`, `handleNextClick()`, `handleSpotifyAppClick()`
- **Constructor**: Accepts optional `onDisconnectCallback` for visibility control
- **Auto-Disconnect**: 2-minute timer with callback to hide controls
- **Optimization**: No automatic connection on app startup, smart visibility management

### CalendarSetup (Calendar/SetupCalendar.kt)
- Google Calendar API with OAuth2 authentication
- Ktor HTTP client for API calls
- **Optimization**: Only created when calendar button is clicked
- **Permissions**: Handles POST_NOTIFICATIONS and SCHEDULE_EXACT_ALARM contextually
- **Token Resilience**: Falls back to cached events when authentication tokens expire
- **Smart Cache Logic**: Checks cache before forcing re-authentication in `handleCalendarButtonClick()`

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
- ✅ **Dynamic Spotify Player Visibility**: Controls hidden by default, shown only when connected, auto-hide on disconnect
- ✅ **2-Minute Auto-Disconnect**: Reduced from 5 minutes for better battery efficiency with visibility callbacks
- ✅ **Calendar Token Expiration Resilience**: Shows cached events when authentication tokens expire
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
- **Dynamic Spotify Integration**: Smart visibility control, 2-minute auto-disconnect, dedicated connection button with 10-second timeout fallback
- **Streamlined Spotify Controls**: Play/pause, previous/next only visible and work when connected
- **Calendar Integration**: View events and set reminders (via clickable date display) with token expiration resilience
- **Smart Calendar Caching**: Daily event storage with midnight expiration and fallback for expired tokens
- **Past Event Filtering**: Only display future events with 15-minute grace period
- **Volume Controls**: Mute, ring/vibrate toggle with dedicated Spotify button
- **Date Display**: Dynamic current date in "Wed, June 18" format
- **App Launchers**: Quick access to WhatsApp, Assistant, Maps
- **Home Launcher**: Can replace default Android launcher

### Performance Characteristics:
- **Fast Startup**: ~50ms improvement from lazy loading
- **Clean Interface**: Dynamic UI visibility reduces visual clutter
- **Low Memory**: Features only consume memory when used
- **Responsive UI**: No blocking operations on main thread
- **Battery Efficient**: 2-minute auto-disconnect, minimal background processing, smart visibility management

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

Last Updated: 2024 - After implementing dynamic Spotify player visibility control, 2-minute auto-disconnect, and calendar token expiration resilience

## Latest Technical Changes

### Dynamic Spotify Player Visibility Control:
- **Hidden by Default**: Controls start hidden (`View.GONE`) for clean interface
- **Show on Connection**: `showSpotifyControls()` called when user clicks Spotify button
- **Hide on Disconnect**: `hideSpotifyControls()` called via callback when disconnected
- **Constructor Callback**: SpotifyManager accepts `onDisconnectCallback: (() -> Unit)?` parameter
- **Auto-Hide on Timeout**: Controls hidden if connection fails after 10-second timeout
- **Battery Optimization**: 2-minute auto-disconnect (reduced from 5 minutes)

### Calendar Token Expiration Resilience:
- **Cache Fallback Logic**: In `handleCalendarButtonClick()`, checks cache before triggering authentication
- **Graceful Token Handling**: When `getValidAccessToken()` returns null, shows cached events instead of forcing auth
- **User Experience**: "Using cached events" toast when showing fallback data
- **Seamless Operation**: Users see calendar events even when tokens expire
- **Smart Recovery**: Re-authentication only required if both token AND cache are invalid

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
[Spotify Controls Row] ← Hidden by default, shown when connected
[Date Display] ---------> [Spotify Button] [Mute Button] [Ring/Vibrate Button]
[Horizontal Divider]
[Notification List]
```

### Key Code Changes:
- **MainActivity.kt**: Added `showSpotifyControls()` and `hideSpotifyControls()` methods
- **SpotifyManager.kt**: Constructor now accepts disconnect callback, 2-minute timer
- **SetupCalendar.kt**: Enhanced `handleCalendarButtonClick()` with cache fallback logic