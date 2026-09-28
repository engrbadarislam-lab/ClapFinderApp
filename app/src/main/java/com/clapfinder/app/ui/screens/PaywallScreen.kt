package com.clapfinder.app.ui.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.clapfinder.app.R
import com.clapfinder.app.billing.BillingManager
import com.clapfinder.app.ui.components.findActivity
import com.clapfinder.app.ui.theme.CF

@Composable
fun PaywallScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var formattedPrice by remember { mutableStateOf<String?>(null) }
    var isPurchasing by remember { mutableStateOf(false) }

    // Initialize BillingManager and handle callbacks
    val billingManager = remember {
        BillingManager(context, object : BillingManager.BillingListener {
            override fun onProductsLoaded(products: Map<String, ProductDetails>) {
                val product = products[BillingManager.LIFETIME_ID]
                val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                mainHandler.post {
                    formattedPrice = price
                }
            }

            override fun onPurchaseSuccess() {
                mainHandler.post {
                    isPurchasing = false
                    Toast.makeText(context, "Welcome to Premium!", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }

            override fun onPurchaseFailed(msg: String) {
                mainHandler.post {
                    isPurchasing = false
                    if (msg != "User cancelled") {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Connect billing client on enter and clean up on exit
    DisposableEffect(Unit) {
        billingManager.start()
        onDispose {
            billingManager.destroy()
        }
    }

    // Main Orange Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CF.Bg)
    ) {
        // Top Right Close Icon
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = CF.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // White Star Icon
            Icon(
                painter = painterResource(R.drawable.premium_icon),
                contentDescription = "Premium",
                tint = CF.White,
                modifier = Modifier.size(90.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Go Premium",
                color = CF.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Get lifetime access to all features, remove ads, and find your phone faster.",
                color = CF.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // White Card with Orange Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CF.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Lifetime Plan",
                        color = CF.DarkOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formattedPrice ?: "Loading...",
                        color = CF.OrangePrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "One-time payment • Lifetime access",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Bouncing White Continue Button at the bottom
        val infiniteTransition = rememberInfiniteTransition(label = "bounce")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce_anim"
        )

        Button(
            onClick = {
                val activity = context.findActivity()
                if (activity != null) {
                    isPurchasing = true
                    billingManager.launchOneTime(activity)
                } else {
                    Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = formattedPrice != null && !isPurchasing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(60.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = CF.White,
                disabledContainerColor = CF.White.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp
            )
        ) {
            Text(
                text = if (isPurchasing) "Processing..." else "Continue",
                color = CF.OrangePrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}