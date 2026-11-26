package com.mlbb.plus.ui.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ============================================================
// plus/ui/model/Guides.kt  ->  NEW
// ------------------------------------------------------------
// Screen-side model of roam_guide.json / jungle_guide.json / mid_guide.json /
// gold_guide.json / exp_guide.json. The overlay widget keeps its own parser for
// the per-minute steps (it needs them every second); this file covers everything
// the SCREENS need: role types, picks, loadout, mechanics, spells, patch version.
//
// Loaded once from GameData.load() AFTER heroes, so pick names can be linked to
// heroes.json ids (HeroPick.heroId) and the screens can open Hero Detail.
//
// Naming: everything here is Guide*/HeroPick/... on purpose. OverlayWidgets.kt
// declares private GuideData/GuidePlan/GuideStep/GuideBlessing in another package;
// do not reuse those names here.
// ============================================================

data class GuideSpell(val name: String, val cooldownSeconds: Int, val note: String = "")

/** Blessing (roam), Retribution type (jungle) or Emblem entry (mid / gold / exp). */
data class GuideBuff(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val effect: String = "",
    val cooldown: String = "",
    val bestFor: List<String> = emptyList(),
    val unlock: String = ""
)

data class GuideNote(val title: String, val text: String)

data class HeroPick(
    val name: String,
    val archetype: String = "",
    val note: String = "",
    /** id in heroes.json, or null when the name is not in heroes.json. */
    val heroId: Int? = null
)

data class GuideType(
    val id: String,
    val label: String,
    val playstyle: String = "",
    val heroes: List<HeroPick> = emptyList(),
    val battleSpells: List<String> = emptyList(),
    val emblem: String = "",
    val coreItems: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val winCondition: String = "",
    val loseCondition: String = "",
    val blessing: String = "",
    val stepCount: Int = 0,
    /** One-line archetype blurb from <role>_types_verified.mapping (may be blank). */
    val blurb: String = ""
) {
    /** "Initiator - Start Fight" -> "Initiator" (what the widget header and dropdowns show). */
    val shortLabel: String get() = label.substringBefore(" - ").trim().ifBlank { id }
}

data class GuideFile(
    val role: String,
    val assetName: String,
    val title: String = "",
    val version: String = "",
    val patch: String = "",
    val latest: Boolean = false,
    val season: String = "",
    val updated: String = "",
    val mechanics: List<GuideNote> = emptyList(),
    val globalRules: List<String> = emptyList(),
    val heroPoolRule: String = "",
    /** Blessings (roam) or Retribution types (jungle); empty for the other roles. */
    val buffs: List<GuideBuff> = emptyList(),
    val buffsLabel: String = "",
    val emblemGuide: List<GuideBuff> = emptyList(),
    val spells: List<GuideSpell> = emptyList(),
    val spellDefaults: Map<String, String> = emptyMap(),
    val types: List<GuideType> = emptyList()
) {
    val stepCount: Int get() = types.sumOf { it.stepCount }
    fun type(id: String): GuideType? = types.firstOrNull { it.id.equals(id, ignoreCase = true) }
}

/** "This hero is a pick in <role> guide, <type>". */
data class HeroGuideRef(val role: String, val typeId: String, val typeLabel: String, val note: String)

/** "This item is a core item in <role> guide, <type>". */
data class ItemGuideRef(val role: String, val typeId: String, val typeLabel: String)

// ---------- loader ----------

