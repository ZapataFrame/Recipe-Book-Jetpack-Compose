package upv_dap.sep_dic_25.itiid_76129.pgu1_eq02
import upv_dap.sep_dic_25.itiid_76129.pgu1_eq02.ui.theme.Z_U1_76129_E_02Theme

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val imageUri: Uri? = null,
    val dateCreated: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val isFavorite: Boolean = false
)

class MainActivity : ComponentActivity() {

    private val selectedImageUri = mutableStateOf<Uri?>(null)

    // State lifted to Activity so we can export/import from outside the composable
    private val recipesState = mutableStateOf<List<Recipe>>(emptyList())
    // Pending imported recipes waiting for user confirmation (replace/merge)
    private val pendingImportRecipes = mutableStateOf<List<Recipe>?>(null)
    // Message to show after export (Snackbar)
    private val exportResultMessage = mutableStateOf<String?>(null)

    // Moshi for JSON (use DTOs for stable serialization)
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // DTO used for serialization (imageUri represented as String, may contain "zip:images/..." prefix)
    @Suppress("DataClassPrivateConstructor")
    data class RecipeDto(
        val id: String,
        val title: String,
        val category: String,
        val ingredients: List<String>,
        val steps: List<String>,
        val imageUri: String?,
        val dateCreated: String,
        val isFavorite: Boolean
    )

    private fun Recipe.toDto(imageUriStr: String? = this.imageUri?.toString()): RecipeDto {
        return RecipeDto(
            id = this.id,
            title = this.title,
            category = this.category,
            ingredients = this.ingredients,
            steps = this.steps,
            imageUri = imageUriStr,
            dateCreated = this.dateCreated,
            isFavorite = this.isFavorite
        )
    }

    private fun RecipeDto.toRecipe(imageUriResolver: (String?) -> Uri?): Recipe {
        val uri = imageUriResolver(this.imageUri)
        return Recipe(
            id = this.id,
            title = this.title,
            category = this.category,
            ingredients = this.ingredients,
            steps = this.steps,
            imageUri = uri,
            dateCreated = this.dateCreated,
            isFavorite = this.isFavorite
        )
    }

    private fun recipesToJsonString(list: List<Recipe>): String {
        val dtoList = list.map { it.toDto() }
        val type = Types.newParameterizedType(List::class.java, RecipeDto::class.java)
        val adapter = moshi.adapter<List<RecipeDto>>(type)
        return adapter.toJson(dtoList)
    }

    private fun recipeToJsonString(recipe: Recipe): String {
        val adapter = moshi.adapter(RecipeDto::class.java)
        return adapter.toJson(recipe.toDto())
    }

    private fun jsonStringToRecipes(text: String): List<Recipe> {
        if (text.isBlank()) return emptyList()
        val type = Types.newParameterizedType(List::class.java, RecipeDto::class.java)
        val adapter = moshi.adapter<List<RecipeDto>>(type)
        val dtoList = try { adapter.fromJson(text) } catch (_: Exception) { null }
        if (dtoList == null) return emptyList()
        return dtoList.map { dto -> dto.toRecipe { uriStr -> if (uriStr.isNullOrBlank()) null else Uri.parse(uriStr) } }
    }

