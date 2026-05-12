# TenantConnect AI Agent Guide

## Architecture Overview
TenantConnect is an Android rental property management app built with Kotlin, using Firebase Realtime Database (hosted in asia-southeast1 region), Firebase Auth, and Firebase Storage. The app supports two user roles: Landlords (manage properties, rooms, tenants) and Tenants (view contracts, pay bills, communicate).

Key data entities (defined in `Models.kt`):
- **User**: Profile data with role ("Landlord" or "Tenant"), linked to landlordId/propertyId
- **Property**: Landlord-owned properties with rooms and amenities
- **Room**: Individual rental units within properties
- **Contract**: Rental agreements linking tenants to rooms
- **Billing**: Monthly charges (rent, utilities) with payment status
- **PaymentTransaction**: Payment records with proof-of-payment uploads
- **Message**: Chat between landlords and tenants
- **Announcement**: Property-wide notifications
- **Invitation/MaintenanceRequest/Document**: Additional features

Data flows through FirebaseManager singleton (`FirebaseManager.kt`), providing typed references to database collections.

## Key Patterns & Conventions

### Activity Structure
- Use View Binding for UI access (e.g., `ActivityDashboardTenantBinding` in `DashboardTenantActivity.kt`)
- Role-based navigation: LoginActivity checks user role from `users/role` and redirects to appropriate dashboard
- Landlord onboarding: If no property exists, redirect to `LandlordDetailsActivity` for setup
- Tenant multi-contract support: Dashboard shows spinner for switching between active contracts

### Firebase Integration
- Real-time listeners for dynamic updates (e.g., contract changes in `DashboardTenantActivity.kt`)
- Queries use `orderByChild().equalTo()` for efficient lookups (e.g., contracts by tenantId)
- Storage refs for profile photos and payment proofs (via `FirebaseManager.profilePhotosRef`)

### UI Components
- BottomSheetDialogFragment for modals (e.g., `InvoiceDialog.kt` for billing creation)
- RecyclerView adapters with view types (e.g., `MessageAdapter.kt` distinguishes sent/received messages)
- Coil library for image loading (e.g., profile photos in dashboards)

### Error Handling & UX
- Loading overlays with 10-second timeouts (e.g., LoginActivity)
- Toast messages for user feedback on Firebase operations
- Graceful fallbacks for missing data (e.g., landlord property checks)

## Developer Workflows

### Building & Testing
- **Debug build**: `./gradlew assembleDebug` or `./gradlew build`
- **Install on device**: `./gradlew installDebug`
- **Unit tests**: `./gradlew test` (JUnit 4.13.2)
- **Instrumentation tests**: `./gradlew connectedAndroidTest` (Espresso 3.7.0)
- **Lint checks**: `./gradlew lint` with auto-fix via `lintFix`

### Debugging
- Firebase console for database inspection (asia-southeast1 RTDB)
- Logcat for real-time listener events
- View Binding reduces findViewById errors; verify binding inflation in onCreate

### Dependencies
- Managed via version catalogs (`gradle/libs.versions.toml`)
- Firebase BOM ensures compatible versions
- Coil for efficient image handling

## Integration Points
- **Firebase Auth**: Email/password login with role-based routing
- **Realtime Database**: Core data storage with listeners for live updates
- **Storage**: File uploads (photos, documents) with download URLs
- **Google Services**: Configured via `google-services.json`

Reference: `FirebaseManager.kt` for all Firebase interactions, `Models.kt` for data schemas.</content>
<parameter name="filePath">C:\Users\God is Good\AndroidStudioProjects\TenantConnect\AGENTS.md
