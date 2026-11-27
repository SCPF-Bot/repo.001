package com.mlbb.plus.ui.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data model for the bundled JSON assets (heroes, items, patch notes) and the
 * process-wide [GameData] store the screens and the overlay read from.
 *
 * Parsing is tolerant by design: one malformed entry is skipped, never the whole list.
 * Missing keys and JSON `null` both fall back to the documented default.
 */

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
    // bestPartners is a list of hero IDs (heroes.json sends numbers), like counters and synergies.
    val bestPartners: List<Int> = emptyList(),
    // Provenance for the current matchup snapshot. Empty means no verified matchup data is shown.
    val matchupDataSource: String = "",
    val matchupDataUpdated: String = "",
    val matchupDataWindow: String = "",
    val matchupDataRank: String = "",
    val lore: String = "",
    val loreFirstParagraph: String = "",
    val goodAgainst: List<Int> = emptyList(),
    val badAgainst: List<Int> = emptyList(),
    val goodWith: List<Int> = emptyList(),
    val tierUpdated: String = "",
    val tierSource: String = "",
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
    val buildPathIds: List<Int> = emptyList(),
    val buildsInto: List<Int> = emptyList(),
    val goodAgainstItemIds: List<Int> = emptyList(),
    val badAgainstItemIds: List<Int> = emptyList(),
    val goodAgainstHeroes: List<Int> = emptyList(),
    val badAgainstHeroes: List<Int> = emptyList(),
    val goodWithHeroes: List<Int> = emptyList(),
    val priceType: String = "shop",
    val priceNote: String = "",
    val counteredBy: List<Int> = emptyList(),
    val counters: List<Int> = emptyList(),
    val synergiesHeroes: List<Int> = emptyList(),
    val worksAgainstHeroes: List<Int> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val image: String = "",
    val portrait: String = "",
    /** active items appear in the catalog; removed entries remain available for legacy builds. */
    val status: String = "active"
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
    val latest: Boolean = false,
    val updated: String = "",
    val originalServer: PatchData = PatchData(),
    val advancedServer: PatchData = PatchData(),
    val nov01OriginalEvent: List<String> = emptyList(),
    val nov01Advanced: PatchData = PatchData(),
    val nov01LivePatch: String = ""
) {
    // NOTE: heroes.json is the authoritative patch manifest, so `version` can carry a
    // data-stamp suffix, e.g. "2.2.23-tier-2026-11-23". Use this for fallbacks/labels
    // that may show the full stamp, and `shortVersion` whenever a bare patch number is
    // meant (headers, and every guide.patch comparison).
    val displayVersion: String get() = originalServer.version.ifBlank { FALLBACK_PATCH_VERSION }

    // Bare patch number: "2.2.23-tier-2026-11-23" -> "2.2.23".
    // Consolidates the substringBefore("-") rule that HomeScreen, HeroesScreen and
    // ItemsScreen each duplicated, and fixes the sites that compared a guide's
    // "2.2.23" against the stamped version and therefore reported every guide as stale
    // (PatchNotesScreen coverage card and the in-match overlay patch label).
    val shortVersion: String get() = displayVersion.substringBefore("-").ifBlank { displayVersion }
    // Same fallback for the Advanced server, so no screen hard-codes the version.
    val advancedDisplayVersion: String get() = advancedServer.version.ifBlank { FALLBACK_ADVANCED_PATCH_VERSION }
}

// ---------- LOADER ----------

// Null receivers (missing key or JSON null) yield an empty list, never an exception.
private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        runCatching {
            val v = array.get(i)
            if (v == JSONObject.NULL) null else v.toString()
        }.getOrNull()
    }
}

private fun JSONArray?.toIntList(): List<Int> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        runCatching {
            when (val v = array.get(i)) {
                is Number -> v.toInt()
                is String -> v.trim().toIntOrNull()
                else -> null
            }
        }.getOrNull()
    }
}

private fun JSONObject.optStr(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) getString(key) else def
private fun JSONObject.optStrNonBlank(key: String, def: String): String =
    optStr(key, def).ifBlank { def }

