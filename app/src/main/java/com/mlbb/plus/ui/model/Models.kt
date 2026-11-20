package com.mlbb.plus.ui.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ============================================================
// plus/ui/model/Models.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/model/Models.kt
// ------------------------------------------------------------
// FIXES:
//  [1] CRITICAL - one bad entry killed ALL heroes/items.
//      Old: toStringList() used getString(), toIntList() used
//      getInt(), and loadHeroes()/loadItems() wrapped the WHOLE
//      list in one try/catch. One bad hero (missing id, a string
//      where an int was expected) -> catch -> return emptyList().
//      App showed 0 heroes. Fix: tolerant helpers (mapNotNull +
//      runCatching, skip bad element) + per-item try/catch in
//      loadHeroes/loadItems, skip only that item.
//  [2] Item statsDetailed value: old code did
//      s.opt("value").toString(). If value was missing/null,
//      opt() returns null and toString() is "null" or NPE,
//      killing the whole items list. Fix: optStr("value").
//  [3] secondaryRole "" -> displayRole "Tank / ".
//      Old: if key exists, optStr returns "" even when blank,
//      so displayRole became "Tank / ". Fix: blank -> null.
//  [4] lane / tier / role blank overrides default.
//      Old: optStr(key, def) returns "" when JSON has "".
//      Fix: optStrNonBlank() -> def when blank. lane defaults
//      to "Gold Lane", tier to "A".
//  [5] coreBuild: name was dropped, itemId 0 kept.
//      Old: BuildItem(itemId, priority) only, so if items.json
//      lookup failed, name stayed "" and UI showed "Item #0".
//      Fix: parse name too, filter itemId == 0.
//  [6] GameData was not thread-safe + rebuilt maps on every read.
//      Old: heroesById / itemsById were getters doing
//      associateBy() on EVERY access (called in every
//      recomposition in Heroes/Items/Detail). Fix: cache maps
//      once in load(), expose as cached vals. load() is now
//      @Synchronized + @Volatile, uses applicationContext
//      (no Activity leak).
//  [7] GameData plain vars are not Compose State. Screens reading
//      GameData.heroes will NOT recompose if load() finishes
//      after composition. load() is still called in onCreate()
//      before setContent() (MainActivity), so first frame is
//      correct. This fix documents that + makes load()
//      idempotent. Do NOT call load() from a composable and
//      expect recomposition - call it in onCreate/onCreate()
//      of Activity/Service only.
//  [8] Single source of truth for roles. Old: role -> roleTypes
//      map duplicated in MainActivity AND HomeScreen. If they
//      diverge, dropdown shows a type MainActivity immediately
//      corrects away. Fix: RoleTypes object here, both files
//      now use it.
//  [10] bestPartners type mismatch + self-reference. See Hero.bestPartners comment.
//  [9] Patch fallback string "2.2.23" duplicated in 5+ files.
//      Fix: PatchNotesFile.displayVersion + FALLBACK_PATCH_VERSION.
// ============================================================

const val FALLBACK_PATCH_VERSION = "2.2.23"
const val FALLBACK_ADVANCED_PATCH_VERSION = "2.2.32"

// Single source of truth for Role -> Type. The ROLES are fixed; the TYPES are whatever
// the role's <role>_guide.json declares in its "roles" array (ids like "initiator",
// "crit", "tank_exp"). Before this was a hardcoded list ("Tank"/"Support" for Roam,
// "Marksman"/"Mage" for Gold, ...) that did not match the guide ids, so the launcher
// could only ever start the widget on the first type of most roles.
// The launcher stores / passes the type ID; use labelFor() to show it.
object RoleTypes {
    val roles = listOf("Roam", "Jungle", "Mid", "Gold", "EXP")

    /** Type ids for a role, in guide-file order. Empty when the role has no guide file. */
    fun typesFor(role: String): List<String> =
        GameData.guides[role]?.types?.map { it.id }.orEmpty()

    /** Short display label ("Initiator") for a type id; falls back to the id itself. */
    fun labelFor(role: String, typeId: String): String =
        GameData.guides[role]?.type(typeId)?.shortLabel ?: typeId

