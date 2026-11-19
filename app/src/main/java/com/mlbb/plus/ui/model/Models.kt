package com.mlbb.plus.ui.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ============================================================
// plus/ui/model/Models.kt
// GitHub path: app/src/main/java/com/mlbb/plus/ui/model/Models.kt
// ------------------------------------------------------------
// THE ONLY file in plus/ui/model/
// ------------------------------------------------------------
// READS:
//   assets/heroes.json      -> 134 heroes
//   assets/items.json       -> 40 items
//   assets/patch_notes.json -> 2.2.23 / 2.2.32 / Nov 01 2.2.30
//   assets/images/heroes/{id}.webp  -> e.g. images/heroes/60.webp (Hanabi), 132.webp (Marcel)
//   assets/images/items/{id}.webp   -> e.g. images/items/1002.webp (Demon Hunter Sword)
// NO hardcoded heroes/items here. Everything comes from JSON.
// ============================================================

// ---------- HERO ----------

data class HeroSkill(
    val name: String = "",
    val type: String = "", // Passive / Skill 1 / Skill 2 / Ultimate / Skill 3 (Dori)
    val description: String = ""
)

data class EmblemInfo(
    val emblem: String = "",
    val talents: List<String> = emptyList()
)

data class HeroStats(
    val hp: Int = 2500,
    val hpGrowth: Int = 150,
    val attack: Int = 110,
    val attackGrowth: Int = 9,
    val defense: Int = 18,
    val magicDefense: Int = 15,
    val attackSpeed: Double = 0.82,
    val movementSpeed: Int = 260
)

data class Hero(
    val id: Int,
    val name: String,
    val slug: String = "",
    val title: String = "",
    val role: String = "", // Tank / Fighter / Assassin / Mage / Marksman / Support
    val secondaryRole: String? = null,
    val lane: String = "Gold Lane",
    val lanes: List<String> = emptyList(),
    val tier: String = "A", // S / A / B
    val difficulty: Int = 5,
    val specialty: List<String> = emptyList(),
    val winRate: Float = 0.50f,
    val pickRate: Float = 0.03f,
    val banRate: Float = 0.03f,
    val patchTrend: Float = 0f,
    val isOP: Boolean = false,
    val isMeta: Boolean = false,
    val releaseYear: Int = 2016,
    val image: String = "", // images/heroes/{id}.webp
    val skills: List<HeroSkill> = emptyList(),
    val recommendedSpells: List<String> = emptyList(),
    val recommendedEmblem: EmblemInfo = EmblemInfo(),
    val coreItems: List<BuildItem> = emptyList(), // itemIds from items.json
    val counteredBy: List<Int> = emptyList(), // hero IDs that BEAT this hero
    val counters: List<Int> = emptyList(),    // hero IDs this hero BEATS
    val synergies: List<Int> = emptyList(),   // BEST PARTNERS (hero IDs)
    val countersReason: String = "",
    val bestPartners: List<String> = emptyList(),
    val lore: String = "",
    val stats: HeroStats = HeroStats(),
    val tags: List<String> = emptyList(),
    val patchVersion: String = "2.2.23",
    val status: String = "live" // live | advanced-server-only (Dori=134)
) {
    val isLive: Boolean get() = status != "advanced-server-only"
    val displayRole: String get() = if (secondaryRole != null) "$role / $secondaryRole" else role
}

data class BuildItem(
    val itemId: Int = 0, // items.json ID, e.g. 1002 = Demon Hunter Sword
    val name: String = "",
    val priority: Int = 1
)

// ---------- ITEM ----------

data class ItemStat(val stat: String = "", val value: String = "")

data class Item(
    val id: Int,
    val name: String,
    val slug: String = "",
    val category: String = "", // Attack / Magic / Defense / Movement / Roam / Jungle
    val tier: String = "A",
    val price: Int = 0,
    val sellPrice: Int = 0,
    val stats: List<String> = emptyList(), // "+35 Physical Attack"
    val statsDetailed: List<ItemStat> = emptyList(),
    val passiveName: String? = null,
    val passiveDescription: String? = null,
    val activeName: String? = null,
    val activeDescription: String? = null,
    val description: String = "",
    val lore: String? = null,
    val buildPath: List<String> = emptyList(),
    val buildsInto: List<Int> = emptyList(),
    val counteredBy: List<Int> = emptyList(), // item IDs that COUNTER this item
    val counters: List<Int> = emptyList(),    // item IDs this item COUNTERS
    val synergiesHeroes: List<Int> = emptyList(),
    val worksAgainstHeroes: List<Int> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val image: String = "" // images/items/{id}.webp
)

