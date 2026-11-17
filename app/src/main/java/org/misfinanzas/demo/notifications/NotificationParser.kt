package org.misfinanzas.demo.notifications

import org.misfinanzas.demo.data.DetectedMovementEntity
import java.security.MessageDigest
import java.util.Locale

object NotificationParser {
    private const val MAX_NOTIFICATION_AGE_MILLIS = 10 * 60_000L
    const val TRANSFER_PAIR_TOLERANCE_CENTS = 100L

    private val amountPatterns = listOf(
        Regex("(?:MXN\\s*)?\\$\\s*([0-9][0-9., ]*)", RegexOption.IGNORE_CASE),
        Regex("(?:por|de|importe:?|monto:?|compra:?|cargo:?)\\s*(?:MXN\\s*)?\\$?\\s*([0-9][0-9., ]*)", RegexOption.IGNORE_CASE),
        Regex("([0-9][0-9., ]*)\\s*(?:MXN|pesos)", RegexOption.IGNORE_CASE)
    )

    private val ignoredTerms = listOf(
        "código", "codigo", "token", "contraseña", "contrasena", "inicia sesión",
        "inicia sesion", "promoción", "promocion", "oferta", "estado de cuenta disponible",
        "gracias por tu pago", "saldo disponible", "límite disponible", "limite disponible",
        "línea de crédito", "linea de credito", "fecha límite", "fecha limite",
        "recibe hasta", "ya puedes obtener tu pré", "ya puedes obtener tu pre",
        "plata préstamo", "plata prestamo", "prueba pagos a meses",
        "divide cualquier compra", "3-12 mensualidades"
    )

    private val purchaseTerms = listOf(
        "compra", "cargo", "pagaste", "pago en", "operación", "operacion",
        "transacción", "transaccion", "consumo", "retiro", "disposición", "disposicion",
        "aprobada", "aprobado", "usaste", "realizaste"
    )

    private val incomingTerms = listOf(
        "transferencia recibida", "recibiste", "depósito recibido", "deposito recibido",
        "abono recibido", "spei recibido", "te transfirió", "te transfirio", "dinero recibido",
        "te enviaron", "has recibido", "abono a tu cuenta", "depósito a tu cuenta",
        "deposito a tu cuenta", "transferencia a tu cuenta", "transferencia en tu cuenta",
        "depósito cheques", "deposito cheques"
    )

    private val outgoingTerms = listOf(
        "transferencia exitosa", "transferencia enviada", "transferencia realizada", "enviaste",
        "transferiste", "spei enviado", "envío exitoso", "envio exitoso", "dinero enviado",
        "realizaste una transferencia"
    )

    private val refundTerms = listOf(
        "devolución", "devolucion", "reembolso", "compra cancelada", "operación devuelta",
        "operacion devuelta", "cargo revertido", "cashback"
    )

    fun parse(packageName: String, title: String, text: String, postedAtMillis: Long): DetectedMovementEntity? {
        if (packageName == "org.misfinanzas.demo" || packageName == "com.google.android.gm") return null
        val combined = "$title $text".trim()
        val lower = combined.lowercase(Locale.ROOT)
        if (ignoredTerms.any(lower::contains)) return null
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(combined)?.groupValues?.getOrNull(1)?.toMoneyCents()
        } ?: return null
        if (amount <= 0) return null

        detectSpecialMovement(packageName, lower)?.let { movement ->
            return DetectedMovementEntity(
                fingerprint = notificationFingerprint(packageName, title, text, postedAtMillis),
                packageName = packageName,
                accountName = movement.source,
                merchant = movement.description,
                amountCents = amount,
                suggestedCategory = "Pagos",
                postedAtMillis = postedAtMillis,
                rawTitle = title,
                rawText = text,
                movementType = movement.type,
                relatedAccountName = movement.destination
            )
        }

        if (purchaseTerms.none(lower::contains)) return null
        val account = detectAccount(packageName, lower) ?: detectLiquidAccount(packageName.lowercase(Locale.ROOT), lower) ?: return null

