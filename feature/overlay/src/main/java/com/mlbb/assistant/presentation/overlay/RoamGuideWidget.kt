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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.OverlayBackground
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import kotlinx.coroutines.delay
import org.json.JSONArray

private val GuideGreen = Color(0xFF72D6A0)
private const val ROAM_GUIDE_ASSET = "roam_guide.json"
private val ReadableTextStyle = TextStyle(
    shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 2f)
)

private data class RoamStep(val startSeconds: Int, val endSeconds: Int, val description: String)
private data class RoamPlan(
    val id: String,
    val label: String,
    val blessing: String,
    val heroes: String,
    val steps: List<RoamStep>
)

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
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(2.dp, GuideGreen, CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Text("R", color = GuideGreen, fontSize = 20.sp, fontWeight = FontWeight.Black, style = ReadableTextStyle)
    }
}

@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widgetWidth = (screenWidthDp * 0.92f).dp
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
            .background(OverlayBackground.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .border(1.dp, GuideGreen.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (plans.isEmpty() || selectedPlan == null) {
            Text(
                "Roam guide unavailable",
                color = TextPrimary,
                fontSize = 10.sp,
                style = ReadableTextStyle
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                style = ReadableTextStyle
            )

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(SurfaceDark.copy(alpha = 0.85f))
                        .border(1.dp, GuideGreen.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                        .clickable { planMenuExpanded = true }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        selectedPlan.label,
                        color = GuideGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = ReadableTextStyle
                    )
                    Text("▾", color = TextPrimary, fontSize = 10.sp, style = ReadableTextStyle)
                }
                DropdownMenu(
                    expanded = planMenuExpanded,
                    onDismissRequest = { planMenuExpanded = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    plans.forEach { plan ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    plan.label,
                                    color = if (plan.id == selectedPlan.id) GuideGreen else TextPrimary,
                                    style = ReadableTextStyle
                                )
                            },
                            onClick = {
                                selectedPlanId = plan.id
                                planMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                selectedPlan.blessing,
                color = MLBBGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = ReadableTextStyle
            )

            Button(
                onClick = { isRunning = !isRunning },
                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) SurfaceMid else GuideGreen),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
                modifier = Modifier.heightIn(min = 26.dp)
            ) {
                Text(
                    if (isRunning) "Pause" else "Start",
                    color = Color(0xFF10151C),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    style = ReadableTextStyle
                )
            }

            Text(
                "Back",
                color = MLBBGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 3.dp, vertical = 4.dp),
                style = ReadableTextStyle
            )
        }

        Spacer(Modifier.height(3.dp))

        if (currentStep != null) {
            Text(
                currentStep.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuideGreen.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                    .border(1.dp, GuideGreen.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = TextPrimary,
                fontSize = 9.sp,
                style = TextStyle(lineHeight = 11.sp, shadow = ReadableTextStyle.shadow)
            )
        }
    }
}