private fun JSONArray?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        runCatching {
            val v = get(i)
            if (v == JSONObject.NULL) null else v.toString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

private fun JSONObject.str(key: String): String =
    if (has(key) && !isNull(key)) optString(key).trim() else ""

private fun titleCase(key: String): String =
    key.split('_').filter { it.isNotEmpty() }.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

object GuideLoader {

    fun assetNameFor(role: String): String = "${role.lowercase()}_guide.json"

    fun loadAll(context: Context, heroes: List<Hero>): Map<String, GuideFile> {
        val byName = heroes.associateBy { it.name.trim().lowercase() }
        val out = LinkedHashMap<String, GuideFile>()
        for (role in RoleTypes.roles) {
            load(context, role, byName)?.let { out[role] = it }
        }
        return out
    }

    private fun parseBuff(o: JSONObject) = GuideBuff(
        id = o.str("id"), name = o.str("name"), type = o.str("type"), effect = o.str("effect"),
        cooldown = o.str("cooldown"), bestFor = o.optJSONArray("best_for").toStrings(), unlock = o.str("unlock")
    )

    private fun parseBuffs(arr: JSONArray?): List<GuideBuff> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseBuff(it) } }
    }

    private fun load(context: Context, role: String, heroesByName: Map<String, Hero>): GuideFile? = runCatching {
        val assetName = assetNameFor(role)
        val root = JSONObject(context.assets.open(assetName).bufferedReader().use { it.readText() })
        val meta = root.optJSONObject("meta") ?: JSONObject()
        val key = role.lowercase()

        val mechanics = buildList {
            val m = meta.optJSONObject("${key}_mechanics")
            if (m != null) {
                val iter = m.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    val text = m.str(k)
                    if (text.isNotBlank()) add(GuideNote(titleCase(k), text))
                }
            }
        }

        val blurbs = buildMap<String, String> {
            val mapping = meta.optJSONObject("${key}_types_verified")?.optJSONObject("mapping")
            if (mapping != null) {
                val iter = mapping.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    put(k, mapping.str(k))
                }
            }
        }
        // Mapping keys are the archetype ("burst"); type ids carry a lane suffix ("burst_gold").
        fun blurbFor(typeId: String): String = blurbs.entries
            .filter { typeId == it.key || typeId.startsWith(it.key + "_") }
            .maxByOrNull { it.key.length }?.value.orEmpty()

        val rolesArr = root.optJSONArray("roles") ?: root.optJSONArray("plans") ?: root.optJSONArray("types") ?: JSONArray()
        val types = buildList {
            for (i in 0 until rolesArr.length()) {
                val o = rolesArr.optJSONObject(i) ?: continue
                val steps = o.optJSONArray("steps")?.length() ?: 0
                if (steps == 0) continue // the widget skips types without steps too
                val id = o.str("id")
                val heroArr = o.optJSONArray("heroes")
                val picks = buildList {
                    if (heroArr != null) {
                        for (j in 0 until heroArr.length()) {
                            val entry = heroArr.optJSONObject(j)
                            val name = entry?.str("name") ?: heroArr.optString(j).trim()
                            if (name.isBlank()) continue
                            add(
                                HeroPick(
                                    name = name,
                                    archetype = entry?.str("archetype").orEmpty(),
                                    note = entry?.str("note").orEmpty(),
                                    heroId = heroesByName[name.lowercase()]?.id
                                )
                            )
                        }
                    }
                }
                val coreArr = o.optJSONArray("core_items")
                val core = buildList {
                    if (coreArr != null) {
                        for (j in 0 until coreArr.length()) {
                            val entry = coreArr.optJSONObject(j)
                            val name = entry?.str("name") ?: coreArr.optString(j).trim()
                            if (name.isNotBlank()) add(name)
                        }
                    }
                }
                add(
                    GuideType(
                        id = id,
                        label = o.str("label").ifBlank { id },
                        playstyle = o.str("playstyle"),
                        heroes = picks,
                        battleSpells = o.optJSONArray("battle_spells").toStrings(),
                        emblem = o.str("emblem"),
                        coreItems = core,
                        strengths = o.optJSONArray("strengths").toStrings(),
                        weaknesses = o.optJSONArray("weaknesses").toStrings(),
                        winCondition = o.str("win_condition"),
                        loseCondition = o.str("lose_condition"),
                        blessing = o.str("blessing"),
                        stepCount = steps,
                        blurb = blurbFor(id)
                    )
                )
            }
        }
        if (types.isEmpty()) return@runCatching null

        val bs = root.optJSONObject("battle_spells")
        val spellsArr = bs?.optJSONArray("spells")
        val spells = buildList {
            if (spellsArr != null) {
                for (i in 0 until spellsArr.length()) {
                    val s = spellsArr.optJSONObject(i) ?: continue
                    val name = s.str("name")
                    val cd = s.optInt("cooldown_seconds", 0)
                    if (name.isNotBlank() && cd > 0) add(GuideSpell(name, cd, s.str("note")))
                }
            }
        }
        val defaults = buildMap<String, String> {
            val d = bs?.optJSONObject("defaults")
            if (d != null) {
                val iter = d.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    put(k, d.str(k))
                }
            }
        }
        val blessings = parseBuffs(root.optJSONArray("blessings"))
        val retributions = parseBuffs(root.optJSONArray("retribution_types"))

        GuideFile(
            role = role,
            assetName = assetName,
            title = meta.str("title"),
            version = meta.str("version"),
            patch = meta.str("patch"),
            latest = meta.optBoolean("latest", root.optBoolean("latest", false)),
            season = meta.str("season"),
            updated = meta.str("updated"),
            mechanics = mechanics,
            globalRules = meta.optJSONArray("global_rules").toStrings(),
            heroPoolRule = meta.optJSONObject("hero_pool_rule")?.str("rule").orEmpty(),
            buffs = blessings.ifEmpty { retributions },
            buffsLabel = if (blessings.isNotEmpty()) "Blessings" else if (retributions.isNotEmpty()) "Retribution" else "",
            emblemGuide = parseBuffs(root.optJSONArray("emblem_guide")),
            spells = spells,
            spellDefaults = defaults,
            types = types
        )
    }.getOrNull()
}
