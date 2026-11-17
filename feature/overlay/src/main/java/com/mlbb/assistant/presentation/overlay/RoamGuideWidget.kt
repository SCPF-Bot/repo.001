package com.mlbb.assistant.presentation.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONArray

private val GuideGreen = Color(0xFF72D6A0)
private const val ROAM_GUIDE_ASSET = "roam_guide.json"

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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("R", color = GuideGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("ROAM", color = TextPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoamGuideWidget(onBack: () -> Unit) {
    val context = LocalContext.current
    val plans = remember(context) { loadRoamPlans(context) }
    var selectedPlanId by remember { mutableStateOf(plans.firstOrNull()?.id.orEmpty()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
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
            .width(240.dp)
            .background(OverlayBackground.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .border(1.dp, GuideGreen.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ROAM", color = GuideGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(
                "BACK",
                color = MLBBGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { isRunning = !isRunning },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) SurfaceMid else GuideGreen),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                    modifier = Modifier.heightIn(min = 28.dp)
                ) {
                    Text(if (isRunning) "Pause" else "Start", color = Color(0xFF10151C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Reset",
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        isRunning = false
                        elapsedSeconds = 0
                    }.padding(4.dp)
                )
            }
        }

        if (plans.isEmpty() || selectedPlan == null) {
            Text(
                "Roam guide unavailable",
                color = TextPrimary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            plans.forEach { plan ->
                val selected = plan.id == selectedPlan.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) GuideGreen.copy(alpha = 0.18f) else SurfaceDark.copy(alpha = 0.7f))
                        .border(1.dp, if (selected) GuideGreen else SurfaceMid.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .clickable { selectedPlanId = plan.id }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        plan.label,
                        color = if (selected) GuideGreen else TextPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (currentStep != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GuideGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, GuideGreen.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                Text(
                    "CURRENT · ${formatRoamTime(currentStep.startSeconds)}–${formatRoamTime(currentStep.endSeconds)}",
                    color = GuideGreen,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    currentStep.description,
                    color = TextPrimary,
                    fontSize = 9.sp,
                    style = TextStyle(lineHeight = 11.sp),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Text(
            "BLESSING · ${selectedPlan.blessing}",
            color = MLBBGold,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

private fun formatRoamTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
