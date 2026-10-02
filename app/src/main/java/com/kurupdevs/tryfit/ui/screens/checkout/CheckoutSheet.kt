package com.kurupdevs.tryfit.ui.screens.checkout

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kurupdevs.tryfit.data.PrefsKeys
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.formatInr
import com.kurupdevs.tryfit.ui.components.BlurScrim
import com.kurupdevs.tryfit.ui.components.ProductImage
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Delivery address, persisted to DataStore. */
@Serializable
data class DeliveryAddress(
    val name: String,
    val phone: String,
    val pincode: String,
    val address: String
)

/** Placed order (demo checkout — no payment gateway in this build). */
@Serializable
data class Order(
    val id: String,
    val productId: String,
    val productName: String,
    val size: String,
    val amountInr: Int,
    val paymentMethod: String,
    val placedAt: Long
)

private enum class CheckoutPhase { Form, Placing, Success }
private enum class PayMethod { UPI, COD }

/**
 * Checkout bottom sheet (new flow, not in SPEC).
 *
 * Order summary (thumb, name, size, ₹price, FREE delivery, total) →
 * delivery address (simple form, saved to DataStore) → payment
 * (UPI intent / Cash on Delivery) → "Place Order" → success state with
 * order id `TF<timestamp>` + "Track in Notifications" (creates a
 * notification row via [com.kurupdevs.tryfit.data.NotificationsRepository]).
 *
 * Honest copy throughout: "Demo checkout — no real charge in this build".
 */
@Composable
fun CheckoutSheet(
    product: Product,
    size: String,
    onDismiss: () -> Unit,
    onOrderPlaced: (Order) -> Unit
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(CheckoutPhase.Form) }
    var address by remember { mutableStateOf<DeliveryAddress?>(null) }
    var editingAddress by remember { mutableStateOf(false) }
    var payMethod by remember { mutableStateOf(PayMethod.UPI) }
    var order by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(Unit) {
        visible = true
        address = loadAddress(container.prefs)
    }

    fun dismiss() {
        scope.launch {
            visible = false
            delay(220)
            onDismiss()
        }
    }

    fun placeOrder() {
        val addr = address ?: return
        scope.launch {
            phase = CheckoutPhase.Placing
            delay(800)
            val placedAt = System.currentTimeMillis()
            val newOrder = Order(
                id = "TF" + SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(placedAt)),
                productId = product.id,
                productName = product.name,
                size = size,
                amountInr = product.priceInr,
                paymentMethod = if (payMethod == PayMethod.UPI) "UPI" else "Cash on Delivery",
                placedAt = placedAt
            )
            runCatching {
                container.prefs.edit {
                    it[PrefsKeys.LAST_ORDER_JSON] = Json.encodeToString(Order.serializer(), newOrder)
                }
                container.notificationsRepository.add(
                    title = "Order placed!",
                    body = "${product.name} (Size $size) is on its way. Order ${newOrder.id}.",
                    type = "system"
                )
            }
            order = newOrder
            phase = CheckoutPhase.Success
            if (payMethod == PayMethod.UPI) {
                val amount = "%.2f".format(Locale.US, product.priceInr.toDouble())
                val upiUri = Uri.parse(
                    "upi://pay?pa=tryfit@upi&pn=TryFit&am=$amount&cu=INR&tn=${newOrder.id}"
                )
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, upiUri))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No UPI app found on this device", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(180))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurScrim()
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Color.White)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0xFFE4E4E7))
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (phase == CheckoutPhase.Success) "Order confirmed" else "Checkout",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TryFitColors.TextPrimary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(top = 12.dp)
                    )
                    IconButton(
                        onClick = ::dismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 4.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close checkout",
                            tint = TryFitColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                when (phase) {
                    CheckoutPhase.Form -> CheckoutForm(
                        product = product,
                        size = size,
                        address = address,
                        editingAddress = editingAddress,
                        onEditAddress = { editingAddress = true },
                        onSaveAddress = { a ->
                            address = a
                            editingAddress = false
                            scope.launch { saveAddress(container.prefs, a) }
                        },
                        payMethod = payMethod,
                        onPayMethod = { payMethod = it },
                        onPlaceOrder = ::placeOrder
                    )
                    CheckoutPhase.Placing -> PlacingState()
                    CheckoutPhase.Success -> SuccessState(
                        order = order,
                        onTrack = { order?.let(onOrderPlaced) }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** Reads the saved delivery address, or null when incomplete. */
private suspend fun loadAddress(
    prefs: DataStore<Preferences>
): DeliveryAddress? {
    val p = prefs.data.first()
    val name = p[PrefsKeys.ADDRESS_NAME].orEmpty()
    val phone = p[PrefsKeys.ADDRESS_PHONE].orEmpty()
    val pin = p[PrefsKeys.ADDRESS_PINCODE].orEmpty()
    val line = p[PrefsKeys.ADDRESS_LINE].orEmpty()
    return if (name.isBlank() || phone.isBlank() || pin.isBlank() || line.isBlank()) {
        null
    } else {
        DeliveryAddress(name, phone, pin, line)
    }
}

private suspend fun saveAddress(
    prefs: DataStore<Preferences>,
    address: DeliveryAddress
) {
    prefs.edit {
        it[PrefsKeys.ADDRESS_NAME] = address.name
        it[PrefsKeys.ADDRESS_PHONE] = address.phone
        it[PrefsKeys.ADDRESS_PINCODE] = address.pincode
        it[PrefsKeys.ADDRESS_LINE] = address.address
    }
}

@Composable
private fun CheckoutForm(
    product: Product,
    size: String,
    address: DeliveryAddress?,
    editingAddress: Boolean,
    onEditAddress: () -> Unit,
    onSaveAddress: (DeliveryAddress) -> Unit,
    payMethod: PayMethod,
    onPayMethod: (PayMethod) -> Unit,
    onPlaceOrder: () -> Unit
) {
    // Order summary.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TryFitColors.BgCanvas)
            .padding(12.dp)
    ) {
        ProductImage(
            imageRef = "asset://${product.imageResName}",
            contentDescription = product.name,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary
            )
            Text(
                text = "${product.brand} · Size $size",
                style = MaterialTheme.typography.labelMedium,
                color = TryFitColors.TextSecondary
            )
        }
        Text(
            text = product.priceLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TryFitColors.TextPrimary
        )
    }

    Spacer(Modifier.height(12.dp))
    SummaryRow(label = "Price", value = product.priceLabel)
    SummaryRow(label = "Delivery", value = "FREE", valueColor = Color(0xFF16A34A))
    HorizontalDivider(color = Color(0xFFF1F1F3), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
    SummaryRow(label = "Total", value = product.priceLabel, bold = true)

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Delivery address",
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        ),
        color = TryFitColors.TextPrimary
    )
    Spacer(Modifier.height(8.dp))

    if (editingAddress || address == null) {
        AddressForm(
            initial = address,
            onSave = onSaveAddress
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = address.name, style = MaterialTheme.typography.labelLarge, color = TryFitColors.TextPrimary)
                Text(
                    text = "${address.address}, ${address.pincode}\nPhone: ${address.phone}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = TryFitColors.TextSecondary
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.OrbViolet,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onEditAddress
                    )
                    .padding(8.dp)
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = "Payment",
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        ),
        color = TryFitColors.TextPrimary
    )
    Spacer(Modifier.height(8.dp))
    PayOption(
        selected = payMethod == PayMethod.UPI,
        title = "UPI",
        subtitle = "Pay via any UPI app",
        onClick = { onPayMethod(PayMethod.UPI) }
    )
    Spacer(Modifier.height(8.dp))
    PayOption(
        selected = payMethod == PayMethod.COD,
        title = "Cash on Delivery",
        subtitle = "Pay when your order arrives",
        onClick = { onPayMethod(PayMethod.COD) }
    )

    Spacer(Modifier.height(12.dp))
    Text(
        text = "Demo checkout — no real charge in this build.",
        style = MaterialTheme.typography.labelSmall,
        color = TryFitColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))

    val canPlace = address != null && !editingAddress
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (canPlace) TryFitColors.NavBg else Color(0xFFE4E4E7))
            .clickable(
                enabled = canPlace,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onPlaceOrder
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Place Order · ${product.priceLabel}",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (canPlace) Color.White else TryFitColors.TextSecondary
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = TryFitColors.TextPrimary,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TryFitColors.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}

