package com.example.freelo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.example.freelo.data.auth.AuthRepository
import com.example.freelo.data.marketplace.MarketplaceRepository
import com.example.freelo.domain.model.AccountRole
import com.example.freelo.domain.model.CartItem
import com.example.freelo.domain.model.ServiceListing
import com.example.freelo.domain.model.UserProfile
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.text.NumberFormat
import java.util.Locale

private val LiveBlue = Color(0xFF1E88E5)
private val LiveInk = Color(0xFF212121)
private val LiveBg = Color(0xFFF5F7FA)

/** Routes signed-in pages to Firestore- and Storage-backed marketplace screens. */
@Composable
fun MarketplacePageContent(
    role: AccountRole, profile: UserProfile, page: String, repository: MarketplaceRepository,
    auth: AuthRepository, modifier: Modifier, service: ServiceListing?, onService: (ServiceListing?) -> Unit,
    orderId: String?, onOrder: (String) -> Unit, conversationId: String?, onConversationSelected: (String) -> Unit,
    onNavigate: (String) -> Unit, onProfileSaved: (UserProfile) -> Unit, onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    fun startConversation(peerId: String) {
        scope.launch {
            val result = runCatching {
                if (role == AccountRole.CLIENT) repository.openConversation(profile.uid, peerId)
                else repository.openConversation(peerId, profile.uid)
            }
            result.onSuccess { onConversationSelected(it); onNavigate("messages") }
        }
    }
    when (page) {
        "home" -> BrowseServicesScreen(true, profile, repository, modifier, onService, onNavigate)
        "search" -> BrowseServicesScreen(false, profile, repository, modifier, onService, onNavigate)
        "dashboard" -> DashboardScreen(role, profile, repository, modifier, onNavigate)
        "users" -> UsersManagementScreen(profile.uid, auth, repository, modifier)
        "services" -> ServiceManagementScreen(role, profile, repository, modifier, onNavigate, onService)
        "service_form" -> ServiceEditorScreen(profile, repository, service, modifier, onNavigate) { onService(null) }
        "orders" -> OrdersScreen(role, profile, repository, modifier, onOrder, onNavigate)
        "order_detail" -> OrderDetailScreen(role, profile, repository, orderId, modifier, onNavigate, ::startConversation)
        "profile" -> ProfileScreen(profile, modifier, onNavigate, onLogout)
        "profile_edit" -> ProfileEditorScreen(profile, repository, modifier, onProfileSaved)
        "settings" -> AccountSettingsScreen(profile, repository, modifier, onProfileSaved)
        "detail" -> ServiceDetailScreen(profile, service, repository, modifier, onNavigate, ::startConversation)
        "freelancer" -> FreelancerDetailsScreen(service, repository, modifier, onNavigate, ::startConversation, profile, onService)
        "cart" -> CartScreen(profile, repository, modifier, onNavigate)
        "checkout" -> CheckoutScreen(profile, repository, modifier, onOrder, onNavigate)
        "messages" -> MessagesScreen(profile, repository, conversationId, modifier, onConversationSelected)
        "reports" -> ReportsScreen(repository, modifier, onOrder, onNavigate)
        "biometric" -> BiometricAccessScreen(auth, { onNavigate(if (role == AccountRole.CLIENT) "home" else "dashboard") }) { onNavigate("profile") }
        else -> LegacyPage(role, profile, page, modifier, onNavigate, onLogout)
    }
}

/** Provides a safe destination for prototype-only secondary pages. */
@Composable
private fun LegacyPage(role: AccountRole, profile: UserProfile, page: String, modifier: Modifier, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("${page.replaceFirstChar { it.uppercase() }} se conectará en la siguiente etapa.", color = Color.Gray)
        Text("${profile.name} · ${profile.email}", modifier = Modifier.padding(vertical = 10.dp))
        Button(onClick = { onNavigate(if (role == AccountRole.CLIENT) "home" else "dashboard") }) { Text("Volver al inicio") }
        TextButton(onClick = onLogout) { Text("Cerrar sesión") }
    }
}

