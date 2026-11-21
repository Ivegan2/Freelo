package com.example.freelo

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.freelo.core.firebase.FirebaseClient
import com.example.freelo.data.auth.AuthRepository
import com.example.freelo.data.marketplace.MarketplaceRepository
import com.example.freelo.domain.model.AccountRole
import com.example.freelo.domain.model.UserProfile
import com.example.freelo.domain.model.ServiceListing
import com.example.freelo.ui.MarketplacePageContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.freelo.ui.theme.FreeloTheme

private val Blue = Color(0xFF1E88E5)
private val Green = Color(0xFF43A047)
private val Orange = Color(0xFFFB8C00)
private val Canvas = Color(0xFFF5F7FA)
private val Ink = Color(0xFF212121)
private val InputTextStyle = TextStyle(
    color = Ink,
    fontFamily = FontFamily.Default,
    fontSize = 16.sp
)

/** Keeps typed input, labels, cursor, and borders legible on Freelo's light surfaces. */
@Composable
private fun freeloInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    cursorColor = Blue,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = Blue,
    unfocusedBorderColor = Color(0xFFB0BEC5),
    focusedLabelColor = Blue,
    unfocusedLabelColor = Color.DarkGray
)

private enum class UserRole(val label: String) {
    CLIENT("Cliente"), FREELANCER("Freelancer"), ADMIN("Administrador")
}

private data class Destination(val id: String, val title: String, val icon: ImageVector)

private val clientDestinations = listOf(
    Destination("home", "Inicio", Icons.Default.Home),
    Destination("search", "Buscar", Icons.Default.Search),
    Destination("orders", "Pedidos", Icons.Default.ShoppingBag),
    Destination("profile", "Perfil", Icons.Default.Person)
)
private val freelancerDestinations = listOf(
    Destination("dashboard", "Panel", Icons.Default.Home),
    Destination("services", "Servicios", Icons.Default.Storefront),
    Destination("orders", "Pedidos", Icons.Default.ShoppingBag),
    Destination("profile", "Perfil", Icons.Default.Person)
)
private val adminDestinations = listOf(
    Destination("dashboard", "Panel", Icons.Default.Home),
    Destination("users", "Usuarios", Icons.Default.Person),
    Destination("services", "Servicios", Icons.Default.Category),
    Destination("reports", "Reportes", Icons.Default.Assessment)
)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clients = FirebaseClient.get(applicationContext)
        val authRepository = AuthRepository(clients)
        val marketplaceRepository = MarketplaceRepository(clients)
        setContent {
            FreeloTheme(darkTheme = false, dynamicColor = false) {
                FreeloApp(authRepository, marketplaceRepository)
            }
        }
    }
}

