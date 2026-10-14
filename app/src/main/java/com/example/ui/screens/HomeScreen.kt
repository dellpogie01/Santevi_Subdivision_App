package com.example.ui.screens

import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import androidx.compose.ui.tooling.preview.Preview
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.MarketplaceFormActions
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.LoginFormState
import coil.compose.AsyncImage
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

enum class HomeTab {
    HOME, MARKETPLACE, CHAT, COMPLAINTS, MAP, PROFILE, ADMIN
}

private data class RoleReviewRequest(val id: String, val uid: String, val displayName: String, val email: String, val currentRole: String, val requestedRole: String, val reason: String)

@Composable
fun rememberUriImageBitmap(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    return remember(uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun PhMobileNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Contact Number"
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }
            if (digits.length <= 10) {
                onValueChange(digits)
            }
        },
        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text("9171234567", fontSize = 13.sp) },
        leadingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 2.dp)
            ) {
                Text("ðŸ‡µðŸ‡­", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text("+63", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                )
            }
        },
        singleLine = true,
        modifier = modifier
    )
}

// Data Models
data class AnnouncementItem(
    val id: Int,
    val title: String,
    val date: String,
    val summary: String,
    val fullText: String
)

data class CommunityConcern(
    val id: Int,
    val title: String,
    val location: String,
    val status: String,
    val details: String,
    val statusColor: Color
)

data class AmenityReservation(
    val id: Int,
    val amenityName: String,
    val date: String,
    val timeSlot: String,
    val reservedBy: String
)

data class CommunityEvent(
    val id: Int,
    val eventName: String,
    val date: String,
    val location: String,
    val description: String
)

data class ChatMessage(
    val id: Int,
    val senderName: String,
    val message: String,
    val timestamp: String,
    val isAnonymous: Boolean,
    val isLostAndFound: Boolean = false,
    val imageUrls: List<String> = emptyList()
)

data class MarketplaceItem(
    val id: String,
    val title: String,
    val price: String,
    val seller: String,
    val contact: String,
    val blockLotPhase: String = "",
    val description: String,
    val imageUrls: List<String> = emptyList()
)

@Composable
fun HomeScreen(
    viewModel: AuthViewModel,
    notificationRoute: String? = null,
    notificationDocumentId: String? = null,
    notificationSequence: Int = 0,
    onNotificationRouteConsumed: () -> Unit = {},
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.homeState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val mapView = remember { mutableStateOf<WebView?>(null) }

    HomeScreenContent(
        state = state,
        loginState = loginState,
        onClearActionMessage = { viewModel.clearActionMessage() },
        onRememberMeChanged = { viewModel.onRememberMeChanged(it) },
        onNotificationPreferenceChanged = { field, enabled -> viewModel.setNotificationPreference(field, enabled) },
        onSaveProfile = { fields, photoUri, proofUri -> viewModel.saveResidentProfile(fields, photoUri, proofUri) },
        mapView = mapView.value,
        onMapViewCreated = { mapView.value = it },
        onSignOut = {
            viewModel.signOut(credentialManager) {
                onNavigateToLogin()
            }
        }
    )
}

@Composable
fun HomeScreenContent(
    state: HomeUiState,
    loginState: LoginFormState,
    onClearActionMessage: () -> Unit = {},
    onRememberMeChanged: (Boolean) -> Unit = {},
    onNotificationPreferenceChanged: (String, Boolean) -> Unit = { _, _ -> },
    onSaveProfile: (Map<String, Any?>, Uri?, Uri?) -> Unit = { _, _, _ -> },
    mapView: WebView? = null,
    onMapViewCreated: (WebView) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(HomeTab.HOME) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAdminAccessDialog by remember { mutableStateOf(false) }
    var tokenAdminAuthorized by remember(state.firebaseUser?.uid) { mutableStateOf(state.isTrustedAdmin) }

    LaunchedEffect(state.actionMessage, state.generalError) {
        val message = state.generalError ?: state.actionMessage
        if (message != null) {
            if (showSettingsDialog && state.generalError != null) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                snackbarHostState.showSnackbar(message)
            }
            onClearActionMessage()
        }
    }

    val firebaseUser = state.firebaseUser
    val userProfile = state.userProfile

    if (showAdminAccessDialog) AlertDialog(
        onDismissRequest = { showAdminAccessDialog = false },
        title = { Text("Administrator access") },
        text = { Text("Reserved for Administrators. You do not currently have administrator access. You may request an eligible community role from your Profile.") },
        confirmButton = { TextButton(onClick = { showAdminAccessDialog = false }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { showAdminAccessDialog = false; activeTab = HomeTab.PROFILE }) { Text("Open Profile") } }
    )

    val displayName = userProfile?.displayName?.ifBlank { null }
        ?: firebaseUser?.displayName?.ifBlank { null }
        ?: userProfile?.username?.ifBlank { null }
        ?: firebaseUser?.email?.substringBefore("@")
        ?: "Homeowner"

    if (showSettingsDialog) {
        SettingsAndAboutDialog(
            rememberMeChecked = loginState.rememberMe,
            profile = state.userProfile,
            onRememberMeChanged = onRememberMeChanged,
            onPreferenceChanged = onNotificationPreferenceChanged,
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    windowInsets = NavigationBarDefaults.windowInsets,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        Triple(HomeTab.HOME, "Home", Icons.Default.Home),
                        Triple(HomeTab.MARKETPLACE, "Marketplace", Icons.Default.ShoppingCart),
                        Triple(HomeTab.CHAT, "Chat", Icons.Default.Forum),
                        Triple(HomeTab.COMPLAINTS, "Concerns", Icons.Default.Campaign),
                        Triple(HomeTab.MAP, "Map", Icons.Default.LocationOn),
                        Triple(HomeTab.PROFILE, "Profile", Icons.Default.AccountCircle)
                    )

                    tabs.forEach { (tab, label, icon) ->
                        val selected = activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { activeTab = tab },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        modifier = Modifier.testTag("home_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar with bigger height & larger animated circular logo image
            TopBarHeader(
                displayName = displayName,
                onOpenSettings = { showSettingsDialog = true },
                onOpenAdmin = {
                    val current = FirebaseAuth.getInstance().currentUser
                    if (current == null) showAdminAccessDialog = true else current.getIdToken(true)
                        .addOnSuccessListener { token ->
                            if (token.claims["admin"] == true) { tokenAdminAuthorized = true; activeTab = HomeTab.ADMIN }
                            else showAdminAccessDialog = true
                        }
                        .addOnFailureListener { showAdminAccessDialog = true }
                }
            )

            // Main Tab Content
            when (activeTab) {
                HomeTab.HOME -> HomeFeedsTab(
                    canPublish = tokenAdminAuthorized
                )
                HomeTab.MARKETPLACE -> MarketplaceTab(currentUserDisplayName = displayName)
                HomeTab.CHAT -> CommunityChatTab(currentUserDisplayName = displayName)
                HomeTab.COMPLAINTS -> ResidentConcernsTab()
                HomeTab.MAP -> SubdivisionMapTab(mapView, onMapViewCreated)
                HomeTab.PROFILE -> ProfileTab(
                    displayName = displayName,
                    profile = userProfile,
                    isTrustedAdmin = state.isTrustedAdmin,
                    onSaveProfile = onSaveProfile,
                    onSignOut = onSignOut
                )
                HomeTab.ADMIN -> if (tokenAdminAuthorized) AdminDashboardScreen() else Text("Administrator access required.")
            }
        }
    }
}