    fun defaultTypeFor(role: String): String = typesFor(role).firstOrNull().orEmpty()
}

// ---------- HERO ----------

data class HeroSkill(
    val name: String = "",
    val type: String = "",
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
    val role: String = "",
    val secondaryRole: String? = null,
    val lane: String = "Gold Lane",
    val lanes: List<String> = emptyList(),
    val tier: String = "A",
    val difficulty: Int = 5,
    val specialty: List<String> = emptyList(),
    // CONFIRMED: winRate / pickRate / banRate / patchTrend are 0..1
    // fractions (0.50 = 50%). UI does winRate * 100. Do NOT store 0..100.
    val winRate: Float = 0.50f,
    val pickRate: Float = 0.03f,
    val banRate: Float = 0.03f,
    val patchTrend: Float = 0f,
    val isOP: Boolean = false,
    val isMeta: Boolean = false,
    val releaseYear: Int = 2016,
    val image: String = "",
    val portrait: String = "",
    val description: String = "",
    val patchNotes: String = "",
    val skills: List<HeroSkill> = emptyList(),
    val recommendedSpells: List<String> = emptyList(),
    val recommendedEmblem: EmblemInfo = EmblemInfo(),
    val coreItems: List<BuildItem> = emptyList(),
    val counteredBy: List<Int> = emptyList(),
    val counters: List<Int> = emptyList(),
    val synergies: List<Int> = emptyList(),
    val countersReason: String = "",
    // FIX [10]: bestPartners was List<String> (names) but heroes.json sends List<Int> (IDs).
    //   Old code parsed [34,55] via toStringList() -> ["34","55"], so Overview showed "34, 55".
    //   Now List<Int> like synergies/counters/counteredBy. UI resolves IDs -> names via heroesById.
    val bestPartners: List<Int> = emptyList(),
    val lore: String = "",
    val stats: HeroStats = HeroStats(),
    val tags: List<String> = emptyList(),
    val patchVersion: String = FALLBACK_PATCH_VERSION,
    val status: String = "live"
) {
    val isLive: Boolean get() = status != "advanced-server-only"
    // heroes.json fills win/pick/ban with 0.50 / 0.03 / 0.03 / 0.0 for heroes that have no
    // tracked data yet. Showing those as real stats is misleading, so the screens use this.
    val hasLiveStats: Boolean get() =
        kotlin.math.abs(winRate - 0.50f) > 0.0005f || kotlin.math.abs(pickRate - 0.03f) > 0.0005f ||
            kotlin.math.abs(banRate - 0.03f) > 0.0005f || kotlin.math.abs(patchTrend) > 0.0005f
    // True when the hero has the rich profile (skills / build / counters), not just a name + lane.
    val hasFullProfile: Boolean get() = skills.isNotEmpty() && (coreItems.isNotEmpty() || skills.size >= 3)
    // FIX [3]: secondaryRole blank is now null, so no trailing " / ".
    val displayRole: String get() = if (secondaryRole != null) "$role / $secondaryRole" else role
}

data class BuildItem(
    val itemId: Int = 0,
    val name: String = "",
    val priority: Int = 1
)

// ---------- ITEM ----------

data class ItemStat(val stat: String = "", val value: String = "")

data class Item(
    val id: Int,
    val name: String,
    val slug: String = "",
    val category: String = "",
    val tier: String = "A",
    val price: Int = 0,
    val sellPrice: Int = 0,
    val stats: List<String> = emptyList(),
    val statsDetailed: List<ItemStat> = emptyList(),
    val passiveName: String? = null,
    val passiveDescription: String? = null,
    val activeName: String? = null,
    val activeDescription: String? = null,
    val description: String = "",
    val lore: String? = null,
    val buildPath: List<String> = emptyList(),
    val buildsInto: List<Int> = emptyList(),
    val counteredBy: List<Int> = emptyList(),
    val counters: List<Int> = emptyList(),
    val synergiesHeroes: List<Int> = emptyList(),
    val worksAgainstHeroes: List<Int> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val image: String = ""
)

