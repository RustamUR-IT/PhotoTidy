package app.phototidy.core

/** A label from the on-device image model, e.g. ("Dog", 0.92). */
data class Label(val text: String, val confidence: Float)

enum class Category(val folder: String, val title: String) {
    PEOPLE("People", "People"),
    PETS("Pets & Animals", "Pets & Animals"),
    FOOD("Food & Drinks", "Food & Drinks"),
    NATURE("Nature", "Nature"),
    CITY("City & Travel", "City & Travel"),
    VEHICLES("Vehicles", "Vehicles"),
    DOCUMENTS("Documents", "Documents"),
    SCREENSHOTS("Screenshots", "Screenshots"),
    OTHER("Other", "Other");

    companion object {
        fun fromName(name: String?): Category =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Turns model labels + OCR text amount into one category.
 * Deliberately conservative: when unsure it answers OTHER, and OTHER is never moved.
 */
object Categorizer {

    private val keywords: Map<Category, Set<String>> = mapOf(
        Category.PEOPLE to setOf(
            "person", "selfie", "smile", "crowd", "team", "baby", "child", "bride", "groom",
            "beard", "party", "dance", "wedding", "gesture"
        ),
        Category.PETS to setOf(
            "dog", "cat", "pet", "bird", "horse", "fish", "animal", "butterfly", "insect",
            "duck", "rabbit", "hamster", "cattle", "sheep", "pony", "turtle", "reptile", "lizard",
            "bear", "squirrel", "goose", "swan", "owl", "parrot", "spider", "bee", "zoo"
        ),
        Category.FOOD to setOf(
            "food", "cuisine", "dessert", "cake", "pizza", "bread", "fruit", "vegetable",
            "drink", "coffee", "wine", "cocktail", "meal", "dish", "sushi", "cookie", "pasta",
            "burger", "hot dog", "sandwich", "salad", "soup", "breakfast", "lunch", "dinner",
            "ice cream", "chocolate", "beer", "juice", "tea", "bento", "noodle", "cheese",
            "meat", "seafood", "pie", "pancake", "cupcake", "donut", "baked goods", "tableware", "plate"
        ),
        Category.NATURE to setOf(
            "sky", "mountain", "beach", "lake", "river", "sea", "ocean", "forest", "flower",
            "plant", "tree", "sunset", "cloud", "snow", "waterfall", "garden", "grass", "leaf",
            "field", "desert", "rock", "cliff", "hill", "jungle", "moon", "star", "rainbow",
            "lightning", "petal", "fog", "glacier", "iceberg", "canyon", "volcano", "coast", "wave"
        ),
        Category.CITY to setOf(
            "building", "skyscraper", "bridge", "city", "tower", "monument", "church", "temple",
            "castle", "road", "street", "airplane", "aircraft", "airport", "train", "station",
            "statue", "fountain", "palace", "museum", "stadium", "landmark", "harbor", "pier",
            "ferris wheel", "architecture", "mosque", "cathedral"
        ),
        Category.VEHICLES to setOf(
            "car", "bicycle", "motorcycle", "truck", "bus", "boat", "ship", "vehicle", "wheel",
            "tire", "scooter", "van", "jet ski", "helicopter", "tractor"
        ),
        Category.DOCUMENTS to setOf(
            "paper", "receipt", "menu", "poster", "newspaper", "book", "text", "document",
            "handwriting", "whiteboard", "screenshot", "font", "letter"
        )
    )

    /** OCR characters above which an image counts as a document. */
    const val DOCUMENT_TEXT_CHARS = 120

    /** Labels that mean "there's a person/animal/food in focus" – OCR is skipped for these. */
    fun isClearlyVisualSubject(labels: List<Label>): Boolean {
        val strong = labels.filter { it.confidence >= 0.75f }.map { it.text.lowercase() }
        val visual = keywords.getValue(Category.PEOPLE) + keywords.getValue(Category.PETS) +
            keywords.getValue(Category.FOOD)
        return strong.any { it in visual }
    }

    fun categorize(labels: List<Label>, ocrChars: Int, isScreenshot: Boolean): Category {
        if (isScreenshot) return Category.SCREENSHOTS
        if (ocrChars >= DOCUMENT_TEXT_CHARS) return Category.DOCUMENTS

        val scores = HashMap<Category, Float>()
        for (label in labels) {
            if (label.confidence < 0.5f) continue
            val text = label.text.lowercase()
            for ((category, words) in keywords) {
                if (text in words) {
                    scores[category] = (scores[category] ?: 0f) + label.confidence
                }
            }
        }
        // A bit of text plus a document-ish label is still a document.
        if (ocrChars >= DOCUMENT_TEXT_CHARS / 3 && (scores[Category.DOCUMENTS] ?: 0f) > 0f) {
            return Category.DOCUMENTS
        }
        // Documents only via OCR evidence, not labels alone (avoids false hits on "Font" etc.)
        scores.remove(Category.DOCUMENTS)

        val best = scores.maxByOrNull { it.value } ?: return Category.OTHER
        return if (best.value >= 0.7f) best.key else Category.OTHER
    }
}
