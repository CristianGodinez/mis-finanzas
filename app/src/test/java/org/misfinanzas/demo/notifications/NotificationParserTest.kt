package org.misfinanzas.demo.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationParserTest {
    @Test
    fun parsesBbvaPurchase() {
        val result = NotificationParser.parse(
            "com.bancomer.mbanking",
            "Compra aprobada",
            "Compra aprobada por \$1,234.56 en AMAZON",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("BBVA Azul", result?.accountName)
        assertEquals(123_456L, result?.amountCents)
        assertEquals("Compras", result?.suggestedCategory)
    }

    @Test
    fun parsesDecimalComma() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Compra aprobada",
            "Realizaste una compra por \$1.234,56 en OXXO",
            1_700_000_000_000
        )
        assertEquals(123_456L, result?.amountCents)
        assertEquals("Nu", result?.accountName)
    }

    @Test
    fun ignoresSecurityCodes() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Código de seguridad",
            "Tu código es 123456",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun parsesIncomingTransferToBbvaDebit() {
        val result = NotificationParser.parse(
            "com.bancomer.mbanking",
            "Transferencia recibida",
            "Recibiste \$500.00 en tu cuenta",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Ingreso", result?.movementType)
        assertEquals("BBVA Débito", result?.accountName)
        assertEquals(50_000L, result?.amountCents)
    }

    @Test
    fun parsesPlataCardPaymentFromNu() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "Pago de tarjeta",
            "\$0.02 desde mi cuenta de Nu",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Pago tarjeta", result?.movementType)
        assertEquals("Cajita Nu", result?.accountName)
        assertEquals("Plata Card", result?.relatedAccountName)
        assertEquals(2L, result?.amountCents)
    }

    @Test
    fun parsesNuOutgoingTransferToPlata() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Transferencia exitosa",
            "Enviaste \$0.02 a Cuenta Plata.",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Pago tarjeta", result?.movementType)
        assertEquals("Cajita Nu", result?.accountName)
        assertEquals("Plata Card", result?.relatedAccountName)
        assertEquals(2L, result?.amountCents)
    }

    @Test
    fun parsesBbvaOutgoingTransferToNu() {
        val result = NotificationParser.parse(
            "com.bancomer.mbanking",
            "Transferencia realizada",
            "Realizaste una transferencia por \$500.00 a tu cuenta de Nu",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Transferencia", result?.movementType)
        assertEquals("BBVA Débito", result?.accountName)
        assertEquals("Cajita Nu", result?.relatedAccountName)
        assertEquals(50_000L, result?.amountCents)
    }

    @Test
    fun parsesIncomingTransferToNu() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Transferencia recibida",
            "Recibiste \$500.00 en tu Cuenta Nu",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Ingreso", result?.movementType)
        assertEquals("Cajita Nu", result?.accountName)
        assertEquals(50_000L, result?.amountCents)
    }

    @Test
    fun parsesBbvaAccountCreditAsIncome() {
        val result = NotificationParser.parse(
            "com.bancomer.mbanking",
            "BBVA",
            "Abono a tu cuenta por \$100.00",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Ingreso", result?.movementType)
        assertEquals("BBVA Débito", result?.accountName)
        assertEquals(10_000L, result?.amountCents)
    }

    @Test
    fun recognizesBbvaAccountDebitNotice() {
        val title = "Aviso de cargo en tu cuenta"
        val text = "Tienes un cargo a tu cuenta *0000 de \$110.00"
        val result = NotificationParser.parse(
            "com.bancomer.mbanking",
            title,
            text,
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("BBVA Débito", result?.accountName)
        assertEquals("Gasto", result?.movementType)
        assertEquals("BBVA Débito", NotificationParser.liquidDebitNoticeAccount("com.bancomer.mbanking", title, text))
    }

    @Test
    fun parsesNuOutgoingTransferToBbvaDebit() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Transferencia exitosa",
            "Enviaste \$110.00 a Cuenta BBVA.",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Transferencia", result?.movementType)
        assertEquals("Cajita Nu", result?.accountName)
        assertEquals("BBVA Débito", result?.relatedAccountName)
        assertEquals(11_000L, result?.amountCents)
    }

    @Test
    fun ignoresOwnAppNotifications() {
        val result = NotificationParser.parse(
            "org.misfinanzas.demo",
            "Movimiento por confirmar",
            "Transferencia enviada · \$55.00 en Cajita Nu",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun ignoresPlataLoanPromotion() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "¡Ya puedes obtener tu préstamo!",
            "Recibe hasta \$100,000 con Plata Préstamo para hacer compras y transferencias",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun ignoresPlataInstallmentPromotion() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "Prueba pagos a meses",
            "Divide cualquier compra desde \$10 en 3-12 mensualidades",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun parsesRealPlataPurchase() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "Compra aprobada",
            "Pagaste \$462.00 en Restaurante Demo",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Plata Card", result?.accountName)
        assertEquals("Gasto", result?.movementType)
        assertEquals(46_200L, result?.amountCents)
    }

    @Test
    fun parsesPlataCashbackAsRefund() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "Cashback",
            "Recibiste cashback por \$133.69",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Plata Card", result?.accountName)
        assertEquals("Devolución", result?.movementType)
        assertEquals(13_369L, result?.amountCents)
    }

    @Test
    fun parsesBanamexDepositToDebitAccount() {
        val result = NotificationParser.parse(
            "com.banamex.mobile",
            "Notificación",
            "Deposito Cheques M.N. 104 monto \$200.00 el 02/11/26 12:05",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Ingreso", result?.movementType)
        assertEquals("Banamex Débito", result?.accountName)
        assertEquals(20_000L, result?.amountCents)
    }

    @Test
    fun parsesBanamexCheckingWithdrawalAsDebitAccountExpense() {
        val title = "Notificación"
        val text = "Retiro/Compra Cheques M.N. 104 monto \$200.35 el 02/11/26 12:47:00 PM. Auto. 862845"
        val result = NotificationParser.parse(
            "com.banamex.mobile",
            title,
            text,
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Gasto", result?.movementType)
        assertEquals("Banamex Débito", result?.accountName)
        assertEquals(20_035L, result?.amountCents)
        assertEquals("Banamex Débito", NotificationParser.liquidDebitNoticeAccount("com.banamex.mobile", title, text))
    }

    @Test
    fun pairsTransferAmountsWithinOnePeso() {
        assertEquals(true, NotificationParser.amountsCanPair(20_035L, 20_000L))
        assertEquals(true, NotificationParser.amountsCanPair(20_000L, 20_100L))
        assertEquals(false, NotificationParser.amountsCanPair(20_000L, 20_101L))
    }

    @Test
    fun classifiesSupportedBankPromotionAsIgnored() {
        assertEquals("Plata", NotificationParser.supportedBankName("com.plata.card"))
        assertEquals(
            "Promoción o aviso informativo",
            NotificationParser.ignoredReason("com.plata.card", "Prueba pagos a meses", "Divide cualquier compra desde \$10")
        )
    }

    @Test
    fun explainsBankAlertWithoutAmount() {
        assertEquals(
            "No se encontró un importe",
            NotificationParser.ignoredReason("com.nu.production", "Transferencia", "Tu movimiento fue procesado")
        )
    }

    @Test
    fun parsesNuOutgoingTransferToBanamexDebit() {
        val result = NotificationParser.parse(
            "com.nu.production",
            "Transferencia exitosa",
            "Enviaste \$200.00 a Cuenta Banamex.",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Transferencia", result?.movementType)
        assertEquals("Cajita Nu", result?.accountName)
        assertEquals("Banamex Débito", result?.relatedAccountName)
    }

    @Test
    fun preservesBanamexCreditCardPurchases() {
        val result = NotificationParser.parse(
            "com.banamex.mobile",
            "Compra aprobada",
            "Compra por \$327.80 en COMERCIO DEMO",
            1_700_000_000_000
        )
        assertNotNull(result)
        assertEquals("Banamex Oro", result?.accountName)
        assertEquals("Gasto", result?.movementType)
    }

    @Test
    fun ignoresGmailBankCopies() {
        val result = NotificationParser.parse(
            "com.google.android.gm",
            "Notificación Banamex",
            "Depósito a cuenta Banamex por \$200.00",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun ignoresUnknownBankAmountWithoutActivity() {
        val result = NotificationParser.parse(
            "com.plata.card",
            "Plata Card",
            "Tu línea disponible es de \$100,000.00",
            1_700_000_000_000
        )
        assertNull(result)
    }

    @Test
    fun rejectsOldAndGroupedNotifications() {
        val now = 1_700_000_000_000
        assertNull(
            NotificationParser.parse(
                "com.google.android.gm",
                "Compra",
                "Compra por \$10.00",
                now
            )
        )
        assertEquals(false, NotificationParser.shouldProcessNotification("com.plata.card", false, now - 11 * 60_000L, now))
        assertEquals(false, NotificationParser.shouldProcessNotification("com.plata.card", true, now, now))
        assertEquals(false, NotificationParser.shouldProcessNotification("org.misfinanzas.demo", false, now, now))
        assertEquals(true, NotificationParser.shouldProcessNotification("com.plata.card", false, now, now))
    }
}

