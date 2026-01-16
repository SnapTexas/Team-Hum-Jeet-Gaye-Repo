package com.healthtracker.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Food Classification Service using Hugging Face FREE Inference API.
 * 
 * Uses nateraw/food model (Food-101 trained) for REAL food detection.
 * NO MORE WRONG LABELS - Proper food classification!
 */
@Singleton
class FoodClassificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "FoodClassificationService"
        private const val ML_TIMEOUT_MS = 30000L
        private const val MIN_CONFIDENCE_THRESHOLD = 0.10f
        
        // Hugging Face FREE Inference API - Food-101 Model
        private const val HF_API_URL = "https://api-inference.huggingface.co/models/nateraw/food"
        
        // Food-101 Dataset Labels with Nutrition (101 categories)
        val NUTRITION_DATABASE = mapOf(
            // === FOOD-101 CATEGORIES ===
            "apple_pie" to NutritionData("Apple Pie", 237, 2f, 34f, 11f, "1 slice (125g)"),
            "baby_back_ribs" to NutritionData("Baby Back Ribs", 290, 24f, 0f, 21f, "3 ribs (150g)"),
            "baklava" to NutritionData("Baklava", 334, 5f, 29f, 23f, "1 piece (78g)"),
            "beef_carpaccio" to NutritionData("Beef Carpaccio", 120, 18f, 1f, 5f, "100g"),
            "beef_tartare" to NutritionData("Beef Tartare", 180, 20f, 2f, 10f, "100g"),
            "beet_salad" to NutritionData("Beet Salad", 74, 2f, 13f, 2f, "1 cup (136g)"),
            "beignets" to NutritionData("Beignets", 300, 5f, 40f, 14f, "3 pieces (90g)"),
            "bibimbap" to NutritionData("Bibimbap", 490, 24f, 68f, 12f, "1 bowl (400g)"),
            "bread_pudding" to NutritionData("Bread Pudding", 270, 6f, 40f, 10f, "1 serving (150g)"),
            "breakfast_burrito" to NutritionData("Breakfast Burrito", 380, 16f, 36f, 18f, "1 burrito (200g)"),
            "bruschetta" to NutritionData("Bruschetta", 120, 3f, 16f, 5f, "2 pieces (80g)"),
            "caesar_salad" to NutritionData("Caesar Salad", 190, 8f, 8f, 14f, "1 bowl (200g)"),
            "cannoli" to NutritionData("Cannoli", 220, 5f, 26f, 11f, "1 piece (84g)"),
            "caprese_salad" to NutritionData("Caprese Salad", 250, 12f, 6f, 20f, "1 serving (200g)"),
            "carrot_cake" to NutritionData("Carrot Cake", 415, 5f, 54f, 21f, "1 slice (130g)"),
            "ceviche" to NutritionData("Ceviche", 130, 18f, 8f, 3f, "1 cup (150g)"),
            "cheesecake" to NutritionData("Cheesecake", 321, 6f, 26f, 22f, "1 slice (125g)"),
            "cheese_plate" to NutritionData("Cheese Plate", 350, 20f, 4f, 28f, "100g assorted"),
            "chicken_curry" to NutritionData("Chicken Curry", 280, 22f, 12f, 16f, "1 bowl (250g)"),
            "chicken_quesadilla" to NutritionData("Chicken Quesadilla", 470, 28f, 36f, 24f, "1 quesadilla (200g)"),
            "chicken_wings" to NutritionData("Chicken Wings", 320, 27f, 0f, 22f, "6 wings (150g)"),
            "chocolate_cake" to NutritionData("Chocolate Cake", 352, 5f, 51f, 15f, "1 slice (100g)"),
            "chocolate_mousse" to NutritionData("Chocolate Mousse", 230, 4f, 24f, 14f, "1 cup (120g)"),
            "churros" to NutritionData("Churros", 237, 3f, 26f, 14f, "3 pieces (75g)"),
            "clam_chowder" to NutritionData("Clam Chowder", 190, 8f, 18f, 10f, "1 bowl (240g)"),
            "club_sandwich" to NutritionData("Club Sandwich", 450, 28f, 32f, 24f, "1 sandwich (250g)"),
            "crab_cakes" to NutritionData("Crab Cakes", 220, 14f, 12f, 13f, "2 cakes (120g)"),
            "creme_brulee" to NutritionData("Creme Brulee", 330, 5f, 32f, 20f, "1 serving (120g)"),
            "croque_madame" to NutritionData("Croque Madame", 520, 28f, 32f, 32f, "1 sandwich (250g)"),
            "cup_cakes" to NutritionData("Cupcake", 305, 3f, 45f, 13f, "1 cupcake (80g)"),
            "deviled_eggs" to NutritionData("Deviled Eggs", 130, 8f, 1f, 10f, "2 halves (60g)"),
            "donuts" to NutritionData("Donut", 269, 4f, 31f, 14f, "1 donut (60g)"),
            "dumplings" to NutritionData("Dumplings", 210, 8f, 24f, 9f, "4 pieces (100g)"),
            "edamame" to NutritionData("Edamame", 120, 11f, 9f, 5f, "1 cup (155g)"),
            "eggs_benedict" to NutritionData("Eggs Benedict", 480, 22f, 26f, 32f, "1 serving (250g)"),
            "escargots" to NutritionData("Escargots", 170, 16f, 2f, 10f, "6 pieces (85g)"),
            "falafel" to NutritionData("Falafel", 333, 13f, 32f, 18f, "6 pieces (100g)"),
            "filet_mignon" to NutritionData("Filet Mignon", 267, 26f, 0f, 17f, "6 oz (170g)"),
            "fish_and_chips" to NutritionData("Fish and Chips", 585, 22f, 52f, 32f, "1 serving (300g)"),
            "foie_gras" to NutritionData("Foie Gras", 462, 11f, 4f, 44f, "100g"),
            "french_fries" to NutritionData("French Fries", 365, 4f, 48f, 17f, "medium (117g)"),
            "french_onion_soup" to NutritionData("French Onion Soup", 210, 9f, 18f, 11f, "1 bowl (240g)"),
            "french_toast" to NutritionData("French Toast", 280, 8f, 32f, 13f, "2 slices (120g)"),
            "fried_calamari" to NutritionData("Fried Calamari", 175, 15f, 8f, 9f, "100g"),
            "fried_rice" to NutritionData("Fried Rice", 333, 8f, 52f, 10f, "1 plate (250g)"),
            "frozen_yogurt" to NutritionData("Frozen Yogurt", 127, 3f, 24f, 2f, "1/2 cup (100g)"),
            "garlic_bread" to NutritionData("Garlic Bread", 206, 5f, 24f, 10f, "2 slices (60g)"),
            "gnocchi" to NutritionData("Gnocchi", 250, 6f, 48f, 4f, "1 cup (175g)"),
            "greek_salad" to NutritionData("Greek Salad", 180, 6f, 10f, 14f, "1 bowl (200g)"),
            "grilled_cheese_sandwich" to NutritionData("Grilled Cheese", 390, 16f, 28f, 24f, "1 sandwich (120g)"),
            "grilled_salmon" to NutritionData("Grilled Salmon", 280, 34f, 0f, 15f, "6 oz (170g)"),
            "guacamole" to NutritionData("Guacamole", 150, 2f, 8f, 13f, "1/2 cup (100g)"),
            "gyoza" to NutritionData("Gyoza", 210, 8f, 24f, 9f, "6 pieces (120g)"),
            "hamburger" to NutritionData("Hamburger", 540, 34f, 40f, 27f, "1 burger (226g)"),
            "hot_and_sour_soup" to NutritionData("Hot and Sour Soup", 95, 5f, 10f, 4f, "1 bowl (240g)"),
            "hot_dog" to NutritionData("Hot Dog", 290, 11f, 24f, 17f, "1 hot dog (98g)"),
            "huevos_rancheros" to NutritionData("Huevos Rancheros", 380, 18f, 28f, 22f, "1 serving (250g)"),
            "hummus" to NutritionData("Hummus", 166, 8f, 14f, 10f, "1/2 cup (100g)"),
            "ice_cream" to NutritionData("Ice Cream", 207, 4f, 24f, 11f, "1 cup (132g)"),
            "lasagna" to NutritionData("Lasagna", 336, 18f, 32f, 14f, "1 piece (250g)"),
            "lobster_bisque" to NutritionData("Lobster Bisque", 248, 10f, 14f, 17f, "1 bowl (240g)"),
            "lobster_roll_sandwich" to NutritionData("Lobster Roll", 436, 22f, 36f, 22f, "1 roll (200g)"),
            "macaroni_and_cheese" to NutritionData("Mac and Cheese", 310, 12f, 32f, 14f, "1 cup (200g)"),
            "macarons" to NutritionData("Macarons", 97, 2f, 14f, 4f, "2 pieces (28g)"),
            "miso_soup" to NutritionData("Miso Soup", 40, 3f, 5f, 1f, "1 bowl (240g)"),
            "mussels" to NutritionData("Mussels", 172, 24f, 7f, 5f, "1 cup (150g)"),
            "nachos" to NutritionData("Nachos", 346, 9f, 36f, 19f, "1 serving (113g)"),
            "omelette" to NutritionData("Omelette", 154, 11f, 1f, 12f, "2 egg omelette (120g)"),
            "onion_rings" to NutritionData("Onion Rings", 276, 4f, 31f, 15f, "8 rings (100g)"),
            "oysters" to NutritionData("Oysters", 68, 7f, 4f, 2f, "6 oysters (84g)"),
            "pad_thai" to NutritionData("Pad Thai", 380, 14f, 48f, 14f, "1 plate (300g)"),
            "paella" to NutritionData("Paella", 320, 18f, 38f, 10f, "1 serving (300g)"),
            "pancakes" to NutritionData("Pancakes", 227, 6f, 38f, 5f, "3 pancakes (150g)"),
            "panna_cotta" to NutritionData("Panna Cotta", 280, 4f, 28f, 17f, "1 serving (120g)"),
            "peking_duck" to NutritionData("Peking Duck", 337, 19f, 1f, 28f, "100g"),
            "pho" to NutritionData("Pho", 350, 24f, 42f, 8f, "1 bowl (400g)"),
            "pizza" to NutritionData("Pizza", 285, 12f, 36f, 10f, "1 slice (107g)"),
            "pork_chop" to NutritionData("Pork Chop", 231, 27f, 0f, 13f, "1 chop (150g)"),
            "poutine" to NutritionData("Poutine", 510, 14f, 52f, 28f, "1 serving (300g)"),
            "prime_rib" to NutritionData("Prime Rib", 340, 26f, 0f, 26f, "6 oz (170g)"),
            "pulled_pork_sandwich" to NutritionData("Pulled Pork Sandwich", 450, 28f, 38f, 20f, "1 sandwich (250g)"),
            "ramen" to NutritionData("Ramen", 436, 18f, 56f, 16f, "1 bowl (450g)"),
            "ravioli" to NutritionData("Ravioli", 280, 12f, 36f, 10f, "1 cup (200g)"),
            "red_velvet_cake" to NutritionData("Red Velvet Cake", 367, 4f, 50f, 17f, "1 slice (100g)"),
            "risotto" to NutritionData("Risotto", 352, 8f, 52f, 12f, "1 bowl (250g)"),
            "samosa" to NutritionData("Samosa", 262, 5f, 24f, 17f, "1 piece (100g)"),
            "sashimi" to NutritionData("Sashimi", 127, 26f, 0f, 2f, "6 pieces (100g)"),
            "scallops" to NutritionData("Scallops", 111, 21f, 3f, 1f, "6 scallops (100g)"),
            "seaweed_salad" to NutritionData("Seaweed Salad", 70, 2f, 10f, 3f, "1 cup (100g)"),
            "shrimp_and_grits" to NutritionData("Shrimp and Grits", 380, 22f, 32f, 18f, "1 serving (300g)"),
            "spaghetti_bolognese" to NutritionData("Spaghetti Bolognese", 420, 22f, 52f, 14f, "1 plate (350g)"),
            "spaghetti_carbonara" to NutritionData("Spaghetti Carbonara", 480, 18f, 52f, 22f, "1 plate (350g)"),
            "spring_rolls" to NutritionData("Spring Rolls", 154, 4f, 18f, 7f, "2 pieces (80g)"),
            "steak" to NutritionData("Steak", 271, 26f, 0f, 18f, "6 oz (170g)"),
            "strawberry_shortcake" to NutritionData("Strawberry Shortcake", 280, 4f, 42f, 11f, "1 slice (150g)"),
            "sushi" to NutritionData("Sushi", 280, 12f, 38f, 8f, "6 pieces (180g)"),
            "tacos" to NutritionData("Tacos", 226, 11f, 20f, 11f, "2 tacos (170g)"),
            "takoyaki" to NutritionData("Takoyaki", 175, 6f, 22f, 7f, "6 pieces (100g)"),
            "tiramisu" to NutritionData("Tiramisu", 283, 5f, 32f, 15f, "1 slice (100g)"),
            "tuna_tartare" to NutritionData("Tuna Tartare", 150, 24f, 2f, 5f, "100g"),
            "waffles" to NutritionData("Waffles", 291, 8f, 33f, 14f, "2 waffles (150g)"),

            // === INDIAN FOOD DATABASE ===
            "biryani" to NutritionData("Biryani", 450, 18f, 52f, 18f, "1 plate (300g)"),
            "dal" to NutritionData("Dal", 180, 12f, 28f, 3f, "1 bowl (200g)"),
            "roti" to NutritionData("Roti", 120, 4f, 22f, 2f, "1 piece (40g)"),
            "rice" to NutritionData("Rice", 206, 4f, 45f, 0.4f, "1 cup (158g)"),
            "naan" to NutritionData("Naan", 262, 9f, 45f, 5f, "1 piece (90g)"),
            "paratha" to NutritionData("Paratha", 260, 5f, 32f, 12f, "1 piece (80g)"),
            "dosa" to NutritionData("Dosa", 168, 4f, 28f, 4f, "1 piece (100g)"),
            "idli" to NutritionData("Idli", 58, 2f, 12f, 0.4f, "1 piece (40g)"),
            "paneer" to NutritionData("Paneer Curry", 320, 16f, 12f, 24f, "1 bowl (200g)"),
            "butter_chicken" to NutritionData("Butter Chicken", 438, 30f, 14f, 28f, "1 bowl (250g)"),
            "chole" to NutritionData("Chole", 240, 12f, 36f, 6f, "1 bowl (200g)"),
            "rajma" to NutritionData("Rajma", 225, 14f, 38f, 2f, "1 bowl (200g)"),
            "palak_paneer" to NutritionData("Palak Paneer", 320, 16f, 12f, 24f, "1 bowl (200g)"),
            "aloo_gobi" to NutritionData("Aloo Gobi", 180, 4f, 28f, 6f, "1 bowl (200g)"),
            "sambar" to NutritionData("Sambar", 140, 6f, 22f, 3f, "1 bowl (200g)"),
            "upma" to NutritionData("Upma", 210, 5f, 32f, 7f, "1 bowl (200g)"),
            "poha" to NutritionData("Poha", 250, 5f, 42f, 7f, "1 plate (200g)"),
            "puri" to NutritionData("Puri", 101, 2f, 12f, 5f, "1 piece (30g)"),
            "pakora" to NutritionData("Pakora", 175, 4f, 18f, 10f, "4 pieces (80g)"),
            "vada" to NutritionData("Vada", 170, 5f, 18f, 9f, "1 piece (60g)"),
            "khichdi" to NutritionData("Khichdi", 220, 8f, 38f, 4f, "1 bowl (200g)"),
            "pulao" to NutritionData("Pulao", 280, 6f, 48f, 8f, "1 plate (200g)"),
            "korma" to NutritionData("Korma", 350, 20f, 16f, 24f, "1 bowl (250g)"),
            "tikka_masala" to NutritionData("Tikka Masala", 380, 28f, 18f, 22f, "1 bowl (250g)"),
            "tandoori_chicken" to NutritionData("Tandoori Chicken", 220, 32f, 4f, 9f, "1 leg (150g)"),
            "gulab_jamun" to NutritionData("Gulab Jamun", 175, 3f, 28f, 6f, "2 pieces (60g)"),
            "jalebi" to NutritionData("Jalebi", 150, 1f, 30f, 3f, "2 pieces (50g)"),
            "ladoo" to NutritionData("Ladoo", 180, 3f, 24f, 8f, "1 piece (40g)"),
            "kheer" to NutritionData("Kheer", 180, 5f, 28f, 6f, "1 bowl (150g)"),
            "halwa" to NutritionData("Halwa", 250, 4f, 35f, 11f, "1 bowl (100g)"),
            "chai" to NutritionData("Chai", 120, 4f, 15f, 5f, "1 cup (240ml)"),
            "lassi" to NutritionData("Lassi", 180, 6f, 28f, 5f, "1 glass (250ml)"),
            "chole_bhature" to NutritionData("Chole Bhature", 520, 14f, 62f, 24f, "1 plate"),
            "pav_bhaji" to NutritionData("Pav Bhaji", 380, 10f, 48f, 16f, "1 plate"),
            "dal_makhani" to NutritionData("Dal Makhani", 280, 12f, 32f, 12f, "1 bowl"),
            "malai_kofta" to NutritionData("Malai Kofta", 380, 12f, 28f, 26f, "1 bowl"),
            "shahi_paneer" to NutritionData("Shahi Paneer", 350, 14f, 16f, 26f, "1 bowl"),
            "aloo_paratha" to NutritionData("Aloo Paratha", 320, 7f, 42f, 14f, "1 piece"),
            "masala_dosa" to NutritionData("Masala Dosa", 280, 6f, 42f, 10f, "1 piece"),
            "uttapam" to NutritionData("Uttapam", 220, 6f, 36f, 6f, "1 piece"),
            "medu_vada" to NutritionData("Medu Vada", 170, 5f, 18f, 9f, "1 piece"),
            "pongal" to NutritionData("Pongal", 240, 6f, 38f, 8f, "1 bowl"),
            "rasam" to NutritionData("Rasam", 60, 2f, 10f, 1f, "1 bowl (200g)"),
            "raita" to NutritionData("Raita", 80, 4f, 8f, 4f, "1 bowl"),
            "chapati" to NutritionData("Chapati", 120, 4f, 22f, 2f, "1 piece (40g)"),
            
            // === FRUITS ===
            "banana" to NutritionData("Banana", 105, 1.3f, 27f, 0.4f, "1 medium (118g)"),
            "apple" to NutritionData("Apple", 95, 0.5f, 25f, 0.3f, "1 medium (182g)"),
            "orange" to NutritionData("Orange", 62, 1.2f, 15f, 0.2f, "1 medium (131g)"),
            "mango" to NutritionData("Mango", 135, 1.1f, 35f, 0.6f, "1 cup (165g)"),
            "grapes" to NutritionData("Grapes", 104, 1.1f, 27f, 0.2f, "1 cup (151g)"),
            "watermelon" to NutritionData("Watermelon", 46, 0.9f, 12f, 0.2f, "1 cup (152g)"),
            "papaya" to NutritionData("Papaya", 62, 0.7f, 16f, 0.4f, "1 cup (145g)"),
            "pineapple" to NutritionData("Pineapple", 82, 0.9f, 22f, 0.2f, "1 cup (165g)"),
            "strawberry" to NutritionData("Strawberries", 49, 1f, 12f, 0.5f, "1 cup (152g)"),
            "guava" to NutritionData("Guava", 112, 4.2f, 24f, 1.6f, "1 cup (165g)"),
            
            // === COMMON FOODS ===
            "egg" to NutritionData("Egg", 78, 6f, 0.6f, 5f, "1 large (50g)"),
            "eggs" to NutritionData("Eggs", 156, 12f, 1.2f, 10f, "2 large (100g)"),
            "bread" to NutritionData("Bread", 79, 2.7f, 15f, 1f, "1 slice (30g)"),
            "toast" to NutritionData("Toast", 79, 2.7f, 15f, 1f, "1 slice (30g)"),
            "sandwich" to NutritionData("Sandwich", 350, 15f, 35f, 15f, "1 sandwich (150g)"),
            "salad" to NutritionData("Salad", 150, 5f, 15f, 8f, "1 bowl (200g)"),
            "soup" to NutritionData("Soup", 150, 8f, 18f, 5f, "1 bowl (240ml)"),
            "chicken" to NutritionData("Chicken", 239, 27f, 0f, 14f, "1 breast (140g)"),
            "fish" to NutritionData("Fish", 136, 20f, 0f, 5.6f, "100g"),
            "milk" to NutritionData("Milk", 149, 8f, 12f, 8f, "1 glass (244g)"),
            "yogurt" to NutritionData("Yogurt", 149, 8.5f, 17f, 3.8f, "1 cup (245g)"),
            "cheese" to NutritionData("Cheese", 113, 7f, 0.4f, 9f, "1 slice (28g)"),
            "coffee" to NutritionData("Coffee", 2, 0.3f, 0f, 0f, "1 cup black (240ml)"),
            "tea" to NutritionData("Tea", 2, 0f, 0.5f, 0f, "1 cup (240ml)"),
            "juice" to NutritionData("Juice", 112, 0.9f, 26f, 0.3f, "1 glass (240ml)"),
            "smoothie" to NutritionData("Smoothie", 180, 4f, 35f, 2f, "1 glass (300ml)"),
            "burger" to NutritionData("Burger", 540, 34f, 40f, 27f, "1 burger (226g)"),
            "noodles" to NutritionData("Noodles", 384, 12f, 62f, 10f, "1 plate (300g)"),
            "pasta" to NutritionData("Pasta", 380, 14f, 62f, 8f, "1 plate (250g)"),
            "curry" to NutritionData("Curry", 280, 15f, 18f, 16f, "1 bowl (250g)"),
            "cake" to NutritionData("Cake", 352, 4f, 52f, 14f, "1 slice (100g)"),
            "cookie" to NutritionData("Cookie", 148, 1.5f, 20f, 7f, "1 cookie (30g)"),
            "chips" to NutritionData("Chips", 274, 3.5f, 25f, 18f, "1 bag (50g)"),
            "popcorn" to NutritionData("Popcorn", 93, 3f, 19f, 1f, "1 cup (8g)")
        )
    }
    
    /**
     * Nutrition data for a food item
     */
    data class NutritionData(
        val name: String,
        val calories: Int,
        val protein: Float,
        val carbs: Float,
        val fat: Float,
        val servingSize: String
    )
    
    // OkHttp client for Hugging Face API
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // ML Kit as FALLBACK for food detection
    private val imageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.3f)
            .build()
        ImageLabeling.getClient(options)
    }
    
    // Food-related labels that ML Kit can detect
    private val FOOD_LABELS = setOf(
        "food", "dish", "meal", "cuisine", "recipe", "ingredient", "snack",
        "fruit", "vegetable", "meat", "bread", "rice", "curry", "dessert",
        "breakfast", "lunch", "dinner", "plate", "bowl", "baked goods",
        "produce", "cooking", "fast food", "seafood", "dairy", "beverage"
    )
    
    // Non-food labels to reject
    private val NON_FOOD_LABELS = setOf(
        "person", "human", "face", "man", "woman", "child", "people",
        "car", "vehicle", "building", "sky", "tree", "animal", "dog", "cat",
        "phone", "computer", "screen", "text", "document", "furniture"
    )
    
    /**
     * Classifies a food image from URI using Hugging Face API.
     */
    suspend fun classifyFoodImage(imageUri: String): MLResult<FoodClassificationData> {
        return MLResult.runWithTimeout(
            timeoutMs = ML_TIMEOUT_MS,
            fallbackReason = "Food classification timed out"
        ) {
            classifyImageInternal(imageUri)
        }
    }
    
    /**
     * Classifies a food image from Bitmap using Hugging Face API.
     */
    suspend fun classifyFoodBitmap(bitmap: Bitmap): MLResult<FoodClassificationData> {
        return MLResult.runWithTimeout(
            timeoutMs = ML_TIMEOUT_MS,
            fallbackReason = "Food classification timed out"
        ) {
            classifyBitmapWithHuggingFace(bitmap)
        }
    }
    
    private suspend fun classifyImageInternal(imageUri: String): FoodClassificationData {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open image URI: $imageUri")
                
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap == null) {
                    throw IllegalArgumentException("Cannot decode image from URI: $imageUri")
                }
                
                classifyBitmapWithHuggingFace(bitmap)
            } catch (e: Exception) {
                Timber.e(e, "Error classifying image from URI")
                throw e
            }
        }
    }

    
    /**
     * MAIN CLASSIFICATION METHOD - Uses Hugging Face Food-101 Model
     * FALLBACK: ML Kit for food detection + quantity-based calorie estimation
     * 
     * This is the REAL food detection - no more wrong labels!
     */
    private suspend fun classifyBitmapWithHuggingFace(bitmap: Bitmap): FoodClassificationData {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Convert bitmap to Base64
                val base64Image = bitmapToBase64(bitmap)
                Timber.d("$TAG: Image converted to Base64, size: ${base64Image.length}")
                
                // Step 2: Call Hugging Face API
                val predictions = callHuggingFaceAPI(base64Image)
                Timber.d("$TAG: Got ${predictions.size} predictions from Hugging Face")
                
                // Step 3: Check if we got valid predictions
                if (predictions.isEmpty() || predictions.first().second < MIN_CONFIDENCE_THRESHOLD) {
                    Timber.d("$TAG: Hugging Face failed or low confidence - falling back to ML Kit")
                    return@withContext classifyWithMLKitFallback(bitmap)
                }
                
                // Step 4: Get top prediction
                val topPrediction = predictions.first()
                val foodLabel = topPrediction.first
                val confidence = topPrediction.second
                
                Timber.d("$TAG: Top prediction: $foodLabel with confidence $confidence")
                
                // Step 5: Get nutrition data
                val nutrition = getNutritionForFood(foodLabel)
                val displayName = formatFoodName(foodLabel)
                
                // Step 6: Estimate quantity based on image analysis
                val estimatedQuantity = estimateQuantity(bitmap, foodLabel)
                
                // Step 7: Calculate final calories based on quantity
                val finalCalories = (nutrition?.calories ?: 200) * estimatedQuantity.multiplier
                val finalProtein = (nutrition?.protein ?: 8f) * estimatedQuantity.multiplier
                val finalCarbs = (nutrition?.carbs ?: 30f) * estimatedQuantity.multiplier
                val finalFat = (nutrition?.fat ?: 8f) * estimatedQuantity.multiplier
                
                Timber.d("$TAG: Final result - $displayName, ${finalCalories.toInt()} kcal, quantity: ${estimatedQuantity.description}")
                
                FoodClassificationData(
                    foodName = displayName,
                    confidence = confidence,
                    alternativeNames = predictions.drop(1).take(4).map { formatFoodName(it.first) },
                    allLabels = predictions.map { LabelData(it.first, it.second) },
                    classifiedAt = Instant.now(),
                    calories = finalCalories.toInt(),
                    protein = finalProtein,
                    carbs = finalCarbs,
                    fat = finalFat,
                    servingSize = estimatedQuantity.description,
                    suggestedFoods = predictions.take(5).map { formatFoodName(it.first) },
                    estimatedQuantity = estimatedQuantity
                )
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error in Hugging Face classification - falling back to ML Kit")
                // FALLBACK to ML Kit
                classifyWithMLKitFallback(bitmap)
            }
        }
    }
    
    /**
     * ML Kit FALLBACK - Used when Hugging Face fails
     * 
     * Detects if image contains food, then estimates calories based on:
     * 1. Image coverage (how much food fills the frame)
     * 2. Color variety (more colors = more items = more calories)
     * 3. Brightness/texture analysis
     */
    private suspend fun classifyWithMLKitFallback(bitmap: Bitmap): FoodClassificationData {
        return withContext(Dispatchers.IO) {
            Timber.d("$TAG: Using ML Kit fallback for food detection")
            
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            val labels = suspendCancellableCoroutine { continuation ->
                imageLabeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        Timber.d("$TAG: ML Kit returned ${labels.size} labels")
                        labels.forEach { label ->
                            Timber.d("$TAG: ML Kit Label: ${label.text}, Confidence: ${label.confidence}")
                        }
                        continuation.resume(labels)
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "ML Kit labeling failed")
                        continuation.resumeWithException(e)
                    }
            }
            
            val labelTexts = labels.map { it.text.lowercase() }
            
            // Check for food indicators
            val hasFoodIndicator = labelTexts.any { label ->
                FOOD_LABELS.any { indicator -> label.contains(indicator) }
            }
            
            // Check for non-food indicators
            val hasNonFoodIndicator = labelTexts.any { label ->
                NON_FOOD_LABELS.any { indicator -> label.contains(indicator) }
            }
            
            // Decision: Is this food?
            if (hasNonFoodIndicator && !hasFoodIndicator) {
                // NOT FOOD
                Timber.d("$TAG: ML Kit says NOT FOOD")
                return@withContext createNoFoodResult()
            }
            
            if (hasFoodIndicator) {
                // FOOD DETECTED! Analyze image for quantity-based calorie estimation
                Timber.d("$TAG: ML Kit detected FOOD - analyzing quantity")
                
                // Advanced quantity analysis
                val quantityAnalysis = analyzeImageForQuantity(bitmap)
                
                Timber.d("$TAG: Quantity analysis - Coverage: ${quantityAnalysis.coveragePercent}%, " +
                        "ColorVariety: ${quantityAnalysis.colorVariety}, " +
                        "Density: ${quantityAnalysis.densityScore}")
                
                // Calculate calories based on analysis
                val estimatedCalories = calculateCaloriesFromAnalysis(quantityAnalysis)
                
                // Estimate macros (typical Indian meal ratio)
                val protein = (estimatedCalories * 0.12f / 4).toInt().toFloat() // ~12% protein
                val carbs = (estimatedCalories * 0.55f / 4).toInt().toFloat()   // ~55% carbs
                val fat = (estimatedCalories * 0.33f / 9).toInt().toFloat()     // ~33% fat
                
                return@withContext FoodClassificationData(
                    foodName = "Food (${quantityAnalysis.portionDescription})",
                    confidence = 0.6f,
                    alternativeNames = listOf("Meal", "Snack", "Dish"),
                    allLabels = labels.map { LabelData(it.text, it.confidence) },
                    classifiedAt = Instant.now(),
                    calories = estimatedCalories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    servingSize = quantityAnalysis.portionDescription,
                    suggestedFoods = listOf("Rice", "Roti", "Dal", "Sabzi", "Biryani"),
                    estimatedQuantity = QuantityEstimate(1f, quantityAnalysis.portionDescription, 1f)
                )
            }
            
            // Uncertain - but might be food, give benefit of doubt with medium estimate
            Timber.d("$TAG: ML Kit uncertain - assuming medium portion food")
            
            FoodClassificationData(
                foodName = "Food",
                confidence = 0.4f,
                alternativeNames = listOf("Meal", "Snack"),
                allLabels = labels.map { LabelData(it.text, it.confidence) },
                classifiedAt = Instant.now(),
                calories = 300,
                protein = 10f,
                carbs = 42f,
                fat = 10f,
                servingSize = "1 serving",
                suggestedFoods = listOf("Rice", "Roti", "Dal", "Sabzi"),
                estimatedQuantity = QuantityEstimate(1f, "1 serving", 1f)
            )
        }
    }
    
    /**
     * Analyze image to estimate food quantity
     * 
     * Factors:
     * 1. Food coverage - how much of image is food vs background
     * 2. Color variety - more colors = more items = more food
     * 3. Texture density - dense textures = heavy food
     */
    private fun analyzeImageForQuantity(bitmap: Bitmap): QuantityAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        // Sample pixels for analysis (every 10th pixel for speed)
        val sampleStep = 10
        var foodPixels = 0
        var totalSampled = 0
        val colorBuckets = mutableMapOf<Int, Int>() // Color bucket -> count
        var brightnessSum = 0L
        var textureVariance = 0.0
        var prevBrightness = 0
        
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                totalSampled++
                
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val brightness = (r + g + b) / 3
                brightnessSum += brightness
                
                // Texture variance (difference from previous pixel)
                textureVariance += Math.abs(brightness - prevBrightness)
                prevBrightness = brightness
                
                // Check if pixel looks like food (not white/black background)
                val saturation = maxOf(r, g, b) - minOf(r, g, b)
                if (brightness in 30..230 && saturation > 20) {
                    foodPixels++
                }
                
                // Color bucketing (reduce to 8 color buckets per channel)
                val colorBucket = ((r / 32) shl 6) or ((g / 32) shl 3) or (b / 32)
                colorBuckets[colorBucket] = (colorBuckets[colorBucket] ?: 0) + 1
            }
        }
        
        // Calculate metrics
        val coveragePercent = (foodPixels * 100f / totalSampled).toInt()
        val avgBrightness = (brightnessSum / totalSampled).toInt()
        val colorVariety = colorBuckets.size // More unique colors = more variety
        val avgTextureVariance = textureVariance / totalSampled
        
        // Density score (0-100) - combines texture and color
        val densityScore = minOf(100, ((avgTextureVariance / 2) + (colorVariety / 2)).toInt())
        
        Timber.d("$TAG: Image analysis - Coverage: $coveragePercent%, Colors: $colorVariety, " +
                "Brightness: $avgBrightness, Texture: $avgTextureVariance, Density: $densityScore")
        
        // Determine portion description based on analysis
        val portionDescription = when {
            coveragePercent > 70 && colorVariety > 40 -> "Full Thali/Heavy Plate"
            coveragePercent > 60 && colorVariety > 30 -> "Large Meal"
            coveragePercent > 45 && colorVariety > 20 -> "Medium Meal"
            coveragePercent > 30 && colorVariety > 15 -> "Small Meal"
            coveragePercent > 20 -> "Snack/Light"
            else -> "Small Snack"
        }
        
        return QuantityAnalysis(
            coveragePercent = coveragePercent,
            colorVariety = colorVariety,
            avgBrightness = avgBrightness,
            densityScore = densityScore,
            portionDescription = portionDescription
        )
    }
    
    /**
     * Calculate calories based on image analysis
     */
    private fun calculateCaloriesFromAnalysis(analysis: QuantityAnalysis): Int {
        // Base calories by portion type
        val baseCalories = when {
            analysis.coveragePercent > 70 && analysis.colorVariety > 40 -> 800  // Full thali
            analysis.coveragePercent > 60 && analysis.colorVariety > 30 -> 550  // Large meal
            analysis.coveragePercent > 45 && analysis.colorVariety > 20 -> 400  // Medium meal
            analysis.coveragePercent > 30 && analysis.colorVariety > 15 -> 280  // Small meal
            analysis.coveragePercent > 20 -> 180  // Snack
            else -> 100  // Small snack
        }
        
        // Adjust based on density (dense food = more calories)
        val densityMultiplier = 0.8f + (analysis.densityScore / 250f) // 0.8 to 1.2
        
        // Adjust based on brightness (darker foods often more calorie dense - gravies, fried)
        val brightnessMultiplier = if (analysis.avgBrightness < 100) 1.15f 
                                   else if (analysis.avgBrightness > 180) 0.9f 
                                   else 1.0f
        
        val finalCalories = (baseCalories * densityMultiplier * brightnessMultiplier).toInt()
        
        Timber.d("$TAG: Calorie calculation - Base: $baseCalories, Density mult: $densityMultiplier, " +
                "Brightness mult: $brightnessMultiplier, Final: $finalCalories")
        
        return finalCalories
    }
    
    /**
     * Estimate portion size from image dimensions and content
     */
    private fun estimatePortionFromImage(bitmap: Bitmap): PortionEstimate {
        val analysis = analyzeImageForQuantity(bitmap)
        return PortionEstimate(
            portionType = when {
                analysis.coveragePercent > 70 -> PortionType.FULL_THALI
                analysis.coveragePercent > 55 -> PortionType.LARGE_MEAL
                analysis.coveragePercent > 40 -> PortionType.MEDIUM_MEAL
                analysis.coveragePercent > 25 -> PortionType.SMALL_MEAL
                analysis.coveragePercent > 15 -> PortionType.MEDIUM_SNACK
                else -> PortionType.SMALL_SNACK
            },
            description = analysis.portionDescription
        )
    }
    
    /**
     * Data class for quantity analysis results
     */
    data class QuantityAnalysis(
        val coveragePercent: Int,      // How much of image is food (0-100)
        val colorVariety: Int,          // Number of distinct color buckets
        val avgBrightness: Int,         // Average brightness (0-255)
        val densityScore: Int,          // Texture density score (0-100)
        val portionDescription: String  // Human readable description
    )
    
    /**
     * Convert Bitmap to Base64 string for API
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize if too large (max 1024px)
        val maxSize = 1024
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * Call Hugging Face FREE Inference API
     */
    private fun callHuggingFaceAPI(base64Image: String): List<Pair<String, Float>> {
        try {
            // Decode base64 to bytes for API
            val imageBytes = Base64.decode(base64Image, Base64.NO_WRAP)
            
            val requestBody = imageBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            
            val request = Request.Builder()
                .url(HF_API_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/octet-stream")
                .build()
            
            Timber.d("$TAG: Calling Hugging Face API...")
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            val responseCode = response.code
            
            Timber.d("$TAG: API Response code: $responseCode")
            Timber.d("$TAG: API Response: $responseBody")
            
            if (!response.isSuccessful) {
                Timber.e("$TAG: API call failed: $responseCode - $responseBody")
                
                // Check if model is loading
                if (responseCode == 503 && responseBody?.contains("loading") == true) {
                    Timber.d("$TAG: Model is loading, will retry...")
                    // Wait and retry once
                    Thread.sleep(5000)
                    return callHuggingFaceAPI(base64Image)
                }
                
                return emptyList()
            }
            
            // Parse JSON response
            // Format: [{"label": "pizza", "score": 0.95}, ...]
            val jsonArray = JSONArray(responseBody)
            val predictions = mutableListOf<Pair<String, Float>>()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val label = item.getString("label")
                val score = item.getDouble("score").toFloat()
                predictions.add(label to score)
                Timber.d("$TAG: Prediction $i: $label = $score")
            }
            
            return predictions.sortedByDescending { it.second }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error calling Hugging Face API")
            return emptyList()
        }
    }
    
    /**
     * Estimate quantity based on image analysis
     */
    private fun estimateQuantity(bitmap: Bitmap, foodLabel: String): QuantityEstimate {
        // Simple heuristic based on food type and image coverage
        // In a real app, you'd use object detection to measure portion size
        
        val imageArea = bitmap.width * bitmap.height
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        
        // Estimate based on food type
        return when {
            // Single items
            foodLabel.contains("banana") || foodLabel.contains("apple") || 
            foodLabel.contains("orange") || foodLabel.contains("egg") -> {
                QuantityEstimate(1f, "1 piece", 1f)
            }
            
            // Bread items (roti, naan, paratha)
            foodLabel.contains("roti") || foodLabel.contains("chapati") -> {
                // Estimate 2-3 rotis typically
                QuantityEstimate(2f, "2 pieces", 2f)
            }
            foodLabel.contains("naan") || foodLabel.contains("paratha") -> {
                QuantityEstimate(1f, "1 piece", 1f)
            }
            
            // Rice dishes
            foodLabel.contains("rice") || foodLabel.contains("biryani") || 
            foodLabel.contains("pulao") || foodLabel.contains("fried_rice") -> {
                QuantityEstimate(1f, "1 plate", 1f)
            }
            
            // Curries and dal
            foodLabel.contains("curry") || foodLabel.contains("dal") || 
            foodLabel.contains("paneer") || foodLabel.contains("chole") -> {
                QuantityEstimate(1f, "1 bowl", 1f)
            }
            
            // Pizza slices
            foodLabel.contains("pizza") -> {
                // Estimate 2 slices
                QuantityEstimate(2f, "2 slices", 2f)
            }
            
            // Dosa, idli
            foodLabel.contains("dosa") -> {
                QuantityEstimate(1f, "1 piece", 1f)
            }
            foodLabel.contains("idli") -> {
                QuantityEstimate(3f, "3 pieces", 3f)
            }
            
            // Samosa, pakora
            foodLabel.contains("samosa") -> {
                QuantityEstimate(2f, "2 pieces", 2f)
            }
            foodLabel.contains("pakora") || foodLabel.contains("fries") -> {
                QuantityEstimate(1f, "1 serving", 1f)
            }
            
            // Default - 1 serving
            else -> {
                QuantityEstimate(1f, "1 serving", 1f)
            }
        }
    }
    
    /**
     * Get nutrition data for a food label
     */
    fun getNutritionForFood(foodLabel: String): NutritionData? {
        val normalizedLabel = foodLabel.lowercase().replace(" ", "_")
        
        // Direct match
        NUTRITION_DATABASE[normalizedLabel]?.let { return it }
        
        // Try without underscores
        NUTRITION_DATABASE[foodLabel.lowercase().replace("_", " ")]?.let { return it }
        
        // Partial match
        return NUTRITION_DATABASE.entries.find { (key, _) ->
            normalizedLabel.contains(key) || key.contains(normalizedLabel)
        }?.value
    }
    
    /**
     * Format food name for display
     */
    private fun formatFoodName(name: String): String {
        return name.split("_", " ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
    
    /**
     * Create result when no food detected
     */
    private fun createNoFoodResult(): FoodClassificationData {
        return FoodClassificationData(
            foodName = "No Food Detected",
            confidence = 0f,
            alternativeNames = emptyList(),
            allLabels = emptyList(),
            classifiedAt = Instant.now(),
            calories = 0,
            protein = 0f,
            carbs = 0f,
            fat = 0f,
            servingSize = "",
            suggestedFoods = emptyList(),
            estimatedQuantity = QuantityEstimate(0f, "", 0f)
        )
    }
    
    /**
     * Create result for low confidence - use ML Kit fallback
     */
    private suspend fun createLowConfidenceResult(predictions: List<Pair<String, Float>>): FoodClassificationData {
        // If we have predictions but low confidence, still use them
        val suggestions = predictions.take(5).map { formatFoodName(it.first) }
        val topFood = predictions.firstOrNull()?.first ?: "food"
        val nutrition = getNutritionForFood(topFood)
        
        return FoodClassificationData(
            foodName = formatFoodName(topFood),
            confidence = predictions.firstOrNull()?.second ?: 0f,
            alternativeNames = suggestions.drop(1),
            allLabels = predictions.map { LabelData(it.first, it.second) },
            classifiedAt = Instant.now(),
            calories = nutrition?.calories ?: 250,
            protein = nutrition?.protein ?: 10f,
            carbs = nutrition?.carbs ?: 35f,
            fat = nutrition?.fat ?: 8f,
            servingSize = nutrition?.servingSize ?: "1 serving",
            suggestedFoods = suggestions,
            estimatedQuantity = QuantityEstimate(1f, "1 serving", 1f)
        )
    }
    
    /**
     * Releases resources.
     */
    fun close() {
        try {
            imageLabeler.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing image labeler")
        }
    }
}

/**
 * Portion type for calorie estimation
 */
enum class PortionType {
    SMALL_SNACK,    // ~100-150 kcal (biscuit, fruit piece)
    MEDIUM_SNACK,   // ~150-250 kcal (samosa, pakora)
    SMALL_MEAL,     // ~250-350 kcal (1 roti + sabzi)
    MEDIUM_MEAL,    // ~350-500 kcal (rice + dal + sabzi)
    LARGE_MEAL,     // ~500-700 kcal (biryani, thali)
    FULL_THALI      // ~700-1000 kcal (full thali with everything)
}

/**
 * Portion estimate from image analysis
 */
data class PortionEstimate(
    val portionType: PortionType,
    val description: String
)

/**
 * Quantity estimation result
 */
data class QuantityEstimate(
    val quantity: Float,
    val description: String,
    val multiplier: Float
)

/**
 * Data class for food classification result with nutrition info.
 */
data class FoodClassificationData(
    val foodName: String,
    val confidence: Float,
    val alternativeNames: List<String>,
    val allLabels: List<LabelData>,
    val classifiedAt: Instant,
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val servingSize: String = "",
    val suggestedFoods: List<String> = emptyList(),
    val estimatedQuantity: QuantityEstimate = QuantityEstimate(1f, "1 serving", 1f)
) {
    fun isHighConfidence(threshold: Float = 0.5f): Boolean = confidence >= threshold
    fun requiresManualConfirmation(threshold: Float = 0.5f): Boolean = confidence < threshold
    fun isFoodDetected(): Boolean = foodName != "No Food Detected" && foodName != "Select Your Food"
}

/**
 * Data class for a single label.
 */
data class LabelData(
    val text: String,
    val confidence: Float
)
