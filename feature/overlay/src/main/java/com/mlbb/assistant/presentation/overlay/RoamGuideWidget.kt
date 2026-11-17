package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.OverlayBackground
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import kotlinx.coroutines.delay

private val GuideMuted = Color(0xFFB6BBC7)
private val GuideGreen = Color(0xFF72D6A0)

private data class RoamStep(val window: String, val task: String)

private data class RoamPlan(
    val id: String,
    val label: String,
    val blessing: String,
    val heroes: String,
    val steps: List<RoamStep>
)

private val roamPlans = listOf(
    RoamPlan(
        "engage", "Engage Tank", "Conceal / Encourage",
        "Tigreal, Khufra, Atlas, Minotaur, Lolita, Johnson, Hylos, Grock, Baxia, Gloo, Edith, Akai, Belerick, Gatotkaca, Kalea",
        listOf(
            RoamStep("00:00–00:40", "Buy Roam blessing. Check the enemy jungle entrance from a safe angle; do not donate first blood."),
            RoamStep("00:40–01:20", "Help mid clear, then move with your jungler. Hold key crowd control; threaten a counter-invade only with lane priority."),
            RoamStep("01:20–01:50", "Move toward the first Turtle side. Sweep river bushes and keep an escape route."),
            RoamStep("01:50–02:30", "Be at Turtle before it spawns. Zone enemy approaches; engage only when your damage dealers can follow."),
            RoamStep("02:30–04:00", "After the objective, protect the nearest carry and help mid regain priority. Do not chase into fog."),
            RoamStep("04:00–06:00", "Track the next Turtle from its actual respawn. Start vision control early and save your ultimate for the contest."),
            RoamStep("06:00–08:00", "Escort your team through river and keep a frontline between enemies and your damage dealers. Objective timing is match-state dependent."),
            RoamStep("08:00–10:00", "Prepare Lord-side vision when Lord becomes available. Look for a numbers advantage before starting a fight."),
            RoamStep("10:00–12:00", "Lead safe rotations, check bushes before carries cross them, and convert won fights into towers or neutral objectives."),
            RoamStep("12:00–15:00", "Do not face-check alone. Hold one reliable control skill to interrupt divers or punish a committed enemy."),
            RoamStep("15:00–18:00", "Stay close enough to peel your main damage dealer. Use Conceal to start a coordinated fight or disengage a losing one.")
        )
    ),
    RoamPlan(
        "pickoff", "Pick-off Tank", "Conceal / Dire Hit",
        "Franco, Kaja, Jawhead",
        listOf(
            RoamStep("00:00–00:40", "Buy Roam blessing and identify the enemy jungler's likely start. Avoid an unsupported invade."),
            RoamStep("00:40–01:20", "Rotate through mid and threaten a hook or suppression only when an ally is in range."),
            RoamStep("01:20–01:50", "Check the Turtle-side river and mark the enemy jungler or mid laner. Keep your key catch skill ready."),
            RoamStep("01:50–02:30", "At first Turtle, punish isolated targets and interrupt the enemy Retribution user; don't abandon your jungler."),
            RoamStep("02:30–04:00", "Move between mid and Gold lane. Create short ambushes from controlled bushes, then leave before being collapsed on."),
            RoamStep("04:00–06:00", "Use the actual next Turtle spawn to time a pick. A caught enemy is an objective window, not a reason to chase farther."),
            RoamStep("06:00–08:00", "Track enemy carry positions and flank routes. Keep your ultimate for a high-value target or objective steal denial."),
            RoamStep("08:00–10:00", "Set up Lord vision from the sides. Catch an enemy rotating alone before your team commits to Lord."),
            RoamStep("10:00–12:00", "Shadow the teammate with burst damage. Avoid starting fights while your follow-up is clearing a wave."),
            RoamStep("12:00–15:00", "Threaten fog angles around objectives. If no clean pick exists, protect your carry instead of forcing one."),
            RoamStep("15:00–18:00", "Save suppression or displacement for the enemy's most dangerous diver or carry. One late-game pick can decide the match.")
        )
    ),
    RoamPlan(
        "healer", "Healer Support", "Favor",
        "Estes, Rafaela, Angela, Floryn",
        listOf(
            RoamStep("00:00–00:40", "Buy Roam blessing. Stay near the team without taking jungle experience or exposing yourself to an invade."),
            RoamStep("00:40–01:20", "Help mid sustain the first clear, then rotate toward the lane most likely to be pressured."),
            RoamStep("01:20–01:50", "Move with an ally to Turtle-side river. Keep healing resources available for the first skirmish."),
            RoamStep("01:50–02:30", "Arrive before Turtle. Heal and protect the jungler; avoid standing where enemy area control hits your whole team."),
            RoamStep("02:30–04:00", "Follow the strongest nearby teammate. Heal after trades and reset safely rather than face-checking."),
            RoamStep("04:00–06:00", "Prepare for the next Turtle based on its real respawn. Keep your carry healthy before the objective starts."),
            RoamStep("06:00–08:00", "Stay within ability range of your frontline and marksman. Save major healing or protection for the enemy engage."),
            RoamStep("08:00–10:00", "Group before Lord. Heal between pokes and keep a safe position behind your frontline."),
            RoamStep("10:00–12:00", "Escort the team through contested areas; let a tank check bushes and do not enter dark river first."),
            RoamStep("12:00–15:00", "Prioritize the teammate who can deal sustained damage. Use Favor's heal trigger on the ally who needs it most."),
            RoamStep("15:00–18:00", "Keep your core carry alive through the first burst. Disengage and regroup if key healing or ultimate tools are unavailable.")
        )
    ),
    RoamPlan(
        "utility", "Utility / Disengage", "Encourage",
        "Diggie, Mathilda, Chip, Carmilla, Faramis, Alice, Nana, Hirara",
        listOf(
            RoamStep("00:00–00:40", "Buy Roam blessing. Scout safely and identify whether your team needs anti-engage, mobility, or teamfight setup."),
            RoamStep("00:40–01:20", "Help mid clear and use utility to secure river access. Avoid spending your escape or cleanse too early."),
            RoamStep("01:20–01:50", "Rotate toward Turtle and establish vision with a teammate. Keep a route to retreat."),
            RoamStep("01:50–02:30", "Support the Turtle contest with shields, cleanse, mobility, or area control. Disrupt enemy setup rather than diving alone."),
            RoamStep("02:30–04:00", "Connect lanes and help teammates rotate faster. Use defensive tools when the enemy commits, not on harmless poke."),
            RoamStep("04:00–06:00", "Track the next objective timer and escort your jungler through river. Save anti-CC or escape tools for the contest."),
            RoamStep("06:00–08:00", "Create safe paths for your team and deny enemy flanks. Use area control to split the fight."),
            RoamStep("08:00–10:00", "Prepare Lord-side routes. Use mobility or disengage to reset a bad setup instead of forcing a 50/50."),
            RoamStep("10:00–12:00", "Stay near the teammate most likely to be caught. Reveal approaches and keep your utility available for counter-engage."),
            RoamStep("12:00–15:00", "Use teamfight tools when multiple allies can benefit. Avoid spending major cooldowns on a single low-value target."),
            RoamStep("15:00–18:00", "Protect the team's formation. Stop dives, help carries reposition, and use Encourage value during grouped fights.")
        )
    ),
    RoamPlan(
        "damage", "Damage / Assassin", "Dire Hit",
        "Flex examples: Selena, Natalia (not tagged as ROAM in this bundled roster)",
        listOf(
            RoamStep("00:00–00:40", "Buy Roam blessing. Scout for information, not a forced level-one fight; leave if the enemy collapses."),
            RoamStep("00:40–01:20", "Help mid secure priority, then threaten a side-lane angle. Do not steal your jungler's camps."),
            RoamStep("01:20–01:50", "Look for a low-risk pick near Turtle side. Keep enough resources to contest the objective."),
            RoamStep("01:50–02:30", "Use traps, marks, or burst to reveal and pressure approaches. Prioritize vision and objective safety over a solo kill."),
            RoamStep("02:30–04:00", "Rotate through fog with an ally and punish isolated squishy targets. Leave after a failed attempt."),
            RoamStep("04:00–06:00", "Time picks around the actual Turtle respawn. Pressure the enemy damage dealer before your team starts the objective."),
            RoamStep("06:00–08:00", "Control side bushes and deny enemy rotations. Avoid trading your life for a low-value support."),
            RoamStep("08:00–10:00", "Set up Lord vision and look for a pick on an enemy who checks alone. Do not start Lord while your team is split."),
            RoamStep("10:00–12:00", "Use burst to remove vision and threaten backline access, but keep an exit route after committing."),
            RoamStep("12:00–15:00", "Play around enemy defensive cooldowns. If the carry is protected, switch to scouting and objective pressure."),
            RoamStep("15:00–18:00", "Wait for the enemy formation to break before diving. Dire Hit is for finishing a valid target, not replacing team coordination.")
        )
    )
)