@Composable
private fun FreeloApp(authRepository: AuthRepository, marketplaceRepository: MarketplaceRepository) {
    var role by remember { mutableStateOf(UserRole.CLIENT) }
    var page by remember { mutableStateOf("splash") }
    val history = remember { mutableStateListOf<String>() }
    var previousPage by remember { mutableStateOf("home") }
    var adminLoginOnly by remember { mutableStateOf(false) }
    var signedInProfile by remember { mutableStateOf<UserProfile?>(null) }
    var selectedService by remember { mutableStateOf<ServiceListing?>(null) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var conversationId by remember { mutableStateOf<String?>(null) }

    fun navigate(target: String) {
        if (page != target) { history.add(page); page = target }
    }
    fun navigateRoot(target: String) { history.clear(); page = target }
    fun roleHome(): String = if (role == UserRole.CLIENT) "home" else "dashboard"

    BackHandler(enabled = page != "splash") {
        if (history.isNotEmpty()) page = history.removeAt(history.lastIndex)
        else when (page) {
            "login" -> { page = "splash"; adminLoginOnly = false }
            "register", "forgot" -> page = "login"
            else -> navigateRoot(roleHome())
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(primary = Blue, secondary = Green, tertiary = Orange, background = Canvas, surface = Color.White, onSurface = Ink)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            when (page) {
                "splash" -> SplashScreen(
                    onContinue = { adminLoginOnly = false; navigateRoot("login") },
                    onAdminAccess = { adminLoginOnly = true; navigateRoot("login") }
                )
                "login" -> LoginScreen(
                    authRepository = authRepository,
                    adminOnly = adminLoginOnly,
                    onLogin = { profile ->
                        signedInProfile = profile; role = profile.role.toUiRole()
                        navigateRoot(if (profile.role == AccountRole.CLIENT) "home" else "dashboard")
                    },
                    onBiometricLogin = { profile ->
                        signedInProfile = profile; role = profile.role.toUiRole()
                        navigateRoot(if (profile.role == AccountRole.CLIENT) "home" else "dashboard")
                    },
                    onForgot = { navigate("forgot") },
                    onRegister = { navigate("register") },
                    onBack = { adminLoginOnly = false; navigateRoot("splash") }
                )
                "register" -> RegistrationScreen(
                    authRepository = authRepository,
                    onRegistered = { profile ->
                        signedInProfile = profile; role = profile.role.toUiRole()
                        navigateRoot(if (profile.role == AccountRole.CLIENT) "home" else "dashboard")
                    },
                    onBack = { navigateRoot("login") }
                )
                "forgot" -> RecoveryScreen(authRepository = authRepository, onBack = { navigateRoot("login") })
                else -> {
                    val profile = signedInProfile
                    if (profile == null) {
                        LoginScreen(authRepository, adminLoginOnly, onLogin = { signedInProfile = it; role = it.role.toUiRole(); navigateRoot(if (it.role == AccountRole.CLIENT) "home" else "dashboard") },
                            onBiometricLogin = { signedInProfile = it; role = it.role.toUiRole(); navigateRoot(if (it.role == AccountRole.CLIENT) "home" else "dashboard") },
                            onForgot = { navigate("forgot") }, onRegister = { navigate("register") }, onBack = { navigateRoot("splash") })
                    } else MainShell(
                        role = role, profile = profile, page = page, repository = marketplaceRepository, authRepository = authRepository,
                        selectedService = selectedService, onService = { selectedService = it }, selectedOrderId = selectedOrderId,
                        onOrder = { selectedOrderId = it }, conversationId = conversationId, onConversation = { conversationId = it },
                        onNavigate = ::navigate, onNavigateRoot = ::navigateRoot,
                        onBiometric = { previousPage = page; navigate("biometric") },
                        onProfileSaved = { signedInProfile = it },
                        onLogout = { authRepository.signOut(); signedInProfile = null; selectedService = null; selectedOrderId = null; conversationId = null; history.clear(); navigateRoot("login") }
                    )
                }
            }
        }
    }
}

private fun AccountRole.toUiRole(): UserRole = when (this) {
    AccountRole.CLIENT -> UserRole.CLIENT
    AccountRole.FREELANCER -> UserRole.FREELANCER
    AccountRole.ADMIN -> UserRole.ADMIN
}

private fun UserRole.toAccountRole(): AccountRole = when (this) {
    UserRole.CLIENT -> AccountRole.CLIENT
    UserRole.FREELANCER -> AccountRole.FREELANCER
    UserRole.ADMIN -> AccountRole.ADMIN
}

@Composable
private fun SplashScreen(onContinue: () -> Unit, onAdminAccess: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(large = true)
            Spacer(Modifier.height(22.dp))
            Text("Tu talento, más cerca", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Servicios locales hechos por personas expertas.", color = Color.Gray)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
        }
        OutlinedButton(onClick = onAdminAccess, modifier = Modifier.align(Alignment.TopEnd)) {
            androidx.compose.material3.Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Administrador")
        }
    }
}

