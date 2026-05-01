package com.example.autotoucher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.autotoucher.ui.screen.PermissionGuideScreen
import com.example.autotoucher.ui.screen.TaskEditScreen
import com.example.autotoucher.ui.screen.TaskListScreen
import com.example.autotoucher.ui.theme.AutoToucherTheme
import com.example.autotoucher.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoToucherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val taskViewModel: TaskViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "permissions"
                    ) {
                        composable("permissions") {
                            PermissionGuideScreen(
                                viewModel = taskViewModel,
                                navController = navController,
                                onAllGranted = {
                                    navController.navigate("tasks") {
                                        popUpTo("permissions") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("tasks") {
                            TaskListScreen(
                                viewModel = taskViewModel,
                                navController = navController
                            )
                        }

                        composable("edit/{taskId}") { backStackEntry ->
                            val taskId = backStackEntry.arguments
                                ?.getString("taskId")
                                ?.toIntOrNull() ?: -1
                            TaskEditScreen(
                                viewModel = taskViewModel,
                                taskId = taskId,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
