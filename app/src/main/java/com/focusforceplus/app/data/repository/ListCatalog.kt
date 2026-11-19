package com.focusforceplus.app.data.repository

/**
 * Common things people put on a shopping list, each with the aisle it is found in.
 * Typing "milk" sorts it under "Dairy & eggs" without anyone having to say so, and
 * the first letters of "tomatoes" are enough to offer it.
 *
 * Names are in English and German, because a shopping list is written in the
 * language its owner thinks in, whatever language the app speaks. Where a user
 * puts an item somewhere else, that is remembered per list and wins over this.
 */
object ListCatalog {

    const val PRODUCE = "Fruit & vegetables"
    const val BAKERY = "Bakery"
    const val DAIRY = "Dairy & eggs"
    const val MEAT = "Meat & fish"
    const val FROZEN = "Frozen"
    const val PANTRY = "Pantry"
    const val DRINKS = "Drinks"
    const val SNACKS = "Snacks & sweets"
    const val HOUSEHOLD = "Household"
    const val CARE = "Personal care"
    const val BABY_PETS = "Baby & pets"

    /** Aisles in the order of a common way through a supermarket; a new shopping list starts with it. */
    val AISLES = listOf(PRODUCE, BAKERY, DAIRY, MEAT, FROZEN, PANTRY, DRINKS, SNACKS, HOUSEHOLD, CARE, BABY_PETS)

    private val RAW: Map<String, String> = mapOf(
        PRODUCE to "apples, bananas, oranges, lemons, limes, grapes, strawberries, blueberries, raspberries, pears, " +
            "peaches, plums, cherries, melon, watermelon, pineapple, mango, kiwi, avocado, tomatoes, cucumber, lettuce, " +
            "salad, spinach, carrots, potatoes, sweet potatoes, onions, garlic, ginger, peppers, bell pepper, chili, " +
            "zucchini, courgette, eggplant, aubergine, broccoli, cauliflower, cabbage, mushrooms, corn, peas, " +
            "green beans, celery, leek, spring onions, herbs, basil, parsley, coriander, cilantro, mint, dill, chives, " +
            "radish, beetroot, pumpkin, asparagus, fruit, vegetables, " +
            "äpfel, apfel, bananen, banane, orangen, zitronen, zitrone, limetten, trauben, weintrauben, erdbeeren, " +
            "heidelbeeren, blaubeeren, himbeeren, birnen, pfirsiche, pflaumen, kirschen, melone, wassermelone, ananas, " +
            "tomaten, tomate, gurke, gurken, salat, kopfsalat, eisbergsalat, rucola, spinat, karotten, möhren, " +
            "kartoffeln, süßkartoffeln, zwiebeln, zwiebel, knoblauch, ingwer, paprika, brokkoli, blumenkohl, kohl, " +
            "weißkohl, rotkohl, pilze, champignons, mais, erbsen, sellerie, lauch, porree, frühlingszwiebeln, kräuter, " +
            "basilikum, petersilie, koriander, minze, schnittlauch, radieschen, rote bete, kürbis, spargel, obst, gemüse",
        BAKERY to "bread, toast, rolls, buns, bagels, baguette, croissants, tortillas, wraps, pita, cake, muffins, " +
            "brot, toastbrot, brötchen, semmeln, brezeln, brezel, kuchen, vollkornbrot, knäckebrot",
        DAIRY to "milk, oat milk, almond milk, soy milk, butter, margarine, cheese, cream cheese, mozzarella, parmesan, " +
            "feta, yogurt, yoghurt, cream, sour cream, eggs, quark, cottage cheese, " +
            "milch, hafermilch, mandelmilch, sojamilch, käse, frischkäse, gouda, joghurt, sahne, schlagsahne, " +
            "saure sahne, schmand, crème fraîche, eier, hüttenkäse, skyr, buttermilch",
        MEAT to "chicken, chicken breast, beef, ground beef, minced meat, pork, bacon, ham, sausages, salami, turkey, " +
            "steak, fish, salmon, tuna, shrimp, prawns, tofu, " +
            "hähnchen, hähnchenbrust, hühnchen, rindfleisch, hackfleisch, schweinefleisch, speck, schinken, würstchen, " +
            "wurst, pute, putenbrust, fisch, lachs, thunfisch, garnelen, aufschnitt",
        FROZEN to "ice cream, frozen pizza, frozen vegetables, frozen berries, fish fingers, fries, ice cubes, " +
            "eis, tiefkühlpizza, pizza, tiefkühlgemüse, fischstäbchen, pommes, eiswürfel, tiefkühlbeeren",
        PANTRY to "pasta, spaghetti, noodles, rice, flour, sugar, salt, pepper, oil, olive oil, vinegar, tomato sauce, " +
            "canned tomatoes, tomato paste, ketchup, mustard, mayonnaise, soy sauce, honey, jam, peanut butter, nutella, " +
            "cereal, oats, oatmeal, muesli, lentils, chickpeas, beans, stock, broth, spices, baking powder, yeast, " +
            "vanilla, cocoa, nuts, almonds, raisins, breadcrumbs, couscous, quinoa, soup, " +
            "nudeln, reis, mehl, zucker, salz, pfeffer, öl, olivenöl, essig, tomatensauce, tomatensoße, " +
            "passierte tomaten, dosentomaten, tomatenmark, senf, sojasauce, honig, marmelade, erdnussbutter, müsli, " +
            "haferflocken, cornflakes, linsen, kichererbsen, bohnen, brühe, gewürze, backpulver, hefe, vanillezucker, " +
            "kakao, nüsse, mandeln, rosinen, paniermehl, semmelbrösel, suppe",
        DRINKS to "water, sparkling water, juice, orange juice, apple juice, cola, soda, lemonade, beer, wine, " +
            "red wine, white wine, coffee, tea, energy drink, " +
            "wasser, mineralwasser, sprudel, saft, orangensaft, apfelsaft, apfelschorle, limo, limonade, bier, wein, " +
            "rotwein, weißwein, kaffee, tee, eistee",
        SNACKS to "chips, crisps, chocolate, cookies, biscuits, candy, sweets, gummy bears, popcorn, crackers, " +
            "pretzels, chewing gum, " +
            "schokolade, kekse, süßigkeiten, gummibärchen, cracker, salzstangen, kaugummi, bonbons, müsliriegel",
        HOUSEHOLD to "toilet paper, paper towels, kitchen roll, tissues, dish soap, washing-up liquid, dishwasher tabs, " +
            "laundry detergent, fabric softener, trash bags, bin bags, sponges, cleaner, all-purpose cleaner, " +
            "aluminium foil, cling film, baking paper, batteries, light bulbs, candles, matches, napkins, freezer bags, " +
            "toilettenpapier, klopapier, küchenrolle, küchenpapier, taschentücher, spülmittel, spülmaschinentabs, " +
            "waschmittel, weichspüler, müllbeutel, müllsäcke, schwämme, reiniger, allzweckreiniger, alufolie, " +
            "frischhaltefolie, backpapier, batterien, glühbirnen, kerzen, streichhölzer, servietten, gefrierbeutel",
        CARE to "shampoo, conditioner, shower gel, soap, toothpaste, toothbrush, deodorant, razor, razor blades, " +
            "shaving cream, lotion, sunscreen, tampons, pads, cotton pads, hand cream, face cream, floss, mouthwash, " +
            "plasters, band-aids, painkillers, vitamins, " +
            "spülung, duschgel, seife, zahnpasta, zahnbürste, deo, rasierer, rasierklingen, rasierschaum, bodylotion, " +
            "sonnencreme, binden, wattepads, handcreme, gesichtscreme, zahnseide, mundspülung, pflaster, " +
            "schmerztabletten, vitamine",
        BABY_PETS to "diapers, nappies, wipes, baby food, cat food, dog food, cat litter, " +
            "windeln, feuchttücher, babynahrung, katzenfutter, hundefutter, katzenstreu",
    )

