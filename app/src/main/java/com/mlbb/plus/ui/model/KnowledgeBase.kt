package com.mlbb.plus.ui.model

enum class KnowledgeActionTarget { HEROES, ITEMS, PATCH }

data class KnowledgeTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

data class KnowledgeAction(
    val label: String,
    val target: KnowledgeActionTarget
)

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val categoryLabel: String,
    val summary: String,
    val details: List<String>,
    val table: KnowledgeTable? = null,
    val mistakes: List<String> = emptyList(),
    val appTip: String? = null,
    val action: KnowledgeAction? = null,
    val relatedIds: List<String> = emptyList()
)

object KnowledgeBaseData {
    val entries: List<KnowledgeEntry> = listOf(
        // First entry on purpose: the Knowledge Base opens on entries.first(), so this is
        // the landing topic. It replaced the QUICK START card that used to sit on Home
        // (removed 2026-11-27 to keep Home focused on position -> playstyle -> launch).
        KnowledgeEntry(
            id = "quick-start",
            title = "Quick Start: Set Up Your Floating Guide",
            categoryLabel = "Getting Started",
            summary = "Three steps to a working in-game guide: choose your position, choose a playstyle, then allow MLBB Plus to appear over other apps.",
            details = listOf(
                "Step 1 — Choose your position: on Home, open the Position dropdown and pick the role you play most often (Roam, Jungle, Mid, Gold or EXP). The briefing updates immediately and the choice is remembered next time you open the app.",
                "Step 2 — Choose a playstyle: the Playstyle dropdown lists the archetypes for that role, for example Crowd Control or Initiator for Roam. Win conditions, what to avoid, recommended picks and the loadout all follow your selection.",
                "Step 3 — Allow the guide to appear: tap Enable Widget. Android opens the “Display over other apps” page — find MLBB Plus, turn the switch on, and return to the app. Home then reads PERMISSION ON.",
                "Launch it: tap Launch Widget. An ongoing notification confirms the overlay is running; switch to Mobile Legends and the widget appears over the match. Stop it from that notification or with the overlay × button at any time.",
                "Android does not let an app grant this permission to itself, so there is no in-app switch. MLBB Plus can only open the system page and then re-read the real permission state when you come back — which is why the status only changes after you return."
            ),
            table = KnowledgeTable(
                headers = listOf("Step", "Where", "What you should see"),
                rows = listOf(
                    listOf("1. Position", "Home → guide card", "Your role in the Position dropdown; briefing text changes"),
                    listOf("2. Playstyle", "Home → guide card", "Win / avoid conditions, picks and loadout for that archetype"),
                    listOf("3. Permission", "Android settings", "“Display over other apps” on for MLBB Plus; Home shows PERMISSION ON"),
                    listOf("4. Launch", "Home → Launch Widget", "Ongoing notification; overlay visible once you switch to the game")
                )
            ),
            mistakes = listOf(
                "Tapping Launch Widget before granting the permission — nothing can appear until “Display over other apps” is on.",
                "Assuming the permission failed because the app looks unchanged: the status chip only flips after you return from Android settings.",
                "Leaving the guide on a playstyle you do not actually play — the timed steps, spells and picks all follow the selected archetype."
            ),
            appTip = "Settings → Overlay permission card (“Open Android settings” / “Manage Android permission”) reopens the Android permission page and shows the current status. Turn on “Start the guide when the app opens” there to launch it automatically once permission is granted.",
            relatedIds = listOf("roles-lanes", "turtle-lord", "meta")
        ),
        KnowledgeEntry(
            id = "voice-guide",
            title = "Voice Guide: Hear Your Next Play",
            categoryLabel = "Getting Started",
            summary = "Turn the floating guide into a coach that speaks: tap Speaker to hear the current step in eight to eighteen words, and let it read each new step automatically while you keep your eyes on the fight.",
            details = listOf(
                "Switch it on first: Settings \u2192 Voice Guide \u2192 Voice Guide. Until then the overlay is unchanged - no voice controls, no audio focus, no voice pack unpacked on disk.",
                "In the overlay you get a 48dp voice row: Speak, Repeat, Prev, Next and Stop. Speak reads the step you are looking at; Prev and Next read the neighbouring steps without touching your game timer, so you can hear what comes next before it happens.",
                "Auto-speak reads each new step once. It is keyed to the step, not to the clock, so a recomposition or a paused timer never repeats a line. Switch it off in Settings if you only want the Speaker button.",
                "What you hear is condensed, not read verbatim. The screen keeps the full paragraph; the voice gets the phase, the call-out and the opening instruction, trimmed at a sentence or clause boundary. Abbreviations are expanded only when spoken - \u201cRetri\u201d becomes \u201cRetribution\u201d, \u201cEXP\u201d becomes \u201cE X P lane\u201d - and field names, ids and links are never read aloud.",
                "Game audio is ducked, not paused. MLBB Plus asks Android for transient focus that may duck, so your game and voice chat drop in volume while the coach talks and come straight back. An incoming call stops the coach immediately.",
                "Four offline voices bundled inside the APK: two Kokoro natural voices (Natural female and Natural male, about 103 MB bundled) and two Piper fast voices (Fast female and Fast male, about 21 MB each bundled). Unpacking runs completely offline with zero network permissions. Delete a pack in Settings and the coach falls back to the next available voice, then to your phone\u2019s own system voice.",
                "Using TalkBack? Voice Guide steps aside automatically while touch exploration is on, so two voices never talk over each other. Turn TalkBack off and it resumes where you left it."
            ),
            table = KnowledgeTable(
                headers = listOf("Control", "Where", "What it does"),
                rows = listOf(
                    listOf("Voice Guide switch", "Settings \u2192 Voice Guide", "Master switch. Off means the overlay is exactly as it was before"),
                    listOf("Speak", "Overlay voice row", "Reads the step currently on screen; unpacks the selected voice pack the first time"),
                    listOf("Pause / Resume", "Overlay voice row", "Holds the sentence mid-way on the neural voices, restarts it on the system voice"),
                    listOf("Repeat", "Overlay voice row", "Says the same step again"),
                    listOf("Prev / Next", "Overlay voice row", "Reads the neighbouring step without moving your game timer"),
                    listOf("Stop", "Overlay voice row or notification", "Silences the coach and hands audio straight back to the game"),
                    listOf("Play / Pause voice", "Ongoing notification", "Drives the coach without touching the overlay"),
                    listOf("Speed", "Settings \u2192 Voice Guide", "0.85\u00d7 relaxed, 1.0\u00d7 normal, 1.25\u00d7 brisk"),
                    listOf("Announce enemy battle spells", "Settings \u2192 Voice Guide", "Off by default: says \u201cEnemy Flicker used\u201d when you tap a lane in the cooldown tracker")
                )
            ),
            mistakes = listOf(
                "Expecting sound the moment the overlay launches - the guide never speaks on its own, the first Speaker tap is what starts it.",
                "Thinking you need internet to install voices - all packs ship inside the APK and unpack locally into private app storage, as long as you have enough free space.",
                "Turning on \u201cAnnounce enemy battle spells\u201d and then wondering why it stays quiet: it only fires when you tap a lane in the cooldown tracker, and at most once per spell per cooldown.",
                "Deleting every voice pack and blaming the feature - with no pack installed the coach falls back to your phone\u2019s system voice, which some devices do not ship in English."
            ),
            appTip = "Settings \u2192 Voice Guide shows which pack is installed and how much space it takes, lets you install or delete it, and has a Play test voice button so you can check the result before a match.",
            relatedIds = listOf("quick-start", "cd-spells", "turtle-lord", "rotation")
        ),
        KnowledgeEntry(
            id = "meta",
            title = "What is \"Meta\" & Top Meta Heroes?",
            categoryLabel = "Meta & App",
            summary = "META (\"Most Effective Tactics Available\") refers to the heroes, item builds, and map strategies winning most reliably in the current patch.",
            details = listOf(
                "A hero enters the meta when recent buffs, item adjustments, or objective changes amplify their kit—making them stronger than average alternatives in ranked play.",
                "In MLBB Plus, heroes marked as meta picks combine strong current-patch performance with high draft priority on Original Server.",
                "The Patch tab summarizes the active patch headline and every hero/item change; Hero Explorer's default Sort by → Recommended puts top-tier heroes (then highest win rate) at the top of the grid."
            ),
            mistakes = listOf(
                "Blind-picking a meta hero you have zero practice on into hard counters—comfort on an A-tier hero often outperforms first-timing an S+ pick.",
                "Assuming last month's meta is unchanged after a balance patch or item rework."
            ),
            appTip = "Open Hero Explorer. The default Sort by → Recommended lists top-tier heroes first, then by win rate.",
            action = KnowledgeAction("Open Hero Explorer", KnowledgeActionTarget.HEROES),
            relatedIds = listOf("tiers", "wr-pr-br", "patch-terms")
        ),
        KnowledgeEntry(
            id = "tiers",
            title = "Hero Tiers (S+, S, A, B)",
            categoryLabel = "Meta & App",
            summary = "Tier badges summarize a hero's overall draft strength based on win rate, ban pressure, lane versatility, and teamfight impact.",
            details = listOf(
                "Higher tiers reflect heroes that consistently apply pressure across different team compositions rather than relying on rare conditions.",
                "You can filter directly by tier or sort the hero grid by Tier inside Hero Explorer."
            ),
            table = KnowledgeTable(
                headers = listOf("Tier", "Priority", "Typical Ranked Role"),
                rows = listOf(
                    listOf("S+", "First pick or ban", "Defines the patch; dominates or flexes into multiple comps"),
                    listOf("S", "Core meta priority", "Consistently strong across almost all ranked drafts"),
                    listOf("A", "Solid standard pick", "Reliable when paired with decent synergy or favorable lanes"),
                    listOf("B", "Situational / comfort", "Needs specific matchups, counters, or high hero mastery")
                )
            ),
            appTip = "Tap any hero portrait to view their full tier snapshot, win/pick/ban context, and core build.",
            action = KnowledgeAction("Filter by Tier in Heroes", KnowledgeActionTarget.HEROES),
            relatedIds = listOf("meta", "wr-pr-br", "counters-synergy")
        ),
        KnowledgeEntry(
            id = "wr-pr-br",
            title = "Win Rate, Pick Rate & Ban Rate",
            categoryLabel = "Meta & App",
            summary = "Three draft metrics that separate truly dominant heroes from niche ones.",
            details = listOf(
                "Always read all three numbers together—Win Rate alone can mislead if a hero is rarely picked or only chosen as a last-pick counter."
            ),
            table = KnowledgeTable(
                headers = listOf("Metric", "What It Measures", "How to Read It"),
                rows = listOf(
                    listOf("Win Rate (WR)", "% of games won", "51%+ is healthy; high-skill assassins can still be strong at 49–50%"),
                    listOf("Pick Rate", "How often chosen", "High pick rate + positive WR proves broad reliability"),
                    listOf("Ban Rate", "Removed in Draft", "High ban rate signals heroes teams fear facing (Epic+ Draft)")
                )
            ),
            mistakes = listOf(
                "Chasing a 54% WR hero with a 0.3% pick rate—often inflated by one-trick specialists."
            ),
            relatedIds = listOf("meta", "tiers", "counters-synergy")
        ),
        KnowledgeEntry(
            id = "counters-synergy",
            title = "Counters, Works Against & Synergies",
            categoryLabel = "Meta & App",
            summary = "Matchup relationships showing which heroes a pick shuts down, struggles into, or combos with.",
            details = listOf(
                "Works Against: Opponents whose mobility, healing, or channeling is disrupted by your hero's kit.",
                "Bad Against: Enemies that out-range, burst, or lock you down before you can function.",
                "Best Partners / Synergies: Allies whose skills set up or protect your hero (for example, an AoE setter tank paired with follow-up burst)."
            ),
            appTip = "Check COUNTERS and SYNERGIES tabs on any hero page, or HERO LINKS inside Item Explorer.",
            relatedIds = listOf("tiers", "cc", "damage-types-pen")
        ),
        KnowledgeEntry(
            id = "patch-terms",
            title = "Patch Tags (Buff, Nerf, Revamp) & Servers",
            categoryLabel = "Meta & App",
            summary = "Labels on Patch Notes explain whether a hero or item was strengthened, tuned down, or rebuilt.",
            details = listOf(
                "Original Server is the live game everyone plays in Ranked. Advanced Server is the early test server where experimental changes appear first."
            ),
            table = KnowledgeTable(
                headers = listOf("Tag", "Meaning", "Practical Impact"),
                rows = listOf(
                    listOf("Buff", "Direct power increase", "Higher damage, shorter CD, or stronger utility"),
                    listOf("Nerf", "Direct power reduction", "Lower numbers or longer cooldowns to balance priority"),
                    listOf("Revamp", "Kit / visual rework", "Skills and playstyle changed significantly (e.g. Popol & Kupa)"),
                    listOf("Adjust", "Power shift", "Stronger in one phase or stat, weaker in another")
                )
            ),
            action = KnowledgeAction("Read Patch Notes", KnowledgeActionTarget.PATCH),
            relatedIds = listOf("meta", "cd-spells")
        ),
        KnowledgeEntry(
            id = "rotation",
            title = "What is Rotation?",
            categoryLabel = "Rotation & Roles",
            summary = "Rotation means moving between lanes and river objectives between waves or camps to create local numbers advantages without sacrificing farm.",
            details = listOf(
                "Wave priority (\"prio\") comes first: clear your minion wave before your lane opponent does so you can leave lane unseen while they are stuck farming.",
                "Standard early rotation: Mid + Roam help Jungler contest Lithowanderer around 0:45, pressure a side lane, and arrive in time for the 2:00 Turtle near EXP lane.",
                "When you are late to a cross-map play, do not chase a lost fight—trade by pushing an outer turret or invading the opposite jungle camp."
            ),
            table = KnowledgeTable(
                headers = listOf("Window", "Priority Movement", "Goal"),
                rows = listOf(
                    listOf("0:00 – 0:45", "Mid clears fast; Roam guards buff", "Secure Lithowanderer & hit Level 2 first"),
                    listOf("0:45 – 1:50", "Jungler hits Lv.4; Roam/Mid shadow", "Look for side-lane gank or burn enemy Flicker"),
                    listOf("1:50 – 2:30", "EXP, Mid, Roam converge EXP side", "Secure or trade first Turtle at 2:00"),
                    listOf("5:00+", "Group around outer turret shields dropping", "Break outer turrets to open enemy jungle")
                )
            ),
            mistakes = listOf(
                "Rotating while a full minion wave crashes into your own turret—you lose guaranteed gold and turret HP.",
                "Walking through unwarded river bushes alone instead of pathing behind your Roamer."
            ),
            appTip = "Use the Floating Widget on Home for real-time phase checklists, vision reminders, and objective alerts.",
            relatedIds = listOf("roles-lanes", "turtle-lord", "vision-bushes", "gank-zone-split")
        ),
        KnowledgeEntry(
            id = "roles-lanes",
            title = "The 5 Roles & Lanes",
            categoryLabel = "Rotation & Roles",
            summary = "Every team assigns five distinct positions—Roam, Jungle, Mid, Gold, and EXP—with built-in gold and EXP rules.",
            details = listOf(
                "Each role guide in MLBB Plus is tailored to that lane's timings, starting routes, and objective duties."
            ),
            table = KnowledgeTable(
                headers = listOf("Position", "Early Income Rule", "Core Responsibility"),
                rows = listOf(
                    listOf("Roam", "Roam Boots funnel gold to allies", "Vision, bush control, engage or peel for carries"),
                    listOf("Jungle", "Jungle Boots + creep camps", "Hit Lv.4 quickly, gank lanes, secure Turtle & Lord"),
                    listOf("Mid", "Standard mid wave", "Fast wave clear, rotate with Roam/Jungle, AoE magic/control"),
                    listOf("Gold", "Bonus gold on Gold Lane siege minions", "Farm safely early; deliver sustained late-game DPS"),
                    listOf("EXP", "Bonus EXP on EXP Lane siege minions", "Hit Lv.4 early; front-line or flank at Turtle/Lord")
                )
            ),
            relatedIds = listOf("rotation", "items-passive-active", "turtle-lord")
        ),
        KnowledgeEntry(
            id = "gank-zone-split",
            title = "Gank, Zone, Peel & Split Push",
            categoryLabel = "Rotation & Roles",
            summary = "Four tactical teamfight and map calls you will see throughout role guide steps.",
            details = listOf(
                "Gank: Ambushing an overextended enemy lane with 2–3 allies to force a kill, spell burn, or turret plate.",
                "Zone: Positioning in front of Turtle, Lord, or a minion wave to threaten CC so enemies cannot safely step in to steal.",
                "Peel: Saving your CC or shields to knock divers off your Marksman or Mage rather than blindly chasing the enemy backline.",
                "Split Push: Pushing a side lane far from the main group to force 1–2 enemies to defend while your team takes an objective elsewhere."
            ),
            relatedIds = listOf("rotation", "wave-control", "cc")
        ),
        KnowledgeEntry(
            id = "wave-control",
            title = "Wave Freeze, Slow Push & Lane Cutting",
            categoryLabel = "Rotation & Roles",
            summary = "Controlling where minion waves collide lets you starve an enemy laner or create extra seconds to rotate.",
            details = listOf(
                "Wave Freeze: Holding enemy minions just outside your turret range and only taking last-hits, forcing the opponent to walk far forward into gank danger.",
                "Slow Push: Clearing enemy ranged/siege minions while leaving melee minions alive so two friendly waves stack together and threaten a turret.",
                "Lane Cutting: Intercepting and clearing an incoming wave behind the enemy outer turret (high risk, high tempo) so you can rotate first."
            ),
            mistakes = listOf(
                "Mindlessly spamming skills on every wave when ahead—breaking your own freeze gives the enemy safe farm under their turret."
            ),
            relatedIds = listOf("rotation", "gank-zone-split")
        ),
        KnowledgeEntry(
            id = "vision-bushes",
            title = "Vision & Bush Checking",
            categoryLabel = "Rotation & Roles",
            summary = "Vision means knowing where enemies are—and safely revealing unwarded river or jungle bushes before your carries walk near them.",
            details = listOf(
                "Never \"face-check\" a dark bush with a squishy Marksman or Mage. Let the tanky Roamer/EXP walk first or probe the bush with a long-range skill.",
                "Common ambush spots: Mid side bushes, the pixel bush near Turtle/Lord river, and the buff wall bushes behind outer turrets.",
                "Checking minimap last-seen icons before crossing river prevents most mid-game throws."
            ),
            relatedIds = listOf("rotation", "gank-zone-split", "turtle-lord")
        ),
        KnowledgeEntry(
            id = "cc",
            title = "CC Meaning (Crowd Control Types)",
            categoryLabel = "Combat & CC",
            summary = "CC (\"Crowd Control\") refers to any skill effect that restricts enemy movement, basic attacks, or ability casting.",
            details = listOf(
                "Chaining CC—layering a second stun right as the first ends—locks high-mobility assassins out of teamfights.",
                "Hard CC interrupts channeling abilities (like Odette or Pharsa ults), whereas Slows and Immobilize/Root do not."
            ),
            table = KnowledgeTable(
                headers = listOf("CC Type", "Severity", "Stops Casts?", "Removed by Purify?"),
                rows = listOf(
                    listOf("Suppress (Franco / Kaja)", "Hardest CC", "Yes", "No (cannot purify or reduce)"),
                    listOf("Stun / Freeze / Petrify", "Hard CC", "Yes", "Yes"),
                    listOf("Airborne / Knockback", "Hard CC", "Yes", "Partial (stops follow-up; displacement finishes)"),
                    listOf("Taunt / Terrify / Morph", "Hard CC", "Yes", "Yes"),
                    listOf("Silence", "Soft CC", "Blocks new casts", "Yes"),
                    listOf("Immobilize / Root", "Soft CC", "No (stops dashes/move)", "Yes"),
                    listOf("Slow", "Soft CC", "No", "Yes")
                )
            ),
            relatedIds = listOf("resilience-purify", "cd-spells", "gank-zone-split")
        ),
        KnowledgeEntry(
            id = "resilience-purify",
            title = "Resilience, Purify & CC Immunity",
            categoryLabel = "Combat & CC",
            summary = "Counter-tools that shorten or cleanse Crowd Control when facing heavy lockdown drafts.",
            details = listOf(
                "Resilience (from Tough Boots): Reduces the duration of most Hard and Soft CC (such as Stun, Slow, Silence, Freeze) by a percentage.",
                "Purify (Battle Spell, 90s CD): Instantly removes debuffs and grants brief CC immunity + movement speed.",
                "CC Immunity skills (e.g. Diggie ult, Martis S2, Popol mount): Block incoming CC effects, though Suppress still pierces normal Purify/Resilience."
            ),
            appTip = "Check Tough Boots in Item Explorer or inspect hero skills for CC immunity notes.",
            relatedIds = listOf("cc", "cd-spells")
        ),
        KnowledgeEntry(
            id = "damage-profiles",
            title = "Burst, DPS, Sustain & Poke",
            categoryLabel = "Combat & CC",
            summary = "Combat profile tags that describe how a hero wins fights and when they are strongest.",
            details = listOf(
                "Knowing a hero's damage profile helps you judge whether to look for quick picks or extended skirmishes."
            ),
            table = KnowledgeTable(
                headers = listOf("Profile", "How It Works", "Counterplay"),
                rows = listOf(
                    listOf("Burst", "Deletes squishies in a 1–2s combo", "Build Immortality, Winter Crown, or Athena/Valkyrie/Antique"),
                    listOf("DPS", "Steady basic-attack damage over time", "Dominance Ice / Blade Armor + diving the backline"),
                    listOf("Sustain", "Outlasts fights with heals & shields", "Buy Anti-Heal (Lifebane) early and focus target"),
                    listOf("Poke", "Chips HP from long range pre-fight", "Hard engage before getting whittled down")
                )
            ),
            relatedIds = listOf("damage-types-pen", "counters-synergy")
        ),
        KnowledgeEntry(
            id = "damage-types-pen",
            title = "Physical, Magic, True Damage, PEN & Anti-Heal",
            categoryLabel = "Combat & CC",
            summary = "Choosing the right defensive or counter-build item depends on what damage type and healing the enemy relies on.",
            details = listOf(
                "Physical Damage is reduced by Physical Defense; Magic Damage is reduced by Magic Defense. Buy Physical PEN (Malefic Roar) or Magic PEN (Divine Glaive) when enemies build heavy armor.",
                "True Damage (white numbers) bypasses Physical/Magic Defense and damage reduction, though HP shields still absorb it.",
                "Anti-Heal / Lifebane cuts enemy HP healing and shield gain—mandatory against healers (Estes, Floryn, Angela) and high-sustain fighters."
            ),
            table = KnowledgeTable(
                headers = listOf("Anti-Heal Item", "Best Built By", "Defense / Stat Focus"),
                rows = listOf(
                    listOf("Dominance Ice", "Tanks, Roamers, EXP Fighters", "Physical Defense + Mana + Attack Speed slow"),
                    listOf("Sea Halberd", "Marksmen, Physical Assassins", "Physical Attack + Attack Speed + bonus vs high HP"),
                    listOf("Glowing Wand", "Mages, Magic Fighters", "Magic Power + % Max HP burn + Lifebane")
                )
            ),
            action = KnowledgeAction("Browse Items", KnowledgeActionTarget.ITEMS),
            relatedIds = listOf("damage-profiles", "items-passive-active")
        ),
        KnowledgeEntry(
            id = "cd-spells",
            title = "CD (Cooldown), CDR Cap & Battle Spell Timers",
            categoryLabel = "Combat & CC",
            summary = "CD is the wait time before a skill or Battle Spell is ready again. Calling out enemy Flicker or Retribution cooldowns sets up free kills.",
            details = listOf(
                "Cooldown Reduction (CDR) on items and emblems lowers hero skill cooldowns up to a 40% cap (45% with Enchanted Talisman), but does NOT reduce Battle Spell cooldowns.",
                "In the Floating Widget, tapping an enemy position icon starts a live countdown for their equipped Battle Spell."
            ),
            table = KnowledgeTable(
                headers = listOf("Battle Spell", "Cooldown", "Tactical Window When Down"),
                rows = listOf(
                    listOf("Flicker", "120s", "Immobilized carries have no blink escape for 2 full minutes"),
                    listOf("Purify / Execute", "90s", "Enemy is vulnerable to chain-CC or lacks finish pressure"),
                    listOf("Sprint / Vengeance / Petrify", "75s", "Sprint buffed from 100s -> 75s in Patch 2.2.23"),
                    listOf("Inspire", "60s", "Marksman loses burst attack-speed steroid"),
                    listOf("Retribution", "35s", "Jungler cannot secure Turtle/Lord cleanly while on CD")
                )
            ),
            appTip = "Tap any enemy spell pill inside the Floating Widget to track their cooldown live.",
            relatedIds = listOf("cc", "resilience-purify", "turtle-lord")
        ),
        KnowledgeEntry(
            id = "turtle-lord",
            title = "Turtle, Lord, Lithowanderer & Turret Shield Timers",
            categoryLabel = "Objectives & Items",
            summary = "Every neutral objective follows fixed match timers—arriving 10–15 seconds early lets your team claim river bushes first.",
            details = listOf(
                "The first Turtle always spawns on the EXP lane side at 2:00. In Season 42's Healing Turtle variant, defeating it summons a Turtle that pushes the strongest lane and heals allies.",
                "Once Lord spawns, focus on securing outer/inner turrets first so Lord marches into high-ground inhibitors safely."
            ),
            table = KnowledgeTable(
                headers = listOf("Objective", "Timing Rule", "Reward / Effect"),
                rows = listOf(
                    listOf("Lithowanderer", "0:45 spawn · 120s respawn", "River crab giving gold, mana regen & short river vision"),
                    listOf("Turret Shield", "0:00 – 5:00 (drops at 5:00)", "Reduced early turret damage; dealing hits grants plating gold"),
                    listOf("Turtle", "2:00 spawn · 120s respawn (max 3)", "Team gold + EXP + shield/heal; stops if slain after 6:00"),
                    listOf("Lord (Base)", "120s after last Turtle (or ~8:00) · 180s respawn", "Pushes weakest lane; charges turrets for True Damage"),
                    listOf("Lord (Evolved)", "18:00+ · 120s respawn", "Enhanced waves + stronger siege durability")
                )
            ),
            relatedIds = listOf("rotation", "roles-lanes", "vision-bushes")
        ),
        KnowledgeEntry(
            id = "items-passive-active",
            title = "Unique Passives, Actives & Boot Blessings",
            categoryLabel = "Objectives & Items",
            summary = "How item stacking rules, manual active skills, and Roam/Jungle boot upgrades work.",
            details = listOf(
                "Unique Passive: Effects marked \"Unique Passive\" (or carrying the same passive name) do not stack—buying two copies wastes gold.",
                "Active Skills: Items like Winter Crown, Wind of Nature, and Roam Conceal add a manual button with a dedicated cooldown.",
                "Roam & Jungle Blessings cost 0 extra gold after buying Boots and evolve automatically once you hit the gold/creep threshold."
            ),
            table = KnowledgeTable(
                headers = listOf("Roam Blessing", "Trigger", "Best For"),
                rows = listOf(
                    listOf("Conceal", "Active skill (camouflage + MS)", "Initiator tanks setting up surprise engages"),
                    listOf("Encourage", "Passive aura (+Attack / Magic / AS)", "Front-liners boosting sustained teamfight DPS"),
                    listOf("Favor", "Auto-heal lowest HP ally on heal/shield", "Support healers/shielders (Estes, Angela, Floryn, Rafaela)"),
                    listOf("Dire Hit", "Bonus hybrid burst below 35% HP", "Damage-oriented roamers (Natalia, Saber, Selena)")
                )
            ),
            action = KnowledgeAction("Open Item Explorer", KnowledgeActionTarget.ITEMS),
            relatedIds = listOf("roles-lanes", "damage-types-pen")
        )
    )
}
