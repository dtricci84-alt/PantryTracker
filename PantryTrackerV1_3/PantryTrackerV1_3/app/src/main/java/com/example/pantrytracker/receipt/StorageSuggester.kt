package com.example.pantrytracker.receipt

import com.example.pantrytracker.data.StorageLocation
import java.util.Locale

internal object StorageSuggester {
    fun classify(itemName: String): StorageLocation? {
        val value = itemName.lowercase(Locale.ROOT)

        val fridgeWords = listOf(
            "milk", "whole milk", "semi skimmed", "skimmed milk", "yogurt", "yoghurt", "cheese",
            "butter", "cream", "creme", "crème", "eggs", "egg", "ham", "sausage", "sausages",
            "bacon", "chicken", "beef", "pork", "meat", "fish", "salmon", "tofu", "hummus",
            "juice", "fresh juice", "deli", "cold cuts", "mozzarella", "feta", "quark",
            "milch", "joghurt", "käse", "kaese", "eier", "sahne", "wurst", "schinken",
            "kip", "melk", "yoghurt", "kaas", "eieren", "room", "vlees", "vis",
            "lait", "yaourt", "fromage", "oeuf", "oeufs", "beurre", "jambon", "viande", "poisson"
        )

        val countertopWords = listOf(
            "banana", "bananas", "apple", "apples", "orange", "oranges", "pear", "pears", "avocado",
            "avocados", "tomato", "tomatoes", "lemon", "lemons", "lime", "limes", "peach", "peaches",
            "nectarine", "melon", "pineapple", "onion", "onions", "garlic", "ginger", "potato", "potatoes",
            "bread", "baguette", "rolls", "bun", "buns", "croissant", "banane", "bananen", "apfel", "äpfel",
            "birne", "tomate", "tomaten", "zwiebel", "zwiebeln", "kartoffel", "kartoffeln", "brot",
            "banaan", "appel", "appels", "peer", "peren", "tomaat", "tomaten", "ui", "uien",
            "aardappel", "aardappelen", "brood", "bananes", "pomme", "pommes", "poire", "poires",
            "tomates", "oignon", "oignons", "pomme de terre", "pain"
        )

        val pantryWords = listOf(
            "soup", "canned", "can ", "beans", "pasta", "spaghetti", "noodles", "rice", "flour", "sugar",
            "cereal", "oats", "coffee", "tea", "peanut butter", "jam", "jelly", "oil", "vinegar", "sauce",
            "ketchup", "mustard", "crackers", "chips", "crisps", "cookies", "biscuits", "chocolate", "tuna can",
            "tomato paste", "stock cube", "broth", "lentils", "chickpeas", "couscous", "quinoa", "muesli",
            "suppe", "dose", "bohnen", "nudeln", "reis", "mehl", "zucker", "müsli", "kaffee", "tee",
            "erdnussbutter", "marmelade", "öl", "oel", "essig", "soße", "sosse", "kekse", "schokolade",
            "soep", "blik", "bonen", "rijst", "bloem", "suiker", "ontbijtgranen", "koffie", "thee",
            "pindakaas", "olie", "azijn", "saus", "koekjes", "chocolade", "soupe", "conserve",
            "haricots", "pâtes", "pates", "riz", "farine", "sucre", "céréales", "cereales", "café", "cafe",
            "thé", "the", "confiture", "huile", "vinaigre", "biscuits", "chocolat"
        )

        return when {
            fridgeWords.any { it in value } -> StorageLocation.FRIDGE
            countertopWords.any { it in value } -> StorageLocation.COUNTERTOP
            pantryWords.any { it in value } -> StorageLocation.PANTRY
            else -> null
        }
    }

    fun suggest(itemName: String, fallback: StorageLocation = StorageLocation.PANTRY): StorageLocation {
        return classify(itemName) ?: fallback
    }
}
