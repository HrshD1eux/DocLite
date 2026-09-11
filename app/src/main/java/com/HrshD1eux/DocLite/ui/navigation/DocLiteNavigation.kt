package com.HrshD1eux.DocLite.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.HrshD1eux.DocLite.core.di.AppViewModelProvider
import com.HrshD1eux.DocLite.models.DocumentFormat
import com.HrshD1eux.DocLite.ui.components.BottomNavTab
import com.HrshD1eux.DocLite.ui.components.DocLiteBottomNav
import com.HrshD1eux.DocLite.ui.screens.excel.ExcelEditorScreen
import com.HrshD1eux.DocLite.ui.screens.excel.ExcelViewModel
import com.HrshD1eux.DocLite.ui.screens.filemanager.FileManagerScreen
import com.HrshD1eux.DocLite.ui.screens.filemanager.FileManagerViewModel
import com.HrshD1eux.DocLite.ui.screens.home.HomeScreen
import com.HrshD1eux.DocLite.ui.screens.home.HomeViewModel
import com.HrshD1eux.DocLite.ui.screens.image.ImageViewerScreen
import com.HrshD1eux.DocLite.ui.screens.image.ImageViewModel
import com.HrshD1eux.DocLite.ui.screens.pdf.PdfAnnotatorScreen
import com.HrshD1eux.DocLite.ui.screens.pdf.PdfViewModel
import com.HrshD1eux.DocLite.ui.screens.powerpoint.PowerPointEditorScreen
import com.HrshD1eux.DocLite.ui.screens.powerpoint.PowerPointViewModel
import com.HrshD1eux.DocLite.ui.screens.settings.SettingsScreen
import com.HrshD1eux.DocLite.ui.screens.settings.SettingsViewModel
import com.HrshD1eux.DocLite.ui.screens.text.TextEditorScreen
import com.HrshD1eux.DocLite.ui.screens.text.TextViewModel
import com.HrshD1eux.DocLite.ui.screens.word.WordEditorScreen
import com.HrshD1eux.DocLite.ui.screens.word.WordViewModel
import com.HrshD1eux.DocLite.bankstatement.ui.BankStatementAnalyserScreen
import com.HrshD1eux.DocLite.bankstatement.ui.BankStatementViewModel

object Routes {
    const val HOME = "home"
    const val FILE_MANAGER = "file_manager"
    const val SETTINGS = "settings"
    const val BANK_STATEMENT_ANALYSER = "bank_statement_analyser"
    const val BANK_STATEMENT_ANALYSER_ROUTE = "bank_statement_analyser?fileUri={fileUri}"
    const val EDITOR_WORD = "editor_word/{fileUri}"
    const val EDITOR_TEXT = "editor_text/{fileUri}"
    const val EDITOR_EXCEL = "editor_excel/{fileUri}"
    const val EDITOR_POWERPOINT = "editor_powerpoint/{fileUri}"
    const val ANNOTATOR_PDF = "annotator_pdf/{fileUri}"
    const val VIEWER_IMAGE = "viewer_image/{fileUri}"

    fun buildWordRoute(uri: Uri): String = "editor_word/${Uri.encode(uri.toString())}"
    fun buildTextRoute(uri: Uri): String = "editor_text/${Uri.encode(uri.toString())}"
    fun buildExcelRoute(uri: Uri): String = "editor_excel/${Uri.encode(uri.toString())}"
    fun buildPowerPointRoute(uri: Uri): String = "editor_powerpoint/${Uri.encode(uri.toString())}"
    fun buildPdfRoute(uri: Uri): String = "annotator_pdf/${Uri.encode(uri.toString())}"
    fun buildImageRoute(uri: Uri): String = "viewer_image/${Uri.encode(uri.toString())}"
    fun buildBankStatementRoute(uri: Uri? = null): String =
        if (uri != null) "bank_statement_analyser?fileUri=${Uri.encode(uri.toString())}"
        else "bank_statement_analyser"
}