// ---------- PATCH NOTES ----------

data class PatchHeroChange(
    val heroId: Int = 0,
    val heroName: String = "",
    val type: String = "",
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
    val server: String = "",
    val season: String = "",
    val title: String = "",
    val summary: String = "",
    val highlights: List<String> = emptyList(),
    val heroChanges: List<PatchHeroChange> = emptyList(),
    val itemChanges: List<PatchItemChange> = emptyList(),
    val battlefieldChanges: List<String> = emptyList(),
    val systemChanges: List<String> = emptyList(),
    val equipmentChanges: List<String> = emptyList(),
    val metaNotes: String = "",
    val warning: String = "",
    // patch_notes.json (flat 2.2.23 layout) also carries these:
    val source: String = "",
    val roamGuideLink: String = "",
    val previousPatch: String = ""
) {
    /** True when this block has anything worth a tab (a version, a summary or changes). */
    val hasContent: Boolean get() = version.isNotBlank() || summary.isNotBlank() || heroChanges.isNotEmpty() ||
        itemChanges.isNotEmpty() || highlights.isNotEmpty()
}

data class PatchNotesFile(
    val version: String = "",
    val updated: String = "",
    val originalServer: PatchData = PatchData(),
    val advancedServer: PatchData = PatchData(),
    val nov01OriginalEvent: List<String> = emptyList(),
    val nov01Advanced: PatchData = PatchData(),
    val nov01LivePatch: String = ""
) {
    // FIX [9]: single fallback, use this everywhere.
    val displayVersion: String get() = originalServer.version.ifBlank { FALLBACK_PATCH_VERSION }
    // NEW batch: same fallback for Advanced server. Old code hardcoded
    // "2.2.32" in SettingsScreen + PatchNotesScreen. Use this instead.
    val advancedDisplayVersion: String get() = advancedServer.version.ifBlank { FALLBACK_ADVANCED_PATCH_VERSION }
}

// ---------- LOADER ----------

private fun JSONArray.toStringList(): List<String> = (0 until length()).mapNotNull { i ->
    runCatching {
        val v = get(i)
        if (v == JSONObject.NULL) null else v.toString()
    }.getOrNull()
}

private fun JSONArray.toIntList(): List<Int> = (0 until length()).mapNotNull { i ->
    runCatching {
        when (val v = get(i)) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull()
            else -> null
        }
    }.getOrNull()
}

private fun JSONObject.optStr(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) getString(key) else def

// FIX [4]: blank string in JSON should fall back to def, not "".
private fun JSONObject.optStrNonBlank(key: String, def: String): String =
    optStr(key, def).ifBlank { def }

object JsonLoader {