    /** Lower-cased name to aisle. */
    private val aisleByName: Map<String, String> = buildMap {
        RAW.forEach { (aisle, names) ->
            names.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { putIfAbsent(it, aisle) }
        }
    }

    private val names: List<String> = aisleByName.keys.sorted()

    /**
     * The aisle for something typed into a shopping list, or null if it is not
     * known. Besides the name itself this tries it without a plural ending, and the
     * last word on its own, so "organic bananas" and "Vollmilch" still find a home
     * where "bananas" and "milch" would.
     */
    fun aisleFor(text: String): String? {
        val key = text.trim().lowercase()
        if (key.isEmpty()) return null
        lookup(key)?.let { return it }
        val words = key.split(' ', '-').filter { it.isNotBlank() }
        // The thing itself is usually named last: "whole milk", "Bio-Eier".
        for (word in words.asReversed()) lookup(word)?.let { return it }
        // German builds one word of it: "Vollmilch", "Bauernbrot".
        if (words.size == 1 && key.length >= 6) {
            names.filter { it.length >= 4 && key.endsWith(it) }.maxByOrNull { it.length }?.let { return aisleByName[it] }
        }
        return null
    }

    private fun lookup(key: String): String? {
        aisleByName[key]?.let { return it }
        for (ending in PLURALS) {
            if (key.length > ending.length + 2 && key.endsWith(ending)) {
                aisleByName[key.dropLast(ending.length)]?.let { return it }
            }
        }
        // And the other way round: "tomato" finds "tomatoes", "egg" finds "eggs".
        for (ending in PLURALS) aisleByName[key + ending]?.let { return it }
        return null
    }

    private val PLURALS = listOf("es", "s", "en", "n")

    /**
     * Names that begin with what is being typed, shortest first, written with a
     * capital the way one would put them on a list. Nothing below two letters:
     * one letter matches half the shop.
     */
    fun suggest(query: String, limit: Int = 6): List<String> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return names.asSequence()
            .filter { it.startsWith(q) && it != q }
            .sortedWith(compareBy({ it.length }, { it }))
            .take(limit)
            .map { it.replaceFirstChar { c -> c.titlecase() } }
            .toList()
    }
}