@Composable
fun DocLiteNavigation(
    navController: NavHostController = rememberNavController(),
    initialIntentUri: Uri? = null,
    initialIntentFormat: DocumentFormat? = null,
    startScreen: com.HrshD1eux.DocLite.models.StartScreen = com.HrshD1eux.DocLite.models.StartScreen.HOME,
    onIntentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME

    LaunchedEffect(initialIntentUri, initialIntentFormat) {
        if (initialIntentUri != null && initialIntentFormat != null) {
            navigateToFormatEditor(navController, initialIntentUri, initialIntentFormat)
            onIntentConsumed()
        }
    }

    val showBottomNav = currentRoute in listOf(Routes.HOME, Routes.FILE_MANAGER, Routes.SETTINGS)
    val startDestination = if (startScreen == com.HrshD1eux.DocLite.models.StartScreen.FILE_MANAGER) Routes.FILE_MANAGER else Routes.HOME

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                DocLiteBottomNav(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(startDestination) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenFile = { file ->
                        val uri = Uri.parse(file.uriString)
                        navigateToFormatEditor(navController, uri, file.format)
                    },
                    onOpenUri = { uri, format ->
                        navigateToFormatEditor(navController, uri, format)
                    },
                    onOpenBankStatementAnalyser = {
                        navController.navigate(Routes.BANK_STATEMENT_ANALYSER)
                    },
                    onAnalyzeStatement = { file ->
                        val uri = Uri.parse(file.uriString)
                        navController.navigate(Routes.buildBankStatementRoute(uri))
                    }
                )
            }

            composable(
                route = Routes.BANK_STATEMENT_ANALYSER_ROUTE,
                arguments = listOf(
                    navArgument("fileUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val uriString = backStackEntry.arguments?.getString("fileUri")
                val initialUri = uriString?.let { Uri.parse(Uri.decode(it)) }
                val bankViewModel: BankStatementViewModel = viewModel(factory = AppViewModelProvider.Factory)
                BankStatementAnalyserScreen(
                    viewModel = bankViewModel,
                    initialUri = initialUri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.FILE_MANAGER) {
                val fileManagerViewModel: FileManagerViewModel = viewModel(factory = AppViewModelProvider.Factory)
                FileManagerScreen(
                    viewModel = fileManagerViewModel,
                    onOpenFile = { file ->
                        val uri = Uri.parse(file.uriString)
                        navigateToFormatEditor(navController, uri, file.format)
                    },
                    onAnalyzeStatement = { file ->
                        val uri = Uri.parse(file.uriString)
                        navController.navigate(Routes.buildBankStatementRoute(uri))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = Routes.EDITOR_WORD,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val wordViewModel: WordViewModel = viewModel(factory = AppViewModelProvider.Factory)
                WordEditorScreen(
                    viewModel = wordViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDITOR_EXCEL,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val excelViewModel: ExcelViewModel = viewModel(factory = AppViewModelProvider.Factory)
                ExcelEditorScreen(
                    viewModel = excelViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDITOR_POWERPOINT,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val powerPointViewModel: PowerPointViewModel = viewModel(factory = AppViewModelProvider.Factory)
                PowerPointEditorScreen(
                    viewModel = powerPointViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ANNOTATOR_PDF,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val pdfViewModel: PdfViewModel = viewModel(factory = AppViewModelProvider.Factory)
                PdfAnnotatorScreen(
                    viewModel = pdfViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDITOR_TEXT,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val textViewModel: TextViewModel = viewModel(factory = AppViewModelProvider.Factory)
                TextEditorScreen(
                    viewModel = textViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.VIEWER_IMAGE,
                arguments = listOf(navArgument("fileUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("fileUri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                val imageViewModel: ImageViewModel = viewModel(factory = AppViewModelProvider.Factory)
                ImageViewerScreen(
                    viewModel = imageViewModel,
                    fileUri = uri,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun navigateToFormatEditor(navController: NavHostController, uri: Uri, format: DocumentFormat) {
    when (format) {
        DocumentFormat.WORD -> navController.navigate(Routes.buildWordRoute(uri))
        DocumentFormat.TXT -> navController.navigate(Routes.buildTextRoute(uri))
        DocumentFormat.EXCEL -> navController.navigate(Routes.buildExcelRoute(uri))
        DocumentFormat.POWERPOINT -> navController.navigate(Routes.buildPowerPointRoute(uri))
        DocumentFormat.PDF -> navController.navigate(Routes.buildPdfRoute(uri))
        DocumentFormat.IMAGE -> navController.navigate(Routes.buildImageRoute(uri))
    }
}

