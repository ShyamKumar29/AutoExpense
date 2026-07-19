package com.autoexpense.app.dashboard

enum class WidgetType {
    FINANCIAL_SUMMARY,
    CASH_FLOW,
    INCOME,
    EXPENSE,
    BUDGET,
    CATEGORY_ANALYTICS,
    SPENDING_ANALYTICS,
    UPCOMING_PAYMENTS,
    RECENT_TRANSACTIONS
}

data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    val order: Int,
    val visible: Boolean = true,
    val pinned: Boolean = false,
    val title: String,
    val collapsible: Boolean = false,
    val defaultExpanded: Boolean = true
)

object DashboardWidgetDefaults {
    fun defaultWidgets(showUpcomingPayments: Boolean): List<DashboardWidget> {
        return listOf(
            DashboardWidget(
                id = "financial_summary",
                type = WidgetType.FINANCIAL_SUMMARY,
                order = 1,
                pinned = true,
                title = "Financial Overview",
                collapsible = false
            ),
            DashboardWidget(
                id = "income",
                type = WidgetType.INCOME,
                order = 2,
                title = "Income",
                collapsible = false
            ),
            DashboardWidget(
                id = "upcoming_payments",
                type = WidgetType.UPCOMING_PAYMENTS,
                order = 3,
                visible = showUpcomingPayments,
                title = "Upcoming Payments",
                collapsible = false
            ),
            DashboardWidget(
                id = "spending_analytics",
                type = WidgetType.SPENDING_ANALYTICS,
                order = 4,
                title = "Spending Analytics",
                collapsible = true,
                defaultExpanded = true
            ),
            DashboardWidget(
                id = "recent_transactions",
                type = WidgetType.RECENT_TRANSACTIONS,
                order = 5,
                title = "Recent Transactions",
                collapsible = true,
                defaultExpanded = true
            )
        ).filter { it.visible }.sortedWith(compareByDescending<DashboardWidget> { it.pinned }.thenBy { it.order })
    }
}
