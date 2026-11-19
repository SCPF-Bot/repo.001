package com.mlbb.plus.ui.model

// Simplified models for MLBB Plus — replaces old domain.model.Hero
// Old Hero had: id, name, role, secondaryRole, lane, tier, winRate, pickRate, banRate, patchTrend,
// counteredBy, counters, synergies, recommendedSpells, coreItems, isOP, isToxicMechanic, flexLanes
// New: same fields, plain data classes, no Room/Hilt

data class Hero(
    val id: Int,
    val name: String,
    val role: String, // Tank / Fighter / Mage / Marksman / Assassin / Support
    val secondaryRole: String? = null,
    val lane: String = "Gold Lane", // Gold / Mid / EXP / Jungle / Roam
    val tier: String = "S", // S / A / B
    val winRate: Float = 0.55f,
    val pickRate: Float = 0.03f,
    val banRate: Float = 0.11f,
    val patchTrend: Float = 0f,
    val isOP: Boolean = false,
    val recommendedSpells: List<String> = emptyList(),
    val coreItems: List<BuildItem> = emptyList(),
    val counteredBy: List<Int> = emptyList(), // Heroes that BEAT this hero
    val counters: List<Int> = emptyList(),    // Heroes this hero BEATS
    val synergies: List<Int> = emptyList(),   // BEST PARTNERS
)

data class BuildItem(val name: String, val priority: Int)

data class Item(
    val id: Int,
    val name: String,
    val category: String, // Attack / Magic / Defense / Movement / Roam
    val tier: String = "S",
    val price: Int = 2180,
    val stats: List<String> = emptyList(), // e.g. "+35 Physical Attack"
    val description: String = "",
    val lore: String? = null, // null = show "No lore for this item"
    val buildPath: List<String> = emptyList(), // component names, e.g. Dagger + Vampire Mallet
    val counteredBy: List<Int> = emptyList(), // Items that COUNTER this item
    val counters: List<Int> = emptyList(),    // Items this item COUNTERS
    val synergiesHeroes: List<Int> = emptyList(), // Heroes GOOD WITH this item
    val worksAgainstHeroes: List<Int> = emptyList(), // Heroes this item WORKS BEST AGAINST
)

// Sample data — Hanabi is canonical, Demon Hunter Sword is canonical
object SampleData {
    val heroes = listOf(
        Hero(1, "Hanabi", "Marksman", lane="Gold Lane", tier="S", winRate=0.55f, pickRate=0.03f, banRate=0.11f, patchTrend=0f, isOP=true,
            recommendedSpells=listOf("Inspire","Flicker"),
            coreItems=listOf(BuildItem("Demon Hunter Sword",1), BuildItem("Wind of Nature",2), BuildItem("Malefic Roar",3)),
            counteredBy=listOf(2,3,4), counters=listOf(5,6,7), synergies=listOf(8,9,10)),
        Hero(2, "Alucard", "Fighter", lane="Jungle", tier="A", winRate=0.52f, pickRate=0.05f, banRate=0.02f),
        Hero(3, "Saber", "Assassin", lane="Jungle", tier="A", winRate=0.51f, pickRate=0.04f, banRate=0.05f),
        Hero(4, "Martis", "Fighter", lane="EXP Lane", tier="S", winRate=0.54f, pickRate=0.06f, banRate=0.08f),
        Hero(5, "Hayabusa", "Assassin", lane="Jungle", tier="S", winRate=0.53f, pickRate=0.07f, banRate=0.12f),
        Hero(6, "Kagura", "Mage", lane="Mid Lane", tier="S", winRate=0.54f, pickRate=0.06f, banRate=0.10f),
        Hero(7, "Khufra", "Tank", lane="Roam", tier="A", winRate=0.52f, pickRate=0.04f, banRate=0.06f),
        Hero(8, "Tigreal", "Tank", lane="Roam", tier="S", winRate=0.55f, pickRate=0.08f, banRate=0.09f),
        Hero(9, "Estes", "Support", lane="Roam", tier="A", winRate=0.53f, pickRate=0.03f, banRate=0.04f),
        Hero(10, "Eudora", "Mage", lane="Mid Lane", tier="A", winRate=0.51f, pickRate=0.05f, banRate=0.03f),
        Hero(11, "Masha", "Fighter", lane="EXP Lane", tier="S", winRate=0.56f, pickRate=0.05f, banRate=0.07f),
        Hero(12, "Marcel", "Mage", secondaryRole="Assassin", lane="Mid Lane", tier="S", winRate=0.56f, pickRate=0.05f, banRate=0.08f),
        Hero(13, "Gord", "Mage", lane="Mid Lane", tier="S", winRate=0.55f, pickRate=0.04f, banRate=0.06f),
        Hero(14, "Gloo", "Tank", lane="Roam", tier="S", winRate=0.54f, pickRate=0.06f, banRate=0.07f),
        Hero(15, "Belerick", "Tank", lane="Roam", tier="A", winRate=0.52f, pickRate=0.03f, banRate=0.04f),
        Hero(16, "Fredrinn", "Tank", secondaryRole="Fighter", lane="Jungle", tier="A", winRate=0.53f, pickRate=0.04f, banRate=0.05f),
        Hero(17, "Moskov", "Marksman", lane="Gold Lane", tier="S", winRate=0.54f, pickRate=0.06f, banRate=0.09f),
    )
    val heroesById = heroes.associateBy { it.id }

    val items = listOf(
        Item(101, "Demon Hunter Sword", "Attack", tier="S", price=2180,
            stats=listOf("+35 Physical Attack", "+25% Attack Speed", "+10% Lifesteal"),
            description="A feared blade forged from the bones of an ancient golem, designed to shred high-health foes. Its passive allows basic attacks to deal extra physical damage based on the target's current HP, while also granting lifesteal, making it essential for marksmen fighting tanky opponents.",
            lore="\"A blade with its core carved from the bones of an Iceland Golem, the power that resides within almost seems to impart the fierce history of those giants in the time before they were slain.\"",
            buildPath=listOf("Dagger", "Vampire Mallet"),
            counteredBy=listOf(102,103,104), counters=listOf(105,106,107),
            synergiesHeroes=listOf(1,11,17), worksAgainstHeroes=listOf(15,14,16)),
        Item(102, "Blade Armor", "Defense", tier="A", price=2200, stats=listOf("+90 Physical Defense", "+20% Crit Damage Reduction")),
        Item(103, "Dominance Ice", "Defense", tier="A", price=2010, stats=listOf("+70 Physical Defense", "+10% Cooldown Reduction")),
        Item(104, "Antique Cuirass", "Defense", tier="A", price=2170, stats=listOf("+92 Physical Defense", "+4 HP Regen")),
        Item(105, "Athena's Shield", "Defense", tier="S", price=2150, stats=listOf("+62 Magic Defense", "+1000 HP")),
        Item(106, "Immortality", "Defense", tier="S", price=2120, stats=listOf("+800 HP", "+40 Physical Defense")),
        Item(107, "Twilight Armor", "Defense", tier="A", price=2100, stats=listOf("+1200 HP", "+20 Physical Defense")),
        Item(108, "Blade of Despair", "Attack", tier="S", price=3010, stats=listOf("+160 Physical Attack")),
        Item(109, "Wind of Nature", "Attack", tier="S", price=1910, stats=listOf("+30 Physical Attack", "+20% Attack Speed")),
        Item(110, "Malefic Roar", "Attack", tier="S", price=2060, stats=listOf("+60 Physical Attack")),
    )
    val itemsById = items.associateBy { it.id }
}