@Composable
private fun AddressForm(
    initial: DeliveryAddress?,
    onSave: (DeliveryAddress) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var pincode by remember { mutableStateOf(initial?.pincode.orEmpty()) }
    var line by remember { mutableStateOf(initial?.address.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = pincode,
                onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Pincode") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = line,
            onValueChange = { line = it },
            label = { Text("Address (house no, street, area, city)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = TryFitColors.AccentBadge)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(TryFitColors.SurfaceThumb)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        error = when {
                            name.isBlank() -> "Please enter your name."
                            phone.length != 10 -> "Please enter a 10-digit phone number."
                            pincode.length != 6 -> "Please enter a 6-digit pincode."
                            line.isBlank() -> "Please enter your address."
                            else -> null
                        }
                        if (error == null) {
                            onSave(DeliveryAddress(name.trim(), phone, pincode, line.trim()))
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save address",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TryFitColors.TextPrimary
            )
        }
    }
}

@Composable
private fun PayOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.5.dp,
                if (selected) TryFitColors.TextPrimary else Color(0xFFE4E4E7),
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = TryFitColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = TryFitColors.TextSecondary)
        }
    }
}

@Composable
private fun PlacingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = TryFitColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Placing your order…",
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary
        )
    }
}

@Composable
private fun SuccessState(
    order: Order?,
    onTrack: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "successPop"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Color(0xFF16A34A).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Order placed!",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        if (order != null) {
            Text(
                text = "Order ${order.id}",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary
            )
            Text(
                text = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                    .format(Date(order.placedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${order.productName} · Size ${order.size} · ${formatInr(order.amountInr)} · ${order.paymentMethod}",
                style = MaterialTheme.typography.labelMedium,
                color = TryFitColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Demo checkout — no real charge in this build.",
            style = MaterialTheme.typography.labelSmall,
            color = TryFitColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(TryFitColors.NavBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onTrack
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Track in Notifications",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }
    }
}