    private fun saveRecipesToInternal() {
        try {
            val file = File(filesDir, "recipes.json")
            file.writeText(recipesToJsonString(recipesState.value), Charsets.UTF_8)
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadRecipesFromInternal(): List<Recipe> {
        return try {
            val file = File(filesDir, "recipes.json")
            if (!file.exists()) return emptyList()
            val text = file.readText(Charsets.UTF_8)
            jsonStringToRecipes(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    private fun cleanOldShareFiles() {
        try {
            val files = cacheDir.listFiles() ?: return
            files.filter { it.name.startsWith("recipe_") && it.name.endsWith(".json") }.forEach { it.delete() }
        } catch (_: Exception) { /* ignore */ }
    }

    // Copy an image Uri (content:// or other) to internal filesDir/images/<recipeId>.<ext>
    private fun copyImageToInternal(imageUri: Uri, recipeId: String): Uri? {
        return try {
            val resolver = applicationContext.contentResolver
            resolver.openInputStream(imageUri)?.use { input ->
                var ext = "jpg"
                try {
                    val type = resolver.getType(imageUri)
                    if (type != null) {
                        val dot = type.substringAfterLast('/').takeIf { it.isNotEmpty() }
                        if (dot != null) ext = dot
                    }
                } catch (_: Exception) {}

                val imagesDir = File(filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val target = File(imagesDir, "$recipeId.$ext")
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (true) {
                        len = input.read(buf)
                        if (len <= 0) break
                        out.write(buf, 0, len)
                    }
                    out.flush()
                }
                Uri.fromFile(target)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun addRecipeInternal(r: Recipe) {
        var recipeToAdd = r
        try {
            if (r.imageUri != null) {
                val isInternal = (r.imageUri.scheme == "file" && r.imageUri.path?.startsWith(File(filesDir, "images").absolutePath) == true)
                if (!isInternal) {
                    val newUri = copyImageToInternal(r.imageUri, r.id)
                    if (newUri != null) recipeToAdd = r.copy(imageUri = newUri)
                }
            }
        } catch (_: Exception) { /* ignore */ }

        recipesState.value = recipesState.value + recipeToAdd
        saveRecipesToInternal()
    }

    private fun updateRecipeInternal(updated: Recipe) {
        var updatedRecipe = updated
        val existing = recipesState.value.find { it.id == updated.id }
        if (existing != null) {
            try {
                // If image changed and new one is external, copy it
                if (updated.imageUri != null && updated.imageUri != existing.imageUri) {
                    val isInternal = (updated.imageUri.scheme == "file" && updated.imageUri.path?.startsWith(File(filesDir, "images").absolutePath) == true)
                    if (!isInternal) {
                        val newUri = copyImageToInternal(updated.imageUri, updated.id)
                        if (newUri != null) {
                            // remove old internal file if any
                            existing.imageUri?.let { old ->
                                try {
                                    if (old.scheme == "file") {
                                        val oldFile = File(old.path ?: "")
                                        if (oldFile.exists() && oldFile.parentFile?.name == "images") oldFile.delete()
                                    }
                                } catch (_: Exception) {}
                            }
                            updatedRecipe = updated.copy(imageUri = newUri)
                        }
                    }
                } else if (updated.imageUri == null && existing.imageUri != null) {
                    // image removed: delete old internal file if present
                    existing.imageUri?.let { old ->
                        try {
                            if (old.scheme == "file") {
                                val oldFile = File(old.path ?: "")
                                if (oldFile.exists() && oldFile.parentFile?.name == "images") oldFile.delete()
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        recipesState.value = recipesState.value.map { if (it.id == updatedRecipe.id) updatedRecipe else it }
        saveRecipesToInternal()
    }

    private fun deleteRecipeInternal(id: String) {
        recipesState.value = recipesState.value.filterNot { it.id == id }
        saveRecipesToInternal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri.value = uri
            } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            }
        }

        // Launcher to create a file (export backup as ZIP)
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, "Export canceled", Toast.LENGTH_SHORT).show()
            } else {
                exportRecipesToZipUri(this, uri, recipesState.value)
            }
        }

        // Launcher to open a JSON file (import/restore)
        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, "Import canceled", Toast.LENGTH_SHORT).show()
            } else {
                importRecipesFromUri(this, uri) { imported ->
                    // Don't immediately replace: store pending and ask user via dialog in Compose
                    pendingImportRecipes.value = imported
                }
            }
        }

        // Load persisted recipes before showing UI
        recipesState.value = loadRecipesFromInternal()
        // Clean temp share files
        cleanOldShareFiles()

        setContent {
            Z_U1_76129_E_02Theme {
                RecipeBookApp(
                    selectedImageUri = selectedImageUri.value,
                    onImageSelect = { imagePickerLauncher.launch("image/*") },
                    onImageClear = { selectedImageUri.value = null },
                    recipes = recipesState.value,
                    onAddRecipe = { r -> addRecipeInternal(r) },
                    onUpdateRecipe = { updated -> updateRecipeInternal(updated) },
                    onDeleteRecipe = { id -> deleteRecipeInternal(id) },
                    onExport = { exportLauncher.launch("recipes_backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "application/*+json", "*/*")) },
                    onShareRecipe = { recipe -> shareRecipe(this, recipe) },
                    // Import confirmation handlers
                    showImportDialog = pendingImportRecipes.value != null,
                    pendingImportCount = pendingImportRecipes.value?.size ?: 0,
                    onConfirmImportReplace = {
                        recipesState.value = pendingImportRecipes.value ?: emptyList()
                        pendingImportRecipes.value = null
                        Toast.makeText(this, "Import applied (replace)", Toast.LENGTH_SHORT).show()
                    },
                    onConfirmImportMerge = {
                        val toImport = pendingImportRecipes.value ?: emptyList()
                        val existing = recipesState.value.toMutableList()
                        val existingIds = existing.map { it.id }.toMutableSet()
                        // compute how many will be added
                        val willAdd = toImport.count { !existingIds.contains(it.id) }
                        toImport.forEach { r -> if (!existingIds.contains(r.id)) { existing.add(r); existingIds.add(r.id) } }
                        recipesState.value = existing
                        pendingImportRecipes.value = null
                        Toast.makeText(this, "Import merged: added $willAdd recipes", Toast.LENGTH_SHORT).show()
                    },
                    onCancelImport = {
                        pendingImportRecipes.value = null
                        Toast.makeText(this, "Import canceled", Toast.LENGTH_SHORT).show()
                    }
                    ,
                    exportResultMessage = exportResultMessage.value,
                    onClearExportMessage = { exportResultMessage.value = null }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Ensure recipes persisted when app is backgrounded
        try {
            saveRecipesToInternal()
        } catch (_: Exception) { }
    }

    // --- Export helpers (ZIP with images) ---
    private fun exportRecipesToZipUri(context: Context, uri: Uri, recipes: List<Recipe>) {
        // create temp zip file in cache
        try {
            val tmpZip = File.createTempFile("recipes_backup", ".zip", cacheDir)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmpZip))).use { zos ->
                // build DTO list and include images inside zip under images/<filename>
                val dtoList = mutableListOf<RecipeDto>()
                recipes.forEach { recipe ->
                    var dtoImageUriStr: String? = null
                    if (recipe.imageUri != null) {
                        try {
                            val resolver = context.contentResolver
                            resolver.openInputStream(recipe.imageUri)?.use { input ->
                                // determine extension if possible
                                var ext = "jpg"
                                try {
                                    val type = resolver.getType(recipe.imageUri)
                                    if (type != null) {
                                        val dot = type.substringAfterLast('/').takeIf { it.isNotEmpty() }
                                        if (dot != null) ext = dot
                                    }
                                } catch (_: Exception) {}

                                val imageName = "${recipe.id}.$ext"
                                val entryName = "images/$imageName"
                                zos.putNextEntry(ZipEntry(entryName))
                                val buf = ByteArray(8192)
                                var len: Int
                                BufferedInputStream(input).use { bin ->
                                    while (true) {
                                        len = bin.read(buf)
                                        if (len <= 0) break
                                        zos.write(buf, 0, len)
                                    }
                                }
                                zos.closeEntry()
                                dtoImageUriStr = "zip:$entryName"
                            }
                        } catch (_: Exception) {
                            dtoImageUriStr = null
                        }
                    }

                    dtoList.add(recipe.toDto(dtoImageUriStr))
                }

                // write recipes.json entry using Moshi
                val type = Types.newParameterizedType(List::class.java, RecipeDto::class.java)
                val adapter = moshi.adapter<List<RecipeDto>>(type)
                zos.putNextEntry(ZipEntry("recipes.json"))
                val jsonBytes = adapter.toJson(dtoList).toByteArray(Charsets.UTF_8)
                zos.write(jsonBytes)
                zos.closeEntry()
            }

            // copy tmpZip to destination uri
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tmpZip).use { fis ->
                    val buf = ByteArray(8192)
                    var len: Int
                    while (true) {
                        len = fis.read(buf)
                        if (len <= 0) break
                        out.write(buf, 0, len)
                    }
                    out.flush()
                }
            }

            tmpZip.delete()
            val msg = "Export (zip) successful"
            exportResultMessage.value = msg
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val err = "Export failed: ${e.message}"
            exportResultMessage.value = err
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
        }
    }

    private fun importRecipesFromUri(context: Context, uri: Uri, callback: (List<Recipe>) -> Unit) {
        try {
            // Try to open as ZIP first
            val input = context.contentResolver.openInputStream(uri) ?: run {
                Toast.makeText(this, "Import failed: cannot open file", Toast.LENGTH_LONG).show()
                return
            }

            var parsedRecipes: List<Recipe>? = null

            try {
                val imagesMap = mutableMapOf<String, File>()
                val zis = ZipInputStream(BufferedInputStream(input))
                var entry: ZipEntry? = zis.nextEntry
                var recipesJsonText: String? = null
                while (entry != null) {
                    val name = entry.name
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (name == "recipes.json") {
                        // read text
                        val baos = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var r: Int
                        while (true) {
                            r = zis.read(buf)
                            if (r <= 0) break
                            baos.write(buf, 0, r)
                        }
                        recipesJsonText = baos.toString(Charsets.UTF_8.name())
                        zis.closeEntry()
                    } else if (name.startsWith("images/")) {
                        // extract image to internal filesDir/images/
                        val imagesDir = File(filesDir, "images")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val target = File(imagesDir, name.substringAfterLast('/'))
                        BufferedOutputStream(FileOutputStream(target)).use { out ->
                            val buf = ByteArray(8192)
                            var r: Int
                            while (true) {
                                r = zis.read(buf)
                                if (r <= 0) break
                                out.write(buf, 0, r)
                            }
                            out.flush()
                        }
                        imagesMap[name] = target
                        zis.closeEntry()
                    } else {
                        // unknown entry, skip
                        zis.closeEntry()
                    }

                    entry = zis.nextEntry
                }
                zis.close()

                if (recipesJsonText != null) {
                    // parse using Moshi DTOs and resolve image URIs
                    val type = Types.newParameterizedType(List::class.java, RecipeDto::class.java)
                    val adapter = moshi.adapter<List<RecipeDto>>(type)
                    val dtoList = try { adapter.fromJson(recipesJsonText) } catch (_: Exception) { null }
                    if (dtoList != null) {
                        val list = mutableListOf<Recipe>()
                        dtoList.forEach { dto ->
                            val imageUriStr = dto.imageUri
                            val imageUri: Uri? = when {
                                imageUriStr == null -> null
                                imageUriStr.startsWith("zip:images/") -> {
                                    val entryName = imageUriStr.removePrefix("zip:")
                                    val saved = imagesMap[entryName]
                                    if (saved != null && saved.exists()) Uri.fromFile(saved) else null
                                }
                                imageUriStr.isNotBlank() -> Uri.parse(imageUriStr)
                                else -> null
                            }

                            val recipe = Recipe(
                                id = dto.id.ifBlank { UUID.randomUUID().toString() },
                                title = dto.title.ifBlank { "Untitled" },
                                category = dto.category,
                                ingredients = dto.ingredients ?: emptyList(),
                                steps = dto.steps ?: emptyList(),
                                imageUri = imageUri,
                                dateCreated = dto.dateCreated.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
                                isFavorite = dto.isFavorite
                            )
                            list.add(recipe)
                        }
                        parsedRecipes = list
                    }
                }
            } catch (zipEx: Exception) {
                // Not a zip or zip processing failed: fallback to plain JSON
                input.close()
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                parsedRecipes = jsonStringToRecipes(text)
            }

            if (parsedRecipes != null) callback(parsedRecipes) else callback(emptyList())

        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareRecipe(context: Context, recipe: Recipe) {
        try {
            // Write single recipe JSON to cache
            val filename = "recipe_${recipe.id}.json"
            val cacheFile = File(context.cacheDir, filename)
            // remove old file if exists
            if (cacheFile.exists()) cacheFile.delete()
            cacheFile.outputStream().use { out ->
                out.write(recipeToJsonString(recipe).toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, cacheFile)

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(share, "Share recipe"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookApp(
    selectedImageUri: Uri?,
    onImageSelect: () -> Unit,
    onImageClear: () -> Unit,
    recipes: List<Recipe>,
    onAddRecipe: (Recipe) -> Unit,
    onUpdateRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShareRecipe: (Recipe) -> Unit,
    exportResultMessage: String? = null,
    onClearExportMessage: () -> Unit = {}
    ,
    // import confirmation UI wiring
    showImportDialog: Boolean = false,
    pendingImportCount: Int = 0,
    onConfirmImportReplace: () -> Unit = {},
    onConfirmImportMerge: () -> Unit = {},
    onCancelImport: () -> Unit = {}
) {
    val navController = rememberNavController()

    // 'recipes' proviene del Activity; no lo redeclaramos aquí.

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            currentRoute == "home" -> "Recipe Book"
                            currentRoute == "add" -> "Add Recipe"
                            currentRoute == "recipes" -> "All Recipes"
                            currentRoute == "favorites" -> "Favorites"
                            currentRoute.startsWith("detail") -> "Recipe Detail"
                            currentRoute.startsWith("duplicate") -> "Duplicate Recipe"
                            else -> "Recipe Book"
                        }
                    )
                },
                actions = {
                    TextButton(onClick = { onImport() }) {
                        Text("Import", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = { onExport() }) {
                        Text("Export", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (currentRoute != "home") {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("Add") },
                    selected = currentRoute == "add",
                    onClick = {
                        navController.navigate("add") {
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Recipes") },
                    selected = currentRoute == "recipes",
                    onClick = {
                        navController.navigate("recipes") {
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Favorites") },
                    selected = currentRoute == "favorites",
                    onClick = {
                        navController.navigate("favorites") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(exportResultMessage) {
            exportResultMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                onClearExportMessage()
            }
        }
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    recipes = recipes,
                    onRecipeClick = { recipe ->
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("add") {
                AddRecipeScreen(
                    selectedImageUri = selectedImageUri,
                    onImageSelect = onImageSelect,
                    onImageClear = onImageClear,
                    onRecipeAdded = { recipe ->
                        onAddRecipe(recipe)
                        onImageClear() // Limpiar imagen después de agregar
                        navController.navigateUp()
                    },
                    onCancel = {
                        onImageClear() // Limpiar imagen al cancelar
                        navController.navigateUp()
                    }
                )
            }

            composable("duplicate/{recipeId}") { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString("recipeId")
                val originalRecipe = recipes.find { it.id == recipeId }

                if (originalRecipe != null) {
                    AddRecipeScreen(
                        selectedImageUri = selectedImageUri,
                        onImageSelect = onImageSelect,
                        onImageClear = onImageClear,
                        onRecipeAdded = { recipe ->
                            onAddRecipe(recipe)
                            onImageClear() // Limpiar imagen después de agregar
                            navController.navigateUp()
                        },
                        onCancel = {
                            onImageClear() // Limpiar imagen al cancelar
                            navController.navigateUp()
                        },
                        duplicateFrom = originalRecipe
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Original recipe not found")
                    }
                }
            }

            composable("recipes") {
                RecipeListScreen(
                    recipes = recipes,
                    onRecipeClick = { recipe ->
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("favorites") {
                FavoritesScreen(
                    favoriteRecipes = recipes.filter { it.isFavorite },
                    onRecipeClick = { recipe ->
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("detail/{recipeId}") { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString("recipeId")
                val recipe = recipes.find { it.id == recipeId }

                if (recipe != null) {
                    RecipeDetailScreen(
                        recipe = recipe,
                        selectedImageUri = selectedImageUri,
                        onImageSelect = onImageSelect,
                        onImageClear = onImageClear,
                        onToggleFavorite = { updatedRecipe ->
                            onUpdateRecipe(updatedRecipe)
                        },
                        onDeleteRecipe = {
                            onDeleteRecipe(recipe.id)
                            onImageClear() // Limpiar imagen al eliminar
                            navController.navigateUp()
                        },
                        onEditRecipe = { updatedRecipe ->
                            onUpdateRecipe(updatedRecipe)
                            onImageClear() // Limpiar imagen después de editar
                        },
                        onShareRecipe = { r -> onShareRecipe(r) },
                        onDuplicateRecipe = { r ->
                            navController.navigate("duplicate/${r.id}")
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Recipe not found")
                    }
                }
            }
        }
    }
    // place the SnackbarHost so it's visible above everything
    if (exportResultMessage != null) {
        // handled in LaunchedEffect above
    }

    // Import confirmation dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { onCancelImport() },
            title = { Text("Restore recipes") },
            text = { Text("Detected $pendingImportCount recipes in the selected file. Do you want to replace your current recipes or merge (add non-duplicates)?") },
            confirmButton = {
                Button(onClick = { onConfirmImportReplace() }) { Text("Replace") }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = { onConfirmImportMerge() }) { Text("Merge") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onCancelImport() }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    val recentRecipes = remember(recipes) {
        recipes.sortedByDescending { it.dateCreated }.take(5)
    }
    val favoriteRecipes = remember(recipes) {
        recipes.filter { it.isFavorite }.take(3)
    }
    val filteredRecipes = remember(recipes, searchText) {
        recipes.filter {
            it.title.contains(searchText, ignoreCase = true) ||
                    it.category.contains(searchText, ignoreCase = true) ||
                    it.ingredients.any { ingredient -> ingredient.contains(searchText, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search recipes, ingredients...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (searchText.isEmpty()) {
            item {
                // Statistics Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Your Recipe Collection",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem("Total", "${recipes.size}")
                            StatItem("Favorites", "${recipes.count { it.isFavorite }}")
                            StatItem("Categories", "${recipes.map { it.category }.distinct().size}")
                        }
                    }
                }
            }

            if (recentRecipes.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Added",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentRecipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onRecipeClick(recipe) },
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }
            }

            if (favoriteRecipes.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Favorites",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favoriteRecipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onRecipeClick(recipe) },
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }
            }

            // Mensaje cuando no hay recetas
            if (recipes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No recipes yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Add your first recipe to get started!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Search Results (${filteredRecipes.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(filteredRecipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe) }
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(
    selectedImageUri: Uri?,
    onImageSelect: () -> Unit,
    onImageClear: () -> Unit,
    onRecipeAdded: (Recipe) -> Unit,
    onCancel: () -> Unit,
    duplicateFrom: Recipe? = null
) {
    var title by remember { mutableStateOf(duplicateFrom?.let { "${it.title} (Copy)" } ?: "") }
    var category by remember { mutableStateOf(duplicateFrom?.category ?: "") }
    var ingredients by remember { mutableStateOf(duplicateFrom?.ingredients?.joinToString("\n") ?: "") }
    var steps by remember { mutableStateOf(duplicateFrom?.steps?.joinToString("\n") ?: "") }

    val categories = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Beverage")
    var expandedCategory by remember { mutableStateOf(false) }

    // Efecto para cargar la imagen original al duplicar (si existe)
    LaunchedEffect(duplicateFrom) {
        if (duplicateFrom?.imageUri != null && selectedImageUri == null) {
            // En caso de duplicar, la imagen original se mantendrá referenciada
            // pero no se pre-selecciona automáticamente para evitar conflictos
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Recipe Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                expandedCategory = false
                            }
                        )
                    }
                }
            }
        }

        // Sección de imagen
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recipe Image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedImageUri != null) {
                        // Mostrar imagen seleccionada
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(selectedImageUri)
                                    .crossfade(true)
                                    .build()
                            ),
                            contentDescription = "Selected recipe image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onImageSelect,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change Image")
                            }

                            OutlinedButton(
                                onClick = onImageClear,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.Red
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove")
                            }
                        }
                    } else {
                        // Mostrar botón para seleccionar imagen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickableWithoutRipple { onImageSelect() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to add image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onImageSelect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Image")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = ingredients,
                onValueChange = { ingredients = it },
                label = { Text("Ingredients") },
                placeholder = { Text("Enter each ingredient on a new line") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 6
            )
        }

        item {
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it },
                label = { Text("Instructions") },
                placeholder = { Text("Enter each step on a new line") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 8
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (title.isNotBlank() && category.isNotBlank() &&
                            ingredients.isNotBlank() && steps.isNotBlank()) {
                            val recipe = Recipe(
                                title = title.trim(),
                                category = category,
                                ingredients = ingredients.split("\n").filter { it.isNotBlank() },
                                steps = steps.split("\n").filter { it.isNotBlank() },
                                imageUri = selectedImageUri ?: duplicateFrom?.imageUri
                            )
                            onRecipeAdded(recipe)
                        }
                    },
                    enabled = title.isNotBlank() && category.isNotBlank() &&
                            ingredients.isNotBlank() && steps.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (duplicateFrom != null) "Duplicate Recipe" else "Save Recipe")
                }
            }
        }
    }
}

@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All") + recipes.map { it.category }.distinct().sorted()
    val filteredRecipes = if (selectedCategory == "All") {
        recipes
    } else {
        recipes.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Filter by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
        }

        item {
            Text(
                text = "${filteredRecipes.size} recipes found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        items(filteredRecipes) { recipe ->
            RecipeCard(
                recipe = recipe,
                onClick = { onRecipeClick(recipe) }
            )
        }
    }
}

@Composable
fun FavoritesScreen(
    favoriteRecipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (favoriteRecipes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No favorite recipes yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Start adding recipes to your favorites!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "${favoriteRecipes.size} favorite recipes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(favoriteRecipes) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { onRecipeClick(recipe) }
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    selectedImageUri: Uri?,
    onImageSelect: () -> Unit,
    onImageClear: () -> Unit,
    onToggleFavorite: (Recipe) -> Unit,
    onDeleteRecipe: () -> Unit,
    onEditRecipe: (Recipe) -> Unit,
    onShareRecipe: (Recipe) -> Unit,
    onDuplicateRecipe: (Recipe) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf(recipe.title) }
    var editCategory by remember { mutableStateOf(recipe.category) }
    var editIngredients by remember { mutableStateOf(recipe.ingredients.joinToString("\n")) }
    var editSteps by remember { mutableStateOf(recipe.steps.joinToString("\n")) }

    val categories = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Beverage")
    var expandedCategory by remember { mutableStateOf(false) }

    var editImageUri by remember(recipe.id) { mutableStateOf<Uri?>(recipe.imageUri) }

    LaunchedEffect(selectedImageUri, isEditing) {
        if (isEditing && selectedImageUri != null) {
            editImageUri = selectedImageUri
        }
    }

    LaunchedEffect(isEditing) {
        if (!isEditing) {
            editImageUri = recipe.imageUri
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onToggleFavorite(recipe.copy(isFavorite = !recipe.isFavorite)) }
                ) {
                    Icon(
                        if (recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (recipe.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }

                IconButton(onClick = { onShareRecipe(recipe) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }

                IconButton(onClick = { onDuplicateRecipe(recipe) }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Duplicate")
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }

        if (!isEditing) {
            if (recipe.imageUri != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(recipe.imageUri)
                                    .crossfade(true)
                                    .build()
                            ),
                            contentDescription = "Recipe image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Badge { Text(recipe.category) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Added: ${recipe.dateCreated}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ingredients",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        recipe.ingredients.forEach { ingredient ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                Text(text = ingredient, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Instructions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        recipe.steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${index + 1}. ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Recipe Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    editCategory = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Recipe Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (editImageUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(editImageUri)
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = "Recipe image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onImageSelect() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change")
                                }

                                OutlinedButton(
                                    onClick = {
                                        editImageUri = null
                                        onImageClear()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.Red
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickableWithoutRipple { onImageSelect() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap to add image",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { onImageSelect() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Image")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = editIngredients,
                    onValueChange = { editIngredients = it },
                    label = { Text("Ingredients") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 6
                )
            }

            item {
                OutlinedTextField(
                    value = editSteps,
                    onValueChange = { editSteps = it },
                    label = { Text("Instructions") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 8
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isEditing = false
                            editTitle = recipe.title
                            editCategory = recipe.category
                            editIngredients = recipe.ingredients.joinToString("\n")
                            editSteps = recipe.steps.joinToString("\n")
                            editImageUri = recipe.imageUri
                            expandedCategory = false
                            onImageClear()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val updatedRecipe = recipe.copy(
                                title = editTitle,
                                category = editCategory,
                                ingredients = editIngredients.split("\n").filter { it.isNotBlank() },
                                steps = editSteps.split("\n").filter { it.isNotBlank() },
                                imageUri = editImageUri
                            )
                            onEditRecipe(updatedRecipe)
                            isEditing = false
                            expandedCategory = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete \"${recipe.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteRecipe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Función helper para clicks sin ripple
fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) {
        onClick()
    }
}

@Composable
fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Imagen de la receta (si existe)
            if (recipe.imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recipe.imageUri)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = "Recipe image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = recipe.category,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (recipe.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${recipe.ingredients.size} ingredients • ${recipe.steps.size} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}