package com.example.safecity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.safecity.auth.AuthViewModel
import com.example.safecity.nav.Routes
import com.example.safecity.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val nav = rememberNavController()
            val authVm: AuthViewModel = viewModel()

            NavHost(
                navController = nav,
                startDestination = Routes.Splash
            ) {

                // ✅ Splash SOLO aquí
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

                // ✅ Register SOLO aquí (elige UNA firma)
                composable(Routes.Register) {
                    RegisterScreen(
                        onGoLogin = { nav.popBackStack() },
                        onRegistered = {
                            nav.navigate(Routes.Home) {
                                popUpTo(Routes.Register) { inclusive = true }
                            }
                        },
                        onGoPhoneRegister = { nav.navigate(Routes.PhoneAuth) }
                    )
                }
                composable(Routes.PhoneAuth) {
                    PhoneAuthScreen(
                        onBack = { nav.popBackStack() },
                        onSuccess = {
                            nav.navigate(Routes.Home) {
                                popUpTo(Routes.Register) { inclusive = true }
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
                        }
                    )
                }
            }
        }
    }
}