// ---------- PATCH NOTES ----------

data class PatchHeroChange(
    val heroId: Int = 0,
    val heroName: String = "",
    val type: String = "", // revamp / buff / nerf / adjust
    val title: String = "",
    val changes: List<String> = emptyList(),
    val skillsAffected: List<String> = emptyList(),
    val impact: String = "",
    val tierChange: String = "",
    val isMeta: Boolean = false
)

data class PatchItemChange(
    val itemId: Int = 0,
    val itemName: String = "",
    val type: String = "",
    val changes: List<String> = emptyList()
)

data class PatchData(
    val version: String = "",
    val date: String = "",
    val server: String = "", // Original Server / Advanced Server
    val season: String = "",
    val title: String = "",
    val summary: String = "",
    val highlights: List<String> = emptyList(),
    val heroChanges: List<PatchHeroChange> = emptyList(),
    val itemChanges: List<PatchItemChange> = emptyList(),
    val battlefieldChanges: List<String> = emptyList(),
    val systemChanges: List<String> = emptyList(),
    val metaNotes: String = "",
    val warning: String = ""
)

data class PatchNotesFile(
    val version: String = "",
    val updated: String = "",
    val originalServer: PatchData = PatchData(),
    val advancedServer: PatchData = PatchData(),
    val nov01OriginalEvent: List<String> = emptyList(),
    val nov01Advanced: PatchData = PatchData(),
    val nov01LivePatch: String = ""
)

// ---------- LOADER ----------
// Uses org.json (built into Android) — no new dependency.

private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }
private fun JSONObject.optStr(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) getString(key) else def

object JsonLoader {