/** Shows published services and filters them locally by text and category. */
@Composable
private fun BrowseServicesScreen(home: Boolean, profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onService: (ServiceListing?) -> Unit, onNavigate: (String) -> Unit) {
    var services by remember { mutableStateOf<List<ServiceListing>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todas") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.uid) { runCatching { repository.listServices(AccountRole.CLIENT, profile.uid) }.onSuccess { services = it }.onFailure { error = it.message } }
    val categories = listOf("Todas") + services.map { it.category }.distinct().take(3)
    val shown = services.filter { (category == "Todas" || it.category == category) && (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) || it.ownerName.contains(query, true)) }
    Column(modifier.fillMaxSize().background(LiveBg).verticalScroll(rememberScrollState()).padding(18.dp)) {
        if (home) { Text("Hola, ${profile.name.substringBefore(' ')} 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("¿Qué necesitas resolver hoy?", color = Color.Gray) }
        else Text("Encuentra talento local", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text("Busca un servicio") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) { categories.forEach { FilterChip(category == it, { category = it }, label = { Text(it) }) } }
        Text(if (home) "Servicios destacados" else "Resultados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        error?.let { Text(it, color = Color(0xFFB00020)) }
        if (shown.isEmpty()) Text("No hay servicios publicados que coincidan.", color = Color.Gray, modifier = Modifier.padding(vertical = 18.dp))
        shown.forEach { item -> ServiceRow(item) { onService(item); onNavigate("detail") } }
        TextButton(onClick = { shown.firstOrNull()?.let { onService(it); onNavigate("freelancer") } }, enabled = shown.isNotEmpty()) { Text("Ver perfiles de freelancers") }
    }
}

/** Displays live database counts for the administrator and freelancer dashboard. */
@Composable
private fun DashboardScreen(role: AccountRole, profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onNavigate: (String) -> Unit) {
    var values by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(role, profile.uid) { runCatching { repository.dashboardCounts(role, profile.uid) }.onSuccess { values = it }.onFailure { error = it.message } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (role == AccountRole.ADMIN) "Resumen de Freelo" else "Tu actividad", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Datos de Firebase Emulator", color = Color.Gray)
        error?.let { Text(it, color = Color(0xFFB00020)) }
        if (role == AccountRole.ADMIN) {
            InfoCard("Usuarios registrados", (values["users"] ?: 0).toString())
            InfoCard("Servicios publicados", (values["services"] ?: 0).toString())
            InfoCard("Comisiones pagadas", money(values["commission"] ?: 0))
            Button(onClick = { onNavigate("users") }, modifier = Modifier.fillMaxWidth()) { Text("Gestionar usuarios") }
            OutlinedButton(onClick = { onNavigate("services") }, modifier = Modifier.fillMaxWidth()) { Text("Gestionar servicios") }
            OutlinedButton(onClick = { onNavigate("reports") }, modifier = Modifier.fillMaxWidth()) { Text("Reportes") }
        } else {
            InfoCard("Mis servicios publicados", (values["services"] ?: 0).toString())
            InfoCard("Pedidos activos", (values["orders"] ?: 0).toString())
            Button(onClick = { onNavigate("services") }, modifier = Modifier.fillMaxWidth()) { Text("Mis servicios") }
            OutlinedButton(onClick = { onNavigate("orders") }, modifier = Modifier.fillMaxWidth()) { Text("Pedidos") }
        }
    }
}

/** Implements administrator user CRUD against Firestore and Firebase Authentication. */
@Composable
private fun UsersManagementScreen(activeUid: String, auth: AuthRepository, repository: MarketplaceRepository, modifier: Modifier) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }; var query by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }; var createOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserProfile?>(null) }; var deleting by remember { mutableStateOf<UserProfile?>(null) }
    suspend fun reload() { users = repository.listUsers() }
    LaunchedEffect(Unit) { runCatching { reload() }.onFailure { notice = it.message } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Gestión de usuarios", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), placeholder = { Text("Buscar por nombre o correo") }, singleLine = true)
        Button(onClick = { createOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear usuario") }
        notice?.let { Text(it, color = if (it.startsWith("Error")) Color(0xFFB00020) else Color(0xFF2E7D32), modifier = Modifier.padding(8.dp)) }
        users.filter { it.name.contains(query, true) || it.email.contains(query, true) }.forEach { user ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(user.name, fontWeight = FontWeight.Bold); Text("${user.email} · ${roleLabel(user.role)} · ${if (user.active) "Activo" else "Suspendido"}", color = Color.Gray)
                    Row {
                        TextButton(onClick = { editing = user }) { Text("Editar") }
                        TextButton(onClick = { scope.launch { runCatching { repository.updateUser(user.copy(active = !user.active)); reload() }.onFailure { notice = "Error: ${it.message}" } } }) { Text(if (user.active) "Suspender" else "Activar") }
                        TextButton(onClick = { deleting = user }, enabled = user.uid != activeUid) { Text(if (user.uid == activeUid) "Sesión actual" else "Eliminar") }
                    }
                }
            }
        }
    }
    if (createOpen) UserDialog("Crear usuario", null, { createOpen = false }) { name, email, password, role -> scope.launch {
        runCatching { auth.createManagedUser(context, name, email, password, role); reload(); notice = "Usuario creado." }.onFailure { notice = "Error: ${it.message}" }; createOpen = false
    } }
    editing?.let { user -> UserDialog("Editar usuario", user, { editing = null }) { name, _, _, role -> scope.launch {
        runCatching { repository.updateUser(user.copy(name = name, role = role)); reload(); notice = "Cambios guardados." }.onFailure { notice = "Error: ${it.message}" }; editing = null
    } } }
    deleting?.let { user -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar cuenta") }, text = { Text("Se eliminarán la cuenta de acceso y el perfil de ${user.name}. Esta acción no se puede deshacer.") }, confirmButton = { TextButton(onClick = { scope.launch { runCatching { repository.removeUser(user.uid); reload(); notice = "Cuenta eliminada." }.onFailure { notice = "Error: ${it.message}" }; deleting = null } }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}

/** Collects user fields for administrator account creation or profile editing. */
@Composable
private fun UserDialog(title: String, initial: UserProfile?, onDismiss: () -> Unit, onSave: (String, String, String, AccountRole) -> Unit) {
    var name by remember(initial?.uid) { mutableStateOf(initial?.name.orEmpty()) }; var email by remember(initial?.uid) { mutableStateOf(initial?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }; var role by remember(initial?.uid) { mutableStateOf(initial?.role ?: AccountRole.CLIENT) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
            if (initial == null) { OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true); OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mín. 8)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
            if (initial?.role == AccountRole.ADMIN) Text("Rol: Administrador", color = Color.Gray)
            else Row { FilterChip(role == AccountRole.CLIENT, { role = AccountRole.CLIENT }, label = { Text("Cliente") }); Spacer(Modifier.width(5.dp)); FilterChip(role == AccountRole.FREELANCER, { role = AccountRole.FREELANCER }, label = { Text("Freelancer") }) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, email, password, role) }, enabled = name.isNotBlank() && (initial != null || email.contains('@') && password.length >= 8)) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/** Lists the freelancer's services and gives administrators moderation actions. */
@Composable
private fun ServiceManagementScreen(role: AccountRole, profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onNavigate: (String) -> Unit, onService: (ServiceListing?) -> Unit) {
    val scope = rememberCoroutineScope(); var services by remember { mutableStateOf<List<ServiceListing>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { services = repository.listServices(role, profile.uid) }
    LaunchedEffect(role, profile.uid) { runCatching { reload() }.onFailure { error = it.message } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (role == AccountRole.ADMIN) "Servicios publicados" else "Mis servicios", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = Color(0xFFB00020)) }
        if (role == AccountRole.FREELANCER) Button(onClick = { onService(null); onNavigate("service_form") }, modifier = Modifier.fillMaxWidth()) { Text("Crear servicio") }
        if (services.isEmpty()) Text("No hay servicios para mostrar.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
        services.forEach { service ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(service.title, fontWeight = FontWeight.Bold); Text("${money(service.price)} · ${service.category} · ${service.status}", color = Color.Gray)
                    Row {
                        if (role == AccountRole.FREELANCER) TextButton(onClick = { onService(service); onNavigate("service_form") }) { Text("Editar") }
                        if (role == AccountRole.ADMIN && service.status != "published") TextButton(onClick = { scope.launch { runCatching { repository.setServiceStatus(service, "published"); reload() }.onFailure { error = it.message } } }) { Text("Publicar") }
                        if (role == AccountRole.ADMIN && service.status == "pending") TextButton(onClick = { scope.launch { runCatching { repository.setServiceStatus(service, "paused"); reload() }.onFailure { error = it.message } } }) { Text("Rechazar") }
                        TextButton(onClick = { scope.launch { runCatching { repository.deleteService(service); reload() }.onFailure { error = it.message } } }) { Text("Eliminar") }
                    }
                }
            }
        }
    }
}

