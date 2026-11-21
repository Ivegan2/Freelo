# Freelo con Firebase Emulator Suite

La app usa Firebase Authentication, Cloud Firestore, Cloud Storage y Cloud Functions locales mientras `USE_FIREBASE_EMULATOR` sea `true` en `app/build.gradle.kts`. Las cuentas, perfiles, servicios, fotos, carrito, pedidos y mensajes quedan en el emulador; no se escriben en Firebase Cloud.

## Requisitos

- Node.js y npm instalados.
- Node.js 22 para el emulador de Cloud Functions.
- Android Studio y un emulador Android o un teléfono conectado por USB.
- Conexión de red la primera vez para descargar Firebase CLI y los emuladores.

## Primera sesión: guardar los datos al cerrar

Instala las dependencias de la función administrativa una sola vez:

```powershell
Set-Location .\functions
npm install
Set-Location ..
```

Desde PowerShell, en la carpeta raíz del proyecto, inicia el conjunto local:

```powershell
npx firebase-tools emulators:start --project demo-freelo --export-on-exit=./emulator-data
```

Deja esa terminal abierta mientras pruebas. Al terminar, detén el emulador con `Ctrl+C`; Authentication, Firestore y Storage se exportan a `emulator-data`.

## Sesiones siguientes: cargar y guardar los datos

```powershell
npx firebase-tools emulators:start --project demo-freelo --import=./emulator-data --export-on-exit
```

La interfaz queda en `http://localhost:4000`. Authentication usa el puerto 9099, Firestore 8080, Storage 9199 y Functions 5001. Cuando se modifica una regla o la función, reinicia los emuladores para que carguen los cambios.

En un emulador Android, `10.0.2.2` apunta al computador. En teléfono físico conectado por USB, abre otra terminal y configura el túnel:

```powershell
& "C:\Users\IVAN0\AppData\Local\Android\Sdk\platform-tools\adb.exe" -d reverse tcp:9099 tcp:9099
& "C:\Users\IVAN0\AppData\Local\Android\Sdk\platform-tools\adb.exe" -d reverse tcp:8080 tcp:8080
& "C:\Users\IVAN0\AppData\Local\Android\Sdk\platform-tools\adb.exe" -d reverse tcp:9199 tcp:9199
& "C:\Users\IVAN0\AppData\Local\Android\Sdk\platform-tools\adb.exe" -d reverse tcp:5001 tcp:5001
```

No subas `emulator-data` al repositorio: puede contener cuentas de prueba. La carpeta debe permanecer en el equipo de desarrollo.

## Administración y pruebas

El registro público permite crear Cliente o Freelancer. Desde la sesión Administrador se pueden crear cuentas de esos roles; la app mantiene abierta la sesión administrativa. Los administradores no se registran desde el formulario público.

Las publicaciones de Freelancer se guardan como borrador o pendientes de revisión; el Administrador puede publicarlas o rechazarlas. Los clientes buscan servicios publicados, los agregan al carrito y crean pedidos con dirección manual o ubicación actual. El Administrador puede eliminar la cuenta y el perfil de otro usuario mediante una función protegida. La pasarela no está configurada por costos: el checkout solo registra un pedido pendiente de pago y no realiza cobros.

Authentication Emulator no envía correos de recuperación. Al solicitar cambio de contraseña muestra el enlace de prueba en la terminal del emulador.

## Firebase Cloud

Antes de usar un proyecto real, registra exactamente el paquete `com.example.freelo`, agrega `google-services.json`, despliega la función y las reglas revisadas, y cambia `USE_FIREBASE_EMULATOR` solo tras verificar los servicios. Storage y Cloud Functions en producción requieren el plan Blaze; el uso que supere las cuotas gratuitas puede facturarse. No subas llaves privadas de pago ni archivos de firma.