object JsonLoader {

    fun loadHeroes(context: Context): List<Hero> {
        return try {
            val text = context.assets.open("heroes.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("heroes")
            val out = mutableListOf<Hero>()
            for (i in 0 until arr.length()) {
                // Per-item catch: one bad hero does not drop the other 132.
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
                    val coreIds = if (o.has("coreItems")) o.optJSONArray("coreItems").toIntList() else emptyList()
                    val finalCore = if (core.isNotEmpty()) core else coreIds.mapIndexed { idx, id2 ->
                        BuildItem(itemId = id2, priority = idx + 1)
                    }.filter { it.itemId != 0 }
                    val skillsArr = o.optJSONArray("skills") ?: JSONArray()
                    val skills = (0 until skillsArr.length()).mapNotNull { j ->
                        runCatching {
                            val s = skillsArr.getJSONObject(j)
                            HeroSkill(name = s.optStr("name"), type = s.optStr("type"), description = s.optStr("description"))
                        }.getOrNull()
                    }
                    val emblemObj = o.optJSONObject("recommendedEmblem") ?: JSONObject()
                    val statsObj = o.optJSONObject("stats") ?: JSONObject()
                    val lane = o.optStrNonBlank("lane", "Gold Lane")
                    out.add(
                        Hero(
                            id = id, name = name, slug = o.optStr("slug"),
                            title = o.optStr("title"), role = o.optStr("role"),
                            secondaryRole = o.optStr("secondaryRole").takeIf { it.isNotBlank() },
                            lane = lane,
                            lanes = if (o.has("lanes")) o.optJSONArray("lanes").toStringList().filter { it.isNotBlank() }.ifEmpty { listOf(lane) } else listOf(lane),
                            tier = o.optStrNonBlank("tier", "A").uppercase().trim(),
                            difficulty = o.optInt("difficulty", 5),
                            specialty = if (o.has("specialty")) o.optJSONArray("specialty").toStringList() else emptyList(),
                            winRate = o.optDouble("winRate", 0.5).toFloat(),
                            pickRate = o.optDouble("pickRate", 0.03).toFloat(),
                            banRate = o.optDouble("banRate", 0.03).toFloat(),
                            patchTrend = o.optDouble("patchTrend", 0.0).toFloat(),
                            isOP = o.optBoolean("isOP", false), isMeta = o.optBoolean("isMeta", false),
                            releaseYear = o.optInt("releaseYear", 2016), image = o.optStrNonBlank("image", o.optStr("portrait")),
                            portrait = o.optStr("portrait"), description = o.optStr("description"), patchNotes = o.optStr("patchNotes"),
                            skills = skills,
                            recommendedSpells = if (o.has("recommendedSpells")) o.optJSONArray("recommendedSpells").toStringList() else emptyList(),
                            recommendedEmblem = EmblemInfo(
                                emblem = emblemObj.optStr("emblem"),
                                talents = if (emblemObj.has("talents")) emblemObj.optJSONArray("talents").toStringList() else emptyList()
                            ),
                            coreItems = finalCore,
                            counteredBy = if (o.has("counteredBy")) o.optJSONArray("counteredBy").toIntList() else emptyList(),
                            counters = if (o.has("counters")) o.optJSONArray("counters").toIntList() else emptyList(),
                            synergies = if (o.has("synergies")) o.optJSONArray("synergies").toIntList() else emptyList(),
                            countersReason = o.optStr("countersReason"),
                            // Parsed as IDs. Legacy name strings are dropped, since synergies covers that case.
                            bestPartners = if (o.has("bestPartners")) o.optJSONArray("bestPartners").toIntList() else emptyList(),
                            matchupDataSource = o.optStr("matchupDataSource"),
                            matchupDataUpdated = o.optStr("matchupDataUpdated"),
                            matchupDataWindow = o.optStr("matchupDataWindow"),
                            matchupDataRank = o.optStr("matchupDataRank"),
                            lore = o.optStr("lore"),
                            loreFirstParagraph = o.optStrNonBlank("loreFirstParagraph", o.optStr("lore")),
                            goodAgainst = if (o.has("goodAgainst")) o.optJSONArray("goodAgainst").toIntList() else if (o.has("counters")) o.optJSONArray("counters").toIntList() else emptyList(),
                            badAgainst = if (o.has("badAgainst")) o.optJSONArray("badAgainst").toIntList() else if (o.has("counteredBy")) o.optJSONArray("counteredBy").toIntList() else emptyList(),
                            goodWith = if (o.has("goodWith")) o.optJSONArray("goodWith").toIntList() else if (o.has("synergies")) o.optJSONArray("synergies").toIntList() else emptyList(),
                            tierUpdated = o.optStr("tierUpdated"), tierSource = o.optStr("tierSource"),
                            stats = HeroStats(
                                hp = statsObj.optInt("hp", 2500), hpGrowth = statsObj.optInt("hpGrowth", 150),
                                attack = statsObj.optInt("attack", 110), attackGrowth = statsObj.optInt("attackGrowth", 9),
                                defense = statsObj.optInt("defense", 18), magicDefense = statsObj.optInt("magicDefense", 15),
                                attackSpeed = statsObj.optDouble("attackSpeed", 0.82), movementSpeed = statsObj.optInt("movementSpeed", 260)
                            ),
                            tags = if (o.has("tags")) o.optJSONArray("tags").toStringList() else emptyList(),
                            patchVersion = o.optStrNonBlank("patchVersion", FALLBACK_PATCH_VERSION), status = o.optStrNonBlank("status", "live")
                        )
                    )
                }
            }
            out
        } catch (e: Exception) {
            // Never crash on bad bundled JSON, but do not fail silently either:
            // Settings reports "MISSING" with no reason otherwise. The broad catch
            // is deliberate (org.json throws JSONException; malformed data can also
            // surface as ClassCastException / IndexOutOfBounds) — flagged by detekt
            // SwallowedException / TooGenericExceptionCaught, 2026-11-27 lint scan.
            android.util.Log.e("GameData", "heroes.json parse failed", e)
            emptyList()
        }
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
                    val sdArr = o.optJSONArray("statsDetailed") ?: JSONArray()
                    out.add(
                        Item(
                            id = id, name = name, slug = o.optStr("slug"),
                            category = o.optStr("category"), tier = o.optStrNonBlank("tier", "A").uppercase().trim(),
                            price = if (o.isNull("price")) 0 else o.optInt("price", 0), sellPrice = o.optInt("sellPrice", 0),
                            stats = if (o.has("stats")) o.optJSONArray("stats").toStringList() else emptyList(),
                            statsDetailed = (0 until sdArr.length()).mapNotNull { j ->
                                runCatching {
                                    val s = sdArr.getJSONObject(j)
                                                    ItemStat(s.optStr("stat"), s.optStr("value"))
                                }.getOrNull()
                            },
                            passiveName = passive?.optStr("name"), passiveDescription = passive?.optStr("description"),
                            activeName = active?.optStr("name"), activeDescription = active?.optStr("description"),
                            description = o.optStr("description"), lore = if (o.isNull("lore")) null else o.optStr("lore"),
                            buildPath = if (o.has("buildPath")) o.optJSONArray("buildPath").toStringList() else emptyList(),
                            buildPathIds = if (o.has("buildPathIds")) o.optJSONArray("buildPathIds").toIntList() else emptyList(),
                            buildsInto = if (o.has("buildsInto")) o.optJSONArray("buildsInto").toIntList() else emptyList(),
                            goodAgainstItemIds = if (o.has("goodAgainst")) o.optJSONArray("goodAgainst").toIntList() else if (o.has("counters")) o.optJSONArray("counters").toIntList() else emptyList(),
                            badAgainstItemIds = if (o.has("badAgainst")) o.optJSONArray("badAgainst").toIntList() else if (o.has("counteredBy")) o.optJSONArray("counteredBy").toIntList() else emptyList(),
                            goodAgainstHeroes = if (o.has("goodAgainstHeroes")) o.optJSONArray("goodAgainstHeroes").toIntList() else if (o.has("worksAgainstHeroes")) o.optJSONArray("worksAgainstHeroes").toIntList() else emptyList(),
                            badAgainstHeroes = if (o.has("badAgainstHeroes")) o.optJSONArray("badAgainstHeroes").toIntList() else emptyList(),
                            goodWithHeroes = if (o.has("goodWithHeroes")) o.optJSONArray("goodWithHeroes").toIntList() else if (o.has("synergiesHeroes")) o.optJSONArray("synergiesHeroes").toIntList() else emptyList(),
                            priceType = o.optStrNonBlank("priceType", "shop"), priceNote = o.optStr("priceNote"),
                            counteredBy = if (o.has("counteredBy")) o.optJSONArray("counteredBy").toIntList() else emptyList(),
                            counters = if (o.has("counters")) o.optJSONArray("counters").toIntList() else emptyList(),
                            synergiesHeroes = if (o.has("synergiesHeroes")) o.optJSONArray("synergiesHeroes").toIntList() else emptyList(),
                            worksAgainstHeroes = if (o.has("worksAgainstHeroes")) o.optJSONArray("worksAgainstHeroes").toIntList() else emptyList(),
                            recommendedFor = if (o.has("recommendedFor")) o.optJSONArray("recommendedFor").toStringList() else emptyList(),
                            tags = if (o.has("tags")) o.optJSONArray("tags").toStringList() else emptyList(),
                            image = o.optStr("image"), portrait = o.optStr("portrait"),
                            status = o.optStrNonBlank("status", "active").lowercase()
                        )
                    )
                }
            }
            out
        } catch (e: Exception) {
            // Never crash on bad bundled JSON, but do not fail silently either:
            // Settings reports "MISSING" with no reason otherwise. The broad catch
            // is deliberate (org.json throws JSONException; malformed data can also
            // surface as ClassCastException / IndexOutOfBounds) — flagged by detekt
            // SwallowedException / TooGenericExceptionCaught, 2026-11-27 lint scan.
            android.util.Log.e("GameData", "items.json parse failed", e)
            emptyList()
        }
    }

    private fun parsePatchData(o: JSONObject): PatchData {
        val heroArr = o.optJSONArray("heroChanges") ?: JSONArray()
        val itemArr = o.optJSONArray("itemChanges") ?: JSONArray()
        return PatchData(
            version = o.optStr("version"), date = o.optStr("date"), server = o.optStr("server"),
            season = o.optStr("season"), title = o.optStr("title"), summary = o.optStr("summary"),
            highlights = if (o.has("highlights")) o.optJSONArray("highlights").toStringList() else emptyList(),
            heroChanges = (0 until heroArr.length()).mapNotNull { j ->
                runCatching {
                    val h = heroArr.getJSONObject(j)
                    PatchHeroChange(
                        heroId = h.optInt("heroId", 0), heroName = h.optStr("heroName"), type = h.optStr("type"), title = h.optStr("title"),
                        changes = if (h.has("changes")) h.optJSONArray("changes").toStringList() else emptyList(),
                        skillsAffected = if (h.has("skillsAffected")) h.optJSONArray("skillsAffected").toStringList() else emptyList(),
                        impact = h.optStr("impact"), tierChange = h.optStr("tierChange"), isMeta = h.optBoolean("isMeta", false)
                    )
                }.getOrNull()
            },
            itemChanges = (0 until itemArr.length()).mapNotNull { j ->
                runCatching {
                    val it = itemArr.getJSONObject(j)
                    PatchItemChange(itemId = it.optInt("itemId", 0), itemName = it.optStr("itemName"), type = it.optStr("type"),
                        changes = if (it.has("changes")) it.optJSONArray("changes").toStringList() else emptyList())
                }.getOrNull()
            },
            battlefieldChanges = if (o.has("battlefieldChanges")) o.optJSONArray("battlefieldChanges").toStringList() else emptyList(),
            systemChanges = if (o.has("systemChanges")) o.optJSONArray("systemChanges").toStringList() else emptyList(),
            metaNotes = o.optStr("metaNotes"), warning = o.optStr("warning"),
            source = o.optStr("source"), roamGuideLink = o.optStr("roamGuideLink"), previousPatch = o.optStr("previousPatch")
        )
    }

    fun loadPatchNotes(context: Context): PatchNotesFile {
        return try {
            val text = context.assets.open("patch_notes.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            // Flat layout (the shipped patch_notes.json): the root IS the Original Server block.
            // Falling back to the root keeps every hero change, item change, highlight and note.
            val orig = root.optJSONObject("originalServer")?.let { parsePatchData(it) } ?: parsePatchData(root)
            val adv = root.optJSONObject("advancedServer")?.let { parsePatchData(it) } ?: PatchData()
            val nov01 = root.optJSONObject("nov01") ?: JSONObject()
            val nov01Orig = nov01.optJSONObject("originalServer") ?: JSONObject()
            val nov01Adv = nov01.optJSONObject("advancedServer")?.let { parsePatchData(it) } ?: PatchData()
            val fileNotes = PatchNotesFile(
                version = root.optStr("version"), latest = root.optBoolean("latest", false),
                updated = root.optStr("updated").ifBlank { root.optStr("date") },
                originalServer = orig, advancedServer = adv,
                nov01OriginalEvent = if (nov01Orig.has("details")) nov01Orig.optJSONArray("details").toStringList() else emptyList(),
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
            // heroes.json carries both `patchDate` (when the patch went live, 2026-11-11)
            // and `updated` (when this data snapshot was refreshed, 2026-11-23). The patch
            // card title reads "{title} • {server} • {date}", so it must show the patch
            // date; using `updated` implied the patch landed on the refresh date.
            val manifestDate = heroRoot.optStr("patchDate").ifBlank { heroRoot.optStr("updated") }
            val patchBlock = fileNotes.originalServer.copy(
                version = manifestVersion.ifBlank { fileNotes.originalServer.version },
                date = manifestDate.ifBlank { fileNotes.originalServer.date },
                season = heroRoot.optStr("season").ifBlank { fileNotes.originalServer.season },
                title = heroRoot.optStr("patchName").ifBlank { fileNotes.originalServer.title },
                heroChanges = mergedHeroes,
                equipmentChanges = (heroRoot.optJSONArray("equipmentPatchChanges") ?: JSONArray()).toStringList(),
                source = heroRoot.optStr("source").ifBlank { fileNotes.originalServer.source }
            )
            fileNotes.copy(version = patchBlock.version.ifBlank { fileNotes.version }, originalServer = patchBlock)
        } catch (e: Exception) {
            // Never crash on bad bundled JSON, but do not fail silently either:
            // Settings reports "MISSING" with no reason otherwise. The broad catch
            // is deliberate (org.json throws JSONException; malformed data can also
            // surface as ClassCastException / IndexOutOfBounds) — flagged by detekt
            // SwallowedException / TooGenericExceptionCaught, 2026-11-27 lint scan.
            android.util.Log.e("GameData", "patch_notes.json parse failed", e)
            PatchNotesFile()
        }
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
    @Volatile var heroesLatest: Boolean = false
        private set
    @Volatile var itemsLatest: Boolean = false
        private set
    @Volatile var isLoaded: Boolean = false
        private set

    // Maps are built once in load(). Rebuilding them on every read caused per-recomposition work.
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

    // Synchronized and idempotent, and uses the application context only. Call it from an
    // Activity or Service onCreate() before the first composition. Do not call it from a
    // @Composable, because the stores are plain properties and reads will not recompose.
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
        heroesLatest = runCatching {
            JSONObject(appContext.assets.open("heroes.json").bufferedReader().use { r -> r.readText() }).optBoolean("latest", false)
        }.getOrDefault(false)
        itemsLatest = runCatching {
            JSONObject(appContext.assets.open("items.json").bufferedReader().use { r -> r.readText() }).optBoolean("latest", false)
        }.getOrDefault(false)
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
