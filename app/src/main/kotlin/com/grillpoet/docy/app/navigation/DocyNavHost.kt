package com.grillpoet.docy.app.navigation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.grillpoet.docy.app.DocyApp
import com.grillpoet.docy.app.di.DocyViewModel
import com.grillpoet.docy.app.markdown.MarkdownScreen
import com.grillpoet.docy.app.ui.DocyScaffold
import com.grillpoet.docy.app.ui.archive.ArchiveScreen
import com.grillpoet.docy.app.ui.folder.FolderScreen
import com.grillpoet.docy.app.ui.settings.SettingsScreen
import com.grillpoet.docy.editor.ui.EditorScreen
import kotlinx.coroutines.launch

@Composable
fun DocyNavHost(navController: NavHostController) {
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity
    val scope = rememberCoroutineScope()

    val docyVm: DocyViewModel = viewModel(
        viewModelStoreOwner = activity ?: error("DocyNavHost 需要 Activity 上下文")
    )

    val app = ctx.applicationContext as DocyApp
    val disabledLanguages: Set<String> by app.prefs.disabledLanguages
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val disabledLibraries: Set<String> by app.prefs.disabledLibraries
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val forceEnabledLibraries: Set<String> by app.prefs.forceEnabledLibraries
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val fontSize: Float by app.prefs.fontSize
        .collectAsStateWithLifecycle(initialValue = 14f)
    val colorfulSyntax: Boolean by app.prefs.colorfulSyntax
        .collectAsStateWithLifecycle(initialValue = true)

    DocyScaffold(navController = navController, docyVm = docyVm) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Routes.HOME) {
                FolderScreen(
                    navController = navController,
                    docyVm = docyVm,
                    path = null
                )
            }
            composable(
                route = Routes.FOLDER,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val encoded = entry.arguments?.getString("path").orEmpty()
                FolderScreen(
                    navController = navController,
                    docyVm = docyVm,
                    path = Uri.decode(encoded)
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val encoded = entry.arguments?.getString("path").orEmpty()
                EditorScreen(
                    path = Uri.decode(encoded),
                    onBack = { navController.popBackStack() },
                    disabledLanguages = disabledLanguages,
                    disabledLibraries = disabledLibraries,
                    forceEnabledLibraries = forceEnabledLibraries,
                    fontSize = fontSize,
                    colorfulSyntax = colorfulSyntax,
                    onFontSizeChange = { newSize ->
                        scope.launch { app.prefs.setFontSize(newSize) }
                    }
                )
            }
            composable(
                route = Routes.MARKDOWN,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val encoded = entry.arguments?.getString("path").orEmpty()
                val p = Uri.decode(encoded)
                MarkdownScreen(
                    path = p,
                    onBack = { navController.popBackStack() },
                    onEdit = { pathToEdit ->
                        navController.navigate(Routes.editor(pathToEdit)) {
                            launchSingleTop = true
                        }
                    },
                    fontSize = fontSize
                )
            }
            composable(
                route = Routes.ARCHIVE,
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { entry ->
                val encoded = entry.arguments?.getString("path").orEmpty()
                ArchiveScreen(
                    path = Uri.decode(encoded),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
