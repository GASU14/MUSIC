package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class MainTab {
    SETTINGS,
    HOME,
    LIBRARY
}

@Composable
fun FloatingNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = when (selectedTab) {
        MainTab.SETTINGS -> 0
        MainTab.HOME -> 1
        MainTab.LIBRARY -> 2
    }

    val itemWidth = 58.dp
    val itemHeight = 44.dp
    val itemSpacing = 16.dp
    val step = itemWidth + itemSpacing

    // Zero-delay smooth spring transition for the Nothing Red indicator
    val indicatorOffset by animateDpAsState(
        targetValue = step * selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nav_red_indicator"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF08090C),
            border = BorderStroke(1.2.dp, Color(0x33FFFFFF)),
            shadowElevation = 20.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Smooth moving Nothing Red highlight indicator
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(width = itemWidth, height = itemHeight)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFD71921)) // Authentic Nothing OS Red
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. SETTINGS
                    NavIconItem(
                        isSelected = selectedTab == MainTab.SETTINGS,
                        onClick = { onTabSelected(MainTab.SETTINGS) },
                        itemWidth = itemWidth,
                        itemHeight = itemHeight
                    ) { isSel ->
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isSel) Color.White else Color(0xFF7E838E),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. HOME
                    NavIconItem(
                        isSelected = selectedTab == MainTab.HOME,
                        onClick = { onTabSelected(MainTab.HOME) },
                        itemWidth = itemWidth,
                        itemHeight = itemHeight
                    ) { isSel ->
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (isSel) Color.White else Color(0xFF7E838E),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 3. LIBRARY
                    NavIconItem(
                        isSelected = selectedTab == MainTab.LIBRARY,
                        onClick = { onTabSelected(MainTab.LIBRARY) },
                        itemWidth = itemWidth,
                        itemHeight = itemHeight
                    ) { isSel ->
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Library",
                            tint = if (isSel) Color.White else Color(0xFF7E838E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavIconItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    icon: @Composable (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(width = itemWidth, height = itemHeight)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon(isSelected)
    }
}
