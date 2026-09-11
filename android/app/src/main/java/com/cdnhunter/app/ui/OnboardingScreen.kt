package com.cdnhunter.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val title: String,
    val subtitle: String,
)

private val onboardingSlides = listOf(
    OnboardingSlide("Blazing Fast", "Optimized routes that keep your connection quick, wherever you are."),
    OnboardingSlide("Rock Solid Secure", "Your traffic, encrypted end to end. No logs, no compromises."),
    OnboardingSlide("Always Free", "The core features stay free. No hidden fees, no surprises."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingSlides.size })
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(AppColors.BgDark)) {
        FullScreenLoopVideo(modifier = Modifier.fillMaxSize())

        // Same dark overlay treatment as AuthScreen, so text stays readable
        // over the video without hiding it completely.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                OnboardingSlideContent(onboardingSlides[page])
            }

            Spacer(Modifier.height(28.dp))

            // Dot indicator
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(onboardingSlides.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(if (selected) 22.dp else 7.dp, label = "dotWidth")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(7.dp)
                            .width(width)
                            .background(
                                if (selected) AppColors.Accent else AppColors.TextMid.copy(alpha = 0.35f),
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            val isLastPage = pagerState.currentPage == onboardingSlides.lastIndex
            Button(
                onClick = {
                    if (isLastPage) {
                        onDone()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) {
                Text(
                    if (isLastPage) "Get Started" else "Next",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!isLastPage) {
                Text(
                    "Skip",
                    fontSize = 13.sp,
                    color = AppColors.TextMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDone() },
                )
            } else {
                Spacer(Modifier.height(21.dp))
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun OnboardingSlideContent(slide: OnboardingSlide) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = com.cdnhunter.app.R.drawable.logo_alien),
            contentDescription = "GroomX",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(120.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            slide.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            slide.subtitle,
            fontSize = 14.sp,
            color = AppColors.TextMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