        val merchant = detectMerchant(title, text, account)
        return DetectedMovementEntity(
            fingerprint = notificationFingerprint(packageName, title, text, postedAtMillis),
            packageName = packageName,
            accountName = account,
            merchant = merchant,
            amountCents = amount,
            suggestedCategory = suggestCategory(merchant),
            postedAtMillis = postedAtMillis,
            rawTitle = title,
            rawText = text
        )
    }

    private data class SpecialMovement(val type: String, val source: String, val destination: String?, val description: String)

    private fun detectSpecialMovement(packageName: String, text: String): SpecialMovement? {
        val pkg = packageName.lowercase(Locale.ROOT)
        val receivingAccount = detectLiquidAccount(pkg, text)
        if (incomingTerms.any(text::contains) && receivingAccount != null) {
            return SpecialMovement(
                type = "Ingreso",
                source = receivingAccount,
                destination = null,
                description = if (text.contains("nómina") || text.contains("nomina")) "Nómina recibida" else "Transferencia recibida"
            )
        }

        if (refundTerms.any(text::contains)) {
            val account = detectAccount(packageName, text) ?: receivingAccount ?: return null
            return SpecialMovement(
                type = "Devolución",
                source = account,
                destination = null,
                description = when {
                    text.contains("cashback") -> "Cashback"
                    text.contains("compra cancelada") -> "Compra cancelada"
                    else -> "Devolución"
                }
            )
        }

        val isCardPayment = text.contains("pago de tarjeta") || text.contains("pago a tarjeta") ||
            (text.contains("pago") && text.contains("desde mi cuenta"))
        if (isCardPayment) {
            val source = detectSourceAccount(text) ?: return null
            val destination = detectCardFromPackage(pkg) ?: detectDestinationAccount("", text)
            return SpecialMovement(
                type = "Pago tarjeta",
                source = source,
                destination = destination,
                description = destination?.let { "Pago $it" } ?: "Pago de tarjeta"
            )
        }

        val isOutgoingTransfer = outgoingTerms.any(text::contains)
        if (isOutgoingTransfer) {
            val source = liquidAccountForPackage(pkg) ?: detectLiquidAccount(pkg, text) ?: detectSourceAccount(text) ?: return null
            val destination = detectDestinationAccount("", text)
            val isCardDestination = destination in setOf("Plata Card", "Banamex Oro", "BBVA Azul", "Mercado Pago Crédito", "Nu", "Klar", "Bradescard", "Openbank")
            return SpecialMovement(
                type = if (isCardDestination) "Pago tarjeta" else "Transferencia",
                source = source,
                destination = destination,
                description = destination?.let { if (isCardDestination) "Pago $it" else "Transferencia a $it" } ?: "Transferencia enviada"
            )
        }
        return null
    }

    private fun detectSourceAccount(text: String): String? = when {
        text.contains("cuenta de nu") || text.contains("cuenta nu") -> "Cajita Nu"
        text.contains("cuenta de bbva") || text.contains("cuenta bbva") -> "BBVA Débito"
        text.contains("mercado pago") -> "Mercado Pago wallet"
        else -> null
    }

    private fun detectLiquidAccount(packageName: String, text: String): String? = when {
        packageName.contains("bbva") || packageName.contains("bancomer") || text.contains("cuenta bbva") || text.contains("cuenta de bbva") -> "BBVA Débito"
        packageName.contains("banamex") || packageName.contains("citibanamex") || text.contains("cuenta banamex") || text.contains("cuenta de banamex") -> "Banamex Débito"
        packageName.contains("nu") || text.contains("cajita nu") || text.contains("cuenta nu") || text.contains("cuenta de nu") -> "Cajita Nu"
        packageName.contains("mercadopago") || packageName.contains("mercado_pago") || text.contains("saldo de mercado pago") -> "Mercado Pago wallet"
        packageName.contains("openbank") && text.contains("cuenta") -> "Openbank Débito"
        else -> null
    }

    fun liquidAccountForPackage(packageName: String): String? {
        val pkg = packageName.lowercase(Locale.ROOT)
        return when {
            pkg.contains("bbva") || pkg.contains("bancomer") -> "BBVA Débito"
            pkg.contains("banamex") || pkg.contains("citibanamex") -> "Banamex Débito"
            pkg.contains("nu") -> "Cajita Nu"
            pkg.contains("mercadopago") || pkg.contains("mercado_pago") -> "Mercado Pago wallet"
            pkg.contains("openbank") -> "Openbank Débito"
            else -> null
        }
    }

    fun liquidDebitNoticeAccount(packageName: String, title: String, text: String): String? {
        val value = "$title $text".lowercase(Locale.ROOT)
        val debitNotice = value.contains("aviso de cargo en tu cuenta") ||
            value.contains("cargo a tu cuenta") || value.contains("cargo en tu cuenta") ||
            value.contains("retiro/compra cheques")
        return if (debitNotice) liquidAccountForPackage(packageName) else null
    }

    fun canPairDestination(current: String?, detected: String, title: String, text: String): Boolean {
        if (current == null || current == detected) return true
        val value = "$title $text".lowercase(Locale.ROOT)
        return current == "BBVA Azul" && detected == "BBVA Débito" &&
            !value.contains("bbva azul") && !value.contains("tarjeta bbva") && !value.contains("pago de tarjeta")
    }

    fun amountsCanPair(firstAmountCents: Long, secondAmountCents: Long): Boolean =
        kotlin.math.abs(firstAmountCents - secondAmountCents) <= TRANSFER_PAIR_TOLERANCE_CENTS

    fun shouldProcessNotification(
        packageName: String,
        isGroupSummary: Boolean,
        postedAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (packageName == "org.misfinanzas.demo" || packageName == "com.google.android.gm" || isGroupSummary) return false
        val age = nowMillis - postedAtMillis
        return age in -60_000L..MAX_NOTIFICATION_AGE_MILLIS
    }

    fun supportedBankName(packageName: String): String? {
        val pkg = packageName.lowercase(Locale.ROOT)
        return when {
            pkg.contains("bbva") || pkg.contains("bancomer") -> "BBVA"
            pkg.contains("banamex") || pkg.contains("citibanamex") -> "Banamex"
            pkg.contains("plata") -> "Plata"
            pkg.contains("mercadopago") || pkg.contains("mercado_pago") -> "Mercado Pago"
            pkg.contains("openbank") -> "Openbank"
            pkg.contains("bradescard") -> "Bradescard"
            pkg.contains("klar") -> "Klar"
            pkg.contains("nu") -> "Nu"
            else -> null
        }
    }

    fun ignoredReason(packageName: String, title: String, text: String): String {
        val combined = "$title $text".trim()
        val lower = combined.lowercase(Locale.ROOT)
        if (ignoredTerms.any(lower::contains)) return "Promoción o aviso informativo"
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(combined)?.groupValues?.getOrNull(1)?.toMoneyCents()
        }
        if (amount == null || amount <= 0) return "No se encontró un importe"
        if (detectSpecialMovement(packageName, lower) == null && purchaseTerms.none(lower::contains)) {
            return "No se reconoció una operación financiera"
        }
        if (detectAccount(packageName, lower) == null && detectLiquidAccount(packageName.lowercase(Locale.ROOT), lower) == null) {
            return "No se identificó la cuenta o tarjeta"
        }
        return "Formato bancario aún no compatible"
    }

    private fun detectCardFromPackage(packageName: String): String? = when {
        packageName.contains("plata") -> "Plata Card"
        packageName.contains("banamex") || packageName.contains("citibanamex") -> "Banamex Oro"
        packageName.contains("bbva") || packageName.contains("bancomer") -> "BBVA Azul"
        packageName.contains("mercadopago") || packageName.contains("mercado_pago") -> "Mercado Pago Crédito"
        packageName.contains("nu") -> "Nu"
        packageName.contains("klar") -> "Klar"
        packageName.contains("bradescard") -> "Bradescard"
        packageName.contains("openbank") -> "Openbank"
        else -> null
    }

    private fun detectDestinationAccount(packageName: String, text: String): String? = when {
        text.contains("cajita nu") || text.contains("cuenta de nu") || text.contains("cuenta nu") -> "Cajita Nu"
        text.contains("bbva débito") || text.contains("bbva debito") || text.contains("cuenta de bbva") || text.contains("cuenta bbva") -> "BBVA Débito"
        text.contains("banamex oro") || text.contains("tarjeta banamex") -> "Banamex Oro"
        text.contains("banamex débito") || text.contains("banamex debito") || text.contains("cuenta de banamex") || text.contains("cuenta banamex") -> "Banamex Débito"
        text.contains("wallet mercado pago") || text.contains("saldo de mercado pago") -> "Mercado Pago wallet"
        packageName.contains("plata") || text.contains("plata") -> "Plata Card"
        text.contains("banamex") -> "Banamex Débito"
        packageName.contains("banamex") || packageName.contains("citibanamex") -> "Banamex Débito"
        text.contains("bbva azul") || text.contains("tarjeta bbva") -> "BBVA Azul"
        text.contains("bbva") -> "BBVA Débito"
        packageName.contains("bbva") || packageName.contains("bancomer") -> "BBVA Azul"
        packageName.contains("mercadopago") || text.contains("mercado pago crédito") -> "Mercado Pago Crédito"
        packageName.contains("nu") || text.contains("tarjeta nu") -> "Nu"
        packageName.contains("klar") || text.contains("klar") -> "Klar"
        packageName.contains("bradescard") || text.contains("bradescard") -> "Bradescard"
        packageName.contains("openbank") || text.contains("openbank") -> "Openbank"
        else -> null
    }

    private fun detectAccount(packageName: String, text: String): String? {
        val pkg = packageName.lowercase(Locale.ROOT)
        return when {
            liquidDebitNoticeAccount(packageName, "", text) != null -> liquidDebitNoticeAccount(packageName, "", text)
            (pkg.contains("bbva") || pkg.contains("bancomer") || text.contains("bbva")) && listOf("débito", "debito", "tarjeta de débito", "tarjeta debito").any(text::contains) -> "BBVA Débito"
            pkg.contains("nu") || text.contains("nu méxico") || text.contains("tarjeta nu") -> "Nu"
            pkg.contains("bbva") || pkg.contains("bancomer") || text.contains("bbva") -> "BBVA Azul"
            pkg.contains("banamex") || pkg.contains("citibanamex") || text.contains("banamex") -> "Banamex Oro"
            pkg.contains("plata") || text.contains("plata card") -> "Plata Card"
            (pkg.contains("mercadopago") || text.contains("mercado pago")) && listOf("wallet", "saldo", "dinero disponible").any(text::contains) -> "Mercado Pago wallet"
            pkg.contains("mercadopago") || pkg.contains("mercado_pago") || text.contains("mercado pago") -> "Mercado Pago Crédito"
            pkg.contains("klar") || text.contains("klar") -> "Klar"
            pkg.contains("bradescard") || text.contains("bradescard") -> "Bradescard"
            pkg.contains("openbank") || text.contains("openbank") -> "Openbank"
            else -> null
        }
    }

    private fun detectMerchant(title: String, text: String, account: String): String {
        val patterns = listOf(
            Regex("(?:en|con|comercio)\\s+([^,.]+)", RegexOption.IGNORE_CASE),
            Regex("compra(?:\\s+aprobada)?\\s+(?:por\\s+\\$?[0-9,.]+\\s+)?(?:en\\s+)?([^,.]+)", RegexOption.IGNORE_CASE)
        )
        val found = patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
        return found?.take(48)?.ifBlank { account } ?: title.ifBlank { account }.take(48)
    }

    private fun suggestCategory(merchant: String): String {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            listOf("uber", "didi ride", "primera plus", "gasolina", "pemex").any(value::contains) -> "Transporte"
            listOf("oxxo", "restaurant", "rest ", "food", "starbucks", "sushi", "hamburgues", "rappi").any(value::contains) -> "Comida"
            listOf("cinemex", "cinepolis", "steam", "xbox", "riot", "kinguin", "gamivo", "juegos").any(value::contains) -> "Entretenimiento"
            listOf("chatgpt", "netflix", "spotify", "disney", "max", "prime video").any(value::contains) -> "Suscripciones"
            listOf("amazon", "mercado libre", "aliexpress", "walmart", "soriana", "tiktok shop").any(value::contains) -> "Compras"
            listOf("airbnb", "hotel", "aeromexico", "volaris", "vivaaerobus").any(value::contains) -> "Viajes"
            listOf("telcel", "cfe", "totalplay", "izzi", "agua", "internet").any(value::contains) -> "Servicios"
            listOf("farmacia", "doctor", "hospital", "laboratorio").any(value::contains) -> "Salud"
            else -> "Sin clasificar"
        }
    }

    private fun String.toMoneyCents(): Long? {
        var value = trim().trimEnd(',', '.').replace(" ", "")
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 && comma > dot -> value.replace(".", "").replace(',', '.')
            comma >= 0 && dot >= 0 -> value.replace(",", "")
            comma >= 0 && value.length - comma - 1 in 1..2 -> value.replace(',', '.')
            comma >= 0 -> value.replace(",", "")
            dot >= 0 && value.length - dot - 1 > 2 -> value.replace(".", "")
            else -> value
        }
        return value.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
    }

    fun notificationFingerprint(packageName: String, title: String, text: String, postedAtMillis: Long): String {
        val minute = postedAtMillis / 60_000L
        val bytes = "$packageName|$title|$text|$minute".toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

