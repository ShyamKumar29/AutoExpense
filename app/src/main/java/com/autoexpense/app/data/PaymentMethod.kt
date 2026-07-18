package com.autoexpense.app.data

enum class PaymentMethod(val label: String) {
    UNKNOWN("Unknown"),
    UPI("UPI"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card"),
    NET_BANKING("Net Banking"),
    WALLET("Wallet"),
    CASH("Cash"),
    EMI("EMI"),
    GIFT_CARD("Gift Card");

    companion object {
        fun fromStored(value: String?): PaymentMethod {
            return values().firstOrNull { it.name.equals(value.orEmpty(), ignoreCase = true) } ?: UNKNOWN
        }

        fun labelFor(value: String?): String = fromStored(value).label
    }
}