    fun loadHeroes(context: Context): List<Hero> {
        return try {
            val text = context.assets.open("heroes.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("heroes")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val coreArr = if (o.has("coreBuild")) o.getJSONArray("coreBuild") else JSONArray()
                val core = (0 until coreArr.length()).map { j ->
                    val c = coreArr.getJSONObject(j)
                    BuildItem(itemId = c.optInt("itemId", 0), priority = c.optInt("priority", j + 1))
                }
                val coreIds = if (o.has("coreItems")) o.getJSONArray("coreItems").toIntList() else emptyList()
                val finalCore = if (core.isNotEmpty()) core else coreIds.mapIndexed { idx, id -> BuildItem(itemId = id, priority = idx + 1) }
                val skillsArr = if (o.has("skills")) o.getJSONArray("skills") else JSONArray()
                val skills = (0 until skillsArr.length()).map { j ->
                    val s = skillsArr.getJSONObject(j)
                    HeroSkill(name = s.optStr("name"), type = s.optStr("type"), description = s.optStr("description"))
                }
                val emblemObj = if (o.has("recommendedEmblem")) o.getJSONObject("recommendedEmblem") else JSONObject()
                val statsObj = if (o.has("stats")) o.getJSONObject("stats") else JSONObject()
                Hero(
                    id = o.getInt("id"), name = o.getString("name"), slug = o.optStr("slug"),
                    title = o.optStr("title"), role = o.optStr("role"),
                    secondaryRole = if (o.isNull("secondaryRole")) null else o.optStr("secondaryRole"),
                    lane = o.optStr("lane", "Gold Lane"),
                    lanes = if (o.has("lanes")) o.getJSONArray("lanes").toStringList() else listOf(o.optStr("lane")),
                    tier = o.optStr("tier", "A"), difficulty = o.optInt("difficulty", 5),
                    specialty = if (o.has("specialty")) o.getJSONArray("specialty").toStringList() else emptyList(),
                    winRate = o.optDouble("winRate", 0.5).toFloat(),
                    pickRate = o.optDouble("pickRate", 0.03).toFloat(),
                    banRate = o.optDouble("banRate", 0.03).toFloat(),
                    patchTrend = o.optDouble("patchTrend", 0.0).toFloat(),
                    isOP = o.optBoolean("isOP", false), isMeta = o.optBoolean("isMeta", false),
                    releaseYear = o.optInt("releaseYear", 2016), image = o.optStr("image"),
                    skills = skills,
                    recommendedSpells = if (o.has("recommendedSpells")) o.getJSONArray("recommendedSpells").toStringList() else emptyList(),
                    recommendedEmblem = EmblemInfo(
                        emblem = emblemObj.optStr("emblem"),
                        talents = if (emblemObj.has("talents")) emblemObj.getJSONArray("talents").toStringList() else emptyList()
                    ),
                    coreItems = finalCore,
                    counteredBy = if (o.has("counteredBy")) o.getJSONArray("counteredBy").toIntList() else emptyList(),
                    counters = if (o.has("counters")) o.getJSONArray("counters").toIntList() else emptyList(),
                    synergies = if (o.has("synergies")) o.getJSONArray("synergies").toIntList() else emptyList(),
                    countersReason = o.optStr("countersReason"),
                    bestPartners = if (o.has("bestPartners")) o.getJSONArray("bestPartners").toStringList() else emptyList(),
                    lore = o.optStr("lore"),
                    stats = HeroStats(
                        hp = statsObj.optInt("hp", 2500), hpGrowth = statsObj.optInt("hpGrowth", 150),
                        attack = statsObj.optInt("attack", 110), attackGrowth = statsObj.optInt("attackGrowth", 9),
                        defense = statsObj.optInt("defense", 18), magicDefense = statsObj.optInt("magicDefense", 15),
                        attackSpeed = statsObj.optDouble("attackSpeed", 0.82), movementSpeed = statsObj.optInt("movementSpeed", 260)
                    ),
                    tags = if (o.has("tags")) o.getJSONArray("tags").toStringList() else emptyList(),
                    patchVersion = o.optStr("patchVersion", "2.2.23"), status = o.optStr("status", "live")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun loadItems(context: Context): List<Item> {
        return try {
            val text = context.assets.open("items.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("items")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val passive = if (o.has("passive") && !o.isNull("passive")) o.getJSONObject("passive") else null
                val active = if (o.has("active") && !o.isNull("active")) o.getJSONObject("active") else null
                val sdArr = if (o.has("statsDetailed")) o.getJSONArray("statsDetailed") else JSONArray()
                Item(
                    id = o.getInt("id"), name = o.getString("name"), slug = o.optStr("slug"),
                    category = o.optStr("category"), tier = o.optStr("tier", "A"),
                    price = o.optInt("price", 0), sellPrice = o.optInt("sellPrice", 0),
                    stats = if (o.has("stats")) o.getJSONArray("stats").toStringList() else emptyList(),
                    statsDetailed = (0 until sdArr.length()).map { j ->
                        val s = sdArr.getJSONObject(j); ItemStat(s.optStr("stat"), s.opt("value").toString())
                    },
                    passiveName = passive?.optStr("name"), passiveDescription = passive?.optStr("description"),
                    activeName = active?.optStr("name"), activeDescription = active?.optStr("description"),
                    description = o.optStr("description"), lore = if (o.isNull("lore")) null else o.optStr("lore"),
                    buildPath = if (o.has("buildPath")) o.getJSONArray("buildPath").toStringList() else emptyList(),
                    buildsInto = if (o.has("buildsInto")) o.getJSONArray("buildsInto").toIntList() else emptyList(),
                    counteredBy = if (o.has("counteredBy")) o.getJSONArray("counteredBy").toIntList() else emptyList(),
                    counters = if (o.has("counters")) o.getJSONArray("counters").toIntList() else emptyList(),
                    synergiesHeroes = if (o.has("synergiesHeroes")) o.getJSONArray("synergiesHeroes").toIntList() else emptyList(),
                    worksAgainstHeroes = if (o.has("worksAgainstHeroes")) o.getJSONArray("worksAgainstHeroes").toIntList() else emptyList(),
                    recommendedFor = if (o.has("recommendedFor")) o.getJSONArray("recommendedFor").toStringList() else emptyList(),
                    tags = if (o.has("tags")) o.getJSONArray("tags").toStringList() else emptyList(),
                    image = o.optStr("image")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parsePatchData(o: JSONObject): PatchData {
        val heroArr = if (o.has("heroChanges")) o.getJSONArray("heroChanges") else JSONArray()
        val itemArr = if (o.has("itemChanges")) o.getJSONArray("itemChanges") else JSONArray()
        return PatchData(
            version = o.optStr("version"), date = o.optStr("date"), server = o.optStr("server"),
            season = o.optStr("season"), title = o.optStr("title"), summary = o.optStr("summary"),
            highlights = if (o.has("highlights")) o.getJSONArray("highlights").toStringList() else emptyList(),
            heroChanges = (0 until heroArr.length()).map { j ->
                val h = heroArr.getJSONObject(j)
                PatchHeroChange(
                    heroId = h.optInt("heroId", 0), heroName = h.optStr("heroName"), type = h.optStr("type"), title = h.optStr("title"),
                    changes = if (h.has("changes")) h.getJSONArray("changes").toStringList() else emptyList(),
                    skillsAffected = if (h.has("skillsAffected")) h.getJSONArray("skillsAffected").toStringList() else emptyList(),
                    impact = h.optStr("impact"), tierChange = h.optStr("tierChange"), isMeta = h.optBoolean("isMeta", false)
                )
            },
            itemChanges = (0 until itemArr.length()).map { j ->
                val it = itemArr.getJSONObject(j)
                PatchItemChange(itemId = it.optInt("itemId", 0), itemName = it.optStr("itemName"), type = it.optStr("type"),
                    changes = if (it.has("changes")) it.getJSONArray("changes").toStringList() else emptyList())
            },
            battlefieldChanges = if (o.has("battlefieldChanges")) o.getJSONArray("battlefieldChanges").toStringList() else emptyList(),
            systemChanges = if (o.has("systemChanges")) o.getJSONArray("systemChanges").toStringList() else emptyList(),
            metaNotes = o.optStr("metaNotes"), warning = o.optStr("warning")
        )
    }

    fun loadPatchNotes(context: Context): PatchNotesFile {
        return try {
            val text = context.assets.open("patch_notes.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val orig = if (root.has("originalServer")) parsePatchData(root.getJSONObject("originalServer"))
                else PatchData(version = root.optStr("version"), date = root.optStr("date"), server = root.optStr("server"), title = root.optStr("title"), summary = root.optStr("summary"))
            val adv = if (root.has("advancedServer")) parsePatchData(root.getJSONObject("advancedServer")) else PatchData()
            val nov01 = if (root.has("nov01")) root.getJSONObject("nov01") else JSONObject()
            val nov01Orig = if (nov01.has("originalServer")) nov01.getJSONObject("originalServer") else JSONObject()
            val nov01Adv = if (nov01.has("advancedServer")) parsePatchData(nov01.getJSONObject("advancedServer")) else PatchData()
            PatchNotesFile(
                version = root.optStr("version"), updated = root.optStr("updated"),
                originalServer = orig, advancedServer = adv,
                nov01OriginalEvent = if (nov01Orig.has("details")) nov01Orig.getJSONArray("details").toStringList() else emptyList(),
                nov01Advanced = nov01Adv, nov01LivePatch = nov01Orig.optStr("livePatchOnNov01")
            )
        } catch (e: Exception) { PatchNotesFile() }
    }
}

// ---------- GameData — single source of truth ----------

object GameData {
    var heroes: List<Hero> = emptyList()
    var items: List<Item> = emptyList()
    var patchNotes: PatchNotesFile = PatchNotesFile()
    var isLoaded: Boolean = false

    val heroesById: Map<Int, Hero> get() = heroes.associateBy { it.id }
    val itemsById: Map<Int, Item> get() = items.associateBy { it.id }

    // Live only — Dori (134) is advanced-only, must NOT show in Heroes list
    val liveHeroes: List<Hero> get() = heroes.filter { it.isLive }
    val advancedHeroes: List<Hero> get() = heroes.filter { !it.isLive }

    // Top Meta for Home: isMeta + highest winRate (Marcel, Hanabi, etc.)
    val topMetaHeroes: List<Hero> get() =
        liveHeroes.filter { it.isMeta }.sortedByDescending { it.winRate }.take(8)
            .ifEmpty { liveHeroes.sortedByDescending { it.winRate }.take(8) }

    val roamHeroes: List<Hero> get() = liveHeroes.filter { it.lane == "Roam" }

    fun load(context: Context) {
        if (isLoaded) return
        val h = JsonLoader.loadHeroes(context)
        val it = JsonLoader.loadItems(context)
        val p = JsonLoader.loadPatchNotes(context)
        // Resolve Hero coreItems names from items.json (e.g. 1002 -> Demon Hunter Sword)
        val itemsMap = it.associateBy { item -> item.id }
        heroes = h.map { hero ->
            hero.copy(coreItems = hero.coreItems.map { b -> b.copy(name = itemsMap[b.itemId]?.name ?: b.name) })
        }
        items = it
        patchNotes = p
        isLoaded = heroes.isNotEmpty() || items.isNotEmpty()
    }
}
