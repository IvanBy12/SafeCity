package com.example.safecity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val nav = rememberNavController()
                    val authVm: AuthViewModel = viewModel()

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
                    }
                }
            }
        }
    }
}