@Composable
private fun BrandMark(large: Boolean = false) {
    val size = if (large) 82.dp else 42.dp
    Box(
        modifier = Modifier.size(size).background(Blue, RoundedCornerShape(if (large) 24.dp else 14.dp)),
        contentAlignment = Alignment.Center
    ) { Text("F", color = Color.White, fontSize = if (large) 44.sp else 26.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun LoginScreen(
    authRepository: AuthRepository,
    adminOnly: Boolean,
    onLogin: (UserProfile) -> Unit,
    onBiometricLogin: (UserProfile) -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark()
        Spacer(Modifier.height(22.dp))
        Text(if (adminOnly) "Acceso de administrador" else "Bienvenido a Freelo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (adminOnly) "Ingresa con una cuenta autorizada de administración."
            else "Encuentra talento local o comparte el tuyo.",
            color = Color.Gray
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        Text("¿Olvidaste tu contraseña?", color = Blue, modifier = Modifier.align(Alignment.End).clickable(onClick = onForgot).padding(12.dp))
        errorMessage?.let { Text(it, color = Color(0xFFB00020), modifier = Modifier.padding(vertical = 6.dp)) }
        Button(onClick = {
            loading = true
            errorMessage = null
            scope.launch {
                try {
                    val profile = withContext(Dispatchers.IO) { authRepository.signIn(email, password) }
                    if (adminOnly && profile.role != AccountRole.ADMIN) {
                        authRepository.signOut()
                        errorMessage = "Esta cuenta no tiene permisos de administrador."
                    } else {
                        onLogin(profile)
                    }
                } catch (error: Exception) {
                    errorMessage = error.message ?: "No fue posible iniciar sesión. Revisa Firebase Emulator."
                } finally { loading = false }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Ingresando…" else "Iniciar sesión") }
        if (authRepository.currentUid() != null) {
            OutlinedButton(onClick = {
                val activity = context as? FragmentActivity
                if (activity == null) {
                    biometricMessage = "La autenticación biométrica no está disponible en esta pantalla."
                } else {
                    val availability = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                        biometricMessage = "Configura una huella en la seguridad del dispositivo para usar este acceso."
                    } else {
                        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                scope.launch {
                                    loading = true
                                    try {
                                        val profile = withContext(Dispatchers.IO) { authRepository.restoreCurrentProfile() }
                                        if (adminOnly && profile.role != AccountRole.ADMIN) {
                                            biometricMessage = "La cuenta activa no tiene permisos de administrador."
                                        } else {
                                            onBiometricLogin(profile)
                                        }
                                    }
                                    catch (error: Exception) { biometricMessage = error.message ?: "No fue posible restaurar la sesión." }
                                    finally { loading = false }
                                }
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                biometricMessage = errString.toString()
                            }
                        })
                        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Iniciar sesión en Freelo")
                            .setSubtitle("Confirma tu identidad con huella o biometría del dispositivo")
                            .setNegativeButtonText("Usar contraseña")
                            .build())
                    }
                }
            }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Ingresar con huella") }
        }
        biometricMessage?.let { Text(it, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        if (adminOnly) {
            Text("Volver", color = Blue, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onBack).padding(12.dp))
        } else {
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
            Text("El rol se asigna al registrar la cuenta y se valida desde el perfil.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun RegistrationScreen(authRepository: AuthRepository, onRegistered: (UserProfile) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(AccountRole.CLIENT) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Crear cuenta", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Regístrate para contratar servicios o publicarlos.", color = Color.Gray, modifier = Modifier.padding(vertical = 10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mínimo 8 caracteres)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        Text("Tipo de cuenta", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AccountRole.CLIENT to "Cliente",
                AccountRole.FREELANCER to "Freelancer",
                AccountRole.ADMIN to "Administrador"
            ).forEach { (value, label) ->
                FilterChip(selected = role == value, onClick = { role = value }, label = { Text(label) })
            }
        }
        errorMessage?.let { Text(it, color = Color(0xFFB00020), modifier = Modifier.padding(vertical = 6.dp)) }
        Button(onClick = {
            loading = true; errorMessage = null
            scope.launch {
                try { onRegistered(withContext(Dispatchers.IO) { authRepository.register(name, email, password, role) }) }
                catch (error: Exception) { errorMessage = error.message ?: "No fue posible crear la cuenta." }
                finally { loading = false }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (loading) "Creando cuenta…" else "Crear cuenta") }
        Text("Volver al inicio de sesión", color = Blue, modifier = Modifier.clickable(onClick = onBack).padding(12.dp))
    }
}

@Composable
private fun RecoveryScreen(authRepository: AuthRepository, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Recuperar contraseña", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Te enviaremos instrucciones al correo asociado a tu cuenta.", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = InputTextStyle, colors = freeloInputColors())
        Spacer(Modifier.height(16.dp))
        message?.let { Text(it, color = Green) }
        errorMessage?.let { Text(it, color = Color(0xFFB00020)) }
        Button(onClick = {
            loading = true; errorMessage = null; message = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { authRepository.sendPasswordReset(email) }
                    message = "Si el correo está registrado, Firebase Emulator procesó la solicitud de recuperación."
                } catch (error: Exception) { errorMessage = error.message ?: "No fue posible enviar la solicitud." }
                finally { loading = false }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Enviando…" else "Enviar instrucciones") }
        Text("Volver al inicio de sesión", color = Blue, modifier = Modifier.clickable(onClick = onBack).padding(12.dp))
    }
}

@Composable
private fun BiometricScreen(onSuccess: () -> Unit, onFallback: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).background(Blue.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Default.Lock, null, tint = Blue, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Acceso biométrico", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("La integración con BiometricPrompt se conecta en la siguiente etapa.", color = Color.Gray)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSuccess) { Text("Simular validación") }
        Text("Usar contraseña", color = Blue, modifier = Modifier.clickable(onClick = onFallback).padding(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    role: UserRole,
    profile: UserProfile,
    page: String,
    repository: MarketplaceRepository,
    authRepository: AuthRepository,
    selectedService: ServiceListing?,
    onService: (ServiceListing?) -> Unit,
    selectedOrderId: String?,
    onOrder: (String) -> Unit,
    conversationId: String?,
    onConversation: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateRoot: (String) -> Unit,
    onBiometric: () -> Unit,
    onProfileSaved: (UserProfile) -> Unit,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val destinations = when (role) {
        UserRole.CLIENT -> clientDestinations
        UserRole.FREELANCER -> freelancerDestinations
        UserRole.ADMIN -> adminDestinations
    }
    val title = allDestinations(role).firstOrNull { it.id == page }?.title ?: page.toScreenTitle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    BrandMark()
                    Text("Freelo · ${role.label}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(12.dp))
                    allDestinations(role).forEach { item ->
                        DrawerRow(item, selected = item.id == page) {
                            onNavigateRoot(item.id)
                            scope.launch { drawerState.close() }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerRow(Destination("biometric", "Acceso biométrico", Icons.Default.Lock), false, onBiometric)
                    DrawerRow(Destination("settings", "Configuración", Icons.Default.Settings), page == "settings") { onNavigateRoot("settings") }
                    DrawerRow(Destination("logout", "Cerrar sesión", Icons.AutoMirrored.Filled.ArrowBack), false, onLogout)
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Canvas,
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        Text("☰", fontSize = 24.sp, color = Blue, modifier = Modifier.clickable { scope.launch { drawerState.open() } }.padding(horizontal = 16.dp))
                    },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { onNavigate("notifications") }) {
                            androidx.compose.material3.Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    destinations.forEach { item ->
                        NavigationBarItem(
                            selected = page == item.id,
                            onClick = { onNavigateRoot(item.id) },
                            icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        ) { padding ->
            MarketplacePageContent(
                role = role.toAccountRole(), profile = profile, page = page, repository = repository,
                auth = authRepository, modifier = Modifier.padding(padding), service = selectedService,
                onService = onService, orderId = selectedOrderId, onOrder = onOrder,
                conversationId = conversationId, onConversationSelected = onConversation, onNavigate = onNavigate,
                onProfileSaved = onProfileSaved, onLogout = onLogout
            )
        }
    }
}

@Composable
private fun DrawerRow(item: Destination, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(if (selected) Blue.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(item.icon, contentDescription = null, tint = if (selected) Blue else Color.DarkGray)
        Spacer(Modifier.width(14.dp))
        Text(item.title, color = if (selected) Blue else Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun allDestinations(role: UserRole): List<Destination> = when (role) {
    UserRole.CLIENT -> clientDestinations + listOf(
        Destination("detail", "Detalle del servicio", Icons.Default.Storefront),
        Destination("freelancer", "Perfil del freelancer", Icons.Default.Person),
        Destination("cart", "Carrito", Icons.Default.ShoppingCart),
        Destination("checkout", "Checkout y pago", Icons.Default.Lock),
        Destination("order_detail", "Detalle del pedido", Icons.Default.ShoppingBag),
        Destination("messages", "Mensajes", Icons.AutoMirrored.Filled.Chat)
    )
    UserRole.FREELANCER -> freelancerDestinations + listOf(
        Destination("messages", "Mensajes", Icons.AutoMirrored.Filled.Chat),
        Destination("earnings", "Ganancias", Icons.Default.Assessment),
        Destination("service_form", "Crear servicio", Icons.Default.Storefront)
    )
    UserRole.ADMIN -> adminDestinations + listOf(
        Destination("audit", "Auditoría", Icons.Default.Lock)
    )
}

@Composable
private fun PageContent(role: UserRole, profile: UserProfile?, page: String, modifier: Modifier, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    when (page) {
        "home" -> ClientHome(modifier, onNavigate)
        "dashboard" -> DashboardPage(role, modifier, onNavigate)
        "search" -> SearchPage(modifier, onNavigate)
        "services" -> ServicesPage(role, modifier, onNavigate)
        "users" -> UsersPage(modifier, onNavigate)
        "orders" -> OrdersPage(role, modifier, onNavigate)
        "profile" -> ProfilePage(role, profile, modifier, onNavigate, onLogout)
        "detail" -> ServiceDetail(modifier, onNavigate)
        "freelancer" -> FreelancerProfile(modifier, onNavigate)
        "cart" -> CartPage(modifier, onNavigate)
        "checkout" -> CheckoutPage(modifier, onNavigate)
        "order_detail" -> OrderDetailPage(modifier, onNavigate)
        "messages" -> MessagesPage(modifier)
        "earnings" -> EarningsPage(modifier)
        "service_form" -> ServiceFormPage(modifier, onNavigate)
        "reports" -> ReportsPage(modifier, onNavigate)
        "settings" -> SettingsPage(modifier)
        "audit" -> AuditPage(modifier)
        "notifications" -> GenericListPage("Notificaciones", listOf("Nuevo pedido recibido", "Tu perfil fue actualizado", "Servicio aprobado"), modifier)
        else -> GenericListPage(page.toScreenTitle(), listOf("Contenido de ejemplo", "Los datos reales se conectarán al backend"), modifier)
    }
}

@Composable
private fun PageColumn(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), content = content)
}

@Composable
private fun ClientHome(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Hola, Alex 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("¿Qué necesitas resolver hoy?", color = Color.Gray)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = "", onValueChange = { onNavigate("search") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Busca un servicio") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(16.dp))
        Text("Explora categorías", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            listOf("Diseño", "Desarrollo", "Foto y video").forEach { CategoryPill(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Servicios destacados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Ver todos", color = Blue, modifier = Modifier.clickable { onNavigate("search") })
        }
        ServiceCard("Identidad visual para tu marca", "Laura Méndez · Diseño gráfico", "$180.000", "⭐ 4.9 · Bogotá") { onNavigate("detail") }
        ServiceCard("Tu sitio web listo para crecer", "Mateo Ruiz · Desarrollo web", "$650.000", "⭐ 5.0 · Medellín") { onNavigate("detail") }
    }
}

@Composable
private fun CategoryPill(name: String) {
    Text(name, color = Blue, fontSize = 12.sp, modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 9.dp))
}

@Composable
private fun ServiceCard(title: String, subtitle: String, price: String, meta: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Default.Storefront, null, tint = Blue, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                Text(meta, color = Color.Gray, fontSize = 12.sp)
                Text("Desde $price", color = Blue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardPage(role: UserRole, modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text(if (role == UserRole.ADMIN) "Resumen de Freelo" else "Tu actividad", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Actualizado hoy · Datos de demostración", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        val stats = if (role == UserRole.ADMIN) listOf("Usuarios activos" to "1.248", "Servicios publicados" to "386", "Ingresos por comisión" to "$8,4 M") else listOf("Ganancias del mes" to "$2,4 M", "Pedidos activos" to "8", "Calificación" to "4,9 ⭐")
        stats.forEach { MetricCard(it.first, it.second) }
        SectionTitle("Accesos rápidos")
        ActionCard("Gestionar servicios", "Revisa publicaciones y estados") { onNavigate("services") }
        ActionCard(if (role == UserRole.ADMIN) "Gestionar usuarios" else "Revisar pedidos", "Consulta la actividad reciente") { onNavigate(if (role == UserRole.ADMIN) "users" else "orders") }
        if (role == UserRole.FREELANCER) ActionCard("Ver ganancias", "Pagos, comisiones y retiros") { onNavigate("earnings") }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Bold, color = Blue, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SearchPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Encuentra talento local", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text("¿Qué servicio buscas?") }, leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { CategoryPill("Categoría"); CategoryPill("Precio"); CategoryPill("Distancia") }
        Text("Resultados cerca de ti", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("📍 Bogotá · Cambiar ubicación", color = Color.Gray, modifier = Modifier.padding(vertical = 6.dp))
        ServiceCard("Fotografía de producto", "Camila Torres · Fotografía", "$220.000", "⭐ 4.8 · A 2 km") { onNavigate("detail") }
        ServiceCard("Diseño de marca", "Laura Méndez · Branding", "$180.000", "⭐ 4.9 · A 4 km") { onNavigate("detail") }
        ActionCard("Ver freelancers en el mapa", "La integración de mapas se añade con la API key") { onNavigate("freelancer") }
    }
}

@Composable
private fun ServicesPage(role: UserRole, modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text(if (role == UserRole.ADMIN) "Servicios publicados" else "Mis servicios", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(if (role == UserRole.ADMIN) "Modera y revisa publicaciones" else "Administra tus publicaciones y métricas", color = Color.Gray)
        if (role == UserRole.FREELANCER) Button(onClick = { onNavigate("service_form") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("＋ Crear servicio") }
        listOf("Identidad visual para tu marca" to "Activo · 24 visitas", "Diseño de piezas para redes" to "En revisión · 8 visitas", "Presentación corporativa" to "Pausado · 12 visitas").forEach { (name, status) ->
            ActionCard(name, status) { onNavigate(if (role == UserRole.FREELANCER) "service_form" else "detail") }
        }
    }
}

@Composable
private fun UsersPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Gestión de usuarios", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("Buscar por nombre o correo") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { CategoryPill("Todos"); CategoryPill("Clientes"); CategoryPill("Freelancers") }
        listOf("Valentina Gómez · Cliente", "Laura Méndez · Freelancer", "Mateo Ruiz · Freelancer").forEach { name ->
            ActionCard(name, "Activo · Ver perfil · Suspender") { onNavigate("profile") }
        }
        Button(onClick = { onNavigate("profile") }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear usuario (demo)") }
    }
}

@Composable
private fun OrdersPage(role: UserRole, modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text(if (role == UserRole.FREELANCER) "Pedidos recibidos" else "Mis pedidos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) { CategoryPill("Todos"); CategoryPill("En proceso"); CategoryPill("Entregados") }
        listOf("Diseño de identidad visual" to "En proceso · 12 nov", "Fotografía de producto" to "Pendiente · 15 nov", "Landing page" to "Entregado · 04 nov").forEach { (name, status) -> ActionCard(name, "$status · $320.000") { onNavigate("order_detail") } }
    }
}

@Composable
private fun ProfilePage(role: UserRole, profile: UserProfile?, modifier: Modifier, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    PageColumn(modifier) {
        Text("Mi perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp).background(Color(0xFFE3F2FD), CircleShape), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Default.AccountCircle, null, tint = Blue, modifier = Modifier.size(52.dp)) }
                Text(profile?.name ?: "Usuario Freelo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(profile?.email ?: role.label, color = Color.Gray)
                Text("Editar perfil", color = Blue, modifier = Modifier.clickable { onNavigate("profile_edit") }.padding(8.dp))
            }
        }
        ActionCard("Configuración y privacidad", "Preferencias, consentimiento y seguridad") { onNavigate("profile_edit") }
        ActionCard("Acceso biométrico", "Protege el acceso a tu cuenta") { onNavigate("biometric") }
        ActionCard("Cerrar sesión", "Salir de Freelo en este dispositivo", onLogout)
    }
}

@Composable
private fun ServiceDetail(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Default.Storefront, null, tint = Blue, modifier = Modifier.size(60.dp)) }
        Text("Identidad visual para tu marca", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Desde $180.000 · Entrega en 5 días · ⭐ 4.9", color = Color.Gray)
        ActionCard("Laura Méndez · Diseñadora", "Ver perfil, portafolio y reseñas") { onNavigate("freelancer") }
        SectionTitle("Sobre este servicio")
        Text("Diseño una identidad visual clara y memorable para emprendimientos locales. Incluye propuesta visual, paleta y archivos finales.", color = Color.DarkGray)
        SectionTitle("Reseñas")
        Text("“Excelente comunicación y resultado impecable.” · ⭐⭐⭐⭐⭐", color = Color.Gray)
        Spacer(Modifier.weight(1f))
        Button(onClick = { onNavigate("cart") }, modifier = Modifier.fillMaxWidth()) { Text("Agregar al carrito · $180.000") }
        OutlinedButton(onClick = { onNavigate("messages") }, modifier = Modifier.fillMaxWidth()) { Text("Contactar freelancer") }
    }
}

@Composable
private fun FreelancerProfile(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Perfil del freelancer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MetricCard("Laura Méndez · Bogotá", "⭐ 4.9")
        Text("Diseñadora gráfica especializada en identidad visual para negocios locales. 6 años de experiencia y 42 proyectos completados.")
        SectionTitle("Especialidades")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CategoryPill("Branding"); CategoryPill("Editorial") }
        SectionTitle("Portafolio y servicios")
        ServiceCard("Identidad visual para tu marca", "Diseño gráfico", "$180.000", "⭐ 4.9") { onNavigate("detail") }
        Button(onClick = { onNavigate("messages") }, modifier = Modifier.fillMaxWidth()) { Text("Contactar") }
    }
}

@Composable
private fun CartPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Tu carrito", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        ActionCard("Identidad visual para tu marca", "Laura Méndez · Paquete estándar") { onNavigate("detail") }
        SectionTitle("Resumen")
        PriceRow("Subtotal", "$180.000")
        PriceRow("Comisión de servicio", "$18.000")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        PriceRow("Total", "$198.000", bold = true)
        Spacer(Modifier.weight(1f))
        Button(onClick = { onNavigate("checkout") }, modifier = Modifier.fillMaxWidth()) { Text("Ir a pagar") }
        OutlinedButton(onClick = { onNavigate("search") }, modifier = Modifier.fillMaxWidth()) { Text("Seguir buscando") }
    }
}

@Composable
private fun CheckoutPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Checkout", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Resumen de contratación", color = Color.Gray)
        ActionCard("Identidad visual para tu marca", "Subtotal $180.000 · Comisión $18.000 · Total $198.000") {}
        SectionTitle("Método de pago")
        listOf("Tarjeta terminada en 4242", "PSE · Cuenta bancaria", "Nequi").forEach { CategoryPill(it) }
        ActionCard("Pago protegido", "La integración con Wompi o PayU se configura con credenciales") {}
        Button(onClick = { onNavigate("order_detail") }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar pago (demo)") }
        Text("La confirmación biométrica real se conectará antes de procesar el pago.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun OrderDetailPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Pedido #FL-2048", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MetricCard("Estado", "En proceso")
        ActionCard("Identidad visual para tu marca", "Freelancer: Laura Méndez · Total: $198.000") {}
        SectionTitle("Seguimiento")
        listOf("Pago confirmado", "Pedido aceptado", "Entrega en preparación").forEach { ActionCard("✓  $it", "Actualización del pedido") {} }
        Button(onClick = { onNavigate("messages") }, modifier = Modifier.fillMaxWidth()) { Text("Escribir al freelancer") }
    }
}

@Composable
private fun MessagesPage(modifier: Modifier) {
    PageColumn(modifier) {
        Text("Mensajes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        listOf("Laura Méndez · ¿Tienes referencias de color?", "Mateo Ruiz · Te envié una propuesta", "Camila Torres · Gracias por contratarme").forEach { ActionCard(it, "Hoy · Conversación de demostración") {} }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Escribe un mensaje") }, trailingIcon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Chat, null) })
    }
}

@Composable
private fun EarningsPage(modifier: Modifier) {
    PageColumn(modifier) {
        Text("Ganancias", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        MetricCard("Saldo disponible", "$840.000")
        MetricCard("Ganancias del mes", "$2.400.000")
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Solicitar retiro (demo)") }
        SectionTitle("Movimientos recientes")
        listOf("Identidad visual · +$320.000", "Diseño de piezas · +$180.000", "Comisión plataforma · -$18.000").forEach { ActionCard(it, "Movimiento de ejemplo · Noviembre") {} }
    }
}

@Composable
private fun ServiceFormPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Crear o editar servicio", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth(), label = { Text("Título del servicio") })
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Descripción") }, minLines = 3)
        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Precio en COP") })
        Spacer(Modifier.height(10.dp))
        ActionCard("Categoría y disponibilidad", "Seleccionar categoría · Definir tiempos de entrega") {}
        ActionCard("Fotos y portafolio", "Cámara o galería se conectarán en la siguiente etapa") {}
        Button(onClick = { onNavigate("services") }, modifier = Modifier.fillMaxWidth()) { Text("Guardar borrador (demo)") }
        OutlinedButton(onClick = { onNavigate("services") }, modifier = Modifier.fillMaxWidth()) { Text("Enviar a revisión (demo)") }
    }
}

