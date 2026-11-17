package com.mlbb.assistant.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier

@Entity(tableName = "heroes")
data class HeroEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val role: String,
    val secondaryRole: String?,
    val lane: String,
    val tier: String,
    val patchTrend: Double,
    val winRate: Double,
    val pickRate: Double,
    val banRate: Double,
    val imageUrl: String,
    val counters: List<Int>,
    val counteredBy: List<Int>,
    val synergies: List<Int>,
    val recommendedSpells: List<String>,
    val coreItems: List<CoreItem>,
    val flexLanes: List<String>,
    val isToxicMechanic: Boolean,
    val isOP: Boolean,
    /**
     * TD-01: True when this hero has a crowd-control ultimate.
     * Added in DB schema v3 — defaults to 0 (false) via migration.
     */
    val hasCCUlt: Boolean = false,
    val roamType: String? = null
) {
    fun toDomain() = Hero(
        id                = id,
        name              = name,
        role              = role,
        secondaryRole     = secondaryRole,
        lane              = Lane.entries.firstOrNull { it.name == lane } ?: Lane.GOLD,
        tier              = Tier.fromString(tier),
        patchTrend        = patchTrend,
        winRate           = winRate,
        pickRate          = pickRate,
        banRate           = banRate,
        imageUrl          = imageUrl,
        counters          = counters,
        counteredBy       = counteredBy,
        synergies         = synergies,
        recommendedSpells = recommendedSpells,
        coreItems         = coreItems,
        flexLanes         = flexLanes.mapNotNull { l -> Lane.entries.firstOrNull { it.name == l } },
        isToxicMechanic   = isToxicMechanic,
        isOP              = isOP,
        hasCCUlt          = hasCCUlt,
        roamType          = roamType
    )
}