    fun loadHeroes(context: Context): List<Hero> {
        return try {
            val text = context.assets.open("heroes.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("heroes")
            val out = mutableListOf<Hero>()
            for (i in 0 until arr.length()) {
                // FIX [1]: per-item catch - one bad hero does not kill all 134.
                runCatching {
                    val o = arr.getJSONObject(i)
                    val id = o.optInt("id", 0)
                    val name = o.optStr("name")
                    if (id == 0 || name.isBlank()) return@runCatching
                    val coreArr = if (o.has("coreBuild")) o.optJSONArray("coreBuild") ?: JSONArray() else JSONArray()
                    // heroes.json 2.2.23 stores coreBuild as item-ID numbers (not objects).
                    // Accept both formats so older exports remain backward-compatible.
                    val core = (0 until coreArr.length()).mapNotNull { j ->
                        runCatching {
                            when (val entry = coreArr.get(j)) {
                                is Number -> entry.toInt().takeIf { it > 0 }?.let { BuildItem(itemId = it, priority = j + 1) }
                                is JSONObject -> entry.optInt("itemId", 0).takeIf { it > 0 }?.let {
                                    BuildItem(itemId = it, name = entry.optStr("name"), priority = entry.optInt("priority", j + 1))
                                }
                                is String -> entry.trim().toIntOrNull()?.takeIf { it > 0 }?.let { BuildItem(itemId = it, priority = j + 1) }
                                else -> null
                            }
                        }.getOrNull()
                    }
                    val coreIds = if (o.has("coreItems")) o.getJSONArray("coreItems").toIntList() else emptyList()
                    val finalCore = if (core.isNotEmpty()) core else coreIds.mapIndexed { idx, id2 ->
                        BuildItem(itemId = id2, priority = idx + 1)
                    }.filter { it.itemId != 0 }
                    val skillsArr = if (o.has("skills")) o.getJSONArray("skills") else JSONArray()
                    val skills = (0 until skillsArr.length()).mapNotNull { j ->
                        runCatching {
                            val s = skillsArr.getJSONObject(j)
                            HeroSkill(name = s.optStr("name"), type = s.optStr("type"), description = s.optStr("description"))
                        }.getOrNull()
                    }
                    val emblemObj = if (o.has("recommendedEmblem")) o.getJSONObject("recommendedEmblem") else JSONObject()
                    val statsObj = if (o.has("stats")) o.getJSONObject("stats") else JSONObject()
                    // FIX [4]: lane / tier use non-blank defaults.
                    val lane = o.optStrNonBlank("lane", "Gold Lane")
                    out.add(
                        Hero(
                            id = id, name = name, slug = o.optStr("slug"),
                            title = o.optStr("title"), role = o.optStr("role"),
                            // FIX [3]: blank secondaryRole -> null.
                            secondaryRole = o.optStr("secondaryRole").takeIf { it.isNotBlank() },
                            lane = lane,
                            lanes = if (o.has("lanes")) o.getJSONArray("lanes").toStringList().filter { it.isNotBlank() }.ifEmpty { listOf(lane) } else listOf(lane),
                            tier = o.optStrNonBlank("tier", "A").uppercase().trim(),
                            difficulty = o.optInt("difficulty", 5),
                            specialty = if (o.has("specialty")) o.getJSONArray("specialty").toStringList() else emptyList(),
                            winRate = o.optDouble("winRate", 0.5).toFloat(),
                            pickRate = o.optDouble("pickRate", 0.03).toFloat(),
                            banRate = o.optDouble("banRate", 0.03).toFloat(),
                            patchTrend = o.optDouble("patchTrend", 0.0).toFloat(),
                            isOP = o.optBoolean("isOP", false), isMeta = o.optBoolean("isMeta", false),
                            releaseYear = o.optInt("releaseYear", 2016), image = o.optStrNonBlank("image", o.optStr("portrait")),
                            portrait = o.optStr("portrait"), description = o.optStr("description"), patchNotes = o.optStr("patchNotes"),
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
                            // FIX [10]: parse as IDs, not names. Legacy name-strings are dropped (synergies covers fallback).
                            bestPartners = if (o.has("bestPartners")) o.getJSONArray("bestPartners").toIntList() else emptyList(),
                            lore = o.optStr("lore"),
                            stats = HeroStats(
                                hp = statsObj.optInt("hp", 2500), hpGrowth = statsObj.optInt("hpGrowth", 150),
                                attack = statsObj.optInt("attack", 110), attackGrowth = statsObj.optInt("attackGrowth", 9),
                                defense = statsObj.optInt("defense", 18), magicDefense = statsObj.optInt("magicDefense", 15),
                                attackSpeed = statsObj.optDouble("attackSpeed", 0.82), movementSpeed = statsObj.optInt("movementSpeed", 260)
                            ),
                            tags = if (o.has("tags")) o.getJSONArray("tags").toStringList() else emptyList(),
                            patchVersion = o.optStrNonBlank("patchVersion", FALLBACK_PATCH_VERSION), status = o.optStrNonBlank("status", "live")
                        )
                    )
                }
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    fun loadItems(context: Context): List<Item> {
        return try {
            val text = context.assets.open("items.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("items")
            val out = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                runCatching {
                    val o = arr.getJSONObject(i)
                    val id = o.optInt("id", 0)
                    val name = o.optStr("name")
                    if (id == 0 || name.isBlank()) return@runCatching
                    val passive = if (o.has("passive") && !o.isNull("passive")) o.getJSONObject("passive") else null
                    val active = if (o.has("active") && !o.isNull("active")) o.getJSONObject("active") else null
                    val sdArr = if (o.has("statsDetailed")) o.getJSONArray("statsDetailed") else JSONArray()
                    out.add(
                        Item(
                            id = id, name = name, slug = o.optStr("slug"),
                            category = o.optStr("category"), tier = o.optStrNonBlank("tier", "A").uppercase().trim(),
                            price = o.optInt("price", 0), sellPrice = o.optInt("sellPrice", 0),
                            stats = if (o.has("stats")) o.getJSONArray("stats").toStringList() else emptyList(),
                            statsDetailed = (0 until sdArr.length()).mapNotNull { j ->
                                runCatching {
                                    val s = sdArr.getJSONObject(j)
                                    // FIX [2]: optStr, not opt().toString() which gives "null" / NPE.
                                    ItemStat(s.optStr("stat"), s.optStr("value"))
                                }.getOrNull()
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
                    )
                }
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    private fun parsePatchData(o: JSONObject): PatchData {
        val heroArr = if (o.has("heroChanges")) o.getJSONArray("heroChanges") else JSONArray()
        val itemArr = if (o.has("itemChanges")) o.getJSONArray("itemChanges") else JSONArray()
        return PatchData(
            version = o.optStr("version"), date = o.optStr("date"), server = o.optStr("server"),
            season = o.optStr("season"), title = o.optStr("title"), summary = o.optStr("summary"),
            highlights = if (o.has("highlights")) o.getJSONArray("highlights").toStringList() else emptyList(),
            heroChanges = (0 until heroArr.length()).mapNotNull { j ->
                runCatching {
                    val h = heroArr.getJSONObject(j)
                    PatchHeroChange(
                        heroId = h.optInt("heroId", 0), heroName = h.optStr("heroName"), type = h.optStr("type"), title = h.optStr("title"),
                        changes = if (h.has("changes")) h.getJSONArray("changes").toStringList() else emptyList(),
                        skillsAffected = if (h.has("skillsAffected")) h.getJSONArray("skillsAffected").toStringList() else emptyList(),
                        impact = h.optStr("impact"), tierChange = h.optStr("tierChange"), isMeta = h.optBoolean("isMeta", false)
                    )
                }.getOrNull()
            },
            itemChanges = (0 until itemArr.length()).mapNotNull { j ->
                runCatching {
                    val it = itemArr.getJSONObject(j)
                    PatchItemChange(itemId = it.optInt("itemId", 0), itemName = it.optStr("itemName"), type = it.optStr("type"),
                        changes = if (it.has("changes")) it.getJSONArray("changes").toStringList() else emptyList())
                }.getOrNull()
            },
            battlefieldChanges = if (o.has("battlefieldChanges")) o.getJSONArray("battlefieldChanges").toStringList() else emptyList(),
            systemChanges = if (o.has("systemChanges")) o.getJSONArray("systemChanges").toStringList() else emptyList(),
            metaNotes = o.optStr("metaNotes"), warning = o.optStr("warning"),
            source = o.optStr("source"), roamGuideLink = o.optStr("roamGuideLink"), previousPatch = o.optStr("previousPatch")
        )
    }

    fun loadPatchNotes(context: Context): PatchNotesFile {
        return try {
            val text = context.assets.open("patch_notes.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val orig = if (root.has("originalServer")) parsePatchData(root.getJSONObject("originalServer"))
                // Flat layout (the shipped patch_notes.json): the root IS the Original Server block.
                // The old fallback copied only title/summary and silently dropped every hero change,
                // item change, highlight, battlefield/system change and the meta notes.
                else parsePatchData(root)
            val adv = if (root.has("advancedServer")) parsePatchData(root.getJSONObject("advancedServer")) else PatchData()
            val nov01 = if (root.has("nov01")) root.getJSONObject("nov01") else JSONObject()
            val nov01Orig = if (nov01.has("originalServer")) nov01.getJSONObject("originalServer") else JSONObject()
            val nov01Adv = if (nov01.has("advancedServer")) parsePatchData(nov01.getJSONObject("advancedServer")) else PatchData()
            val fileNotes = PatchNotesFile(
                version = root.optStr("version"), updated = root.optStr("updated").ifBlank { root.optStr("date") },
                originalServer = orig, advancedServer = adv,
                nov01OriginalEvent = if (nov01Orig.has("details")) nov01Orig.getJSONArray("details").toStringList() else emptyList(),
                nov01Advanced = nov01Adv, nov01LivePatch = nov01Orig.optStr("livePatchOnNov01")
            )
            // The new heroes.json is the authoritative patch manifest for hero changes.
            // Merge its compact {hero,id,type,detail} entries into the richer legacy patch model.
            val heroRoot = runCatching {
                JSONObject(context.assets.open("heroes.json").bufferedReader().use { it.readText() })
            }.getOrNull() ?: return fileNotes
            val manifestChanges = heroRoot.optJSONArray("patchChanges") ?: JSONArray()
            val manifestHeroes = (0 until manifestChanges.length()).mapNotNull { index ->
                runCatching {
                    val item = manifestChanges.getJSONObject(index)
                    val id = item.optInt("id", 0)
                    if (id <= 0) return@runCatching null
                    val detail = item.optStr("detail")
                    PatchHeroChange(
                        heroId = id,
                        heroName = item.optStr("hero"),
                        type = item.optStr("type"),
                        title = item.optStr("type").ifBlank { "Patch update" },
                        changes = listOfNotNull(detail.takeIf { it.isNotBlank() })
                    )
                }.getOrNull()
            }
            val mergedHeroes = (fileNotes.originalServer.heroChanges + manifestHeroes)
                .associateBy { it.heroId }.values.toList().sortedBy { it.heroId }
            val manifestVersion = heroRoot.optStr("version").removeSuffix("-fixed")
            val patchBlock = fileNotes.originalServer.copy(
                version = manifestVersion.ifBlank { fileNotes.originalServer.version },
                date = heroRoot.optStr("updated").ifBlank { fileNotes.originalServer.date },
                season = heroRoot.optStr("season").ifBlank { fileNotes.originalServer.season },
                title = heroRoot.optStr("patchName").ifBlank { fileNotes.originalServer.title },
                heroChanges = mergedHeroes,
                equipmentChanges = (heroRoot.optJSONArray("equipmentPatchChanges") ?: JSONArray()).toStringList(),
                source = heroRoot.optStr("source").ifBlank { fileNotes.originalServer.source }
            )
            fileNotes.copy(version = patchBlock.version.ifBlank { fileNotes.version }, originalServer = patchBlock)
        } catch (e: Exception) { PatchNotesFile() }
    }
}

// ---------- GameData - single source of truth ----------

object GameData {
    @Volatile var heroes: List<Hero> = emptyList()
        private set
    @Volatile var items: List<Item> = emptyList()
        private set
    @Volatile var patchNotes: PatchNotesFile = PatchNotesFile()
        private set
    @Volatile var guides: Map<String, GuideFile> = emptyMap()
        private set
    @Volatile var isLoaded: Boolean = false
        private set

    // FIX [6]: cached maps - do NOT rebuild on every recomposition.
    @Volatile private var cachedHeroesById: Map<Int, Hero> = emptyMap()
    @Volatile private var cachedItemsById: Map<Int, Item> = emptyMap()
    val heroesById: Map<Int, Hero> get() = cachedHeroesById
    val itemsById: Map<Int, Item> get() = cachedItemsById

    // Indexes over the guide files, built once in load().
    @Volatile private var heroGuideIndex: Map<Int, List<HeroGuideRef>> = emptyMap()
    @Volatile private var itemGuideIndex: Map<String, List<ItemGuideRef>> = emptyMap()
    @Volatile private var heroesByCoreItem: Map<Int, List<Hero>> = emptyMap()

    /** Every guide type that lists this hero as a pick (heroes.json id). */
    fun guideRefsForHero(heroId: Int): List<HeroGuideRef> = heroGuideIndex[heroId].orEmpty()

    /** Every guide type that lists this item (by name, case-insensitive) as a core item. */
    fun guideRefsForItem(itemName: String): List<ItemGuideRef> = itemGuideIndex[itemName.trim().lowercase()].orEmpty()

    /** Heroes that have this item id in their core build. */
    fun heroesUsingItem(itemId: Int): List<Hero> = heroesByCoreItem[itemId].orEmpty()

    /** This patch's change entry for a hero / item (patch_notes.json), or null. */
    fun patchChangeForHero(heroId: Int): PatchHeroChange? =
        patchNotes.originalServer.heroChanges.firstOrNull { it.heroId == heroId }
    fun patchChangeForItem(itemId: Int): PatchItemChange? =
        patchNotes.originalServer.itemChanges.firstOrNull { it.itemId == itemId }

    /** Guide picks whose name is not in heroes.json / core items not in items.json (data health). */
    val unlinkedGuidePicks: List<String> get() = guides.values
        .flatMap { g -> g.types.flatMap { t -> t.heroes.filter { it.heroId == null }.map { it.name } } }.distinct().sorted()
    val unlinkedGuideItems: List<String> get() {
        val known = items.map { it.name.trim().lowercase() }.toSet()
        return guides.values.flatMap { g -> g.types.flatMap { it.coreItems } }
            .filter { it.trim().lowercase() !in known }.distinct().sorted()
    }

    val liveHeroes: List<Hero> get() = heroes.filter { it.isLive }
    val advancedHeroes: List<Hero> get() = heroes.filter { !it.isLive }

    val topMetaHeroes: List<Hero> get() =
        liveHeroes.filter { it.isMeta }.sortedByDescending { it.winRate }.take(8)
            .ifEmpty { liveHeroes.sortedByDescending { it.winRate }.take(8) }

    val roamHeroes: List<Hero> get() = liveHeroes.filter { it.lane == "Roam" }

    // FIX [6] + [7]: synchronized, idempotent, applicationContext only.
    // Call from Activity/Service onCreate() before first composition.
    // Do NOT call from a @Composable expecting recomposition.
    @Synchronized
    fun load(context: Context) {
        if (isLoaded) return
        val appContext = context.applicationContext
        val h = JsonLoader.loadHeroes(appContext)
        val it = JsonLoader.loadItems(appContext)
        val p = JsonLoader.loadPatchNotes(appContext)
        val itemsMap = it.associateBy { item -> item.id }
        heroes = h.map { hero ->
            hero.copy(coreItems = hero.coreItems.map { b -> b.copy(name = itemsMap[b.itemId]?.name ?: b.name) })
        }
        items = it
        patchNotes = p
        cachedHeroesById = heroes.associateBy { hero -> hero.id }
        cachedItemsById = items.associateBy { item -> item.id }
        guides = GuideLoader.loadAll(appContext, heroes)

        val heroRefs = HashMap<Int, MutableList<HeroGuideRef>>()
        val itemRefs = HashMap<String, MutableList<ItemGuideRef>>()
        for ((role, g) in guides) {
            for (t in g.types) {
                for (pick in t.heroes) {
                    val id = pick.heroId ?: continue
                    heroRefs.getOrPut(id) { mutableListOf() }.add(HeroGuideRef(role, t.id, t.shortLabel, pick.note))
                }
                for (itemName in t.coreItems) {
                    itemRefs.getOrPut(itemName.trim().lowercase()) { mutableListOf() }.add(ItemGuideRef(role, t.id, t.shortLabel))
                }
            }
        }
        heroGuideIndex = heroRefs
        itemGuideIndex = itemRefs
        val byItem = HashMap<Int, MutableList<Hero>>()
        for (hero in heroes) for (b in hero.coreItems) byItem.getOrPut(b.itemId) { mutableListOf() }.add(hero)
        heroesByCoreItem = byItem
        isLoaded = heroes.isNotEmpty() || items.isNotEmpty()
    }
}