@Composable
private fun ReportsPage(modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Reportes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        listOf("Servicio con información incompleta", "Reporte de usuario · Solicitud de revisión", "Disputa de pago · Pedido #FL-2048").forEach { ActionCard(it, "Pendiente de revisión") { onNavigate("order_detail") } }
        ActionCard("Resumen de ventas", "Ingresos, comisiones y transacciones") {}
    }
}

@Composable
private fun SettingsPage(modifier: Modifier) {
    PageColumn(modifier) {
        Text("Configuración", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        listOf("Datos personales", "Privacidad y consentimiento", "Preferencias de notificaciones", "Métodos de pago", "Términos y condiciones").forEach { ActionCard(it, "Administrar preferencia") {} }
    }
}

@Composable
private fun AuditPage(modifier: Modifier) {
    PageColumn(modifier) {
        Text("Auditoría", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Registro de acciones administrativas", color = Color.Gray)
        listOf("Usuario suspendido · Admin #02", "Servicio aprobado · Admin #01", "Comisión actualizada · Admin #01").forEach { ActionCard(it, "08 nov 2026 · Ver registro") {} }
    }
}

@Composable
private fun GenericListPage(title: String, rows: List<String>, modifier: Modifier) {
    PageColumn(modifier) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        rows.forEach { ActionCard(it, "Freelo · Ejemplo de interfaz") {} }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun PriceRow(label: String, amount: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(amount, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun String.toScreenTitle(): String = when (this) {
    "home" -> "Inicio"
    "dashboard" -> "Dashboard"
    "search" -> "Búsqueda"
    "services" -> "Servicios"
    "users" -> "Usuarios"
    "orders" -> "Pedidos"
    "profile" -> "Perfil"
    "detail" -> "Detalle del servicio"
    "freelancer" -> "Perfil del freelancer"
    "cart" -> "Carrito"
    "checkout" -> "Pago"
    "order_detail" -> "Detalle del pedido"
    "messages" -> "Mensajes"
    "earnings" -> "Ganancias"
    "service_form" -> "Crear servicio"
    "reports" -> "Reportes"
    "settings" -> "Configuración"
    "audit" -> "Auditoría"
    "notifications" -> "Notificaciones"
    else -> replaceFirstChar { it.uppercase() }
}
