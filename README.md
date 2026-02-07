# 🛡️ SafeCity - App de Reportes Comunitarios

## 📱 Descripción
SafeCity es una aplicación móvil Android que permite a los ciudadanos reportar y visualizar incidentes de seguridad e infraestructura en tiempo real usando mapas interactivos.

---

## ✅ Funcionalidades Implementadas

### Dashboard
- [x] **Mapa interactivo con Google Maps**
- [x] **Marcadores diferenciados** (🔴 Seguridad / 🔵 Infraestructura)
- [x] **Filtros por categoría** (Seguridad/Infraestructura)
- [x] **Filtro por verificados**
- [x] **Bottom sheet con detalles del incidente**
- [x] **Cálculo de distancia en tiempo real**
- [x] **Validación comunitaria** (botón "Confirmar")
- [x] **Listeners de Firestore en tiempo real**
- [x] **Notificaciones push (FCM configurado)**
- [x] **Manejo de permisos de ubicación**
- [x] **Botón "Mi ubicación"**
- [x] **FAB para crear nuevo reporte**

### Autenticación
- [x] Login con email/contraseña
- [x] Login con Google
- [x] Registro con email
- [x] Recuperación de contraseña

---

## 🔧 Configuración del Proyecto

### 1. Clonar el repositorio
```bash
git clone https://github.com/IvanBy12/SafeCity.git
cd SafeCity
```

### 2. Configurar Firebase

#### A. Crear proyecto en Firebase Console
1. Ve a [Firebase Console](https://console.firebase.google.com/)
2. Crea un nuevo proyecto llamado "SafeCity"
3. Habilita **Authentication** (Email/Password y Google)
4. Habilita **Cloud Firestore**
5. Habilita **Cloud Messaging (FCM)**

#### B. Agregar app Android
1. En Firebase Console → Project Settings → Add App → Android
2. Package name: `com.example.safecity`
3. Agrega tu **SHA-1** (para Google Sign-In):
   ```bash
   # En Windows:
   keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   
   # En Mac/Linux:
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
4. Descarga el archivo `google-services.json`
5. Cópialo en `app/google-services.json`

#### C. Configurar Firestore Security Rules
En Firebase Console → Firestore → Rules:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /incidents/{incidentId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update: if request.auth != null;
      allow delete: if request.auth != null && 
                      request.auth.uid == resource.data.userId;
    }
  }
}
```

### 3. Configurar Google Maps API

#### A. Obtener API Key
1. Ve a [Google Cloud Console](https://console.cloud.google.com/)
2. Selecciona tu proyecto de Firebase
3. Habilita **Maps SDK for Android**
4. Ve a **Credentials** → Create credentials → API key
5. Restringe la API key:
   - Application restrictions: Android apps
   - Agrega tu package name y SHA-1

#### B. Agregar API Key al proyecto
Edita `app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="TU_API_KEY_AQUI" />
```

### 4. Sincronizar dependencias
```bash
./gradlew build
```

---

## 🚀 Ejecutar el Proyecto

### Opción 1: Emulador
1. Abre el proyecto en Android Studio
2. Crea un AVD (Android Virtual Device) con Android 7.0+
3. Click en "Run" ▶️

### Opción 2: Dispositivo físico
1. Habilita "Opciones de desarrollador" en tu dispositivo
2. Activa "Depuración USB"
3. Conecta el dispositivo y ejecuta

---

## 📊 Estructura del Proyecto

```
app/src/main/java/com/example/safecity/
├── auth/                      # Autenticación
│   ├── AuthRepository.kt
│   └── AuthViewModel.kt
├── models/                    # Modelos de datos
│   └── Incident.kt
├── repository/                # Acceso a datos
│   └── IncidentRepository.kt
├── viewmodel/                 # ViewModels
│   └── DashboardViewModel.kt
├── screens/                   # Pantallas
│   ├── LoginScreen.kt
│   ├── RegisterScreen.kt
│   ├── HomeScreen.kt
│   └── dashboard/
│       ├── DashboardScreen.kt
│       ├── IncidentDetailsSheet.kt
│       └── CreateIncidentScreen.kt
├── services/                  # Servicios
│   └── FCMService.kt
├── utils/                     # Utilidades
│   └── PermissionUtils.kt
└── MainActivity.kt
```

---

## 🔐 Permisos Requeridos

```xml
<!-- Ubicación -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Internet -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Notificaciones (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 📦 Dependencias Principales

- **Jetpack Compose** - UI moderna
- **Firebase Auth** - Autenticación
- **Cloud Firestore** - Base de datos en tiempo real
- **FCM** - Notificaciones push
- **Google Maps Compose** - Mapas interactivos
- **Location Services** - Geolocalización
- **Accompanist Permissions** - Manejo de permisos

---

## 🎯 Próximos Pasos

- [ ] Subir imágenes de incidentes (Firebase Storage)
- [ ] Perfil de usuario editable
- [ ] Historial de reportes del usuario
- [ ] Modo oscuro
- [ ] Tests unitarios
- [ ] CI/CD con GitHub Actions

---

## 🐛 Troubleshooting

### Error: "Google Sign-In failed"
✅ Verifica que agregaste el **SHA-1** en Firebase Console

### Error: "Map not showing"
✅ Verifica que la **API Key de Maps** esté correcta y restringida

### Error: "Location permission denied"
✅ Acepta los permisos cuando la app lo solicite

### Error: "Firestore permission denied"
✅ Verifica las **Security Rules** en Firebase Console

---

## 📄 Licencia
MIT License - Libre para usar en proyectos personales y comerciales

---

## 👨‍💻 Autor
**IvanBy12**
- GitHub: [@IvanBy12](https://github.com/IvanBy12)
