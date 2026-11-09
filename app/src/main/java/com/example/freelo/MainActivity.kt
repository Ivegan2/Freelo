package com.example.freelo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Blue = Color(0xFF1E88E5)
private val Green = Color(0xFF43A047)
private val Orange = Color(0xFFFB8C00)
private val Canvas = Color(0xFFF5F7FA)
private val Ink = Color(0xFF212121)

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreeloApp() }
    }
}

@Composable
private fun FreeloApp() {
    var role by remember { mutableStateOf(UserRole.CLIENT) }
    var page by remember { mutableStateOf("splash") }
    var previousPage by remember { mutableStateOf("home") }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Blue,
            secondary = Green,
            tertiary = Orange,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            when (page) {
                "splash" -> SplashScreen { page = "login" }
                "login" -> LoginScreen(
                    role = role,
                    onRoleChange = { role = it },
                    onLogin = { page = if (role == UserRole.CLIENT) "home" else "dashboard" },
                    onForgot = { page = "forgot" }
                )
                "forgot" -> RecoveryScreen(onBack = { page = "login" })
                "biometric" -> BiometricScreen(
                    onSuccess = { page = previousPage },
                    onFallback = { page = "login" }
                )
                else -> MainShell(
                    role = role,
                    page = page,
                    onNavigate = { page = it },
                    onBiometric = { previousPage = page; page = "biometric" },
                    onLogout = { page = "login" }
                )
            }
        }
    }
}

@Composable
private fun SplashScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
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
    role: UserRole,
    onRoleChange: (UserRole) -> Unit,
    onLogin: () -> Unit,
    onForgot: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark()
        Spacer(Modifier.height(22.dp))
        Text("Bienvenido a Freelo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Encuentra talento local o comparte el tuyo.", color = Color.Gray)
        Spacer(Modifier.height(22.dp))
        Text("Entrar como", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.entries.forEach { item ->
                FilterChip(selected = role == item, onClick = { onRoleChange(item) }, label = { Text(item.label) })
            }
        }
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
        Text("¿Olvidaste tu contraseña?", color = Blue, modifier = Modifier.align(Alignment.End).clickable(onClick = onForgot).padding(12.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión (demo)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
        Text("La selección de rol es solo para explorar esta demostración.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RecoveryScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Recuperar contraseña", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Te enviaremos instrucciones al correo asociado a tu cuenta.", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Enviar instrucciones (demo)") }
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
    page: String,
    onNavigate: (String) -> Unit,
    onBiometric: () -> Unit,
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
                            onNavigate(item.id)
                            scope.launch { drawerState.close() }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerRow(Destination("biometric", "Acceso biométrico", Icons.Default.Lock), false, onBiometric)
                    DrawerRow(Destination("settings", "Configuración", Icons.Default.Settings), page == "settings") { onNavigate("settings") }
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
                            onClick = { onNavigate(item.id) },
                            icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        ) { padding ->
            PageContent(role, page, Modifier.padding(padding), onNavigate)
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
        Destination("messages", "Mensajes", Icons.AutoMirrored.Filled.Chat),
        Destination("settings", "Configuración", Icons.Default.Settings)
    )
    UserRole.FREELANCER -> freelancerDestinations + listOf(
        Destination("messages", "Mensajes", Icons.AutoMirrored.Filled.Chat),
        Destination("earnings", "Ganancias", Icons.Default.Assessment),
        Destination("service_form", "Crear servicio", Icons.Default.Storefront),
        Destination("settings", "Configuración", Icons.Default.Settings)
    )
    UserRole.ADMIN -> adminDestinations + listOf(
        Destination("reports", "Reportes", Icons.Default.Assessment),
        Destination("settings", "Configuración", Icons.Default.Settings),
        Destination("audit", "Auditoría", Icons.Default.Lock)
    )
}

@Composable
private fun PageContent(role: UserRole, page: String, modifier: Modifier, onNavigate: (String) -> Unit) {
    when (page) {
        "home" -> ClientHome(modifier, onNavigate)
        "dashboard" -> DashboardPage(role, modifier, onNavigate)
        "search" -> SearchPage(modifier, onNavigate)
        "services" -> ServicesPage(role, modifier, onNavigate)
        "users" -> UsersPage(modifier, onNavigate)
        "orders" -> OrdersPage(role, modifier, onNavigate)
        "profile" -> ProfilePage(role, modifier, onNavigate)
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
private fun ProfilePage(role: UserRole, modifier: Modifier, onNavigate: (String) -> Unit) {
    PageColumn(modifier) {
        Text("Mi perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp).background(Color(0xFFE3F2FD), CircleShape), contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Default.AccountCircle, null, tint = Blue, modifier = Modifier.size(52.dp)) }
                Text(if (role == UserRole.CLIENT) "Alex Ramírez" else "Laura Méndez", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (role == UserRole.FREELANCER) "Diseñadora · Bogotá · ⭐ 4.9" else "alex@email.com", color = Color.Gray)
                Text("Editar perfil", color = Blue, modifier = Modifier.clickable { onNavigate("settings") }.padding(8.dp))
            }
        }
        ActionCard("Configuración y privacidad", "Preferencias, consentimiento y seguridad") { onNavigate("settings") }
        ActionCard("Acceso biométrico", "Protege el acceso a tu cuenta") { onNavigate("biometric") }
        ActionCard("Cerrar sesión", "Salir de Freelo en este dispositivo") { onNavigate("settings") }
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