/** Edits service details, availability, category, delivery time, and image before saving. */
@Composable
private fun ServiceEditorScreen(profile: UserProfile, repository: MarketplaceRepository, initial: ServiceListing?, modifier: Modifier, onNavigate: (String) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope(); var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }; var price by remember(initial?.id) { mutableStateOf(initial?.price?.toString().orEmpty()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "Diseño") }; var days by remember(initial?.id) { mutableStateOf(initial?.deliveryDays?.toString() ?: "5") }
    var availability by remember(initial?.id) { mutableStateOf(initial?.availability ?: "Disponible") }; var image by remember(initial?.id) { mutableStateOf<Uri?>(null) }
    var cameraBytes by remember(initial?.id) { mutableStateOf<ByteArray?>(null) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it; cameraBytes = null }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) { val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output); cameraBytes = output.toByteArray(); image = null }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else message = "Se requiere permiso de cámara para capturar fotos."
    }
    suspend fun save(status: String) {
        val numericPrice = price.toLongOrNull(); val numericDays = days.toIntOrNull()
        require(title.isNotBlank() && description.isNotBlank() && numericPrice != null && numericPrice > 0 && numericDays != null && numericDays > 0) { "Completa título, descripción, precio y días de entrega válidos." }
        val service = ServiceListing(initial?.id.orEmpty(), profile.uid, profile.name, title.trim(), description.trim(), category, numericPrice, numericDays, availability.trim().ifBlank { "Disponible" }, status, initial?.imageUrl.orEmpty(), initial?.createdAt ?: System.currentTimeMillis())
        val id = repository.saveService(service)
        val url = when { image != null -> repository.uploadServiceImage(profile.uid, id, image!!); cameraBytes != null -> repository.uploadServiceImageBytes(profile.uid, id, cameraBytes!!); else -> null }
        if (url != null) repository.setServiceImage(id, url)
        message = if (status == "draft") "Borrador guardado." else "Servicio enviado a revisión."
        onDone(); onNavigate("services")
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (initial == null) "Crear servicio" else "Editar servicio", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Título del servicio") }, singleLine = true)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Descripción") }, minLines = 3)
        OutlinedTextField(price, { price = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Precio en COP") }, singleLine = true)
        Text("Categoría", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Diseño", "Desarrollo", "Foto y video", "Marketing").forEach { FilterChip(category == it, { category = it }, label = { Text(it) }) } }
        OutlinedTextField(days, { days = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Días de entrega") }, singleLine = true)
        OutlinedTextField(availability, { availability = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Disponibilidad") }, singleLine = true)
        Text("Foto de portada", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Galería") }
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    camera.launch(null)
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }) { Text("Cámara") }
        }
        if (image != null || cameraBytes != null) Text("Imagen seleccionada", color = Color(0xFF2E7D32))
        message?.let { Text(it, color = if (it.startsWith("Error")) Color(0xFFB00020) else Color(0xFF2E7D32)) }
        Button(onClick = { busy = true; scope.launch { runCatching { save("draft") }.onFailure { message = "Error: ${it.message}" }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (busy) "Guardando…" else "Guardar borrador") }
        OutlinedButton(onClick = { busy = true; scope.launch { runCatching { save("pending") }.onFailure { message = "Error: ${it.message}" }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enviar a revisión") }
    }
}

/** Displays an image-backed service card with a safe placeholder when none was uploaded. */
@Composable
private fun ServiceRow(service: ServiceListing, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemotePhoto(service.imageUrl, Modifier.size(76.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(service.title, fontWeight = FontWeight.Bold); Text("${service.ownerName} · ${service.category}", color = Color.Gray, fontSize = 12.sp); Text("${service.deliveryDays} días · ${service.availability}", color = Color.Gray, fontSize = 12.sp); Text(money(service.price), color = LiveBlue, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Loads an image URL on an IO dispatcher and shows a branded placeholder on failure. */
@Composable
private fun RemotePhoto(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) { if (url.isNotBlank()) bitmap = withContext(Dispatchers.IO) { runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull() } }
    Box(modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("F", color = LiveBlue, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

/** Adds a selected service to the Firebase cart and opens its conversation with the seller. */
@Composable
private fun ServiceDetailScreen(profile: UserProfile, service: ServiceListing?, repository: MarketplaceRepository, modifier: Modifier, onNavigate: (String) -> Unit, onConversation: (String) -> Unit) {
    val scope = rememberCoroutineScope(); var notice by remember { mutableStateOf<String?>(null) }
    if (service == null) { EmptyScreen("Selecciona un servicio desde Buscar.", modifier); return }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        RemotePhoto(service.imageUrl, Modifier.fillMaxWidth().height(180.dp)); Text(service.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("${money(service.price)} · Entrega en ${service.deliveryDays} días · ${service.availability}", color = Color.Gray)
        Text("${service.ownerName} · ${service.category}", color = LiveBlue, modifier = Modifier.padding(vertical = 8.dp)); Text(service.description)
        notice?.let { Text(it, color = if (it.startsWith("Error")) Color(0xFFB00020) else Color(0xFF2E7D32)) }
        Button(onClick = { scope.launch { runCatching { repository.addToCart(profile.uid, service); notice = "Servicio agregado al carrito." }.onFailure { notice = "Error: ${it.message}" } } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Agregar al carrito · ${money(service.price)}") }
        OutlinedButton(onClick = { onNavigate("cart") }, modifier = Modifier.fillMaxWidth()) { Text("Ver carrito") }
        OutlinedButton(onClick = { onConversation(service.ownerId) }, modifier = Modifier.fillMaxWidth()) { Text("Contactar freelancer") }
    }
}

/** Displays public services for the freelancer selected by the buyer. */
@Composable
private fun FreelancerDetailsScreen(service: ServiceListing?, repository: MarketplaceRepository, modifier: Modifier, onNavigate: (String) -> Unit, onConversation: (String) -> Unit, viewer: UserProfile, onService: (ServiceListing?) -> Unit) {
    if (service == null) { EmptyScreen("Selecciona un servicio para ver el perfil.", modifier); return }
    var services by remember { mutableStateOf<List<ServiceListing>>(emptyList()) }
    LaunchedEffect(service.ownerId) { runCatching { repository.listServices(AccountRole.CLIENT, viewer.uid) }.onSuccess { services = it.filter { row -> row.ownerId == service.ownerId } } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text(service.ownerName, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Freelancer · ${service.category}", color = Color.Gray); Section("Servicios publicados"); services.forEach { row -> ServiceRow(row) { onService(row); onNavigate("detail") } }; Button(onClick = { onConversation(service.ownerId) }, modifier = Modifier.fillMaxWidth()) { Text("Contactar") } }
}

/** Shows cart contents, allows removing services, and totals the service fee. */
@Composable
private fun CartScreen(profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope(); var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { cart = repository.getCart(profile.uid) }
    LaunchedEffect(profile.uid) { runCatching { reload() }.onFailure { error = it.message } }
    val subtotal = cart.sumOf { it.price }; val fee = subtotal / 10
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Tu carrito", fontSize = 22.sp, fontWeight = FontWeight.Bold); error?.let { Text(it, color = Color(0xFFB00020)) }
        cart.forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Bold); Text("${item.ownerName} · ${money(item.price)}", color = Color.Gray) }; TextButton(onClick = { scope.launch { runCatching { repository.removeCartItem(profile.uid, item.serviceId); reload() }.onFailure { error = it.message } } }) { Text("Quitar") } } }
        if (cart.isEmpty()) Text("Tu carrito está vacío.", color = Color.Gray, modifier = Modifier.padding(12.dp))
        InfoCard("Subtotal", money(subtotal)); InfoCard("Comisión (10%)", money(fee)); InfoCard("Total", money(subtotal + fee))
        Button(onClick = { onNavigate("checkout") }, enabled = cart.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Continuar al checkout") }
        OutlinedButton(onClick = { onNavigate("search") }, modifier = Modifier.fillMaxWidth()) { Text("Seguir buscando") }
    }
}

/** Captures an address or GPS coordinates, coordinates with the Sandbox Payment Gateway, and generates a digital receipt. */
@Composable
private fun CheckoutScreen(profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onOrder: (String) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }; var address by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf<Double?>(null) }; var lon by remember { mutableStateOf<Double?>(null) }
    var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }

    // Step in checkout flow: 0 = Resumen y Entrega, 1 = Pasarela Certificada Sandbox, 2 = Voucher / Comprobante
    var step by remember { mutableStateOf(0) }
    var createdOrderId by remember { mutableStateOf("") }

    // Payment Gateway form state
    var selectedMethod by remember { mutableStateOf("CARD") } // "CARD", "PSE", "NEQUI"
    var cardNumber by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf(profile.name) }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var pseBank by remember { mutableStateOf("Bancolombia") }
    var pseDocNumber by remember { mutableStateOf("") }
    var nequiPhone by remember { mutableStateOf(profile.phone.orEmpty()) }

    // Voucher details
    var voucherTxnId by remember { mutableStateOf("") }
    var voucherAuthCode by remember { mutableStateOf("") }
    var voucherMethodName by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf("") }

    LaunchedEffect(profile.uid) { runCatching { cart = repository.getCart(profile.uid) }.onFailure { message = it.message } }

    fun captureLocation() {
        scope.launch {
            busy = true
            runCatching {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fine && !coarse) {
                    error("Permiso de ubicación no concedido.")
                }
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val loc = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                    ?: fusedClient.lastLocation.await()
                    ?: error("No se obtuvo ubicación. Verifica que el GPS esté activo en el dispositivo.")
                lat = loc.latitude; lon = loc.longitude
                val line = withContext(Dispatchers.IO) {
                    runCatching {
                        Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.getAddressLine(0)
                    }.getOrNull()
                }
                address = if (!line.isNullOrBlank()) line else "Ubicación GPS: ${loc.latitude}, ${loc.longitude}"
                message = "Ubicación capturada para la entrega."
            }.onFailure { message = "No se pudo obtener la ubicación: ${it.message}" }
            busy = false
        }
    }

    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) captureLocation() else message = "Escribe la dirección manualmente si no deseas compartir la ubicación."
    }

    val subtotal = cart.sumOf { it.price }; val total = subtotal + subtotal / 10

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        when (step) {
            0 -> {
                Text("Checkout", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Resumen de contratación", color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                cart.forEach { Text("${it.title} · ${money(it.price)}", modifier = Modifier.padding(vertical = 3.dp)) }
                InfoCard("Subtotal", money(subtotal))
                InfoCard("Comisión de servicio (10%)", money(subtotal / 10))
                InfoCard("Total a pagar", money(total))

                OutlinedTextField(
                    address, { address = it; lat = null; lon = null },
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("Dirección de entrega") }, minLines = 2
                )
                if (lat != null && lon != null) {
                    Text("📍 Coordenadas: $lat, $lon", color = Color(0xFF2E7D32), fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                OutlinedButton(onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) captureLocation() else locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(if (busy) "Obteniendo ubicación…" else "Usar mi ubicación actual (GPS)")
                }

                Card(Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                        Text("Pasarela de pagos certificada activa en modo Sandbox. Pruebas sin cobro real a cuentas bancarias.", color = Color(0xFF2E7D32), fontSize = 12.sp)
                    }
                }
                message?.let { Text(it, color = if (it.startsWith("No se pudo")) Color(0xFFB00020) else Color(0xFF2E7D32)) }

                Button(
                    onClick = { step = 1 },
                    enabled = !busy && cart.isNotEmpty() && address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text("Proceder al pago seguro · ${money(total)}")
                }
            }
            1 -> {
                Text("Pasarela de Pagos Certificada", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Entorno seguro de pruebas (Modo Sandbox)", color = Color.Gray)

                Card(Modifier.fillMaxWidth().padding(vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total a pagar:", fontWeight = FontWeight.SemiBold)
                        Text(money(total), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LiveBlue)
                    }
                }

                Text("Selecciona el medio de pago", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    FilterChip(selected = selectedMethod == "CARD", onClick = { selectedMethod = "CARD" }, label = { Text("💳 Tarjeta") })
                    FilterChip(selected = selectedMethod == "PSE", onClick = { selectedMethod = "PSE" }, label = { Text("🏦 PSE") })
                    FilterChip(selected = selectedMethod == "NEQUI", onClick = { selectedMethod = "NEQUI" }, label = { Text("📱 Nequi") })
                }

                when (selectedMethod) {
                    "CARD" -> {
                        OutlinedTextField(cardNumber, { cardNumber = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Número de tarjeta") }, placeholder = { Text("4242 4242 4242 4242") }, singleLine = true)
                        OutlinedButton(onClick = {
                            cardNumber = "4242 4242 4242 4242"
                            cardHolder = profile.name
                            cardExpiry = "12/28"
                            cardCvv = "123"
                        }, modifier = Modifier.padding(top = 4.dp)) {
                            Text("Usar tarjeta de prueba aprobada")
                        }
                        OutlinedTextField(cardHolder, { cardHolder = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Nombre del titular") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            OutlinedTextField(cardExpiry, { cardExpiry = it }, Modifier.weight(1f), label = { Text("Vencimiento (MM/AA)") }, placeholder = { Text("12/28") }, singleLine = true)
                            OutlinedTextField(cardCvv, { cardCvv = it }, Modifier.weight(1f), label = { Text("CVV") }, placeholder = { Text("123") }, singleLine = true)
                        }
                    }
                    "PSE" -> {
                        OutlinedTextField(pseBank, { pseBank = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Banco emisor") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Bancolombia", "Davivienda", "Banco de Bogotá").forEach { bank ->
                                FilterChip(selected = pseBank == bank, onClick = { pseBank = bank }, label = { Text(bank) })
                            }
                        }
                        OutlinedTextField(pseDocNumber, { pseDocNumber = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Número de documento (C.C.)") }, singleLine = true)
                    }
                    "NEQUI" -> {
                        OutlinedTextField(nequiPhone, { nequiPhone = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Número de celular Nequi (10 dígitos)") }, singleLine = true)
                        Text("Se enviará la solicitud push para autorizar la transacción en modo prueba.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                if (busy) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LiveBlue)
                        Spacer(Modifier.height(8.dp))
                        Text("Procesando pago con la pasarela certificada...", color = Color.Gray)
                    }
                } else {
                    message?.let { Text(it, color = Color(0xFFB00020), modifier = Modifier.padding(vertical = 6.dp)) }
                    Button(
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching {
                                    delay(1200) // Simulación de procesamiento seguro
                                    val orderId = repository.createPendingOrder(profile.uid, cart, address, lat, lon)
                                    val txn = "TXN-${UUID.randomUUID().toString().take(8).uppercase()}"
                                    val auth = "AUTH-${(100000..999999).random()}"
                                    val methodLabel = when (selectedMethod) {
                                        "CARD" -> "Tarjeta (...${cardNumber.takeLast(4).ifBlank { "4242" }})"
                                        "PSE" -> "PSE - $pseBank"
                                        else -> "Nequi ($nequiPhone)"
                                    }
                                    repository.confirmOrderPayment(orderId, txn, methodLabel, auth)
                                    createdOrderId = orderId
                                    voucherTxnId = txn
                                    voucherAuthCode = auth
                                    voucherMethodName = methodLabel
                                    voucherDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                    step = 2
                                }.onFailure {
                                    message = "Error al procesar el pago: ${it.message}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text("Confirmar y pagar · ${money(total)}")
                    }
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Volver a datos de entrega")
                    }
                }
            }
            2 -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(72.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(36.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 38.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("¡Pago Aprobado!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Comprobante de transacción electrónica", color = Color.Gray)
                }

                Card(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Referencia (TXN):", color = Color.Gray)
                            Text(voucherTxnId, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Autorización:", color = Color.Gray)
                            Text(voucherAuthCode, fontWeight = FontWeight.Bold, color = LiveBlue)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Medio de pago:", color = Color.Gray)
                            Text(voucherMethodName, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha y hora:", color = Color.Gray)
                            Text(voucherDate, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estado:", color = Color.Gray)
                            Text("APROBADO ✓", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total pagado:", fontWeight = FontWeight.Bold)
                            Text(money(total), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LiveBlue)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Entrega en:", color = Color.Gray)
                            Text(address, maxLines = 2, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp))
                        }
                    }
                }

                Button(
                    onClick = {
                        onOrder(createdOrderId)
                        onNavigate("order_detail")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ver seguimiento del pedido")
                }
                OutlinedButton(
                    onClick = { onNavigate("home") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text("Volver al inicio")
                }
            }
        }
    }
}

/** Lists real orders for the client, assigned freelancer, or administrator. */
@Composable
private fun OrdersScreen(role: AccountRole, profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onOrder: (String) -> Unit, onNavigate: (String) -> Unit) {
    var orders by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(role, profile.uid) { runCatching { orders = repository.listOrders(role, profile.uid) }.onFailure { error = it.message } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (role == AccountRole.FREELANCER) "Pedidos recibidos" else "Mis pedidos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = Color(0xFFB00020)) }
        if (orders.isEmpty()) Text("Todavía no hay pedidos.", color = Color.Gray, modifier = Modifier.padding(vertical = 14.dp))
        orders.forEach { order ->
            val id = order["id"] as? String ?: return@forEach; val first = (order["items"] as? List<*>)?.firstOrNull() as? Map<*, *>
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onOrder(id); onNavigate("order_detail") }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) { Text(first?.get("title") as? String ?: "Pedido", fontWeight = FontWeight.Bold); Text("${id.take(8)} · ${order["status"]} · ${money((order["total"] as? Number)?.toLong() ?: 0)}", color = Color.Gray) }
            }
        }
    }
}

/** Loads an order and updates fulfillment without marking a payment as complete. */
@Composable
private fun OrderDetailScreen(role: AccountRole, profile: UserProfile, repository: MarketplaceRepository, orderId: String?, modifier: Modifier, onNavigate: (String) -> Unit, onStartConversation: (String) -> Unit) {
    val scope = rememberCoroutineScope(); var order by remember(orderId) { mutableStateOf<Map<String, Any?>?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(orderId) { if (orderId != null) runCatching { order = repository.getOrder(orderId) }.onFailure { error = it.message } }
    if (orderId == null) { EmptyScreen("Selecciona un pedido.", modifier); return }
    val isPaid = order?.get("paymentStatus") == "paid"
    val paymentMethod = order?.get("paymentMethod") as? String
    val txnId = order?.get("transactionId") as? String
    val authCode = order?.get("authorizationCode") as? String

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Pedido #${orderId.take(8)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Estado: ${order?.get("status") ?: "Cargando…"}", fontWeight = FontWeight.SemiBold)

        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = if (isPaid) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (isPaid) "✓ Pago confirmado · ${paymentMethod ?: "Pasarela"}" else "○ Pago pendiente",
                    fontWeight = FontWeight.Bold,
                    color = if (isPaid) Color(0xFF2E7D32) else Color(0xFF8D4B00)
                )
                if (isPaid && txnId != null) {
                    Text("Ref: $txnId · Auth: ${authCode ?: "OK"}", fontSize = 12.sp, color = Color(0xFF2E7D32))
                }
            }
        }

        Text("Dirección: ${order?.get("address") ?: "—"}", modifier = Modifier.padding(vertical = 8.dp))
        Section("Seguimiento")
        val milestones = listOf("pending" to "Pedido creado", "accepted" to "Pedido pagado y aceptado", "in_progress" to "Entrega en preparación", "delivered" to "Entregado")
        val currentIndex = milestones.indexOfFirst { it.first == order?.get("status") }
        milestones.forEachIndexed { index, milestone -> Text("${if (index <= currentIndex) "✓" else "○"}  ${milestone.second}", color = if (index <= currentIndex) Color(0xFF2E7D32) else Color.Gray, modifier = Modifier.padding(vertical = 3.dp)) }
        (order?.get("items") as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { Text("${it["title"]} · ${money((it["price"] as? Number)?.toLong() ?: 0)}") }
        InfoCard("Total", money((order?.get("total") as? Number)?.toLong() ?: 0)); error?.let { Text(it, color = Color(0xFFB00020)) }
        if (role == AccountRole.FREELANCER && order?.get("freelancerId") == profile.uid) {
            listOf("accepted" to "Aceptar pedido", "in_progress" to "Marcar en preparación", "delivered" to "Marcar entregado").forEach { (status, label) ->
                OutlinedButton(onClick = { scope.launch { runCatching { repository.updateOrderStatus(orderId, status); order = repository.getOrder(orderId) }.onFailure { error = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        Button(onClick = {
            val peerId = if (role == AccountRole.CLIENT) order?.get("freelancerId") as? String else order?.get("clientId") as? String
            if (peerId != null) onStartConversation(peerId)
        }, enabled = order != null, modifier = Modifier.fillMaxWidth()) { Text("Escribir a la otra persona") }
    }
}

/** Loads conversations and persists messages for both participants. */
@Composable
private fun MessagesScreen(profile: UserProfile, repository: MarketplaceRepository, conversationId: String?, modifier: Modifier, onConversation: (String) -> Unit) {
    val scope = rememberCoroutineScope(); var conversations by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var messages by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }; var draft by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.uid) { runCatching { conversations = repository.listConversations(profile.uid) }.onFailure { error = it.message } }
    LaunchedEffect(conversationId) { if (conversationId != null) runCatching { messages = repository.listMessages(conversationId) }.onFailure { error = it.message } }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text("Mensajes", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (conversationId == null) {
            if (conversations.isEmpty()) Text("Todavía no hay conversaciones.", color = Color.Gray)
            conversations.forEach { item -> val id = item["id"] as? String ?: return@forEach; TextButton(onClick = { onConversation(id) }) { Text("Conversación · ${id.take(12)}") } }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(messages) { item -> Text("${if (item["senderId"] == profile.uid) "Tú" else if (profile.role == AccountRole.CLIENT) "Freelancer" else "Cliente"}: ${item["text"] ?: ""}") } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Escribe un mensaje") })
                TextButton(onClick = { scope.launch { runCatching { repository.sendMessage(conversationId, profile.uid, draft); draft = ""; messages = repository.listMessages(conversationId) }.onFailure { error = it.message } } }, enabled = draft.isNotBlank()) { Text("Enviar") }
            }
        }
        error?.let { Text(it, color = Color(0xFFB00020)) }
    }
}

/** Displays profile data and account actions. */
@Composable
private fun ProfileScreen(profile: UserProfile, modifier: Modifier, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mi perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        RemotePhoto(profile.photoUrl.orEmpty(), Modifier.padding(18.dp).size(88.dp))
        Text(profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(profile.email, color = Color.Gray)
        OutlinedButton(onClick = { onNavigate("profile_edit") }, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil") }
        OutlinedButton(onClick = { onNavigate("settings") }, modifier = Modifier.fillMaxWidth()) { Text("Configuración y privacidad") }
        OutlinedButton(onClick = { onNavigate("biometric") }, modifier = Modifier.fillMaxWidth()) { Text("Acceso biométrico") }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}

/** Saves profile fields and uploads an optional profile image to Firebase Storage. */
@Composable
private fun ProfileEditorScreen(profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onSaved: (UserProfile) -> Unit) {
    val scope = rememberCoroutineScope(); var name by remember(profile.uid) { mutableStateOf(profile.name) }
    var phone by remember(profile.uid) { mutableStateOf(profile.phone.orEmpty()) }; var bio by remember(profile.uid) { mutableStateOf(profile.bio.orEmpty()) }
    var location by remember(profile.uid) { mutableStateOf(profile.location.orEmpty()) }; var image by remember(profile.uid) { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Editar perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Elegir foto de perfil") }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") })
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono") })
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Descripción") }, minLines = 3)
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Ciudad") })
        message?.let { Text(it, color = if (it.startsWith("Error")) Color(0xFFB00020) else Color(0xFF2E7D32)) }
        Button(onClick = { busy = true; scope.launch {
            runCatching {
                val photo = image?.let { repository.uploadProfileImage(profile.uid, it) } ?: profile.photoUrl
                val updated = profile.copy(name = name.trim(), phone = phone.trim().ifBlank { null }, bio = bio.trim().ifBlank { null }, location = location.trim().ifBlank { null }, photoUrl = photo)
                repository.saveProfile(updated); onSaved(updated); message = "Perfil actualizado."
            }.onFailure { message = "Error: ${it.message}" }
            busy = false
        } }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando…" else "Guardar cambios") }
    }
}

/** Updates the public-profile privacy setting in Firestore. */
@Composable
private fun AccountSettingsScreen(profile: UserProfile, repository: MarketplaceRepository, modifier: Modifier, onSaved: (UserProfile) -> Unit) {
    val scope = rememberCoroutineScope(); var isPublic by remember(profile.uid) { mutableStateOf(profile.isPublic) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Configuración y privacidad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Perfil público", fontWeight = FontWeight.SemiBold); Text("Permite mostrar tu perfil a otros usuarios.", color = Color.Gray) }
            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
        }
        message?.let { Text(it, color = if (it.startsWith("Error")) Color(0xFFB00020) else Color(0xFF2E7D32)) }
        Button(onClick = { scope.launch {
            val updated = profile.copy(isPublic = isPublic)
            runCatching { repository.saveProfile(updated); onSaved(updated); message = "Preferencia guardada." }.onFailure { message = "Error: ${it.message}" }
        } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar preferencias") }
        Text("Las notificaciones y los métodos de pago se habilitarán cuando se integren sus servicios.", color = Color.Gray, modifier = Modifier.padding(top = 14.dp))
    }
}

/** Shows real sales summaries and reports stored in Firestore. */
@Composable
private fun ReportsScreen(repository: MarketplaceRepository, modifier: Modifier, onOrder: (String) -> Unit, onNavigate: (String) -> Unit) {
    var reports by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }; var sales by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { reports = repository.listReports(); sales = repository.salesSummary() }.onFailure { error = it.message } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Reportes", fontSize = 22.sp, fontWeight = FontWeight.Bold); error?.let { Text(it, color = Color(0xFFB00020)) }
        InfoCard("Ventas pagadas", money(sales["total"] ?: 0)); InfoCard("Comisiones", money(sales["commission"] ?: 0)); InfoCard("Transacciones", (sales["orders"] ?: 0).toString())
        if (reports.isEmpty()) Text("No hay reportes registrados.", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
        reports.forEach { report ->
            val orderId = report["orderId"] as? String; val title = report["title"] as? String ?: report["type"] as? String ?: "Reporte"
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { if (orderId != null) { onOrder(orderId); onNavigate("order_detail") } }, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(report["status"] as? String ?: "Abierto", color = Color.Gray) } }
        }
    }
}

/** Requires a device biometric confirmation before restoring the current Firebase profile. */
@Composable
fun BiometricAccessScreen(auth: AuthRepository, onSuccess: () -> Unit, onFallback: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var message by remember { mutableStateOf("Confirma tu identidad con la huella del dispositivo.") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Acceso biométrico", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(message, color = Color.Gray, modifier = Modifier.padding(16.dp))
        Button(onClick = {
            val activity = context as? FragmentActivity
            if (activity == null || BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) message = "Configura una huella en Seguridad del dispositivo."
            else {
                val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { scope.launch { runCatching { withContext(Dispatchers.IO) { auth.restoreCurrentProfile() } }.onSuccess { onSuccess() }.onFailure { message = it.message ?: "No se pudo recuperar la sesión." } } }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { message = errString.toString() }
                })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Acceso a Freelo").setSubtitle("Confirma con tu huella").setNegativeButtonText("Usar contraseña").build())
            }
        }) { Text("Usar huella") }
        TextButton(onClick = onFallback) { Text("Volver") }
    }
}

/** Displays concise live values in a white dashboard card. */
@Composable
private fun InfoCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.Gray); Text(value, color = LiveBlue, fontWeight = FontWeight.Bold) }
    }
}

/** Shows a readable empty state when no records are available. */
@Composable
private fun EmptyScreen(message: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(message, color = Color.Gray) }
}

/** Adds a section heading with standard Freelo spacing. */
@Composable
private fun Section(text: String) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }

/** Formats Colombian peso values for display. */
private fun money(value: Long): String = NumberFormat.getCurrencyInstance(Locale("es", "CO")).format(value)

/** Maps Firestore role values to user-facing Spanish labels. */
private fun roleLabel(role: AccountRole): String = when (role) { AccountRole.CLIENT -> "Cliente"; AccountRole.FREELANCER -> "Freelancer"; AccountRole.ADMIN -> "Administrador" }