@Composable
fun RoamGuideBubble(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(2.dp, GuideGreen, CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("R", color = GuideGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("ROAM", color = TextPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    var selectedPlanId by remember { mutableStateOf(roamPlans.first().id) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    val selectedPlan = roamPlans.first { it.id == selectedPlanId }

    // Deliberately starts false. The clock advances only after the user presses Start.
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1
            else isRunning = false
        }
    }

    val minute = elapsedSeconds / 60
    val second = elapsedSeconds % 60
    val currentStep = selectedPlan.steps.firstOrNull { step ->
        val start = step.window.substringBefore("–").split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        val end = step.window.substringAfter("–").split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        elapsedSeconds in start until end
    } ?: selectedPlan.steps.last()

    Column(
        modifier = Modifier
            .width(300.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, GuideGreen.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ROAM GUIDE", color = GuideGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("Manual match timer · 18-minute guide", color = GuideMuted, fontSize = 9.sp)
            }
            Text("BACK", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("%02d:%02d".format(minute, second), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(if (isRunning) "TIMER RUNNING" else "TIMER STOPPED", color = if (isRunning) GuideGreen else GuideMuted, fontSize = 9.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button(
                    onClick = { isRunning = !isRunning },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) SurfaceMid else GuideGreen),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isRunning) "Pause" else "Start", color = Color(0xFF10151C), fontSize = 11.sp)
                }
                Button(
                    onClick = { isRunning = false; elapsedSeconds = 0 },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceMid),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 4.dp)
                ) { Text("Reset", color = TextPrimary, fontSize = 11.sp) }
            }
        }

        Text("ROAM TYPE", color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            roamPlans.forEach { plan ->
                val selected = plan.id == selectedPlanId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) GuideGreen.copy(alpha = 0.18f) else SurfaceDark)
                        .border(1.dp, if (selected) GuideGreen else SurfaceMid, RoundedCornerShape(16.dp))
                        .clickable { selectedPlanId = plan.id }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(plan.label, color = if (selected) GuideGreen else TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 285.dp).padding(top = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GuidePanel("CURRENT ACTION · ${currentStep.window}", currentStep.task)
            GuidePanel("ROAM BLESSING · ${selectedPlan.blessing}", blessingDescription(selectedPlan.id))
            Text("HEROES", color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(selectedPlan.heroes, color = GuideMuted, fontSize = 10.sp)
            Text("TIMEFRAME ROTATION", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Black)
            selectedPlan.steps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(7.dp)).padding(7.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(step.window, color = GuideGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
                    Text(step.task, color = TextPrimary, fontSize = 9.sp, style = TextStyle(lineHeight = 12.sp))
                }
            }
            Text(
                "Objective reminder: first Turtle spawns at 02:00; arrive about 15–20 seconds early. Later Turtles respawn 120 seconds after the previous Turtle is defeated (up to four total). A Turtle killed after 06:00 does not return; Lord appears two minutes later. If Turtle is still alive at 08:00, it transforms into Lord after five seconds without damage. Use the live objective state, not fixed 04:00/06:00 assumptions.",
                color = GuideMuted, fontSize = 9.sp, style = TextStyle(lineHeight = 12.sp),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GuidePanel(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(GuideGreen.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
            .border(1.dp, GuideGreen.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(title, color = GuideGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(body, color = TextPrimary, fontSize = 10.sp, style = TextStyle(lineHeight = 13.sp), modifier = Modifier.padding(top = 3.dp))
    }
}

private fun blessingDescription(id: String): String = when (id) {
    "engage" -> "Choose Conceal for a coordinated tank engage; choose Encourage when your tank is mainly enabling grouped teamfights and carry protection."
    "pickoff" -> "Conceal supports team catches; Dire Hit is an alternative when your roam build is focused on finishing isolated targets."
    "healer" -> "Favor adds healing support and fits heal-focused roamers."
    "utility" -> "Encourage improves nearby allies during grouped fights; choose it when teamfight value matters more than a pick."
    else -> "Dire Hit suits damage-focused roamers that can reliably pressure and finish targets. Selena and Natalia are examples, but are not tagged as ROAM in this project's current roster."
}
