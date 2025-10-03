package upv_dap.sep_dic_25.itiid_76129.pgu1_eq02
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import upv_dap.sep_dic_25.itiid_76129.pgu1_eq02.ui.theme.Z_U1_76129_E_02Theme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val prepTime: String,
    val servings: String,
    val imageUrl: String = "",
    val dateCreated: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val isFavorite: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Z_U1_76129_E_02Theme {
                RecipeBookApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val recipesFile = File(context.filesDir, "recipes.json")

    var recipes by remember { mutableStateOf(loadRecipes(recipesFile)) }
    var currentScreen by remember { mutableStateOf("home") }

    // Función para guardar recetas
    val saveRecipes = { recipeList: List<Recipe> ->
        try {
            val json = Json.encodeToString(recipeList)
            recipesFile.writeText(json)
            recipes = recipeList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when(currentScreen) {
                            "home" -> "Recipe Book"
                            "add" -> "Add Recipe"
                            "recipes" -> "All Recipes"
                            "favorites" -> "Favorites"
                            "detail" -> "Recipe Detail"
                            else -> "Recipe Book"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (currentScreen != "home") {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.Default.ArrowBack,
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
                    selected = currentScreen == "home",
                    onClick = {
                        currentScreen = "home"
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("Add") },
                    selected = currentScreen == "add",
                    onClick = {
                        currentScreen = "add"
                        navController.navigate("add")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Recipes") },
                    selected = currentScreen == "recipes",
                    onClick = {
                        currentScreen = "recipes"
                        navController.navigate("recipes")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Favorites") },
                    selected = currentScreen == "favorites",
                    onClick = {
                        currentScreen = "favorites"
                        navController.navigate("favorites")
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                currentScreen = "home"
                HomeScreen(
                    recipes = recipes,
                    onRecipeClick = { recipe ->
                        currentScreen = "detail"
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("add") {
                currentScreen = "add"
                AddRecipeScreen(
                    onRecipeAdded = { recipe ->
                        val updatedRecipes = recipes + recipe
                        saveRecipes(updatedRecipes)
                        navController.navigateUp()
                    },
                    onCancel = { navController.navigateUp() }
                )
            }

            composable("recipes") {
                currentScreen = "recipes"
                RecipeListScreen(
                    recipes = recipes,
                    onRecipeClick = { recipe ->
                        currentScreen = "detail"
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("favorites") {
                currentScreen = "favorites"
                FavoritesScreen(
                    favoriteRecipes = recipes.filter { it.isFavorite },
                    onRecipeClick = { recipe ->
                        currentScreen = "detail"
                        navController.navigate("detail/${recipe.id}")
                    }
                )
            }

            composable("detail/{recipeId}") { backStackEntry ->
                currentScreen = "detail"
                val recipeId = backStackEntry.arguments?.getString("recipeId")
                val recipe = recipes.find { it.id == recipeId }

                if (recipe != null) {
                    RecipeDetailScreen(
                        recipe = recipe,
                        onToggleFavorite = { updatedRecipe ->
                            val updatedRecipes = recipes.map {
                                if (it.id == updatedRecipe.id) updatedRecipe else it
                            }
                            saveRecipes(updatedRecipes)
                        },
                        onDeleteRecipe = {
                            val updatedRecipes = recipes.filter { it.id != recipe.id }
                            saveRecipes(updatedRecipes)
                            navController.navigateUp()
                        },
                        onEditRecipe = { updatedRecipe ->
                            val updatedRecipes = recipes.map {
                                if (it.id == updatedRecipe.id) updatedRecipe else it
                            }
                            saveRecipes(updatedRecipes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    recipes: List<Recipe>,
    onRecipeClick: (Recipe) -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    val recentRecipes = recipes.sortedByDescending { it.dateCreated }.take(5)
    val favoriteRecipes = recipes.filter { it.isFavorite }.take(3)
    val filteredRecipes = recipes.filter {
        it.title.contains(searchText, ignoreCase = true) ||
                it.category.contains(searchText, ignoreCase = true) ||
                it.ingredients.any { ingredient -> ingredient.contains(searchText, ignoreCase = true) }
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
                        items(recentRecipes) { recipe ->
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
                        items(favoriteRecipes) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onRecipeClick(recipe) },
                                modifier = Modifier.width(200.dp)
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

            items(filteredRecipes) { recipe ->
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
    onRecipeAdded: (Recipe) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var prepTime by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("") }

    val categories = listOf("Breakfast", "Lunch", "Dinner", "Dessert", "Snack", "Beverage")
    var expandedCategory by remember { mutableStateOf(false) }

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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prepTime,
                    onValueChange = { prepTime = it },
                    label = { Text("Prep Time") },
                    placeholder = { Text("30 min") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Servings") },
                    placeholder = { Text("4") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
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
                                prepTime = prepTime.ifBlank { "Not specified" },
                                servings = servings.ifBlank { "Not specified" }
                            )
                            onRecipeAdded(recipe)
                        }
                    },
                    enabled = title.isNotBlank() && category.isNotBlank() &&
                            ingredients.isNotBlank() && steps.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Recipe")
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
    onToggleFavorite: (Recipe) -> Unit,
    onDeleteRecipe: () -> Unit,
    onEditRecipe: (Recipe) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf(recipe.title) }
    var editCategory by remember { mutableStateOf(recipe.category) }
    var editIngredients by remember { mutableStateOf(recipe.ingredients.joinToString("\n")) }
    var editSteps by remember { mutableStateOf(recipe.steps.joinToString("\n")) }
    var editPrepTime by remember { mutableStateOf(recipe.prepTime) }
    var editServings by remember { mutableStateOf(recipe.servings) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Action buttons
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

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }

        if (!isEditing) {
            // Display mode
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Badge { Text(recipe.category) }
                            Text(
                                text = "Prep: ${recipe.prepTime}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Serves: ${recipe.servings}",
                                style = MaterialTheme.typography.bodyMedium
                            )
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
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = ingredient,
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Edit mode
            item {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Recipe Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = editCategory,
                    onValueChange = { editCategory = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editPrepTime,
                        onValueChange = { editPrepTime = it },
                        label = { Text("Prep Time") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editServings,
                        onValueChange = { editServings = it },
                        label = { Text("Servings") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = editIngredients,
                    onValueChange = { editIngredients = it },
                    label = { Text("Ingredients") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 6
                )
            }

            item {
                OutlinedTextField(
                    value = editSteps,
                    onValueChange = { editSteps = it },
                    label = { Text("Instructions") },
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
                        onClick = {
                            isEditing = false
                            // Reset values
                            editTitle = recipe.title
                            editCategory = recipe.category
                            editIngredients = recipe.ingredients.joinToString("\n")
                            editSteps = recipe.steps.joinToString("\n")
                            editPrepTime = recipe.prepTime
                            editServings = recipe.servings
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
                                prepTime = editPrepTime,
                                servings = editServings
                            )
                            onEditRecipe(updatedRecipe)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recipe.prepTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recipe.servings,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${recipe.ingredients.size} ingredients • ${recipe.steps.size} steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// Función para cargar recetas desde archivo
fun loadRecipes(file: File): List<Recipe> {
    return try {
        if (file.exists()) {
            val json = file.readText()
            Json.decodeFromString<List<Recipe>>(json)
        } else {
            // Recetas de ejemplo
            listOf(
                Recipe(
                    title = "Spaghetti Carbonara",
                    category = "Dinner",
                    ingredients = listOf(
                        "400g spaghetti",
                        "200g pancetta or bacon",
                        "4 large eggs",
                        "100g Parmesan cheese, grated",
                        "2 cloves garlic",
                        "Salt and black pepper",
                        "Fresh parsley"
                    ),
                    steps = listOf(
                        "Cook spaghetti according to package instructions until al dente",
                        "In a large pan, cook pancetta until crispy",
                        "Beat eggs with Parmesan cheese, salt, and pepper",
                        "Drain pasta, reserving 1 cup pasta water",
                        "Add hot pasta to the pan with pancetta",
                        "Remove from heat and quickly stir in egg mixture",
                        "Add pasta water gradually until creamy",
                        "Garnish with parsley and serve immediately"
                    ),
                    prepTime = "20 minutes",
                    servings = "4",
                    isFavorite = true
                ),
                Recipe(
                    title = "Chocolate Chip Cookies",
                    category = "Dessert",
                    ingredients = listOf(
                        "2¼ cups all-purpose flour",
                        "1 tsp baking soda",
                        "1 tsp salt",
                        "1 cup butter, softened",
                        "¾ cup granulated sugar",
                        "¾ cup brown sugar",
                        "2 large eggs",
                        "2 tsp vanilla extract",
                        "2 cups chocolate chips"
                    ),
                    steps = listOf(
                        "Preheat oven to 375°F (190°C)",
                        "Mix flour, baking soda, and salt in a bowl",
                        "Cream butter and sugars until fluffy",
                        "Beat in eggs and vanilla",
                        "Gradually blend in flour mixture",
                        "Stir in chocolate chips",
                        "Drop rounded tablespoons onto ungreased cookie sheets",
                        "Bake for 9-11 minutes until golden brown",
                        "Cool on baking sheet for 2 minutes before removing"
                    ),
                    prepTime = "15 minutes",
                    servings = "36 cookies"
                ),
                Recipe(
                    title = "Greek Salad",
                    category = "Lunch",
                    ingredients = listOf(
                        "2 large cucumbers, diced",
                        "4 tomatoes, cut into wedges",
                        "1 red onion, thinly sliced",
                        "200g feta cheese, cubed",
                        "½ cup Kalamata olives",
                        "¼ cup olive oil",
                        "2 tbsp red wine vinegar",
                        "1 tsp dried oregano",
                        "Salt and pepper to taste"
                    ),
                    steps = listOf(
                        "Combine cucumbers, tomatoes, and red onion in a large bowl",
                        "Add feta cheese and olives",
                        "Whisk together olive oil, vinegar, and oregano",
                        "Pour dressing over salad and toss gently",
                        "Season with salt and pepper",
                        "Let sit for 10 minutes before serving"
                    ),
                    prepTime = "15 minutes",
                    servings = "4",
                    isFavorite = true
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