@Composable
fun TopBarHeader(
    displayName: String,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HeaderLogoRotation")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HeaderRotationDegrees"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circle logo with animated rotating circular border - bigger (52.dp)
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationDegrees)
                    ) {
                        val strokeWidth = 2.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.White,
                                    primaryColor,
                                    secondaryColor,
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White
                                )
                            ),
                            radius = radius,
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.appstore),
                        contentDescription = "SantevÃ­ Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .testTag("home_header_logo"),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Santevi Community App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Green active login status symbol
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = Color(0xFF22C55E)
                        ) {}
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Welcome, $displayName",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Upper right Action Icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenAdmin, modifier = Modifier.size(44.dp).testTag("home_admin_button")) {
                    Icon(Icons.Default.Security, contentDescription = "Administrator dashboard", tint = Color.White, modifier = Modifier.size(23.dp))
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings & App Preferences",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: HOME FEEDS DASHBOARD (NON-SCROLLABLE WITH 5 CLEAN READABLE BOXES)
// -----------------------------------------------------------------------------

@Composable
fun HomeFeedsTab(canPublish: Boolean = false) {
    val context = LocalContext.current
    var expandedBoxIndex by remember { mutableStateOf<Int?>(null) }

    val announcements = remember { mutableStateListOf<AnnouncementItem>() }
    val homeownerConcerns = remember { mutableStateListOf<CommunityConcern>() }
    val amenityReservations = remember { mutableStateListOf<AmenityReservation>() }
    val communityEvents = remember { mutableStateListOf<CommunityEvent>() }
    val emergencyContacts = remember { mutableStateListOf<com.google.firebase.firestore.DocumentSnapshot>() }
    var postDialogType by remember { mutableStateOf<String?>(null) }

    postDialogType?.let { type ->
        CommunityPostDialog(
            type = type,
            onDismiss = { postDialogType = null },
            onPublish = { values ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid.isNullOrBlank()) {
                    Toast.makeText(context, "Sign in to publish community updates.", Toast.LENGTH_SHORT).show()
                    return@CommunityPostDialog
                }
                val collection = when (type) {
                    "announcement" -> "announcements"
                    "reservation" -> "amenity_reservations"
                    else -> "community_events"
                }
                val data = values.toMutableMap<String, Any>().apply {
                    put("createdByUid", uid)
                    put("publicationStatus", "published")
                    if (type == "announcement") put("message", remove("fullText") ?: values["summary"].orEmpty())
                    put("createdAt", FieldValue.serverTimestamp())
                }
                FirebaseFirestore.getInstance().collection(collection).add(data)
                    .addOnSuccessListener { postDialogType = null }
                    .addOnFailureListener {
                        Toast.makeText(context, "Could not publish. Check your role verification and connection.", Toast.LENGTH_LONG).show()
                    }
            }
        )
    }
    // Connect all 4 boxes to real Firestore database with safe error guards
    LaunchedEffect(Unit) {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                val db = FirebaseFirestore.getInstance()

                // Box 1: Announcements
                db.collection("announcements").whereEqualTo("publicationStatus", "published").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        try {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val title = doc.getString("title") ?: ""
                                if (title.isNotBlank()) {
                                    AnnouncementItem(
                                        id = doc.id.hashCode(),
                                        title = title,
                                        date = doc.getString("date") ?: "",
                                        summary = doc.getString("summary") ?: "",
                                        fullText = doc.getString("fullText") ?: doc.getString("message") ?: ""
                                    )
                                } else null
                            }
                            announcements.clear()
                            announcements.addAll(fetched)
                        } catch (e: Exception) {
                            // Safe catch
                        }
                    }
                }

                // Box 2: Homeowner Concerns
                db.collection("homeowner_concerns").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        try {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val title = doc.getString("title") ?: ""
                                val statusStr = doc.getString("status") ?: "Pending"
                                val color = when (statusStr.lowercase()) {
                                    "resolved" -> Color(0xFF059669)
                                    "in progress" -> Color(0xFFD97706)
                                    else -> Color(0xFF2563EB)
                                }
                                if (title.isNotBlank()) {
                                    CommunityConcern(
                                        id = doc.id.hashCode(),
                                        title = title,
                                        location = doc.getString("location") ?: "",
                                        status = statusStr,
                                        details = doc.getString("details") ?: "",
                                        statusColor = color
                                    )
                                } else null
                            }
                            if (fetched.isNotEmpty()) {
                                homeownerConcerns.clear()
                                homeownerConcerns.addAll(fetched)
                            }
                        } catch (e: Exception) {
                            // Safe catch
                        }
                    }
                }

                // Box 3: Amenity Reservations
                db.collection("amenity_reservations").whereEqualTo("publicationStatus", "published").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        try {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val amenityName = doc.getString("amenityName") ?: ""
                                if (amenityName.isNotBlank()) {
                                    AmenityReservation(
                                        id = doc.id.hashCode(),
                                        amenityName = amenityName,
                                        date = doc.getString("date") ?: "",
                                        timeSlot = listOfNotNull(doc.getString("startTime"), doc.getString("endTime")).joinToString(" - "),
                                        reservedBy = doc.getString("instructions") ?: ""
                                    )
                                } else null
                            }
                            amenityReservations.clear()
                            amenityReservations.addAll(fetched)
                        } catch (e: Exception) {
                            // Safe catch
                        }
                    }
                }

                // Box 4: Community Events
                db.collection("community_events").whereEqualTo("publicationStatus", "published").addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        try {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val eventName = doc.getString("eventName") ?: ""
                                if (eventName.isNotBlank()) {
                                    CommunityEvent(
                                        id = doc.id.hashCode(),
                                        eventName = eventName,
                                        date = doc.getString("date") ?: "",
                                        location = doc.getString("location") ?: "",
                                        description = doc.getString("description") ?: ""
                                    )
                                } else null
                            }
                            communityEvents.clear()
                            communityEvents.addAll(fetched)
                        } catch (e: Exception) {
                            // Safe catch
                        }
                    }
                }
                db.collection("emergency_contacts")
                    .whereEqualTo("publicationStatus", "published")
                    .whereEqualTo("active", true)
                    .orderBy("displayOrder")
                    .addSnapshotListener { snapshot, _ ->
                        emergencyContacts.clear()
                        snapshot?.documents?.let(emergencyContacts::addAll)
                    }
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Community Banner Box (Reduced height, zero extra padding, no handshake icon)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Respect Your Neighbors. Protect Our Community.",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Box 1: Santevi Announcements
        if (expandedBoxIndex == null || expandedBoxIndex == 1) {
            CardGroupBox(
                title = "Santevi Announcements",
                isExpanded = expandedBoxIndex == 1,
                onToggleExpand = {
                    expandedBoxIndex = if (expandedBoxIndex == 1) null else 1
                }
            ) {
                if (canPublish) {
                    TextButton(onClick = { postDialogType = "announcement" }) { Text("Create announcement") }
                }
                if (expandedBoxIndex != 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        announcements.forEach { item ->
                            BulletItemRow(
                                pinColor = Color(0xFFDC2626),
                                text = "${item.title} (${item.date}): ${item.summary}"
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        announcements.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.date,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.fullText,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Box 2: Homeowner Concerns and Status
        if (expandedBoxIndex == null || expandedBoxIndex == 2) {
            CardGroupBox(
                title = "Homeowner Concerns and Status",
                isExpanded = expandedBoxIndex == 2,
                onToggleExpand = {
                    expandedBoxIndex = if (expandedBoxIndex == 2) null else 2
                }
            ) {
                if (expandedBoxIndex != 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        homeownerConcerns.forEach { item ->
                            BulletItemRow(
                                pinColor = Color(0xFFD97706),
                                text = "[${item.status}] ${item.title} (${item.location})"
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        homeownerConcerns.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = item.status,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = item.statusColor
                                        )
                                    }
                                    Text(
                                        text = item.location,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.details,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Box 3: Upcoming Amenity Reservations
        if (expandedBoxIndex == null || expandedBoxIndex == 3) {
            CardGroupBox(
                title = "Upcoming Amenity Reservations",
                isExpanded = expandedBoxIndex == 3,
                onToggleExpand = {
                    expandedBoxIndex = if (expandedBoxIndex == 3) null else 3
                }
            ) {
                if (canPublish) {
                    TextButton(onClick = { postDialogType = "reservation" }) { Text("Add reservation") }
                }
                if (expandedBoxIndex != 3) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        amenityReservations.forEach { item ->
                            BulletItemRow(
                                pinColor = Color(0xFF059669),
                                text = "${item.amenityName} - ${item.date} (${item.timeSlot})"
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        amenityReservations.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.amenityName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.date} | ${item.timeSlot}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = item.reservedBy,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Box 4: Community Events Calendar
        if (expandedBoxIndex == null || expandedBoxIndex == 4) {
            CardGroupBox(
                title = "Community Events Calendar",
                isExpanded = expandedBoxIndex == 4,
                onToggleExpand = {
                    expandedBoxIndex = if (expandedBoxIndex == 4) null else 4
                }
            ) {
                if (canPublish) {
                    TextButton(onClick = { postDialogType = "event" }) { Text("Add community event") }
                }
                if (expandedBoxIndex != 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        communityEvents.forEach { item ->
                            BulletItemRow(
                                pinColor = Color(0xFF2563EB),
                                text = "${item.eventName} - ${item.date} @ ${item.location}"
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        communityEvents.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.eventName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${item.date} @ ${item.location}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.description,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Box 5: Emergency Contacts (Default: Hidden list / 0 items shown unless clicked)
        if (expandedBoxIndex == null || expandedBoxIndex == 5) {
            CardGroupBox(
                title = "Emergency Contacts",
                isExpanded = expandedBoxIndex == 5,
                onToggleExpand = {
                    expandedBoxIndex = if (expandedBoxIndex == 5) null else 5
                }
            ) {
                if (expandedBoxIndex != 5) {
                    // SHOW NO / 0 LIST ITEMS WHEN COLLAPSED
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        emergencyContacts.forEach { contact ->
                            val name = contact.getString("name").orEmpty()
                            val number = contact.getString("phoneNumber").orEmpty()
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = number,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))) } }) { Icon(Icons.Default.Phone, "Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BulletItemRow(
    pinColor: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = null,
            tint = pinColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CardGroupBox(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(3.dp))

            content()
        }
    }
}

@Composable
private fun CommunityPostDialog(
    type: String,
    onDismiss: () -> Unit,
    onPublish: (Map<String, String>) -> Unit
) {
    val fields = when (type) {
        "announcement" -> listOf("title" to "Title", "date" to "Date", "summary" to "Summary", "fullText" to "Full details")
        "reservation" -> listOf("amenityName" to "Amenity", "date" to "Date", "startTime" to "Start time", "endTime" to "End time", "location" to "Location", "instructions" to "Reservation instructions")
        else -> listOf("eventName" to "Event name", "date" to "Date", "location" to "Location", "description" to "Description")
    }
    val values = remember(type) { androidx.compose.runtime.mutableStateListOf(*Array(fields.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (type) { "announcement" -> "New announcement"; "reservation" -> "Upcoming amenity reservation"; else -> "New community event" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEachIndexed { index, (_, label) ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { values[index] = it },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (label in listOf("Summary", "Full details", "Description", "Reservation details")) 2 else 1
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPublish(fields.mapIndexed { index, field -> field.first to values[index].trim() }.toMap()) },
                enabled = values.indices.filter { it in when (type) { "announcement" -> listOf(0, 3); "reservation" -> listOf(0, 1); else -> listOf(0, 1, 3) } }.all { values[it].isNotBlank() }) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -----------------------------------------------------------------------------
// TAB 2: MARKETPLACE TAB
// -----------------------------------------------------------------------------

@Composable
fun MarketplaceTab(currentUserDisplayName: String = "Homeowner") {
    val context = LocalContext.current
    var showSellForm by remember { mutableStateOf(false) }
    var itemTitle by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemContact by remember { mutableStateOf("9171234567") }
    var itemAddress by remember { mutableStateOf("") }
    var itemDesc by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }

    val selectedImageUris = remember { mutableStateListOf<Uri>() }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cancelListingForm = {
        showSellForm = false
        itemTitle = ""
        itemPrice = ""
        itemContact = "9171234567"
        itemAddress = ""
        itemDesc = ""
        selectedImageUris.clear()
    }

    fun createTempCameraUri(): Uri? {
        return try {
            val tempFile = File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                context.cacheDir
            ).apply {
                createNewFile()
                deleteOnExit()
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            if (selectedImageUris.size < 3) {
                selectedImageUris.add(tempCameraUri!!)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val available = 3 - selectedImageUris.size
            if (available > 0) {
                selectedImageUris.addAll(uris.take(available))
            }
        }
    }

    val items = remember {
        mutableStateListOf(
            MarketplaceItem(
                id = "1",
                title = "Pre-loved Mountain Bike (26-inch)",
                price = "â‚±3,500",
                seller = "Juan D.",
                contact = "+63 917-555-0192",
                blockLotPhase = "Block 3, Lot 8",
                description = "Good condition shimano gears, helmet included. Perfect for subdivision morning rides."
            ),
            MarketplaceItem(
                id = "2",
                title = "Fresh Organic Garden Tomatoes (per kg)",
                price = "â‚±120",
                seller = "Elena R.",
                contact = "+63 918-444-2211",
                blockLotPhase = "Block 5, Lot 14",
                description = "Harvested fresh daily from backyard garden. Free delivery anywhere inside SantevÃ­!"
            ),
            MarketplaceItem(
                id = "3",
                title = "Solid Wooden Dining Table Set (6-Seater)",
                price = "â‚±7,800",
                seller = "Mark T.",
                contact = "+63 922-888-3344",
                blockLotPhase = "Block 1, Lot 22",
                description = "Hardwood mahogany finish. Slightly used, well maintained. Buyer pick-up at Block 1."
            )
        )
    }

    // Load / sync with real Firestore database
    LaunchedEffect(Unit) {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("marketplace_items")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val title = doc.getString("title") ?: ""
                                if (title.isNotBlank()) {
                                    @Suppress("UNCHECKED_CAST")
                                    val imageUrls = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                    MarketplaceItem(
                                        id = doc.id,
                                        title = title,
                                        price = doc.getString("price") ?: "",
                                        seller = doc.getString("seller") ?: "",
                                        contact = doc.getString("contact") ?: "",
                                        blockLotPhase = doc.getString("blockLotPhase") ?: "",
                                        description = doc.getString("description") ?: "",
                                        imageUrls = imageUrls
                                    )
                                } else null
                            }
                            if (fetched.isNotEmpty()) {
                                items.clear()
                                items.addAll(fetched)
                            }
                        }
                    }
            } catch (e: Exception) {
                // Keep local fallback list if offline or unconfigured
            }
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = {
                Text(
                    text = "Add Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                val available = 3 - selectedImageUris.size
                                if (available > 0) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Choose from Gallery",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Choose from Gallery",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                if (selectedImageUris.size < 3) {
                                    val uri = createTempCameraUri()
                                    if (uri != null) {
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Take a Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Take a Photo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    val columnModifier = if (showSellForm) {
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    } else {
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Homeowners Marketplace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Buy & sell items directly with neighbors.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showSellForm = !showSellForm },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (showSellForm) Icons.Default.Close else Icons.Default.AddCircle,
                    contentDescription = if (showSellForm) "Close" else "Sell",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showSellForm) "View List" else "Sell Item", fontSize = 11.sp)
            }
        }

        if (showSellForm) {
            // ONLY the Sell Item Form Box is visible (No items list displayed below)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "List an Item for Sale",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = itemTitle,
                        onValueChange = { itemTitle = it },
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                        label = { Text("Item Title", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Mountain Bike, Table", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = itemPrice,
                            onValueChange = { itemPrice = it },
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                            label = { Text("Price (â‚±)", fontSize = 11.sp) },
                            placeholder = { Text("1500", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = itemAddress,
                            onValueChange = { itemAddress = it },
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                            label = { Text("Block / Lot / Phase", fontSize = 11.sp) },
                            placeholder = { Text("Optional (e.g. B3 L4 P3)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }

                    PhMobileNumberField(
                        value = itemContact,
                        onValueChange = { itemContact = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = itemDesc,
                        onValueChange = { itemDesc = it },
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                        label = { Text("Item Description", fontSize = 11.sp) },
                        placeholder = { Text("Condition, inclusions, pick-up details...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    // Standardized Photo Upload Button (exact label "Upload Photos (max 3)", primary styling & height)
                    Button(
                        onClick = {
                            if (selectedImageUris.size < 3) {
                                showPhotoSourceDialog = true
                            }
                        },
                        enabled = !isPosting && selectedImageUris.size < 3,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Upload Photos (max 3)",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload Photos (max 3)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (selectedImageUris.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            selectedImageUris.forEachIndexed { index, uri ->
                                val bitmap = rememberUriImageBitmap(uri)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Photo ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    IconButton(
                                        onClick = { selectedImageUris.removeAt(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Primary Submission Actions (Exact label "Post Marketplace Listing" retained at all times)
                    MarketplaceFormActions(
                        isSaving = isPosting,
                        canSave = itemTitle.isNotBlank() && itemPrice.isNotBlank(),
                        saveLabel = "Post Marketplace Listing",
                        onCancel = { cancelListingForm() },
                        onSave = {
                            if (itemTitle.isNotBlank() && itemPrice.isNotBlank()) {
                                isPosting = true
                                val newId = UUID.randomUUID().toString()
                                val formattedPrice = if (itemPrice.trim().startsWith("â‚±")) itemPrice.trim() else "â‚±${itemPrice.trim()}"
                                val uriStringList = selectedImageUris.map { it.toString() }
                                val formattedContact = "+63 $itemContact"

                                val newItem = MarketplaceItem(
                                    id = newId,
                                    title = itemTitle.trim(),
                                    price = formattedPrice,
                                    seller = currentUserDisplayName,
                                    contact = formattedContact,
                                    blockLotPhase = itemAddress.trim(),
                                    description = itemDesc.trim(),
                                    imageUrls = uriStringList
                                )

                                items.add(0, newItem)

                                if (FirebaseApp.getApps(context).isNotEmpty()) {
                                    try {
                                        val db = FirebaseFirestore.getInstance()
                                        val itemData = mapOf(
                                            "id" to newId,
                                            "title" to itemTitle.trim(),
                                            "price" to formattedPrice,
                                            "seller" to currentUserDisplayName,
                                            "contact" to formattedContact,
                                            "blockLotPhase" to itemAddress.trim(),
                                            "description" to itemDesc.trim(),
                                            "imageUrls" to uriStringList,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                        db.collection("marketplace_items").document(newId).set(itemData)
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                }

                                itemTitle = ""
                                itemPrice = ""
                                itemAddress = ""
                                itemDesc = ""
                                selectedImageUris.clear()
                                isPosting = false
                                showSellForm = false
                            }
                        }
                    )
                }
            }
        } else {
            // Display items list when NOT on sell item screen
            items.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.price,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (item.blockLotPhase.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.blockLotPhase,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (item.imageUrls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item.imageUrls.forEach { url ->
                                    val uri = try { Uri.parse(url) } catch (e: Exception) { null }
                                    val bitmap = if (uri != null) rememberUriImageBitmap(uri) else null
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "Item Photo",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ShoppingCart,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.seller,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.contact,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 3: COMMUNITY HUB (CHAT WITH TOGGLEABLE POST FORMS FOR CHAT & LOST & FOUND)
// -----------------------------------------------------------------------------

@Composable
fun CommunityChatTab(currentUserDisplayName: String) {
    val context = LocalContext.current
    var showPostForms by remember { mutableStateOf(false) }

    var newMessageText by remember { mutableStateOf("") }
    var isAnonymousPost by remember { mutableStateOf(false) }

    // Lost and Found Box state
    var lostFoundDesc by remember { mutableStateOf("") }
    var isPostingLostFound by remember { mutableStateOf(false) }
    val lostFoundImageUris = remember { mutableStateListOf<Uri>() }

    val lostFoundPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            lostFoundImageUris.clear()
            lostFoundImageUris.addAll(uris.take(3))
        }
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = 1,
                senderName = "Maria Santos (Block 4)",
                message = "Good morning neighbors! Has anyone seen a stray golden retriever puppy near Gate 2?",
                timestamp = "8:30 AM",
                isAnonymous = false
            ),
            ChatMessage(
                id = 2,
                senderName = "Anonymous Homeowner",
                message = "Reminding everyone to please turn on outdoor porch lights at night for community safety.",
                timestamp = "9:15 AM",
                isAnonymous = true
            ),
            ChatMessage(
                id = 3,
                senderName = "Engr. Ricardo (Block 7)",
                message = "Thanks HOA maintenance team for repairing the park bench so promptly!",
                timestamp = "10:05 AM",
                isAnonymous = false
            ),
            ChatMessage(
                id = 4,
                senderName = "Security Guard Gate 1",
                message = "#LostAndFound Found a set of car keys with a blue keychain near the Central Park gazebo.",
                timestamp = "11:20 AM",
                isAnonymous = false,
                isLostAndFound = true
            )
        )
    }

    // Connect to Firestore real-time snapshot listeners for chat & lost and found
    LaunchedEffect(Unit) {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                val db = FirebaseFirestore.getInstance()

                db.collection("community_chat")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
                            val fetched = snapshot.documents.mapNotNull { doc ->
                                val msgText = doc.getString("message") ?: ""
                                if (msgText.isNotBlank()) {
                                    @Suppress("UNCHECKED_CAST")
                                    val imageUrls = (doc.get("imageUrls") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                                    ChatMessage(
                                        id = doc.id.hashCode(),
                                        senderName = doc.getString("senderName") ?: "Homeowner",
                                        message = msgText,
                                        timestamp = doc.getString("timestamp") ?: "Just now",
                                        isAnonymous = doc.getBoolean("isAnonymous") ?: false,
                                        isLostAndFound = doc.getBoolean("isLostAndFound") ?: false,
                                        imageUrls = imageUrls
                                    )
                                } else null
                            }
                            if (fetched.isNotEmpty()) {
                                messages.clear()
                                messages.addAll(fetched)
                            }
                        }
                    }
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    val columnModifier = if (showPostForms) {
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    } else {
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    }

    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Upper Header with Toggle Button (identical size, width, height, and shape as Marketplace's button)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Community Hub Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Connect and post messages or report lost & found items.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { showPostForms = !showPostForms },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (showPostForms) Icons.Default.Close else Icons.Default.AddCircle,
                    contentDescription = if (showPostForms) "Close" else "New Post",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showPostForms) "Close" else "New Post", fontSize = 11.sp)
            }
        }

        if (showPostForms) {
            // SHOW ONLY THE 2 POST CREATION CONTAINERS (CHAT MESSAGES FEED IS HIDDEN)

            // 1. Post Community Message Container Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Post Community Message",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = newMessageText,
                        onValueChange = { newMessageText = it },
                        placeholder = { Text("Write a message to the community...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isAnonymousPost = !isAnonymousPost }
                        ) {
                            Checkbox(
                                checked = isAnonymousPost,
                                onCheckedChange = { isAnonymousPost = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Anonymously",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                if (newMessageText.isNotBlank()) {
                                    val sender = if (isAnonymousPost) "Anonymous Homeowner" else currentUserDisplayName
                                    val msgContent = newMessageText.trim()
                                    val newMsg = ChatMessage(
                                        id = System.currentTimeMillis().toInt(),
                                        senderName = sender,
                                        message = msgContent,
                                        timestamp = "Just now",
                                        isAnonymous = isAnonymousPost
                                    )
                                    messages.add(0, newMsg)

                                    if (FirebaseApp.getApps(context).isNotEmpty()) {
                                        try {
                                            val db = FirebaseFirestore.getInstance()
                                            val newDoc = db.collection("community_chat").document()
                                            val data = mapOf(
                                                "id" to newDoc.id,
                                                "senderName" to sender,
                                                "message" to msgContent,
                                                "timestamp" to "Just now",
                                                "isAnonymous" to isAnonymousPost,
                                                "isLostAndFound" to false,
                                                "createdAt" to FieldValue.serverTimestamp()
                                            )
                                            newDoc.set(data)
                                        } catch (e: Exception) {
                                            // Ignore fallback
                                        }
                                    }
                                    newMessageText = ""
                                    showPostForms = false
                                }
                            },
                            enabled = newMessageText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Post", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Report Lost and Found Item Container Box (Identical Container Format, Colors & Buttons in 1 Line)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Report Lost and Found Item",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = lostFoundDesc,
                        onValueChange = { lostFoundDesc = it },
                        placeholder = { Text("#LostAndFound", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    if (lostFoundImageUris.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            lostFoundImageUris.forEachIndexed { index, uri ->
                                val bitmap = rememberUriImageBitmap(uri)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Lost Photo ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    IconButton(
                                        onClick = { lostFoundImageUris.removeAt(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // 2 Buttons in 1 Line / Row: Camera Icon Button + Post Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                lostFoundPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = lostFoundImageUris.size < 3,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Upload Photos", modifier = Modifier.size(18.dp))
                        }

                        Button(
                            onClick = {
                                if (lostFoundDesc.isNotBlank()) {
                                    isPostingLostFound = true
                                    val msgWithHashtag = if (lostFoundDesc.trim().startsWith("#LostAndFound")) lostFoundDesc.trim() else "#LostAndFound ${lostFoundDesc.trim()}"
                                    val uriStringList = lostFoundImageUris.map { it.toString() }

                                    val newMsg = ChatMessage(
                                        id = System.currentTimeMillis().toInt(),
                                        senderName = currentUserDisplayName,
                                        message = msgWithHashtag,
                                        timestamp = "Just now",
                                        isAnonymous = false,
                                        isLostAndFound = true,
                                        imageUrls = uriStringList
                                    )
                                    messages.add(0, newMsg)

                                    if (FirebaseApp.getApps(context).isNotEmpty()) {
                                        try {
                                            val db = FirebaseFirestore.getInstance()
                                            val newDoc = db.collection("community_chat").document()
                                            val data = mapOf(
                                                "id" to newDoc.id,
                                                "senderName" to currentUserDisplayName,
                                                "message" to msgWithHashtag,
                                                "timestamp" to "Just now",
                                                "isAnonymous" to false,
                                                "isLostAndFound" to true,
                                                "imageUrls" to uriStringList,
                                                "createdAt" to FieldValue.serverTimestamp()
                                            )
                                            newDoc.set(data)
                                        } catch (e: Exception) {
                                            // Ignore fallback
                                        }
                                    }

                                    lostFoundDesc = ""
                                    lostFoundImageUris.clear()
                                    isPostingLostFound = false
                                    showPostForms = false
                                }
                            },
                            enabled = lostFoundDesc.isNotBlank() && !isPostingLostFound,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Post", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // DISPLAY CHAT MESSAGES FEED WHEN NOT CREATING A POST
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messages.forEach { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isLostAndFound) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
                            } else if (msg.isAnonymous) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (msg.isLostAndFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        shape = CircleShape,
                                        color = if (msg.isLostAndFound) MaterialTheme.colorScheme.primary else if (msg.isAnonymous) Color.Gray else MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (msg.isLostAndFound) Icons.Default.AddAPhoto else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = msg.senderName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (msg.isLostAndFound) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "#LostAndFound",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = msg.timestamp,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (msg.imageUrls.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    msg.imageUrls.forEach { url ->
                                        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
                                        val bitmap = if (uri != null) rememberUriImageBitmap(uri) else null
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = "Lost & Found Photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 4: RESIDENT CONCERNS / REQUESTS TAB (TITLE ONLY - NO ICON)
// -----------------------------------------------------------------------------

@Composable
fun ResidentConcernsTab() {
    val context = LocalContext.current
    var concernTitle by remember { mutableStateOf("") }
    var concernLocation by remember { mutableStateOf("") }
    var concernDesc by remember { mutableStateOf("") }
    var submittedSuccess by remember { mutableStateOf(false) }

    val selectedImageUris = remember { mutableStateListOf<Uri>() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris.take(3))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Clean Title without icon beside it
        Column {
            Text(
                text = "Resident Concerns & Requests Portal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Report issues or submit requests directly to SantevÃ­ Admin.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Submit a New Request / Concern",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = concernTitle,
                    onValueChange = {
                        concernTitle = it
                        submittedSuccess = false
                    },
                    label = { Text("Request / Concern Subject", fontSize = 11.sp) },
                    placeholder = { Text("e.g. Broken Streetlight, Gate Pass", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = concernLocation,
                    onValueChange = { concernLocation = it },
                    label = { Text("Location / Block, Lot, Phase", fontSize = 11.sp) },
                    placeholder = { Text("Location / Block, Lot, Phase", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = concernDesc,
                    onValueChange = { concernDesc = it },
                    label = { Text("Details & Description", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Upload Photos (Max 3)
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = selectedImageUris.size < 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Upload Photos",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedImageUris.isEmpty()) "Upload Photos (Max 3)" else "Photos Attached (${selectedImageUris.size}/3)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedImageUris.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        selectedImageUris.forEachIndexed { index, uri ->
                            val bitmap = rememberUriImageBitmap(uri)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Photo ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                IconButton(
                                    onClick = { selectedImageUris.removeAt(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (concernTitle.isNotBlank()) {
                            submittedSuccess = true
                            if (FirebaseApp.getApps(context).isNotEmpty()) {
                                try {
                                    val db = FirebaseFirestore.getInstance()
                                    val newDoc = db.collection("homeowner_concerns").document()
                                    val data = mapOf(
                                        "id" to newDoc.id,
                                        "title" to concernTitle.trim(),
                                        "location" to concernLocation.trim(),
                                        "status" to "Pending",
                                        "details" to concernDesc.trim(),
                                        "imageUrls" to selectedImageUris.map { it.toString() },
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                    newDoc.set(data)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            }
                            concernTitle = ""
                            concernLocation = ""
                            concernDesc = ""
                            selectedImageUris.clear()
                        }
                    },
                    enabled = concernTitle.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Request to HOA", fontSize = 12.sp)
                }

                if (submittedSuccess) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Request submitted! Maintenance and HOA office have been notified.",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 5: SUBDIVISION LOCATION MAP TAB (LIVE GOOGLE MAP - SANTEVI PINNED)
// -----------------------------------------------------------------------------

@Composable
fun SubdivisionMapTab(mapView: WebView?, onMapViewCreated: (WebView) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Title without icon beside it
        Column {
            Text(
                text = "Location Map",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Santevi, Barangay San Vicente, San Pablo City, Laguna 4000",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Interactive Google Map Card centered on SantevÃ­ Subdivision
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Live Google Map View inside iframe centered on SantevÃ­ Subdivision
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AndroidView(
                        factory = { _ ->
                            (mapView ?: WebView(context).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                webViewClient = object : WebViewClient() {
                                    private var fallbackLoaded = false

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        if (!fallbackLoaded && request?.url?.host?.contains("google.com") == true) {
                                            fallbackLoaded = true
                                            val fallbackHtml = """
                                                <!DOCTYPE html>
                                                <html>
                                                <head>
                                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                                    <style>html, body { height: 100%; margin: 0; } iframe { width: 100%; height: 100%; border: 0; }</style>
                                                </head>
                                                <body><iframe title="Subdivision map" src="https://maps.google.com/maps?q=14.0722,121.3256&z=19&output=embed" allowfullscreen></iframe></body>
                                                </html>
                                            """.trimIndent()
                                            view?.loadDataWithBaseURL("https://maps.google.com", fallbackHtml, "text/html", "UTF-8", null)
                                        }
                                    }
                                }
                                val placeQuery = Uri.encode("SouthRoof by J&K Transient House, San Pablo City, Laguna")
                                val mapHtml = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                                        <style>
                                            html, body { height: 100%; margin: 0; padding: 0; background: #e5e3df; overflow: hidden; }
                                            iframe { width: 100%; height: 100%; border: 0; }
                                        </style>
                                    </head>
                                    <body>
                                        <iframe 
                                            src="https://maps.google.com/maps?q=$placeQuery&z=19&output=embed"
                                            allowfullscreen 
                                            loading="lazy">
                                        </iframe>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://maps.google.com", mapHtml, "text/html", "UTF-8", null)
                            }).also(onMapViewCreated)
                        },
                        update = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Homeowner Google Maps Recommendation Disclaimer Box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Pin your home address on Google Maps (e.g., B1 L2 P3) for easy navigation, deliveries, and emergency response.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 6: PROFILE TAB (TITLE ONLY - NO ICON)
// -----------------------------------------------------------------------------

@Composable
private fun AdminDashboardScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val requests = remember { mutableStateListOf<RoleReviewRequest>() }
    val requestHistory = remember { mutableStateListOf<com.google.firebase.firestore.DocumentSnapshot>() }
    val content = remember { mutableStateMapOf<String, List<com.google.firebase.firestore.DocumentSnapshot>>() }
    var selectedCollection by remember { mutableStateOf("announcements") }
    var editorType by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var pendingDecision by remember { mutableStateOf<Pair<RoleReviewRequest, String>?>(null) }
    val types = listOf("announcements", "amenity_reservations", "community_events", "emergency_contacts")

    DisposableEffect(Unit) {
        val registrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        registrations += db.collection("roleRequests").whereEqualTo("status", "pending")
            .orderBy("submittedAt", Query.Direction.DESCENDING).addSnapshotListener { snapshot, error ->
                if (error != null) Toast.makeText(context, error.localizedMessage, Toast.LENGTH_LONG).show()
                requests.clear()
                snapshot?.documents?.forEach { doc ->
                    requests += RoleReviewRequest(doc.id, doc.getString("requesterUid").orEmpty(), doc.getString("displayName").orEmpty(), doc.getString("email").orEmpty(), doc.getString("currentRole").orEmpty(), doc.getString("requestedRole").orEmpty(), doc.getString("reason").orEmpty())
                }
            }
        registrations += db.collection("roleRequests").orderBy("submittedAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, _ ->
                requestHistory.clear()
                snapshot?.documents?.let(requestHistory::addAll)
            }
        types.forEach { collection -> registrations += db.collection(collection).addSnapshotListener { snap, _ -> content[collection] = snap?.documents.orEmpty() } }
        onDispose { registrations.forEach { it.remove() } }
    }

    pendingDecision?.let { (request, decision) -> AlertDialog(
        onDismissRequest = { pendingDecision = null },
        title = { Text(if (decision == "approved") "Approve role request?" else "Reject role request?") },
        text = { Text("${request.displayName} requested ${request.requestedRole}. This action will be recorded in the request history.") },
        confirmButton = { TextButton(onClick = {
            pendingDecision = null
            FirebaseFunctions.getInstance().getHttpsCallable("reviewRoleRequest").call(mapOf("requestId" to request.id, "decision" to decision))
                .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Review failed", Toast.LENGTH_LONG).show() }
        }) { Text(if (decision == "approved") "Approve" else "Reject") } },
        dismissButton = { TextButton(onClick = { pendingDecision = null }) { Text("Cancel") } }
    ) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Santevi Admin Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${requests.size} pending role request${if (requests.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        requests.forEach { request -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(request.displayName, fontWeight = FontWeight.Bold); Text(request.email)
            Text("${request.currentRole} → ${request.requestedRole}")
            if (request.reason.isNotBlank()) Text("Reason: ${request.reason}")
            Row { TextButton(onClick = { pendingDecision = request to "approved" }) { Text("Approve") }; TextButton(onClick = { pendingDecision = request to "rejected" }) { Text("Reject") } }
        } } }
        Text("Recent role request history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val reviewed = requestHistory.filter { it.getString("status") != "pending" }
        if (reviewed.isEmpty()) Text("No reviewed requests yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        reviewed.forEach { doc ->
            Text("${doc.getString("displayName").orEmpty()} · ${doc.getString("requestedRole").orEmpty()} · ${doc.getString("status").orEmpty()}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Community content", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            types.forEach { type -> TextButton(onClick = { selectedCollection = type }) { Text(type.substringAfterLast('_').replaceFirstChar(Char::uppercase)) } }
        }
        Button(onClick = { editorType = selectedCollection; editing = null }) { Text("Create ${selectedCollection.substringAfterLast('_').replaceFirstChar(Char::uppercase)}") }
        content[selectedCollection].orEmpty().forEach { doc ->
            val data = doc.data.orEmpty()
            val title = (data["title"] ?: data["eventName"] ?: data["amenityName"] ?: data["name"] ?: doc.id).toString()
            Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text("${data["publicationStatus"] ?: data["status"] ?: "draft"}") }
                TextButton(onClick = { editorType = selectedCollection; editing = doc }) { Text("Edit") }
                TextButton(onClick = { doc.reference.update(mapOf("publicationStatus" to "archived", "updatedAt" to FieldValue.serverTimestamp()))
                    .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Archive failed", Toast.LENGTH_LONG).show() } }) { Text("Archive") }
                if (selectedCollection == "community_events") TextButton(onClick = { doc.reference.update(mapOf("status" to "cancelled", "publicationStatus" to "unpublished", "updatedAt" to FieldValue.serverTimestamp()))
                    .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Could not cancel event", Toast.LENGTH_LONG).show() } }) { Text("Cancel") }
            } }
        }
    }
    editorType?.let { type -> AdminContentEditor(type, editing, onClose = { editorType = null; editing = null }, onSave = { fields ->
        val ref = editing?.reference ?: db.collection(type).document()
        val values = fields.toMutableMap()
        values["updatedAt"] = FieldValue.serverTimestamp()
        values["createdByUid"] = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        values["publicationStatus"] = values["publicationStatus"] ?: "draft"
        if (editing == null) values["createdAt"] = FieldValue.serverTimestamp()
        ref.set(values, com.google.firebase.firestore.SetOptions.merge()).addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Save failed", Toast.LENGTH_LONG).show() }
        editorType = null; editing = null
    }) }
}

@Composable
private fun AdminContentEditor(
    type: String,
    existing: com.google.firebase.firestore.DocumentSnapshot?,
    onClose: () -> Unit,
    onSave: (Map<String, Any>) -> Unit
) {
    val fields = when (type) {
        "announcements" -> listOf("title", "message", "date", "summary", "imageUrl", "publicationStatus", "isPinned", "expiresAt")
        "amenity_reservations" -> listOf("amenityName", "date", "startTime", "endTime", "location", "instructions", "capacity", "availability", "publicationStatus")
        "community_events" -> listOf("eventName", "date", "startTime", "endTime", "location", "description", "imageUrl", "publicationStatus")
        else -> listOf("name", "organization", "category", "phoneNumber", "address", "instructions", "displayOrder", "active", "publicationStatus")
    }
    val values = remember(type, existing?.id) { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { fields.forEach { key -> put(key, existing?.get(key)?.toString().orEmpty().ifBlank { when (key) { "publicationStatus" -> "draft"; "active" -> "true"; "isPinned", "displayOrder" -> if (key == "displayOrder") "0" else "false"; else -> "" } }) } } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${if (existing == null) "Create" else "Edit"} ${type.substringAfterLast('_').replaceFirstChar(Char::uppercase)}") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fields.forEach { key -> OutlinedTextField(value = values[key].orEmpty(), onValueChange = { values[key] = it }, label = { Text(key.replaceFirstChar(Char::uppercase)) }, modifier = Modifier.fillMaxWidth(), minLines = if (key in listOf("message", "description", "instructions", "summary")) 2 else 1) }
        } },
        confirmButton = { TextButton(onClick = {
            val data = values.filterValues { it.isNotBlank() }.mapValues { (key, value) -> when (key) { "active", "isPinned" -> value.toBoolean(); "capacity", "displayOrder" -> value.toIntOrNull() ?: 0; "expiresAt" -> runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(value) }.getOrNull() ?: value; else -> value } }
            onSave(data)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(
    displayName: String,
    profile: UserProfile?,
    isTrustedAdmin: Boolean,
        onSaveProfile: (Map<String, Any?>, Uri?, Uri?) -> Unit,
    onSignOut: () -> Unit = {}
) {
    var phoneNumberDigits by remember(profile?.uid) { mutableStateOf(profile?.contact.orEmpty().filter { it.isDigit() }.takeLast(10)) }
    var blockNumber by remember(profile?.uid) { mutableStateOf(profile?.block.orEmpty()) }
    var lotNumber by remember(profile?.uid) { mutableStateOf(profile?.lot.orEmpty()) }
    var phaseNumber by remember(profile?.uid) { mutableStateOf(profile?.phase.orEmpty()) }
    var selectedRole by remember(profile?.uid) { mutableStateOf(profile?.role.orEmpty().ifBlank { "Visitor" }) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember(profile?.uid) { mutableStateOf<Uri?>(null) }
    var profilePhotoUrl by remember(profile?.uid) { mutableStateOf(profile?.photoUrl.orEmpty()) }
    var selectedProofUri by remember(profile?.uid) { mutableStateOf<Uri?>(null) }
    var saveFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val roleOptions = listOf("Visitor", "Homeowner", "Security", "HOA Officer", "Property Manager", "Maintenance", "Community Staff", "Service Provider", "Emergency Response")
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedPhotoUri = it }
    val proofPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedProofUri = it }
    val fieldStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    val roleRequests = remember { mutableStateListOf<RoleReviewRequest>() }
    val myRoleRequests = remember { mutableStateListOf<Pair<String, String>>() }
    var showRoleRequests by remember { mutableStateOf(false) }
    var showRoleConfirmation by remember { mutableStateOf(false) }
    var roleReason by remember { mutableStateOf("") }
    var profilePendingDecision by remember { mutableStateOf<Pair<RoleReviewRequest, String>?>(null) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(profile?.photoStoragePath) {
        val path = profile?.photoStoragePath.orEmpty()
        if (path.isNotBlank()) {
            FirebaseStorage.getInstance().reference.child(path).downloadUrl
                .addOnSuccessListener { profilePhotoUrl = it.toString() }
        }
    }

    DisposableEffect(profile?.uid) {
        val uid = profile?.uid
        if (uid.isNullOrBlank()) return@DisposableEffect onDispose { }
        val listener = FirebaseFirestore.getInstance().collection("roleRequests")
            .whereEqualTo("requesterUid", uid).orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(20).addSnapshotListener { snapshot, _ ->
                myRoleRequests.clear()
                snapshot?.documents?.forEach { doc ->
                    myRoleRequests += doc.getString("requestedRole").orEmpty() to doc.getString("status").orEmpty()
                }
            }
        onDispose { listener.remove() }
    }

    DisposableEffect(isTrustedAdmin) {
        if (!isTrustedAdmin) return@DisposableEffect onDispose { }
        val listener = FirebaseFirestore.getInstance().collection("roleRequests")
            .whereEqualTo("status", "pending")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    roleRequests.clear()
                    roleRequests.addAll(snapshot.documents.mapNotNull { document ->
                        val requested = document.getString("requestedRole").orEmpty()
                        if (requested.isBlank()) null
                        else RoleReviewRequest(
                            id = document.id,
                            uid = document.getString("requesterUid").orEmpty(),
                            displayName = document.getString("displayName") ?: "Community member",
                            email = document.getString("email").orEmpty(),
                            currentRole = document.getString("currentRole").orEmpty(),
                            requestedRole = requested,
                            reason = document.getString("reason").orEmpty()
                        )
                    })
                }
            }
        onDispose { listener.remove() }
    }

    profilePendingDecision?.let { (request, decision) -> AlertDialog(
        onDismissRequest = { profilePendingDecision = null },
        title = { Text(if (decision == "approved") "Approve role request?" else "Reject role request?") },
        text = { Text("${request.displayName} requested ${request.requestedRole}. This decision will be recorded.") },
        confirmButton = { TextButton(onClick = {
            profilePendingDecision = null
            FirebaseFunctions.getInstance().getHttpsCallable("reviewRoleRequest")
                .call(mapOf("requestId" to request.id, "decision" to decision))
                .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Review failed", Toast.LENGTH_LONG).show() }
        }) { Text(if (decision == "approved") "Approve" else "Reject") } },
        dismissButton = { TextButton(onClick = { profilePendingDecision = null }) { Text("Cancel") } }
    ) }
    if (showRoleConfirmation) AlertDialog(
        onDismissRequest = { showRoleConfirmation = false },
        title = { Text("Request community role") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$selectedRole requires administrator approval. Your current authorized role remains ${profile?.role ?: "Visitor"} until approved.")
                OutlinedTextField(value = roleReason, onValueChange = { roleReason = it.take(1000) }, label = { Text("Optional reason") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = {
            showRoleConfirmation = false
            FirebaseFunctions.getInstance().getHttpsCallable("submitRoleRequest").call(mapOf("requestedRole" to selectedRole, "reason" to roleReason))
                .addOnSuccessListener { saveFeedbackMessage = "Role request submitted for administrator review." }
                .addOnFailureListener { validationMessage = it.localizedMessage ?: "Could not submit role request." }
        }) { Text("Submit request") } },
        dismissButton = { TextButton(onClick = { showRoleConfirmation = false }) { Text("Cancel") } }
    )
    if (showRoleRequests && isTrustedAdmin) {
        AlertDialog(
            onDismissRequest = { showRoleRequests = false },
            title = { Text("Role verification requests") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (roleRequests.isEmpty()) Text("No pending requests.")
                    roleRequests.forEach { request ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${request.displayName} (${request.email})", fontWeight = FontWeight.Bold)
                                Text("${request.currentRole} → ${request.requestedRole}")
                                if (request.reason.isNotBlank()) Text(request.reason)
                                Row {
                                    TextButton(onClick = { profilePendingDecision = request to "approved" }) { Text("Approve") }
                                    TextButton(onClick = { profilePendingDecision = request to "rejected" }) { Text("Reject") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRoleRequests = false }) { Text("Close") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("Resident Profile & Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Manage your subdivision contact and address details.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isTrustedAdmin) {
            TextButton(onClick = { showRoleRequests = true }) { Text("Review role verification (${roleRequests.size})") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resident Details for $displayName", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                        val avatarBitmap = selectedPhotoUri?.let { rememberUriImageBitmap(it) }
                        if (avatarBitmap != null) {
                            Image(avatarBitmap, "Profile Picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else if (profilePhotoUrl.isNotBlank()) {
                            AsyncImage(model = profilePhotoUrl, contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.AddAPhoto, "Upload Profile Photo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(value = phoneNumberDigits, onValueChange = { phoneNumberDigits = it.filter(Char::isDigit).take(10) }, label = { Text("Mobile Contact Number", fontSize = 11.sp) }, placeholder = { Text("9171234567", fontSize = 14.sp) }, prefix = { Text("+63 ", style = fieldStyle) }, textStyle = fieldStyle, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(blockNumber, { blockNumber = it }, label = { Text("Block", fontSize = 11.sp) }, textStyle = fieldStyle, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(lotNumber, { lotNumber = it }, label = { Text("Lot", fontSize = 11.sp) }, textStyle = fieldStyle, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(phaseNumber, { phaseNumber = it }, label = { Text("Phase", fontSize = 11.sp) }, textStyle = fieldStyle, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Note: Block, Lot, and Phase are optional for non-homeowners.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Community Role / Classification", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(expanded = isDropdownExpanded, onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }) {
                    OutlinedTextField(value = selectedRole, onValueChange = {}, readOnly = true, placeholder = { Text("Select classification", fontSize = 14.sp) }, textStyle = fieldStyle, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors())
                    ExposedDropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { isDropdownExpanded = false }) {
                        roleOptions.forEach { role -> DropdownMenuItem(text = { Text(role) }, onClick = { selectedRole = role; isDropdownExpanded = false }) }
                    }
                }
                if (selectedRole.isNotBlank() && selectedRole !in listOf("Homeowner", "Visitor")) {
                    Button(
                        onClick = { proofPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Upload ID or Verification Document", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    if (selectedProofUri != null || !profile?.proofDocumentPath.isNullOrBlank()) {
                        Text("Verification document attached", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (profile?.roleRequestStatus == "pending") Text("Pending request: ${profile.requestedRole}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!profile?.role.equals(selectedRole, ignoreCase = true)) Text("Posting access is locked until an admin verifies your role.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (profile?.roleRequestStatus == "rejected") Text("Your previous role request was rejected. You may submit a new request.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                myRoleRequests.forEach { (requestedRole, status) ->
                    Text("Role request: $requestedRole — $status", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                saveFeedbackMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = {
                    val isHomeowner = selectedRole.equals("Homeowner", true)
                    validationMessage = when {
                        phoneNumberDigits.length != 10 -> "Enter a valid 10-digit mobile contact number."
                        selectedRole.isBlank() -> "Select a Community Role / Classification."
                        isHomeowner && (blockNumber.isBlank() || lotNumber.isBlank() || phaseNumber.isBlank()) -> "Enter your Block, Lot, and Phase."
                        else -> null
                    }
                    if (validationMessage == null) {
                        val fields = mutableMapOf<String, Any?>("contact" to "+63 $phoneNumberDigits", "block" to blockNumber.trim(), "lot" to lotNumber.trim(), "phase" to phaseNumber.trim())
                        onSaveProfile(fields, selectedPhotoUri, selectedProofUri)
                        if (selectedRole != profile?.role.orEmpty()) {
                            if (selectedRole in roleOptions.drop(2)) showRoleConfirmation = true
                            else FirebaseFunctions.getInstance().getHttpsCallable("setOrdinaryRole").call(mapOf("role" to selectedRole))
                                .addOnSuccessListener { saveFeedbackMessage = "Role updated." }
                                .addOnFailureListener { validationMessage = it.localizedMessage ?: "Could not update role." }
                        }
                        saveFeedbackMessage = "Saving profile changes…"
                    }
                },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Save Profile Changes", fontSize = 12.sp) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}
// SETTINGS GEAR DIALOG (PUSH NOTIFICATIONS, REMEMBER LOGIN CREDENTIALS)
// -----------------------------------------------------------------------------

@Composable
fun SettingsAndAboutDialog(
    rememberMeChecked: Boolean,
    profile: UserProfile?,
    onRememberMeChanged: (Boolean) -> Unit,
    onPreferenceChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingNotificationField by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val field = pendingNotificationField
        pendingNotificationField = null
        if (granted && field != null) onPreferenceChanged(field, true)
        else if (!granted) Toast.makeText(context, "Allow Santevi notifications in Android settings to receive push alerts.", Toast.LENGTH_LONG).show()
    }
    val updatePreference: (String, Boolean) -> Unit = { field, enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationField = field
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else onPreferenceChanged(field, enabled)
    }
    val preferences = listOf(
        Triple("Enable notifications for Marketplace", "notifyMarketplace", profile?.notifyMarketplace ?: true),
        Triple("Enable notifications for Community Chat", "notifyCommunityChat", profile?.notifyCommunityChat ?: true),
        Triple("Enable notifications for Concerns & Requests", "notifyConcernsRequests", profile?.notifyConcernsRequests ?: true),
        Triple("Enable notifications for Santevi Announcements", "notifyAnnouncements", profile?.notifyAnnouncements ?: false),
        Triple("Enable notifications for Upcoming Amenity Reservations", "notifyAmenityReservations", profile?.notifyAmenityReservations ?: false),
        Triple("Enable notifications for Community Events Calendar", "notifyCommunityEvents", profile?.notifyCommunityEvents ?: false),
        Triple("Enable notifications for Emergency Contacts", "notifyEmergencyContactUpdates", profile?.notifyEmergencyContactUpdates ?: false)
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).clip(RoundedCornerShape(16.dp))) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("App Settings & Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Text("Phone Push Notifications", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                preferences.forEach { (label, field, enabled) ->
                    Row(Modifier.fillMaxWidth().clickable { updatePreference(field, !enabled) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enabled, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        Text(label, fontSize = 11.5.sp)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Remember Login Credentials", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = rememberMeChecked, onCheckedChange = onRememberMeChanged,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MyApplicationTheme {
        HomeScreenContent(
            state = HomeUiState(
                userProfile = UserProfile(
                    displayName = "Delio Labaddan",
                    username = "delio",
                    email = "delio@example.com"
                )
            ),
            loginState = LoginFormState()
        )
    }
}
