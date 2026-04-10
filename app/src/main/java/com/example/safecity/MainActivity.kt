package com.example.safecity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.safecity.auth.AuthViewModel
import com.example.safecity.nav.Routes
import com.example.safecity.screens.*
import com.example.safecity.screens.dashboard.CreateIncidentScreen
import com.example.safecity.screens.detail.IncidentDetailScreen
import com.example.safecity.screens.myreports.MyReportsScreen
import com.example.safecity.screens.profile.ProfileScreen
import com.example.safecity.screens.statistics.StatisticsScreen
import com.example.safecity.screens.reports.ReportsScreen
import com.example.safecity.store.UserPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        /** Extra que FCMService incluye en el Intent cuando el usuario toca una notificación */
        const val EXTRA_INCIDENT_ID = "incidentId"
    }

    /**
     * ID de incidente pendiente de navegación.
     * Se establece en onCreate (app fría) y en onNewIntent (app en background/foreground).
     * El composable lo observa y navega al detalle en cuanto la pila de navegación está lista.
     */
    private val _pendingNotifId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─── Inicializar preferencias antes de montar la UI ───
        UserPreferencesStore.init(applicationContext)

        // Verificar si la app fue abierta desde una notificación (arranque en frío)
        processNotificationIntent(intent)

        setContent {
            MaterialTheme {
                Surface {
                    val nav = rememberNavController()
                    val authVm: AuthViewModel = viewModel()

                    // Observar el ID de incidente pendiente de la notificación
                    val pendingNotifId by _pendingNotifId.collectAsState()

                    // Ruta actual para saber cuándo está listo el navegador
                    val currentBackStackEntry by nav.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    NavHost(
                        navController = nav,
                        startDestination = Routes.Splash
                    ) {
                        composable(Routes.Splash) {
                            SplashScreen(
                                isLoggedIn = authVm.isLoggedIn(),
                                goHome = {
                                    nav.navigate(Routes.Home) {
                                        popUpTo(Routes.Splash) { inclusive = true }
                                    }
                                },
                                goLogin = {
                                    nav.navigate(Routes.Login) {
                                        popUpTo(Routes.Splash) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.Login) {
                            LoginScreen(
                                vm = authVm,
                                onGoRegister = { nav.navigate(Routes.Register) },
                                onLoggedIn = {
                                    nav.navigate(Routes.Home) {
                                        popUpTo(Routes.Login) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.Register) {
                            RegisterScreen(
                                onGoLogin = { nav.popBackStack() },
                                onRegistered = {
                                    nav.navigate(Routes.Home) {
                                        popUpTo(Routes.Register) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.PhoneAuth) {
                            PhoneAuthScreen(
                                onBack = { nav.popBackStack() },
                                onSuccess = {
                                    nav.navigate(Routes.Home) {
                                        popUpTo(Routes.PhoneAuth) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.Home) {
                            HomeScreen(
                                onLogout = {
                                    authVm.logout {
                                        nav.navigate(Routes.Login) {
                                            popUpTo(Routes.Home) { inclusive = true }
                                        }
                                    }
                                },
                                onNavigateToCreateIncident = {
                                    nav.navigate(Routes.CreateIncident)
                                },
                                onNavigateToProfile = {
                                    nav.navigate(Routes.Profile)
                                },
                                onNavigateToIncidentDetail = { incidentId ->
                                    nav.navigate(Routes.incidentDetail(incidentId))
                                },
                                onNavigateToReports = {
                                    nav.navigate(Routes.Reports)
                                }
                            )
                        }

                        composable(Routes.CreateIncident) {
                            CreateIncidentScreen(
                                onBack = { nav.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.IncidentDetail,
                            arguments = listOf(
                                navArgument("incidentId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val incidentId = backStackEntry.arguments?.getString("incidentId") ?: ""
                            IncidentDetailScreen(
                                incidentId = incidentId,
                                onNavigateBack = { nav.popBackStack() }
                            )
                        }

                        composable(Routes.MyReports) {
                            MyReportsScreen(
                                onNavigateBack = { nav.popBackStack() },
                                onNavigateToDetail = { incidentId ->
                                    nav.navigate(Routes.incidentDetail(incidentId))
                                }
                            )
                        }

                        composable(Routes.Profile) {
                            ProfileScreen(
                                onNavigateBack = { nav.popBackStack() },
                                onNavigateToMyReports = { nav.navigate(Routes.MyReports) },
                                onNavigateToStatistics = { nav.navigate(Routes.Statistics) },
                                onLogout = {
                                    authVm.logout {
                                        nav.navigate(Routes.Login) {
                                            popUpTo(Routes.Home) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable(Routes.Statistics) {
                            StatisticsScreen(
                                onNavigateBack = { nav.popBackStack() }
                            )
                        }

                        composable(Routes.Reports) {
                            ReportsScreen(
                                onNavigateBack = { nav.popBackStack() }
                            )
                        }
                    }

                    // ─── Deep link desde notificación ───
                    // Solo navega cuando el usuario ya superó el Splash y está en Home.
                    // Funciona tanto para arranque en frío (onCreate) como en background (onNewIntent).
                    LaunchedEffect(pendingNotifId, currentRoute) {
                        val id = pendingNotifId ?: return@LaunchedEffect
                        if (currentRoute == Routes.Home && id.isNotBlank()) {
                            nav.navigate(Routes.incidentDetail(id))
                            _pendingNotifId.value = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Se llama cuando la app ya está abierta (en foreground o background reciente)
     * y el usuario toca una notificación.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processNotificationIntent(intent)
    }

    /**
     * Lee el extra [EXTRA_INCIDENT_ID] del intent de la notificación y lo pone
     * en el flujo que el composable observa.
     */
    private fun processNotificationIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_INCIDENT_ID)?.takeIf { it.isNotBlank() }?.let { id ->
            _pendingNotifId.value = id
        }
    }
}
