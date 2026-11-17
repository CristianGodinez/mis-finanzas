package org.misfinanzas.demo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class AccountIconOption(val key: String, val label: String, val icon: ImageVector)

val accountIconOptions = listOf(
    AccountIconOption("wallet", "Billetera", Icons.Default.AccountBalanceWallet),
    AccountIconOption("bank", "Banco", Icons.Default.AccountBalance),
    AccountIconOption("cash", "Efectivo", Icons.Default.Payments),
    AccountIconOption("savings", "Ahorro", Icons.Default.Savings),
    AccountIconOption("digital", "Digital", Icons.Default.Smartphone),
    AccountIconOption("card", "Tarjeta", Icons.Default.CreditCard),
    AccountIconOption("store", "Comercio", Icons.Default.Storefront)
)

val accountColorOptions = listOf(
    0xFF58E0C2,
    0xFF2563EB,
    0xFF8B5CF6,
    0xFFF43F5E,
    0xFFF59E0B,
    0xFF10B981,
    0xFF06B6D4,
    0xFFEC4899,
    0xFF94A3B8,
    0xFFFFFFFF
)

fun accountIcon(key: String): ImageVector = accountIconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Default.AccountBalanceWallet

fun accountColor(value: Long): Color = Color(value)

