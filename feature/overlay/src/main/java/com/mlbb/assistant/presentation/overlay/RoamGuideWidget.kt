package com.mlbb.assistant.presentation.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray

private val GithubCanvas = Color(0xFF0D1117)
private val GithubSurface = Color(0xFF161B22)
private val GithubBorder = Color(0xFF30363D)
private val GithubAccent = Color(0xFF58A6FF)
private val GithubText = Color(0xFFC9D1D9)
private val GithubMuted = Color(0xFF8B949E)
private val GithubGold = Color(0xFFE3B341)
private const val ROAM_GUIDE_ASSET = "roam_guide.json"
private const val WIDGET_ALPHA = 0.25f // 75% transparent

private data class RoamStep(val startSeconds: Int, val endSeconds: Int, val description: String)
private data class RoamPlan(
    val id: String,
    val label: String,
    val blessing: String,
    val heroes: String,
    val steps: List<RoamStep>
)

@Composable
private fun OutlinedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            color = Color.Black,
            maxLines = maxLines,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                drawStyle = Stroke(width = 3f),
                shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)
            )
        )
        Text(
            text = text,
            color = color,
            maxLines = maxLines,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 1f)
            )
        )
    }
}

private fun loadRoamPlans(context: Context): List<RoamPlan> = runCatching {
    val json = context.assets.open(ROAM_GUIDE_ASSET).bufferedReader().use { it.readText() }
    val plans = JSONArray(json)
    buildList {
        for (planIndex in 0 until plans.length()) {
            val plan = plans.getJSONObject(planIndex)
            val stepsJson = plan.getJSONArray("steps")
            val steps = buildList {
                for (stepIndex in 0 until stepsJson.length()) {
                    val step = stepsJson.getJSONObject(stepIndex)
                    add(
                        RoamStep(
                            startSeconds = step.getInt("startSeconds"),
                            endSeconds = step.getInt("endSeconds"),
                            description = step.getString("description")
                        )
                    )
                }
            }
            if (steps.isNotEmpty()) {
                add(
                    RoamPlan(
                        id = plan.getString("id"),
                        label = plan.getString("label"),
                        blessing = plan.getString("blessing"),
                        heroes = plan.optString("heroes"),
                        steps = steps.sortedBy(RoamStep::startSeconds)
                    )
                )
            }
        }
    }
}.getOrDefault(emptyList())

@Composable
fun RoamGuideBubble(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(GithubSurface)
            .border(2.dp, GithubAccent, CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        OutlinedText("R", GithubAccent, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widgetWidth = (screenWidthDp * 0.98f).dp
    val plans = remember(context) { loadRoamPlans(context) }
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000L)
            if (elapsedSeconds < 18 * 60) elapsedSeconds += 1 else isRunning = false
        }
    }

    val currentStep = selectedPlan?.steps
        ?.firstOrNull { elapsedSeconds >= it.startSeconds && elapsedSeconds < it.endSeconds }
        ?: selectedPlan?.steps?.lastOrNull()

    Column(
        modifier = Modifier
            .width(widgetWidth)
            .background(GithubCanvas.copy(alpha = WIDGET_ALPHA), RoundedCornerShape(6.dp))
            .border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        if (plans.isEmpty() || selectedPlan == null) {
            OutlinedText("Roam guide unavailable", GithubText, fontSize = 9.sp)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The timer itself is the start/pause control.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(GithubSurface.copy(alpha = WIDGET_ALPHA))
                    .border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .clickable { isRunning = !isRunning }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                OutlinedText(
                    "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                    GithubText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                OutlinedText(if (isRunning) "Ⅱ" else "▶", GithubAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(GithubSurface.copy(alpha = WIDGET_ALPHA))
                        .border(1.dp, GithubBorder.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .clickable { planMenuExpanded = true }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedText(
                        selectedPlan.label,
                        GithubAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    OutlinedText("▾", GithubMuted, fontSize = 9.sp)
                }
                DropdownMenu(
                    expanded = planMenuExpanded,
                    onDismissRequest = { planMenuExpanded = false },
                    modifier = Modifier.background(GithubSurface)
                ) {
                    plans.forEach { plan ->
                        DropdownMenuItem(
                            text = {
                                OutlinedText(
                                    plan.label,
                                    if (plan.id == selectedPlan.id) GithubAccent else GithubText,
                                    fontSize = 9.sp
                                )
                            },
                            onClick = {
                                selectedPlanId = plan.id
                                planMenuExpanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            OutlinedText(
                selectedPlan.blessing,
                GithubGold,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            OutlinedText(
                "Back",
                GithubMuted,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 2.dp, vertical = 2.dp),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(2.dp))

        if (currentStep != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GithubSurface.copy(alpha = WIDGET_ALPHA), RoundedCornerShape(4.dp))
                    .border(1.dp, GithubBorder.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                OutlinedText(
                    currentStep.description,
                    GithubText,
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                )
            }
        }
    }
}
