from app.summary import portfolio_summary


def row(code, phase, budget, status="ACTIVE", currency="KES"):
    return {
        "code": code,
        "phase": phase,
        "status": status,
        "budget_minor": budget,
        "currency": currency,
    }


def test_empty_portfolio():
    result = portfolio_summary([])
    assert result["active_count"] == 0
    assert result["total_budget_minor"] == 0
    assert result["by_phase"] == []
    assert result["mixed_currency"] is False


def test_counts_and_budgets_by_phase_in_lifecycle_order():
    result = portfolio_summary(
        [
            row("A", "EXECUTION", 300),
            row("B", "PROCUREMENT", 100),
            row("C", "EXECUTION", 200),
        ]
    )
    assert result["active_count"] == 3
    assert result["total_budget_minor"] == 600
    assert result["by_phase"] == [
        {"phase": "PROCUREMENT", "count": 1, "budget_minor": 100},
        {"phase": "EXECUTION", "count": 2, "budget_minor": 500},
    ]


def test_ignores_inactive_projects():
    result = portfolio_summary([row("A", "PLANNING", 100), row("B", "PLANNING", 900, "CANCELLED")])
    assert result["active_count"] == 1
    assert result["total_budget_minor"] == 100


def test_flags_other_currencies_and_leaves_them_out_of_totals():
    result = portfolio_summary(
        [row("A", "PLANNING", 100), row("B", "PLANNING", 50, currency="USD")]
    )
    assert result["active_count"] == 2
    assert result["total_budget_minor"] == 100
    assert result["mixed_currency"] is True
    assert result["by_phase"] == [{"phase": "PLANNING", "count": 2, "budget_minor": 100}]
